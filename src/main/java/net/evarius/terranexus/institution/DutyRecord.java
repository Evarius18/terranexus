package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record DutyRecord(String institutionId, String playerUuid, boolean onDuty, long clockedInAt,
                         long lastClockOutAt, long totalWorkedMillis, List<DutySession> sessions) {
    public static final Codec<DutyRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("institution_id").forGetter(DutyRecord::institutionId),
            Codec.STRING.fieldOf("player_uuid").forGetter(DutyRecord::playerUuid),
            Codec.BOOL.optionalFieldOf("on_duty", false).forGetter(DutyRecord::onDuty),
            Codec.LONG.optionalFieldOf("clocked_in_at", 0L).forGetter(DutyRecord::clockedInAt),
            Codec.LONG.optionalFieldOf("last_clock_out_at", 0L).forGetter(DutyRecord::lastClockOutAt),
            Codec.LONG.optionalFieldOf("total_worked_millis", 0L).forGetter(DutyRecord::totalWorkedMillis),
            DutySession.CODEC.listOf().optionalFieldOf("sessions", List.of()).forGetter(DutyRecord::sessions)
    ).apply(instance, DutyRecord::new));

    public static DutyRecord empty(String institutionId, String playerUuid) {
        return new DutyRecord(institutionId, playerUuid, false, 0L, 0L, 0L, List.of());
    }

    public DutyRecord clockIn(long now) {
        return new DutyRecord(institutionId, playerUuid, true, now, lastClockOutAt, totalWorkedMillis, sessions);
    }

    public DutyRecord clockOut(long now, int maximumSessions) {
        if (!onDuty) return this;
        long safeEnd = Math.max(now, clockedInAt);
        long duration = Math.max(0L, safeEnd - clockedInAt);
        long total;
        try { total = Math.addExact(totalWorkedMillis, duration); }
        catch (ArithmeticException ignored) { total = Long.MAX_VALUE; }
        ArrayList<DutySession> history = new ArrayList<>(sessions);
        history.add(new DutySession(clockedInAt, safeEnd));
        int remove = Math.max(0, history.size() - Math.max(1, maximumSessions));
        if (remove > 0) history.subList(0, remove).clear();
        return new DutyRecord(institutionId, playerUuid, false, 0L, safeEnd, total, List.copyOf(history));
    }

    public long workedMillis(long now) {
        if (!onDuty) return totalWorkedMillis;
        long current = Math.max(0L, now - clockedInAt);
        try { return Math.addExact(totalWorkedMillis, current); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
}
