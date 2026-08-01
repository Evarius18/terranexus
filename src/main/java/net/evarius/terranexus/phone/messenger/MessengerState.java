package net.evarius.terranexus.phone.messenger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persisted only for the configured retention window; expired message bodies are physically removed. */
public final class MessengerState extends PersistentState {
    private static final Codec<MessengerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MessengerConversation.CODEC.listOf().optionalFieldOf("conversations", List.of()).forGetter(state -> List.copyOf(state.conversations.values()))
    ).apply(instance, MessengerState::new));
    private static final PersistentStateType<MessengerState> TYPE = new PersistentStateType<>(
            "terranexus_messenger", MessengerState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, MessengerConversation> conversations = new HashMap<>();
    public MessengerState() {}
    private MessengerState(List<MessengerConversation> values) { values.forEach(value -> conversations.put(value.id(), normalize(value))); }
    public static MessengerState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }

    public synchronized List<MessengerConversation> forMember(UUID player, long now) {
        purgeExpired(now);
        String id = player.toString();
        return conversations.values().stream().filter(value -> value.members().contains(id))
                .sorted(Comparator.comparingLong(MessengerState::lastActivity).reversed()).toList();
    }
    public synchronized MessengerConversation getForMember(String conversationId, UUID player, long now) {
        purgeExpired(now);
        MessengerConversation value = conversations.get(conversationId);
        return value != null && value.members().contains(player.toString()) ? value : null;
    }
    public synchronized MessengerConversation direct(UUID first, UUID second, long now) {
        String a = first.toString(), b = second.toString();
        if (a.equals(b)) return null;
        for (MessengerConversation value : conversations.values())
            if (!value.group() && value.members().size() == 2 && value.members().contains(a) && value.members().contains(b)) return value;
        if (forMember(first, now).size() >= ConfigManager.phone().messengerMaximumConversationsPerPlayer
                || forMember(second, now).size() >= ConfigManager.phone().messengerMaximumConversationsPerPlayer) return null;
        MessengerConversation created = new MessengerConversation(UUID.randomUUID().toString(), MessengerConversation.DIRECT,
                "", a, List.of(a, b), List.of(), Map.of(), now);
        conversations.put(created.id(), created); markDirty(); return created;
    }
    public synchronized MessengerConversation createGroup(UUID owner, String name, List<UUID> selected, long now) {
        if (forMember(owner, now).size() >= ConfigManager.phone().messengerMaximumConversationsPerPlayer) return null;
        LinkedHashSet<String> members = new LinkedHashSet<>(); members.add(owner.toString());
        selected.stream().limit(ConfigManager.phone().messengerMaximumGroupMembers - 1L).map(UUID::toString).forEach(members::add);
        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2 || clean.length() > 48 || members.size() < 2
                || selected.stream().anyMatch(member -> forMember(member, now).size()
                >= ConfigManager.phone().messengerMaximumConversationsPerPlayer)) return null;
        MessengerConversation created = new MessengerConversation(UUID.randomUUID().toString(), MessengerConversation.GROUP,
                clean, owner.toString(), List.copyOf(members), List.of(), Map.of(), now);
        conversations.put(created.id(), created); markDirty(); return created;
    }
    public synchronized boolean setGroupMembers(String id, UUID actor, List<UUID> selected) {
        MessengerConversation old = conversations.get(id);
        if (old == null || !old.group() || !old.ownerId().equals(actor.toString())) return false;
        LinkedHashSet<String> members = new LinkedHashSet<>(); members.add(old.ownerId());
        selected.stream().limit(ConfigManager.phone().messengerMaximumGroupMembers - 1L).map(UUID::toString).forEach(members::add);
        conversations.put(id, new MessengerConversation(old.id(), old.type(), old.name(), old.ownerId(), List.copyOf(members),
                old.messages(), old.lastReadAt(), old.createdAt())); markDirty(); return true;
    }
    public synchronized MessengerMessage send(String conversationId, UUID sender, String body, long now) {
        MessengerConversation old = conversations.get(conversationId);
        String clean = body == null ? "" : body.trim();
        if (old == null || !old.members().contains(sender.toString()) || clean.isBlank()
                || clean.length() > ConfigManager.phone().messengerMaximumMessageLength) return null;
        long retention;
        try { retention = Math.multiplyExact((long) ConfigManager.phone().messengerRetentionHours, 3_600_000L); }
        catch (ArithmeticException ignored) { retention = Long.MAX_VALUE - now; }
        MessengerMessage message = new MessengerMessage(UUID.randomUUID().toString(), sender.toString(), clean, now, now + retention);
        List<MessengerMessage> messages = new ArrayList<>(old.messages()); messages.add(message);
        int maximum = ConfigManager.phone().messengerMaximumMessagesPerConversation;
        if (messages.size() > maximum) messages.subList(0, messages.size() - maximum).clear();
        conversations.put(old.id(), new MessengerConversation(old.id(), old.type(), old.name(), old.ownerId(), old.members(),
                List.copyOf(messages), old.lastReadAt(), old.createdAt())); markDirty(); return message;
    }
    public synchronized void markRead(String id, UUID player, long now) {
        MessengerConversation old = conversations.get(id);
        if (old == null || !old.members().contains(player.toString())) return;
        Map<String, Long> read = new HashMap<>(old.lastReadAt()); read.put(player.toString(), now);
        conversations.put(id, new MessengerConversation(old.id(), old.type(), old.name(), old.ownerId(), old.members(), old.messages(), Map.copyOf(read), old.createdAt())); markDirty();
    }
    public synchronized int unread(UUID player, long now) {
        String id = player.toString(); int count = 0;
        for (MessengerConversation value : forMember(player, now)) {
            long read = value.lastReadAt().getOrDefault(id, 0L);
            count += (int) value.messages().stream().filter(message -> !message.senderId().equals(id) && message.sentAt() > read).count();
        }
        return count;
    }
    public synchronized int purgeExpired(long now) {
        int removed = 0;
        for (MessengerConversation old : new ArrayList<>(conversations.values())) {
            List<MessengerMessage> kept = old.messages().stream().filter(message -> message.expiresAt() > now).toList();
            removed += old.messages().size() - kept.size();
            if (kept.size() != old.messages().size()) conversations.put(old.id(), new MessengerConversation(old.id(), old.type(), old.name(),
                    old.ownerId(), old.members(), kept, old.lastReadAt(), old.createdAt()));
        }
        if (removed > 0) markDirty(); return removed;
    }
    public synchronized void removeCitizen(UUID citizen) {
        String id = citizen.toString(); boolean changed = false;
        for (MessengerConversation old : new ArrayList<>(conversations.values())) {
            if (!old.members().contains(id)) continue;
            if (!old.group()) { conversations.remove(old.id()); changed = true; continue; }
            List<String> members = old.members().stream().filter(member -> !member.equals(id)).toList();
            if (members.isEmpty()) conversations.remove(old.id());
            else conversations.put(old.id(), new MessengerConversation(old.id(), old.type(), old.name(),
                    old.ownerId().equals(id) ? members.getFirst() : old.ownerId(), members,
                    old.messages().stream().filter(message -> !message.senderId().equals(id)).toList(),
                    old.lastReadAt().entrySet().stream().filter(entry -> !entry.getKey().equals(id))
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)), old.createdAt()));
            changed = true;
        }
        if (changed) markDirty();
    }
    private static MessengerConversation normalize(MessengerConversation value) {
        return new MessengerConversation(value.id(), value.type(), value.name(), value.ownerId(), List.copyOf(new LinkedHashSet<>(value.members())),
                List.copyOf(value.messages()), Map.copyOf(value.lastReadAt()), value.createdAt());
    }
    private static long lastActivity(MessengerConversation value) { return value.messages().isEmpty() ? value.createdAt() : value.messages().getLast().sentAt(); }
}
