package net.evarius.terranexus.phone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.evarius.terranexus.compatibility.rpvoice.PhoneFeatureProvider;
import net.evarius.terranexus.compatibility.rpvoice.RpVoiceCompatibility;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.network.phone.PhoneActionPayload;
import net.evarius.terranexus.network.phone.PhoneStatePayload;
import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneActionResult;
import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneClientState;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative session, validation and synchronization boundary for the phone app. */
public final class PhoneGuiService {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static long ticks;

    private PhoneGuiService() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PhoneActionPayload.ID, PhoneActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhoneStatePayload.ID, PhoneStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PhoneActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handle(context.player(), payload)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> VIEWERS.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % ConfigManager.phone().synchronizationIntervalTicks != 0L) return;
            PhoneFeatureProvider provider = RpVoiceCompatibility.provider();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (VIEWERS.contains(player.getUuid())) send(player, provider.snapshot(player));
            }
        });
    }

    public static void open(ServerPlayerEntity player) {
        PhoneFeatureProvider provider = RpVoiceCompatibility.provider();
        provider.execute(player, PhoneAction.ALLOCATE_NUMBER, "", "");
        PhoneSnapshot snapshot = provider.snapshot(player);
        if (!snapshot.available() || !ServerPlayNetworking.canSend(player, PhoneStatePayload.ID)) {
            player.sendMessage(Text.literal("Telefonintegration ist derzeit nicht verfügbar.")
                    .formatted(Formatting.RED), false);
            VIEWERS.remove(player.getUuid());
            PhoneScreen.open(player);
            return;
        }
        VIEWERS.add(player.getUuid());
        send(player, snapshot);
    }

    private static void handle(ServerPlayerEntity player, PhoneActionPayload payload) {
        PhoneAction action = PhoneAction.parse(payload.action());
        if (action == null) return;
        if (action == PhoneAction.CLOSE) {
            VIEWERS.remove(player.getUuid());
            return;
        }
        if (action == PhoneAction.RETURN_TO_APPS) {
            VIEWERS.remove(player.getUuid());
            PhoneScreen.open(player);
            return;
        }
        if (!VIEWERS.contains(player.getUuid())) return;
        PhoneFeatureProvider provider = RpVoiceCompatibility.provider();
        PhoneSnapshot before = provider.snapshot(player);
        if (!before.available()) {
            VIEWERS.remove(player.getUuid());
            send(player, before);
            return;
        }
        String value = normalize(payload.value(), action == PhoneAction.UPSERT_CONTACT ? 80
                : ConfigManager.phone().maximumDialLength);
        String secondaryValue = normalize(payload.secondaryValue(), ConfigManager.phone().maximumDialLength);
        if (action == PhoneAction.CALL) {
            String rejection = value.isBlank() ? "Ungültiges Anrufziel."
                    : !before.coverage() ? "Keine Netzabdeckung."
                    : before.state() != PhoneCallState.IDLE ? "Es läuft bereits ein Anruf." : "";
            if (!rejection.isBlank()) {
                send(player, withNotice(before, rejection));
                return;
            }
        } else if (isCallControl(action) && !validForState(action, before.state())) {
            send(player, before);
            return;
        }
        if ((action == PhoneAction.ANSWER || action == PhoneAction.DECLINE)
                && (!value.equals(before.callId()) || value.isBlank())) {
            send(player, before);
            return;
        }
        if (action == PhoneAction.CLEAR_ALL_HISTORIES && !"CONFIRM".equals(secondaryValue)) {
            send(player, withNotice(before, "Sicherheitsbestätigung fehlt."));
            return;
        }
        PhoneActionResult result = provider.execute(player, action, value, secondaryValue);
        PhoneSnapshot after = provider.snapshot(player);
        if (!result.notice().isBlank()) after = withNotice(after, result.notice());
        send(player, after);
    }

    private static boolean isCallControl(PhoneAction action) {
        return action == PhoneAction.ANSWER || action == PhoneAction.DECLINE
                || action == PhoneAction.HANGUP || action == PhoneAction.TOGGLE_SPEAKER;
    }

    private static boolean validForState(PhoneAction action, PhoneCallState state) {
        return switch (action) {
            case ANSWER, DECLINE -> state == PhoneCallState.INCOMING;
            case HANGUP -> state == PhoneCallState.ACTIVE || state == PhoneCallState.RINGING;
            case TOGGLE_SPEAKER -> state == PhoneCallState.ACTIVE;
            default -> false;
        };
    }

    private static void send(ServerPlayerEntity player, PhoneSnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(player, PhoneStatePayload.ID)) {
            VIEWERS.remove(player.getUuid());
            return;
        }
        PhoneClientState state = new PhoneClientState(snapshot.available(), snapshot.state(),
                snapshot.callId(), snapshot.peer(), snapshot.peerNumber(), snapshot.savedContact(),
                snapshot.number(), snapshot.speaker(), snapshot.coverage(), snapshot.notice(),
                snapshot.contacts(), snapshot.emergencyNumbers(), snapshot.history(),
                player.hasPermissionLevel(2));
        ServerPlayNetworking.send(player, new PhoneStatePayload(GSON.toJson(state)));
        if (!snapshot.available()) VIEWERS.remove(player.getUuid());
    }

    private static String normalize(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static PhoneSnapshot withNotice(PhoneSnapshot snapshot, String notice) {
        return new PhoneSnapshot(snapshot.available(), snapshot.state(), snapshot.callId(),
                snapshot.peer(), snapshot.peerNumber(), snapshot.savedContact(), snapshot.number(),
                snapshot.speaker(), snapshot.coverage(), notice, snapshot.contacts(), snapshot.emergencyNumbers(),
                snapshot.history());
    }
}
