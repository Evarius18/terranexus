package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.network.UndoSurveyPointPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class LandSurveyEvents {
    private LandSurveyEvents() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(UndoSurveyPointPayload.ID, UndoSurveyPointPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UndoSurveyPointPayload.ID, (payload, context) -> undo(context.player()));
    }

    private static void undo(ServerPlayerEntity player) {
        if (!player.getMainHandStack().isOf(ModItems.LAND_SURVEY_TOOL)
                && !player.getOffHandStack().isOf(ModItems.LAND_SURVEY_TOOL)) return;
        if (!AuthorityState.maySurveyLand(player)) {
            player.sendMessage(Text.literal("Keine Vermessungsberechtigung.").formatted(Formatting.RED), true);
            return;
        }
        LandSelectionState selections = LandSelectionState.get(player.getServer());
        LandSelection selection = selections.get(player.getUuid());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (selection == null || !selection.dimension().equals(dimension)) return;
        if (!selections.undo(player.getUuid())) {
            player.sendMessage(Text.literal("Es ist kein Vermessungspunkt vorhanden.").formatted(Formatting.YELLOW), true);
            return;
        }
        int remaining = selections.points(player.getUuid()).size();
        player.sendMessage(Text.literal("Letzten Vermessungspunkt entfernt · verbleibend: " + remaining)
                .formatted(Formatting.YELLOW), true);
        LandVisuals.preview(player, selections.points(player.getUuid()));
    }
}
