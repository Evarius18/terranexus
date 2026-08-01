package net.evarius.terranexus.phone.messenger;

import net.evarius.terranexus.config.ConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class MessengerService {
    private static long ticks;
    private MessengerService() {}
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            int unread = MessengerState.get(server).unread(handler.player.getUuid(), System.currentTimeMillis());
            if (unread > 0) handler.player.sendMessage(Text.translatable("message.terranexus.messenger.unread", unread)
                    .formatted(Formatting.AQUA), false);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % ConfigManager.phone().messengerCleanupIntervalTicks == 0)
                MessengerState.get(server).purgeExpired(System.currentTimeMillis());
        });
    }

    static MessengerConversation openDirect(ServerPlayerEntity player, UUID recipient, long now) {
        if (!MessengerContactPolicy.mayCreate(player, List.of(recipient))) return null;
        return MessengerState.get(player.getServer()).direct(player.getUuid(), recipient, now);
    }

    static MessengerConversation createGroup(ServerPlayerEntity player, String name,
                                              List<UUID> recipients, long now) {
        if (!MessengerContactPolicy.mayCreate(player, recipients)) return null;
        return MessengerState.get(player.getServer()).createGroup(player.getUuid(), name, recipients, now);
    }

    static boolean setGroupMembers(ServerPlayerEntity player, String conversationId, List<UUID> recipients) {
        MessengerConversation current = MessengerState.get(player.getServer())
                .getForMember(conversationId, player.getUuid(), System.currentTimeMillis());
        if (current == null) return false;
        List<UUID> additions = recipients.stream().filter(recipient -> !current.members().contains(recipient.toString())).toList();
        if (!additions.isEmpty() && !MessengerContactPolicy.mayCreate(player, additions)) return false;
        return MessengerState.get(player.getServer()).setGroupMembers(conversationId, player.getUuid(), recipients);
    }

    static MessengerMessage send(ServerPlayerEntity player, String conversationId, String body, long now) {
        MessengerState state = MessengerState.get(player.getServer());
        MessengerConversation conversation = state.getForMember(conversationId, player.getUuid(), now);
        if (!MessengerContactPolicy.maySend(player, conversation)) return null;
        return state.send(conversationId, player.getUuid(), body, now);
    }
    static void notifyRecipients(ServerPlayerEntity sender, MessengerConversation conversation) {
        for (String member : conversation.members()) {
            if (member.equals(sender.getUuidAsString())) continue;
            try {
                ServerPlayerEntity online = sender.getServer().getPlayerManager().getPlayer(java.util.UUID.fromString(member));
                if (online != null) online.sendMessage(Text.translatable("message.terranexus.messenger.received", MessengerScreen.name(sender.getServer(), sender.getUuid()))
                        .formatted(Formatting.AQUA), false);
            } catch (IllegalArgumentException ignored) { }
        }
    }
}
