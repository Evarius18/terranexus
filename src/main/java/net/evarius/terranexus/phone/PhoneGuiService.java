package net.evarius.terranexus.phone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.evarius.terranexus.compatibility.rpvoice.PhoneFeatureProvider;
import net.evarius.terranexus.compatibility.rpvoice.RpVoiceCompatibility;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.network.phone.PhoneActionPayload;
import net.evarius.terranexus.network.phone.PhoneStatePayload;
import net.evarius.terranexus.phone.history.PhoneHistoryService;
import net.evarius.terranexus.phone.history.PhoneHistoryState;
import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneClientState;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
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
                (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            VIEWERS.remove(handler.player.getUuid());
            PhoneHistoryService.remove(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % ConfigManager.phone().synchronizationIntervalTicks != 0L) return;
            PhoneFeatureProvider provider = RpVoiceCompatibility.provider();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PhoneSnapshot snapshot = provider.snapshot(player);
                if (snapshot.available()) PhoneHistoryService.observe(player, snapshot);
                if (VIEWERS.contains(player.getUuid())) send(player, snapshot);
            }
        });
    }

    public static void open(ServerPlayerEntity player) {
        PhoneSnapshot snapshot = RpVoiceCompatibility.provider().snapshot(player);
        if (!snapshot.available() || !ServerPlayNetworking.canSend(player, PhoneStatePayload.ID)) {
            player.sendMessage(Text.literal("Telefonintegration ist derzeit nicht verfügbar.")
                    .formatted(Formatting.RED), false);
            VIEWERS.remove(player.getUuid());
            PhoneScreen.open(player);
            return;
        }
        VIEWERS.add(player.getUuid());
        PhoneHistoryService.observe(player, snapshot);
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
        String value = normalize(payload.value(), ConfigManager.phone().maximumDialLength);
        if (action == PhoneAction.CALL) {
            if (value.isBlank() || !before.coverage() || before.state() != PhoneCallState.IDLE) {
                send(player, before);
                return;
            }
            PhoneHistoryService.noteDial(player, value);
        } else if (!validForState(action, before.state())) {
            send(player, before);
            return;
        }
        boolean accepted = provider.execute(player, action, value);
        PhoneSnapshot after = provider.snapshot(player);
        PhoneHistoryService.observe(player, after);
        if (action == PhoneAction.CALL && !accepted) PhoneHistoryService.failedDial(player, value);
        send(player, after);
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
        List<net.evarius.terranexus.phone.model.PhoneHistoryEntry> history =
                PhoneHistoryState.get(player.getServer()).entries(player.getUuid());
        PhoneClientState state = new PhoneClientState(snapshot.available(), snapshot.state(),
                snapshot.peer(), snapshot.number(), snapshot.speaker(), snapshot.coverage(), snapshot.notice(),
                snapshot.contacts(), snapshot.emergencyNumbers(), history);
        ServerPlayNetworking.send(player, new PhoneStatePayload(GSON.toJson(state)));
        if (!snapshot.available()) VIEWERS.remove(player.getUuid());
    }

    private static String normalize(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }
}
