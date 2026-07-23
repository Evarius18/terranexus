package net.evarius.terranexus.institution;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.config.TimeClockRuleConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TimeClockService {
    public record RuleStatus(String ruleId, String label, String description, int onDuty, int threshold,
                             String comparison, boolean satisfied, boolean overridden, boolean warningEnabled) {}

    private TimeClockService() {}

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!ConfigManager.timeClock().clockOutOnDisconnect) return;
            TimeClockState.get(server).clockOutEverywhere(server, handler.player.getUuid(), System.currentTimeMillis());
        });
    }

    public static TimeClockState.ChangeResult clockIn(ServerPlayerEntity player, String institutionId) {
        TimeClockState.ChangeResult result = TimeClockState.get(player.getServer())
                .clockIn(player.getServer(), institutionId, player.getUuid(), System.currentTimeMillis());
        if (result.success()) notifyChange(player.getServer(), institutionId, player, true);
        return result;
    }

    public static TimeClockState.ChangeResult clockOut(ServerPlayerEntity player, String institutionId) {
        TimeClockState.ChangeResult result = TimeClockState.get(player.getServer())
                .clockOut(player.getServer(), institutionId, player.getUuid(), System.currentTimeMillis());
        if (result.success()) notifyChange(player.getServer(), institutionId, player, false);
        return result;
    }

    public static List<RuleStatus> statuses(MinecraftServer server, String institutionId) {
        Institution institution = InstitutionState.get(server).get(institutionId);
        if (institution == null) return List.of();
        TimeClockState state = TimeClockState.get(server);
        int onDuty = state.onDutyCount(institutionId);
        List<RuleStatus> result = new ArrayList<>();
        ConfigManager.timeClock().rules.forEach((ruleId, rule) -> {
            if (!applies(institution.type(), rule)) return;
            int threshold = state.threshold(institutionId, ruleId, rule.defaultThreshold);
            result.add(new RuleStatus(ruleId, rule.label, rule.description, onDuty, threshold,
                    rule.comparison, compare(onDuty, threshold, rule.comparison),
                    state.hasThresholdOverride(institutionId, ruleId), rule.warnWhenUnsatisfied));
        });
        return List.copyOf(result);
    }

    public static boolean ruleSatisfied(MinecraftServer server, String institutionId, String ruleId) {
        return statuses(server, institutionId).stream().filter(status -> status.ruleId().equals(ruleId))
                .findFirst().map(RuleStatus::satisfied).orElse(false);
    }

    public static int globalOnDutyForRule(MinecraftServer server, String ruleId) {
        TimeClockRuleConfig rule = ConfigManager.timeClock().rules.get(ruleId);
        if (rule == null) return 0;
        TimeClockState state = TimeClockState.get(server);
        int count = 0;
        for (Institution institution : InstitutionState.get(server).all())
            if (applies(institution.type(), rule)) count += state.onDutyCount(institution.id());
        return count;
    }

    public static void tick(MinecraftServer server, long ticks) {
        var config = ConfigManager.timeClock();
        if (!config.enabled || !config.showDutyActionbar || ticks % config.statusRefreshTicks != 0) return;
        Map<UUID, List<String>> messages = new HashMap<>();
        TimeClockState state = TimeClockState.get(server);
        for (DutyRecord record : state.activeRecords()) {
            ServerPlayerEntity player;
            try { player = server.getPlayerManager().getPlayer(UUID.fromString(record.playerUuid())); }
            catch (IllegalArgumentException ignored) { continue; }
            if (player == null) continue;
            Institution institution = InstitutionState.get(server).get(record.institutionId());
            if (institution == null) continue;
            String warning = statuses(server, institution.id()).stream()
                    .filter(status -> status.warningEnabled() && !status.satisfied())
                    .findFirst().map(status -> " · ⚠ " + status.label()).orElse("");
            messages.computeIfAbsent(player.getUuid(), ignored -> new ArrayList<>()).add(
                    institution.name() + ": Im Dienst · " + state.onDutyCount(institution.id()) + " aktiv" + warning);
        }
        messages.forEach((id, lines) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) player.sendMessage(Text.literal(String.join(" | ", lines)).formatted(Formatting.GREEN), true);
        });
    }

    public static String comparisonLabel(String comparison, int threshold) {
        return switch (comparison) {
            case "MORE_THAN" -> "mehr als " + threshold;
            case "AT_MOST" -> "höchstens " + threshold;
            case "LESS_THAN" -> "weniger als " + threshold;
            default -> "mindestens " + threshold;
        };
    }

    private static boolean applies(String institutionType, TimeClockRuleConfig rule) {
        if (rule.institutionTypeKeywords == null || rule.institutionTypeKeywords.isEmpty()) return true;
        String type = institutionType.toLowerCase(Locale.ROOT);
        return rule.institutionTypeKeywords.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(type::contains);
    }

    private static boolean compare(int value, int threshold, String comparison) {
        return switch (comparison) {
            case "MORE_THAN" -> value > threshold;
            case "AT_MOST" -> value <= threshold;
            case "LESS_THAN" -> value < threshold;
            default -> value >= threshold;
        };
    }

    private static void notifyChange(MinecraftServer server, String institutionId, ServerPlayerEntity actor, boolean onDuty) {
        Institution institution = InstitutionState.get(server).get(institutionId);
        if (institution == null) return;
        int count = TimeClockState.get(server).onDutyCount(institutionId);
        String actorName = actor.getCustomName() == null ? actor.getName().getString() : actor.getCustomName().getString();
        for (String playerId : institution.employees().keySet()) {
            ServerPlayerEntity recipient;
            try { recipient = server.getPlayerManager().getPlayer(UUID.fromString(playerId)); }
            catch (IllegalArgumentException ignored) { continue; }
            if (recipient == null || recipient != actor
                    && !InstitutionAccess.has(recipient, institutionId, InstitutionPermission.VIEW_TIME_CLOCK)) continue;
            recipient.sendMessage(Text.literal(actorName + (onDuty ? " ist jetzt im Dienst" : " ist jetzt außer Dienst")
                    + " · " + count + " Mitarbeiter im Dienst").formatted(onDuty ? Formatting.GREEN : Formatting.YELLOW), true);
        }
    }
}
