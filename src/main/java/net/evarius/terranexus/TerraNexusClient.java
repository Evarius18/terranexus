package net.evarius.terranexus;

import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.network.UndoSurveyPointPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class TerraNexusClient implements ClientModInitializer {
    private boolean attackPressed;

    @Override
    public void onInitializeClient() {
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
