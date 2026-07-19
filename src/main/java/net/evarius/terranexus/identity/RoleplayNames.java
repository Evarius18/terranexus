package net.evarius.terranexus.identity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class RoleplayNames {
    private RoleplayNames() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.player));
    }

    public static void apply(ServerPlayerEntity player) {
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(player.getUuid());
        player.setCustomName(identity == null ? null : Text.literal(identity.firstName() + " " + identity.lastName()));
    }

    public static Text displayName(ServerPlayerEntity player) {
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(player.getUuid());
        return identity == null ? Text.literal("Unregistrierter Bürger")
                : Text.literal(identity.firstName() + " " + identity.lastName());
    }
}
