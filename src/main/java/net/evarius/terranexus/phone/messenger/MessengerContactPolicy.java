package net.evarius.terranexus.phone.messenger;

import net.evarius.terranexus.compatibility.rpvoice.RpVoiceCompatibility;
import net.evarius.terranexus.phone.model.PhoneDirectoryContact;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Central server-side policy that binds messenger recipients to the RP-VCA address book. */
public final class MessengerContactPolicy {
    private MessengerContactPolicy() {}

    public static List<PhoneDirectoryContact> contacts(ServerPlayerEntity player) {
        if (player == null || !RpVoiceCompatibility.provider().installed()
                || !RpVoiceCompatibility.provider().healthy()) return List.of();
        return RpVoiceCompatibility.provider().messengerContacts(player);
    }

    public static Set<UUID> contactIds(ServerPlayerEntity player) {
        return contacts(player).stream().map(PhoneDirectoryContact::playerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean mayCreate(ServerPlayerEntity player, List<UUID> recipients) {
        if (recipients == null || recipients.isEmpty()) return false;
        Set<UUID> contacts = contactIds(player);
        return recipients.stream().distinct().allMatch(contacts::contains);
    }

    public static boolean maySend(ServerPlayerEntity player, MessengerConversation conversation) {
        if (player == null || conversation == null || !conversation.members().contains(player.getUuidAsString()))
            return false;
        Set<UUID> contacts = contactIds(player);
        for (String member : conversation.members()) {
            if (member.equals(player.getUuidAsString())) continue;
            try {
                if (!contacts.contains(UUID.fromString(member))) return false;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return true;
    }
}
