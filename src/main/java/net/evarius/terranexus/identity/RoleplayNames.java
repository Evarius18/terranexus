package net.evarius.terranexus.identity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.TabListService;
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
        TabListService.refreshPlayerName(player);
    }

    public static Text displayName(ServerPlayerEntity player) {
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(player.getUuid());
        return identity == null ? Text.literal("Unregistrierter Bürger")
                : Text.literal(identity.firstName() + " " + identity.lastName());
    }

    public static Text playerListName(ServerPlayerEntity player) {
        if (player.getServer() == null)
            return Text.literal(ConfigManager.general().anonymousPlayerListName);
        IdentityState identities = IdentityState.get(player.getServer());
        CitizenIdentity identity = identities.get(player.getUuid());
        if (identity != null && identities.isApproved(player.getUuid()))
            return Text.literal(identity.firstName() + " " + identity.lastName());
        return Text.literal(ConfigManager.general().anonymousPlayerListName + "-"
                + player.getUuidAsString().substring(0, 4).toUpperCase(java.util.Locale.ROOT));
    }
}
