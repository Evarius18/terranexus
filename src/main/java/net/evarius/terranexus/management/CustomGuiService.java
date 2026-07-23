package net.evarius.terranexus.management;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.logging.AuditLogger;
import net.evarius.terranexus.network.gui.CloseGuiPayload;
import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.evarius.terranexus.network.gui.GuiMenuElement;
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
        ServerPlayNetworking.registerGlobalReceiver(GuiActionPayload.ID,
                (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
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
                            player.getUuidAsString(), session.title, exception);
                    close(player, session.token);
                }
            }
        });
    }

    public static void open(ServerPlayerEntity player, SimpleInventory inventory,
                            Map<Integer, Consumer<PlayerEntity>> actions, Text title) {
        open(player, inventory, actions, title, null, 0);
    }

    public static void openLive(ServerPlayerEntity player, SimpleInventory inventory,
                                Map<Integer, Consumer<PlayerEntity>> actions, Text title,
                                Runnable refresh, int refreshTicks) {
        open(player, inventory, actions, title, refresh, Math.max(1, refreshTicks));
    }

    private static void open(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<PlayerEntity>> actions, Text title,
                             Runnable refresh, int refreshTicks) {
        if (!ServerPlayNetworking.canSend(player, OpenGuiPayload.ID)) {
            player.sendMessage(Text.translatable("gui.terranexus.client_required").formatted(Formatting.RED), false);
            return;
        }
        String token = UUID.randomUUID().toString();
        Session session = new Session(player.getUuid(), token, inventory, Map.copyOf(actions),
                trim(title.getString(), 160), refresh, refreshTicks, ticks + refreshTicks);
        SESSIONS.put(player.getUuid(), session);
        send(player, session);
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
            if (session != null && session.token.equals(payload.sessionToken())) SESSIONS.remove(player.getUuid());
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
                    player.getUuidAsString(), payload.elementId(), session.title, exception);
        }
        if (!SESSIONS.containsKey(player.getUuid())) close(player, session.token);
    }

    private static void send(ServerPlayerEntity player, Session session) {
        ServerPlayNetworking.send(player, new OpenGuiPayload(session.token, session.title,
                elements(session.inventory, session.actions)));
    }

    private static void close(ServerPlayerEntity player, String token) {
        Session current = SESSIONS.get(player.getUuid());
        if (current != null && current.token.equals(token)) SESSIONS.remove(player.getUuid());
        if (ServerPlayNetworking.canSend(player, CloseGuiPayload.ID))
            ServerPlayNetworking.send(player, new CloseGuiPayload(token));
    }

    private static List<GuiMenuElement> elements(SimpleInventory inventory,
                                                  Map<Integer, Consumer<PlayerEntity>> actions) {
        List<GuiMenuElement> result = new ArrayList<>();
        for (int slot = 0; slot < Math.min(54, inventory.size()); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            String tooltip = lore == null ? "" : lore.lines().stream().map(Text::getString)
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            boolean selected = stack.isOf(Items.LIME_CONCRETE) || stack.isOf(Items.LIME_STAINED_GLASS_PANE);
            result.add(new GuiMenuElement(slot, GuiIcon.fromItem(stack.getItem()).name(),
                    trim(stack.getName().getString(), 128), trim(tooltip, 512), actions.containsKey(slot), selected));
        }
        return List.copyOf(result);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static final class Session {
        private final UUID playerId;
        private final String token;
        private final SimpleInventory inventory;
        private final Map<Integer, Consumer<PlayerEntity>> actions;
        private final String title;
        private final Runnable refresh;
        private final int refreshTicks;
        private long nextRefreshTick;

        private Session(UUID playerId, String token, SimpleInventory inventory,
                        Map<Integer, Consumer<PlayerEntity>> actions, String title,
                        Runnable refresh, int refreshTicks, long nextRefreshTick) {
            this.playerId = playerId;
            this.token = token;
            this.inventory = inventory;
            this.actions = actions;
            this.title = title;
            this.refresh = refresh;
            this.refreshTicks = refreshTicks;
            this.nextRefreshTick = nextRefreshTick;
        }
    }
}
