package net.evarius.terranexus.management;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.logging.AuditLogger;
import net.evarius.terranexus.network.gui.OpenSearchPayload;
import net.evarius.terranexus.network.gui.SearchActionPayload;
import net.evarius.terranexus.network.gui.SearchStatusPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class CustomSearchService {
    private static final int ABSOLUTE_MAXIMUM_LENGTH = 80;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private CustomSearchService() { }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenSearchPayload.ID, OpenSearchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchStatusPayload.ID, SearchStatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchActionPayload.ID, SearchActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SearchActionPayload.ID,
                (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
    }

    public static void open(ServerPlayerEntity player, String title, String placeholder, String initialValue,
                            int minimumLength, int maximumLength, Consumer<String> submit, Runnable cancel) {
        if (!ServerPlayNetworking.canSend(player, OpenSearchPayload.ID)) return;
        int maximum = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_LENGTH, maximumLength));
        int minimum = Math.max(0, Math.min(maximum, minimumLength));
        String token = UUID.randomUUID().toString();
        String initial = trim(initialValue, maximum);
        SESSIONS.put(player.getUuid(), new Session(token, minimum, maximum, submit, cancel));
        ServerPlayNetworking.send(player, new OpenSearchPayload(token, trim(title, 160),
                trim(placeholder, 256), initial, minimum, maximum));
    }

    private static void handle(ServerPlayerEntity player, SearchActionPayload payload) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || !session.token.equals(payload.token())) {
            AuditLogger.denied(player, "custom_search", "invalid_session");
            return;
        }
        if (payload.action().equals("CANCEL")) {
            SESSIONS.remove(player.getUuid());
            player.getServer().execute(session.cancel);
            return;
        }
        if (!payload.action().equals("SUBMIT")) {
            AuditLogger.denied(player, "custom_search", "invalid_action");
            return;
        }

        String query = payload.query() == null ? "" : payload.query().trim();
        if (query.length() < session.minimumLength || query.length() > session.maximumLength) {
            String message = query.length() < session.minimumLength
                    ? "Der Suchbegriff muss mindestens " + session.minimumLength + " Zeichen enthalten."
                    : "Der Suchbegriff ist zu lang.";
            ServerPlayNetworking.send(player, new SearchStatusPayload(session.token, "ERROR", message));
            return;
        }

        SESSIONS.remove(player.getUuid());
        player.getServer().execute(() -> {
            try { session.submit.accept(query); }
            catch (RuntimeException exception) {
                TerraNexus.LOGGER.error("TerraNexus-Suche fehlgeschlagen: Spieler={} Titel={}",
                        player.getUuidAsString(), payload.token(), exception);
                if (!player.isDisconnected()) {
                    SESSIONS.put(player.getUuid(), session);
                    ServerPlayNetworking.send(player, new SearchStatusPayload(session.token, "ERROR",
                            "Die Suche konnte serverseitig nicht ausgeführt werden."));
                }
            }
        });
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private record Session(String token, int minimumLength, int maximumLength,
                           Consumer<String> submit, Runnable cancel) { }
}
