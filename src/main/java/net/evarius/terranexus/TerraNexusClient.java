package net.evarius.terranexus;

import net.evarius.terranexus.client.gui.TerraNexusMenuScreen;
import net.evarius.terranexus.client.gui.TerraNexusSearchScreen;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.network.gui.CloseGuiPayload;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.evarius.terranexus.network.gui.OpenSearchPayload;
import net.evarius.terranexus.network.gui.SearchStatusPayload;
import net.evarius.terranexus.network.UndoSurveyPointPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class TerraNexusClient implements ClientModInitializer {
    private boolean attackPressed;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenGuiPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof TerraNexusSearchScreen search)
                        search.finishFromServer();
                    if (context.client().currentScreen instanceof TerraNexusMenuScreen screen
                            && screen.belongsTo(payload.sessionToken())) screen.update(payload);
                    else context.client().setScreen(new TerraNexusMenuScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(OpenSearchPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new TerraNexusSearchScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(SearchStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof TerraNexusSearchScreen search
                            && search.belongsTo(payload.token())) search.updateStatus(payload);
                }));
        ClientPlayNetworking.registerGlobalReceiver(CloseGuiPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof TerraNexusMenuScreen screen
                            && screen.belongsTo(payload.sessionToken())) screen.closeFromServer();
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = client.options.attackKey.isPressed();
            if (pressed && !attackPressed && client.player != null && client.currentScreen == null
                    && (client.player.getMainHandStack().isOf(ModItems.LAND_SURVEY_TOOL)
                    || client.player.getOffHandStack().isOf(ModItems.LAND_SURVEY_TOOL))
                    && ClientPlayNetworking.canSend(UndoSurveyPointPayload.ID)) {
                ClientPlayNetworking.send(UndoSurveyPointPayload.INSTANCE);
            }
            attackPressed = pressed;
        });
    }
}
