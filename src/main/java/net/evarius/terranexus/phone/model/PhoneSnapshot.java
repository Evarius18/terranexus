package net.evarius.terranexus.phone.model;

import java.util.List;

public record PhoneSnapshot(boolean available, PhoneCallState state, String peer, String number,
                            boolean speaker, boolean coverage, String notice,
                            List<PhoneContact> contacts, List<EmergencyNumber> emergencyNumbers) {
    public PhoneSnapshot {
        state = state == null ? PhoneCallState.IDLE : state;
        peer = safe(peer);
        number = safe(number);
        notice = safe(notice);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        emergencyNumbers = emergencyNumbers == null ? List.of() : List.copyOf(emergencyNumbers);
    }

    public static PhoneSnapshot unavailable() {
        return new PhoneSnapshot(false, PhoneCallState.IDLE, "", "", false, false,
                "Telefonintegration nicht verfügbar", List.of(), List.of());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
