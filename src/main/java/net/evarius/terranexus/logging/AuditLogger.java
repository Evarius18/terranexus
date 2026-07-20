package net.evarius.terranexus.logging;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AuditLogger {
    private AuditLogger() {}
    public static void denied(ServerPlayerEntity player, String module, String action) {
        if (ConfigManager.logging().logDeniedActions)
            TerraNexus.LOGGER.warn("Zugriff verweigert: Spieler={} UUID={} Modul={} Aktion={}",
                    player.getName().getString(), player.getUuidAsString(), module, action);
    }
    public static void debug(String message, Object... arguments) {
        if (ConfigManager.logging().debugMode) TerraNexus.LOGGER.info("[DEBUG] " + message, arguments);
    }
}
