package net.evarius.terranexus.phone.model;

import java.util.List;

public record PhoneSnapshot(boolean available, PhoneCallState state, String callId,
                            String peer, String peerNumber, boolean savedContact, String number,
                            boolean speaker, boolean coverage, String notice,
                            List<PhoneContact> contacts, List<EmergencyNumber> emergencyNumbers,
                            List<PhoneHistoryEntry> history) {
    public PhoneSnapshot {
        state = state == null ? PhoneCallState.IDLE : state;
        callId = safe(callId);
        peer = safe(peer);
        peerNumber = safe(peerNumber);
        number = safe(number);
        notice = safe(notice);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        emergencyNumbers = emergencyNumbers == null ? List.of() : List.copyOf(emergencyNumbers);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public static PhoneSnapshot unavailable() {
        return new PhoneSnapshot(false, PhoneCallState.IDLE, "", "", "", false, "", false, false,
                "Telefonintegration nicht verfügbar", List.of(), List.of(), List.of());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
