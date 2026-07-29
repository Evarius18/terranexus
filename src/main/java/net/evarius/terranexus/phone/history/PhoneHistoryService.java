package net.evarius.terranexus.phone.history;

import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneContact;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Derives trusted history from public server-side call-state transitions. */
public final class PhoneHistoryService {
    private static final Map<UUID, TrackedCall> TRACKED = new HashMap<>();

    private PhoneHistoryService() {}

    public static void noteDial(ServerPlayerEntity player, String destination) {
        TrackedCall current = TRACKED.get(player.getUuid());
        if (current == null || current.state == PhoneCallState.IDLE)
            TRACKED.put(player.getUuid(), new TrackedCall(PhoneCallState.IDLE, "OUTGOING",
                    destination, destination, System.currentTimeMillis(), 0L));
    }

    public static void observe(ServerPlayerEntity player, PhoneSnapshot snapshot) {
        if (!snapshot.available()) return;
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();
        TrackedCall previous = TRACKED.get(playerId);
        if (previous == null) {
            TRACKED.put(playerId, create(snapshot, now, null));
            return;
        }
        if (previous.state == snapshot.state()) {
            if (snapshot.state() == PhoneCallState.ACTIVE && previous.activeAt == 0L)
                TRACKED.put(playerId, previous.withActiveAt(now));
            return;
        }
        if (snapshot.state() == PhoneCallState.IDLE && previous.state != PhoneCallState.IDLE) {
            save(player, previous, snapshot.notice(), now);
            TRACKED.put(playerId, create(snapshot, now, null));
            return;
        }
        TRACKED.put(playerId, create(snapshot, now, previous));
    }

    public static void failedDial(ServerPlayerEntity player, String destination) {
        PhoneHistoryState.get(player.getServer()).add(player.getUuid(), new PhoneHistoryEntry(
                System.currentTimeMillis(), destination, destination, "OUTGOING", "FAILED", 0L));
        TRACKED.put(player.getUuid(), new TrackedCall(PhoneCallState.IDLE, "", "", "", 0L, 0L));
    }

    public static void remove(UUID playerId) { TRACKED.remove(playerId); }

    private static TrackedCall create(PhoneSnapshot snapshot, long now, TrackedCall previous) {
        if (snapshot.state() == PhoneCallState.IDLE)
            return new TrackedCall(PhoneCallState.IDLE, "", "", "", 0L, 0L);
        String direction = snapshot.state() == PhoneCallState.INCOMING ? "INCOMING"
                : previous != null && !previous.direction.isBlank() ? previous.direction : "OUTGOING";
        String peer = !snapshot.peer().isBlank() ? snapshot.peer() : previous == null ? "" : previous.peer;
        String number = resolveNumber(snapshot, peer);
        if (number.isBlank() && previous != null) number = previous.number;
        if (number.isBlank()) number = peer;
        long startedAt = previous != null && previous.startedAt > 0L ? previous.startedAt : now;
        long activeAt = snapshot.state() == PhoneCallState.ACTIVE
                ? previous != null && previous.activeAt > 0L ? previous.activeAt : now : 0L;
        return new TrackedCall(snapshot.state(), direction, peer, number, startedAt, activeAt);
    }

    private static String resolveNumber(PhoneSnapshot snapshot, String peer) {
        for (PhoneContact contact : snapshot.contacts())
            if (contact.name().equalsIgnoreCase(peer)) return contact.number();
        return "";
    }

    private static void save(ServerPlayerEntity player, TrackedCall call, String notice, long now) {
        String normalized = notice == null ? "" : notice.toLowerCase(Locale.ROOT);
        String outcome;
        if (call.state == PhoneCallState.ACTIVE) outcome = "ANSWERED";
        else if (normalized.contains("abgelehnt")) outcome = "REJECTED";
        else if (call.state == PhoneCallState.INCOMING) outcome = "MISSED";
        else outcome = normalized.contains("keine antwort") ? "MISSED" : "FAILED";
        long duration = call.activeAt <= 0L ? 0L : Math.max(0L, (now - call.activeAt) / 1000L);
        PhoneHistoryState.get(player.getServer()).add(player.getUuid(), new PhoneHistoryEntry(
                call.startedAt > 0L ? call.startedAt : now, call.peer, call.number,
                call.direction, outcome, duration));
    }

    private record TrackedCall(PhoneCallState state, String direction, String peer, String number,
                               long startedAt, long activeAt) {
        private TrackedCall withActiveAt(long value) {
            return new TrackedCall(state, direction, peer, number, startedAt, value);
        }
    }
}
