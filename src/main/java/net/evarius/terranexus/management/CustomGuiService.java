package net.evarius.terranexus.management;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.logging.AuditLogger;
import net.evarius.terranexus.network.gui.CloseGuiPayload;
import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.evarius.terranexus.network.gui.GuiTextActionPayload;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative bridge between the existing menu builders and the custom client screen. */
public final class CustomGuiService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long ticks;

    private CustomGuiService() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenGuiPayload.ID, OpenGuiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseGuiPayload.ID, CloseGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GuiActionPayload.ID, GuiActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GuiTextActionPayload.ID, GuiTextActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GuiActionPayload.ID,
                (payload, context) -> handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(GuiTextActionPayload.ID,
                (payload, context) -> handleText(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.player.getUuid());
            runCloseHandler(session);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            for (Session session : List.copyOf(SESSIONS.values())) {
                if (session.refresh == null || ticks < session.nextRefreshTick) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerId);
                if (player == null) { SESSIONS.remove(session.playerId); continue; }
                session.nextRefreshTick = ticks + session.refreshTicks;
                try {
                    session.refresh.run();
                    send(player, session);
                } catch (RuntimeException exception) {
                    TerraNexus.LOGGER.error("Custom-GUI-Aktualisierung fehlgeschlagen: Spieler={} Titel={}",
                            player.getUuidAsString(), session.title.getString(), exception);
                    close(player, session.token);
                }
            }
        });
    }

    public static void open(ServerPlayerEntity player, SimpleInventory inventory,
                            Map<Integer, Consumer<PlayerEntity>> actions, Text title) {
        open(player, inventory, actions, title, null, 0, null);
    }

    public static void openWithTextActions(ServerPlayerEntity player, SimpleInventory inventory,
                                           Map<Integer, Consumer<PlayerEntity>> actions,
                                           Map<Integer, Consumer<String>> textActions, Text title) {
        open(player, inventory, actions, textActions, title, null, 0, null);
    }

    public static void openWithCloseHandler(ServerPlayerEntity player, SimpleInventory inventory,
                                            Map<Integer, Consumer<PlayerEntity>> actions, Text title,
                                            Runnable closeHandler) {
        open(player, inventory, actions, Map.of(), title, null, 0, closeHandler);
    }

    public static void openLive(ServerPlayerEntity player, SimpleInventory inventory,
                                Map<Integer, Consumer<PlayerEntity>> actions, Text title,
                                Runnable refresh, int refreshTicks) {
        open(player, inventory, actions, Map.of(), title, refresh, Math.max(1, refreshTicks), null);
    }

    private static void open(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<PlayerEntity>> actions, Text title,
                             Runnable refresh, int refreshTicks, Runnable closeHandler) {
        open(player, inventory, actions, Map.of(), title, refresh, refreshTicks, closeHandler);
    }

    private static void open(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<PlayerEntity>> actions, Map<Integer, Consumer<String>> textActions,
                             Text title, Runnable refresh, int refreshTicks, Runnable closeHandler) {
        if (!ServerPlayNetworking.canSend(player, OpenGuiPayload.ID)) {
            player.sendMessage(Text.translatable("gui.terranexus.client_required").formatted(Formatting.RED), false);
            return;
        }
        String token = UUID.randomUUID().toString();
        Session session = new Session(player.getUuid(), token, inventory, Map.copyOf(actions), Map.copyOf(textActions),
                title.copy(), refresh, refreshTicks, ticks + refreshTicks, closeHandler);
        Session previous = SESSIONS.put(player.getUuid(), session);
        runCloseHandler(previous);
        send(player, session);
    }

    private static void handleText(ServerPlayerEntity player, GuiTextActionPayload payload) {
        Session session = SESSIONS.get(player.getUuid());
        Consumer<String> handler = session == null ? null : session.textActions.get(payload.elementId());
        if (session == null || !session.token.equals(payload.sessionToken()) || handler == null) {
            AuditLogger.denied(player, "custom_gui", "invalid_text_action");
            return;
        }
        SESSIONS.remove(player.getUuid());
        try { handler.accept(payload.value() == null ? "" : payload.value()); }
        catch (RuntimeException exception) {
            TerraNexus.LOGGER.error("Custom-GUI-Texteingabe fehlgeschlagen: Spieler={} Element={} Titel={}",
                    player.getUuidAsString(), payload.elementId(), session.title.getString(), exception);
            runCloseHandler(session);
        }
        if (!SESSIONS.containsKey(player.getUuid())) close(player, session.token);
    }

    private static void handle(ServerPlayerEntity player, GuiActionPayload payload) {
        Session session = SESSIONS.get(player.getUuid());
        GuiAction action;
        try { action = GuiAction.valueOf(payload.action()); }
        catch (IllegalArgumentException exception) {
            AuditLogger.denied(player, "custom_gui", "invalid_action");
            return;
        }
        if (action == GuiAction.CLOSE) {
            if (session != null && session.token.equals(payload.sessionToken())) {
                SESSIONS.remove(player.getUuid());
                runCloseHandler(session);
            }
            return;
        }
        if (session == null || !session.token.equals(payload.sessionToken())) {
            AuditLogger.denied(player, "custom_gui", "invalid_session");
            return;
        }
        Consumer<PlayerEntity> handler = session.actions.get(payload.elementId());
        if (payload.elementId() < 0 || payload.elementId() >= 54 || handler == null) {
            AuditLogger.denied(player, "custom_gui", "invalid_element_" + payload.elementId());
            return;
        }

        // A displayed action is single-use. Its target screen creates a fresh token after re-validating state and rights.
        SESSIONS.remove(player.getUuid());
        try { handler.accept(player); }
        catch (RuntimeException exception) {
            TerraNexus.LOGGER.error("Custom-GUI-Aktion fehlgeschlagen: Spieler={} Element={} Titel={}",
                    player.getUuidAsString(), payload.elementId(), session.title.getString(), exception);
            runCloseHandler(session);
        }
        if (!SESSIONS.containsKey(player.getUuid())) close(player, session.token);
    }

    private static void send(ServerPlayerEntity player, Session session) {
        ServerPlayNetworking.send(player, new OpenGuiPayload(session.token, session.title,
                elements(session.inventory, session.actions, session.textActions)));
    }

    private static void close(ServerPlayerEntity player, String token) {
        Session current = SESSIONS.get(player.getUuid());
        if (current != null && current.token.equals(token)) {
            SESSIONS.remove(player.getUuid());
            runCloseHandler(current);
        }
        if (ServerPlayNetworking.canSend(player, CloseGuiPayload.ID))
            ServerPlayNetworking.send(player, new CloseGuiPayload(token));
    }

    private static List<GuiMenuElement> elements(SimpleInventory inventory,
                                                  Map<Integer, Consumer<PlayerEntity>> actions,
                                                  Map<Integer, Consumer<String>> textActions) {
        List<GuiMenuElement> result = new ArrayList<>();
        for (int slot = 0; slot < Math.min(54, inventory.size()); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            Text tooltip = lore == null ? Text.empty() : joinLines(lore.lines());
            boolean selected = stack.isOf(Items.LIME_CONCRETE) || stack.isOf(Items.LIME_STAINED_GLASS_PANE);
            result.add(new GuiMenuElement(slot, GuiIcon.fromItem(stack.getItem()).name(),
                    stack.getName().copy(), tooltip, actions.containsKey(slot) || textActions.containsKey(slot), selected));
        }
        return List.copyOf(result);
    }

    private static Text joinLines(List<Text> lines) {
        var result = Text.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) result.append("\n");
            result.append(lines.get(index));
        }
        return result;
    }

    private static void runCloseHandler(Session session) {
        if (session == null || session.closeHandler == null) return;
        try { session.closeHandler.run(); }
        catch (RuntimeException exception) {
            TerraNexus.LOGGER.error("Custom-GUI-Schließaktion fehlgeschlagen: Spieler={} Titel={}",
                    session.playerId, session.title.getString(), exception);
        }
    }

    private static final class Session {
        private final UUID playerId;
        private final String token;
        private final SimpleInventory inventory;
        private final Map<Integer, Consumer<PlayerEntity>> actions;
        private final Map<Integer, Consumer<String>> textActions;
        private final Text title;
        private final Runnable refresh;
        private final int refreshTicks;
        private final Runnable closeHandler;
        private long nextRefreshTick;

        private Session(UUID playerId, String token, SimpleInventory inventory,
                        Map<Integer, Consumer<PlayerEntity>> actions, Map<Integer, Consumer<String>> textActions, Text title,
                        Runnable refresh, int refreshTicks, long nextRefreshTick, Runnable closeHandler) {
            this.playerId = playerId;
            this.token = token;
            this.inventory = inventory;
            this.actions = actions;
            this.textActions = textActions;
            this.title = title;
            this.refresh = refresh;
            this.refreshTicks = refreshTicks;
            this.nextRefreshTick = nextRefreshTick;
            this.closeHandler = closeHandler;
        }
    }
}
