package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.landlord.LandSelection;
import net.evarius.terranexus.landlord.LandSelectionState;
import net.evarius.terranexus.landlord.LandVisuals;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public class LandSurveyToolItem extends Item {
    public LandSurveyToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player))
            return ActionResult.SUCCESS;
        if (!AuthorityState.maySurveyLand(player)) {
            message(player, "Keine Vermessungsberechtigung.", Formatting.RED);
            return ActionResult.FAIL;
        }

        LandSelectionState selections = LandSelectionState.get(player.getServer());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (selections.add(player.getUuid(), dimension, context.getBlockPos())) {
            int count = selections.points(player.getUuid()).size();
            message(player, "Vermessungspunkt " + count + " gesetzt. Linksklick nimmt den letzten Punkt zurück.", Formatting.GREEN);
            LandVisuals.preview(player, selections.points(player.getUuid()));
            return ActionResult.SUCCESS;
        }

        LandSelection selection = selections.get(player.getUuid());
        if (selection == null || !selection.dimension().equals(dimension))
            message(player, "Starte zuerst eine Freiformvermessung im Bauamt.", Formatting.RED);
        else if (selections.points(player.getUuid()).size() >= ConfigManager.claims().maximumPolygonPoints)
            message(player, "Die konfigurierte Höchstzahl an Vermessungspunkten ist erreicht.", Formatting.RED);
        else
            message(player, "Dieser Vermessungspunkt wurde bereits gesetzt.", Formatting.YELLOW);
        return ActionResult.SUCCESS;
    }

    private static void message(ServerPlayerEntity player, String message, Formatting color) {
        player.sendMessage(Text.literal(message).formatted(color), true);
    }
}
