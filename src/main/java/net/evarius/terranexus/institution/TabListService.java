package net.evarius.terranexus.institution;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.RoleplayNames;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public final class TabListService {
    private static String lastDutyOverview = "";

    private TabListService() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            refreshPlayerName(handler.player);
            sendDutyOverview(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastDutyOverview = "");
    }

    public static void tick(MinecraftServer server, long ticks) {
        if (ticks % ConfigManager.timeClock().statusRefreshTicks != 0) return;
        String current = dutyOverviewKey(server);
        if (current.equals(lastDutyOverview)) return;
        lastDutyOverview = current;
        PlayerListHeaderS2CPacket packet = dutyPacket(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            player.networkHandler.sendPacket(packet);
    }

    public static void refreshPlayerName(ServerPlayerEntity player) {
        if (player.getServer() == null) return;
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME), List.of(player));
        player.getServer().getPlayerManager().sendToAll(packet);
    }

    private static void sendDutyOverview(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(dutyPacket(player.getServer()));
    }

    private static PlayerListHeaderS2CPacket dutyPacket(MinecraftServer server) {
        if (!ConfigManager.timeClock().showDutyOverviewInPlayerList)
            return new PlayerListHeaderS2CPacket(Text.empty(), Text.empty());
        MutableText header = Text.literal("Im Dienst").formatted(Formatting.GOLD, Formatting.BOLD);
        for (String type : ConfigManager.timeClock().playerListOrganizationTypes) {
            String normalized = type.toUpperCase(Locale.ROOT);
            String label = ConfigManager.institutions().organizationTypes.getOrDefault(normalized, normalized);
            int count = TimeClockService.onlineOnDutyForOrganization(server, normalized);
            header.append(Text.literal("\n" + icon(normalized) + " " + label + ": ")
                    .formatted(color(normalized)))
                    .append(Text.literal(String.valueOf(count)).formatted(Formatting.WHITE));
        }
        return new PlayerListHeaderS2CPacket(header, Text.empty());
    }

    private static String dutyOverviewKey(MinecraftServer server) {
        if (!ConfigManager.timeClock().showDutyOverviewInPlayerList) return "disabled";
        StringBuilder key = new StringBuilder();
        for (String type : ConfigManager.timeClock().playerListOrganizationTypes)
            key.append(type).append('=').append(TimeClockService.onlineOnDutyForOrganization(server, type)).append(';');
        return key.toString();
    }

    private static String icon(String type) {
        return switch (type) {
            case "FW" -> "🚒";
            case "POL" -> "🚓";
            case "RD" -> "🚑";
            case "BAU" -> "🛠";
            case "JUSTIZ" -> "⚖";
            default -> "•";
        };
    }

    private static Formatting color(String type) {
        return switch (type) {
            case "FW" -> Formatting.RED;
            case "POL" -> Formatting.BLUE;
            case "RD" -> Formatting.AQUA;
            case "BAU" -> Formatting.YELLOW;
            case "JUSTIZ" -> Formatting.DARK_PURPLE;
            default -> Formatting.GRAY;
        };
    }
}
