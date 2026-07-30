package net.evarius.terranexus.phone.model;

/** Immutable transport view mapped from RP-VCA's public CallHistoryEntryView. */
public record PhoneHistoryEntry(String id, long timestamp, String peer, String number, String direction,
                                String outcome, long durationSeconds) {
    public PhoneHistoryEntry {
        id = safe(id);
        peer = safe(peer);
        number = safe(number);
        direction = safe(direction);
        outcome = safe(outcome);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
