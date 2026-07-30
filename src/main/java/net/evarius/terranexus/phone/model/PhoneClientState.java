package net.evarius.terranexus.phone.model;

import java.util.List;

public record PhoneClientState(boolean available, PhoneCallState state, String callId,
                               String peer, String peerNumber, boolean savedContact, String number,
                               boolean speaker, boolean coverage, String notice,
                               List<PhoneContact> contacts, List<EmergencyNumber> emergencyNumbers,
                               List<PhoneHistoryEntry> history, boolean historyAdministrator) {
    public PhoneClientState {
        state = state == null ? PhoneCallState.IDLE : state;
        callId = callId == null ? "" : callId;
        peer = peer == null ? "" : peer;
        peerNumber = peerNumber == null ? "" : peerNumber;
        number = number == null ? "" : number;
        notice = notice == null ? "" : notice;
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        emergencyNumbers = emergencyNumbers == null ? List.of() : List.copyOf(emergencyNumbers);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
