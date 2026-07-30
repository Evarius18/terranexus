package net.evarius.terranexus.compatibility.rpvoice;

import com.evarius.rpvca.api.CallMutationResult;
import com.evarius.rpvca.api.PhoneApi;
import com.evarius.rpvca.api.PhoneStatusView;
import com.evarius.rpvca.api.RpVcaApi;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.phone.model.EmergencyNumber;
import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneActionResult;
import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneContact;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direct, compile-only adapter to RP-VCA's public API. The class is loaded only after a mod check. */
public final class RpVoicePhoneProvider implements PhoneFeatureProvider {
    static PhoneFeatureProvider create() {
        return new RpVoicePhoneProvider();
    }

    @Override public boolean installed() { return true; }
    @Override public boolean healthy() { return true; }

    @Override
    public PhoneSnapshot snapshot(ServerPlayerEntity player) {
        PhoneApi api = RpVcaApi.getPhoneService().orElse(null);
        if (api == null) return PhoneSnapshot.unavailable();
        PhoneStatusView status = api.getStatus(player);
        List<PhoneContact> contacts = api.getContacts(player).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new PhoneContact(entry.getKey(), entry.getValue())).toList();
        List<EmergencyNumber> emergency = api.getEmergencyNumbers().stream()
                .map(entry -> new EmergencyNumber(entry.displayName(), entry.number())).toList();
        List<PhoneHistoryEntry> history = api.getCallHistory(player).stream()
                .limit(ConfigManager.phone().historyLimit)
                .map(entry -> new PhoneHistoryEntry(entry.entryId().toString(), entry.startedAt(),
                        entry.remoteDisplayName(), entry.remoteNumber(), entry.direction().name(),
                        entry.status().name(), entry.durationSeconds())).toList();
        return new PhoneSnapshot(true, PhoneCallState.parse(status.state()),
                status.callId() == null ? "" : status.callId().toString(),
                status.peer(), status.peerNumber(), status.savedContact(), status.number(),
                status.speaker(), status.coverage(), status.notice(),
                contacts, emergency, history);
    }

    @Override
    public PhoneActionResult execute(ServerPlayerEntity player, PhoneAction action,
                                     String value, String secondaryValue) {
        PhoneApi api = RpVcaApi.getPhoneService().orElse(null);
        if (api == null || action == null) return PhoneActionResult.rejected("Telefonservice nicht verfügbar.");
        try {
            return switch (action) {
                case ALLOCATE_NUMBER -> result(api.allocateNumber(player).successful());
                case UPSERT_CONTACT -> result(api.upsertContact(player, value, secondaryValue).successful());
                case REMOVE_CONTACT -> result(api.removeContact(player, value).successful());
                case REMOVE_HISTORY_ENTRY -> result(api.removeHistoryEntry(player, UUID.fromString(value)).successful());
                case CLEAR_OWN_HISTORY -> result(api.clearOwnCallHistory(player).successful());
                case CLEAR_HISTORY -> result(api.clearCallHistory(player, UUID.fromString(value)).successful());
                case CLEAR_ALL_HISTORIES -> result(api.clearAllCallHistories(player).successful());
                case CALL -> result(api.startCall(player, value).successful());
                case ANSWER -> result(api.acceptCall(player, UUID.fromString(value)).successful());
                case DECLINE -> result(api.declineCall(player, UUID.fromString(value)).successful());
                case HANGUP -> result(api.hangup(player));
                case TOGGLE_SPEAKER -> {
                    api.toggleSpeaker(player);
                    yield PhoneActionResult.accepted("");
                }
                default -> PhoneActionResult.rejected("Ungültige Telefonaktion.");
            };
        } catch (IllegalArgumentException exception) {
            return PhoneActionResult.rejected("Ungültige Anfrage.");
        }
    }

    private static PhoneActionResult result(boolean successful) {
        return successful ? PhoneActionResult.accepted("")
                : PhoneActionResult.rejected("Aktion wurde vom Telefonservice abgelehnt.");
    }
}
