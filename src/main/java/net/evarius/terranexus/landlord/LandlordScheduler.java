package net.evarius.terranexus.landlord;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.management.EconomyScreen;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandlordScheduler {
    private static long ticks;
    private static final Map<UUID, String> LAST_PROPERTY = new HashMap<>();
    private LandlordScheduler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            var config = ConfigManager.performance();
            var players = server.getPlayerManager().getPlayerList();
            if (ticks % config.visualRefreshTicks == 0 && !players.isEmpty()) {
                LandSelectionState selections = LandSelectionState.get(server);
                for (ServerPlayerEntity player : players) {
                    LandSelection selection = selections.get(player.getUuid());
                    String dimension = dimension(player);
                    if (selection != null && selection.dimension().equals(dimension))
                        LandVisuals.preview(player, selections.points(player.getUuid()));
                    LandMarkerService.tick(player);
                }
            }
            if (ConfigManager.claims().showPropertyEntryMessage && ticks % config.propertyEntryCheckTicks == 0)
                for (ServerPlayerEntity player : players) notifyProperty(player, dimension(player));
            if (ticks % config.maintenanceIntervalTicks == 0) {
                Set<UUID> online = new HashSet<>();
                for (ServerPlayerEntity player : players) online.add(player.getUuid());
                LAST_PROPERTY.keySet().retainAll(online);
                LandMarkerService.retainOnlineIds(online);
                PropertyDrafts.retainOnline(online);
                EconomyScreen.retainOnline(online);
                long now = System.currentTimeMillis();
                LandManagementState.get(server).processRents(server, now);
                LandManagementState.get(server).processAreaPayroll(server, now);
                InstitutionState.get(server).processPayroll(server, now);
            }
        });
    }

    private static String dimension(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().getValue().toString();
    }
    private static void notifyProperty(ServerPlayerEntity player, String dimension) {
        LandProperty property = LandlordState.get(player.getServer()).at(dimension, player.getBlockPos());
        LandManagementState management = LandManagementState.get(player.getServer());
        LandResolution resolution = management.resolve(property);
        String current = property == null ? LandManagementState.ROOT_AREA_ID : property.id();
        String previous = LAST_PROPERTY.getOrDefault(player.getUuid(), "");
        if (current.equals(previous)) return;
        LAST_PROPERTY.put(player.getUuid(), current);
        if (property != null) {
            Text message = Text.literal("Grundstück: ").formatted(Formatting.GOLD)
                    .append(Text.literal(property.name()).formatted(Formatting.WHITE));
            if (resolution.jurisdiction() != null
                    && !LandManagementState.ROOT_AREA_ID.equals(resolution.jurisdiction().id()))
                message = message.copy().append(Text.literal(" · " + resolution.jurisdiction().name()).formatted(Formatting.GRAY));
            player.sendMessage(message, true);
        }
        else player.sendMessage(Text.literal("Gebiet: ").formatted(Formatting.GRAY)
                .append(Text.literal(resolution.jurisdiction().name()).formatted(Formatting.WHITE)), true);
    }
}
