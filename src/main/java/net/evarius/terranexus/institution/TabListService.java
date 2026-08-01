package net.evarius.terranexus.institution;

import net.evarius.terranexus.config.ConfigManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class TabListService {
    private static final List<String> DISPLAY_ORDER = List.of("FW", "RD", "POL", "BAU");
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
        MutableText header = Text.empty();
        List<String> types = visibleOrganizationTypes();
        for (int index = 0; index < types.size(); index++) {
            String type = types.get(index);
            int count = TimeClockService.onlineOnDutyForOrganization(server, type);
            if (index > 0) header.append(Text.literal(index % 2 == 0 ? "\n" : "    |    ")
                    .formatted(Formatting.DARK_GRAY));
            header.append(Text.literal(shortLabel(type) + ": ").formatted(color(type)))
                    .append(Text.literal(String.valueOf(count)).formatted(Formatting.WHITE));
        }
        return new PlayerListHeaderS2CPacket(header, Text.empty());
    }

    private static String dutyOverviewKey(MinecraftServer server) {
        if (!ConfigManager.timeClock().showDutyOverviewInPlayerList) return "disabled";
        StringBuilder key = new StringBuilder();
        for (String type : visibleOrganizationTypes())
            key.append(type).append('=').append(TimeClockService.onlineOnDutyForOrganization(server, type)).append(';');
        return key.toString();
    }

    private static List<String> visibleOrganizationTypes() {
        List<String> configured = ConfigManager.timeClock().playerListOrganizationTypes;
        List<String> result = new ArrayList<>(DISPLAY_ORDER.size());
        for (String type : DISPLAY_ORDER)
            if (configured.stream().anyMatch(value -> value.equalsIgnoreCase(type))) result.add(type);
        return result;
    }

    private static String shortLabel(String type) {
        return type.equals("BAU") ? "Bauamt" : type;
    }

    private static Formatting color(String type) {
        return switch (type) {
            case "FW" -> Formatting.RED;
            case "POL" -> Formatting.BLUE;
            case "RD" -> Formatting.AQUA;
            case "BAU" -> Formatting.YELLOW;
            default -> Formatting.GRAY;
        };
    }
}
