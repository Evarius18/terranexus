package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TimeClockState extends PersistentState {
    public record ChangeResult(boolean success, boolean onDuty, long timestamp, String message) {}

    private static final Codec<TimeClockState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, DutyRecord.CODEC).optionalFieldOf("records", Map.of()).forGetter(state -> state.records),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("duty_counts", Map.of()).forGetter(state -> state.dutyCounts),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("threshold_overrides", Map.of()).forGetter(state -> state.thresholdOverrides)
    ).apply(instance, TimeClockState::new));
    private static final PersistentStateType<TimeClockState> TYPE = new PersistentStateType<>(
            "terranexus_time_clock", TimeClockState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, DutyRecord> records;
    private final Map<String, Integer> dutyCounts;
    private final Map<String, Integer> thresholdOverrides;
    private boolean runtimeInitialized;

    public TimeClockState() { this(new HashMap<>(), new HashMap<>(), new HashMap<>()); }
    private TimeClockState(Map<String, DutyRecord> records, Map<String, Integer> dutyCounts,
                           Map<String, Integer> thresholdOverrides) {
        this.records = new HashMap<>(records);
        this.dutyCounts = new HashMap<>(dutyCounts);
        this.thresholdOverrides = new HashMap<>(thresholdOverrides);
    }

    public static TimeClockState get(MinecraftServer server) {
        TimeClockState state = server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
        state.ensureIntegrity(server);
        return state;
    }

    public synchronized DutyRecord record(String institutionId, UUID playerId) {
        return records.getOrDefault(recordKey(institutionId, playerId.toString()),
                DutyRecord.empty(institutionId, playerId.toString()));
    }

    public synchronized List<DutyRecord> records(String institutionId) {
        return records.values().stream().filter(record -> record.institutionId().equals(institutionId))
                .sorted(Comparator.comparing(DutyRecord::onDuty).reversed()
                        .thenComparing(DutyRecord::playerUuid)).toList();
    }

    public synchronized List<DutyRecord> activeRecords() {
        return records.values().stream().filter(DutyRecord::onDuty).toList();
    }

    public synchronized int onDutyCount(String institutionId) {
        return Math.max(0, dutyCounts.getOrDefault(institutionId, 0));
    }

    public synchronized ChangeResult clockIn(MinecraftServer server, String institutionId, UUID playerId, long now) {
        if (!ConfigManager.timeClock().enabled) return fail(false, now, "Die Stempeluhr ist serverseitig deaktiviert.");
        Institution institution = InstitutionState.get(server).get(institutionId);
        if (institution == null || !institution.employees().containsKey(playerId.toString()))
            return fail(false, now, "Du bist bei dieser Institution nicht beschäftigt.");
        String key = recordKey(institutionId, playerId.toString());
        DutyRecord current = records.getOrDefault(key, DutyRecord.empty(institutionId, playerId.toString()));
        if (current.onDuty()) return fail(true, current.clockedInAt(), "Du bist bereits im Dienst.");
        records.put(key, current.clockIn(now));
        recount(institutionId, server);
        markDirty();
        return new ChangeResult(true, true, now, "Erfolgreich eingestempelt.");
    }

    public synchronized ChangeResult clockOut(MinecraftServer server, String institutionId, UUID playerId, long now) {
        String key = recordKey(institutionId, playerId.toString());
        DutyRecord current = records.getOrDefault(key, DutyRecord.empty(institutionId, playerId.toString()));
        if (!current.onDuty()) return fail(false, current.lastClockOutAt(), "Du bist bereits außer Dienst.");
        records.put(key, current.clockOut(now, ConfigManager.timeClock().maximumSessionsPerEmployee));
        recount(institutionId, server);
        markDirty();
        return new ChangeResult(true, false, now, "Erfolgreich ausgestempelt.");
    }

    public synchronized void employmentEnded(MinecraftServer server, String institutionId, UUID playerId, long now) {
        String key = recordKey(institutionId, playerId.toString());
        DutyRecord current = records.get(key);
        if (current == null || !current.onDuty()) return;
        records.put(key, current.clockOut(now, ConfigManager.timeClock().maximumSessionsPerEmployee));
        recount(institutionId, server);
        markDirty();
    }

    public synchronized List<String> clockOutEverywhere(MinecraftServer server, UUID playerId, long now) {
        List<String> changed = new ArrayList<>();
        for (DutyRecord current : new ArrayList<>(records.values())) {
            if (!current.playerUuid().equals(playerId.toString()) || !current.onDuty()) continue;
            records.put(recordKey(current.institutionId(), current.playerUuid()),
                    current.clockOut(now, ConfigManager.timeClock().maximumSessionsPerEmployee));
            recount(current.institutionId(), server);
            changed.add(current.institutionId());
        }
        if (!changed.isEmpty()) markDirty();
        return List.copyOf(changed);
    }

    public synchronized int threshold(String institutionId, String ruleId, int fallback) {
        return thresholdOverrides.getOrDefault(thresholdKey(institutionId, ruleId), fallback);
    }

    public synchronized boolean setThreshold(ServerPlayerEntity actor, String institutionId, String ruleId, int value) {
        if (value < 0 || value > 10_000 || !ConfigManager.timeClock().rules.containsKey(ruleId)
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_TIME_CLOCK_SETTINGS)) return false;
        thresholdOverrides.put(thresholdKey(institutionId, ruleId), value);
        markDirty();
        return true;
    }

    public synchronized boolean resetThreshold(ServerPlayerEntity actor, String institutionId, String ruleId) {
        if (!ConfigManager.timeClock().rules.containsKey(ruleId)
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_TIME_CLOCK_SETTINGS)) return false;
        boolean changed = thresholdOverrides.remove(thresholdKey(institutionId, ruleId)) != null;
        if (changed) markDirty();
        return true;
    }

    public synchronized boolean hasThresholdOverride(String institutionId, String ruleId) {
        return thresholdOverrides.containsKey(thresholdKey(institutionId, ruleId));
    }

    private synchronized void ensureIntegrity(MinecraftServer server) {
        if (runtimeInitialized) return;
        boolean changed = false;
        long now = System.currentTimeMillis();
        InstitutionState institutions = InstitutionState.get(server);
        for (Map.Entry<String, DutyRecord> entry : new ArrayList<>(records.entrySet())) {
            DutyRecord record = entry.getValue();
            Institution institution = institutions.get(record.institutionId());
            boolean validEmployee = institution != null && institution.employees().containsKey(record.playerUuid());
            if (record.onDuty() && !validEmployee) {
                records.put(entry.getKey(), record.clockOut(now, ConfigManager.timeClock().maximumSessionsPerEmployee));
                changed = true;
            }
        }
        Map<String, Integer> rebuilt = rebuildCounts(server);
        if (!rebuilt.equals(dutyCounts)) { dutyCounts.clear(); dutyCounts.putAll(rebuilt); changed = true; }
        if (thresholdOverrides.entrySet().removeIf(entry -> entry.getValue() < 0 || entry.getValue() > 10_000)) changed = true;
        runtimeInitialized = true;
        if (changed) markDirty();
    }

    private void recount(String institutionId, MinecraftServer server) {
        Institution institution = InstitutionState.get(server).get(institutionId);
        if (institution == null) { dutyCounts.remove(institutionId); return; }
        int count = 0;
        for (DutyRecord record : records.values())
            if (record.institutionId().equals(institutionId) && record.onDuty()
                    && institution.employees().containsKey(record.playerUuid())) count++;
        if (count == 0) dutyCounts.remove(institutionId); else dutyCounts.put(institutionId, count);
    }

    private Map<String, Integer> rebuildCounts(MinecraftServer server) {
        HashMap<String, Integer> rebuilt = new HashMap<>();
        InstitutionState institutions = InstitutionState.get(server);
        for (DutyRecord record : records.values()) {
            Institution institution = institutions.get(record.institutionId());
            if (record.onDuty() && institution != null && institution.employees().containsKey(record.playerUuid()))
                rebuilt.merge(record.institutionId(), 1, Integer::sum);
        }
        return rebuilt;
    }

    private static ChangeResult fail(boolean onDuty, long timestamp, String message) {
        return new ChangeResult(false, onDuty, timestamp, message);
    }
    private static String recordKey(String institutionId, String playerId) { return institutionId + '|' + playerId; }
    private static String thresholdKey(String institutionId, String ruleId) { return institutionId + '|' + ruleId; }
}
