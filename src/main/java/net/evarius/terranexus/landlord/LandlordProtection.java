package net.evarius.terranexus.landlord;

import net.evarius.terranexus.institution.InstitutionState;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class LandlordProtection {
    private LandlordProtection() {}
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> allowed(player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player.getStackInHand(hand).getItem() instanceof BlockItem) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            return allowed(serverPlayer, hit.getBlockPos().offset(hit.getSide())) ? ActionResult.PASS : ActionResult.FAIL;
        });
    }
    private static boolean allowed(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || player.hasPermissionLevel(2)) return true;
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        LandProperty property = LandlordState.get(player.getServer()).at(dimension, pos);
        if (property == null || property.isOwnedBy(player.getUuid())) return true;
        if (property.ownerType().equals("institution") && InstitutionState.get(player.getServer()).mayManage(property.ownerId(), player.getUuid())) return true;
        player.sendMessage(Text.literal("Dieses Grundstück gehört „" + property.name() + "“.").formatted(Formatting.RED), true);
        return false;
    }
}
