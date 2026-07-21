package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandAccess;
import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandProtectionMixin {
    @Redirect(method = "onLandedUpon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FarmlandBlock;setToDirt(Lnet/minecraft/entity/Entity;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
    private void terranexus$preventUnauthorizedTrampling(Entity entity, BlockState state, World world, BlockPos pos) {
        if (!ConfigManager.claims().protectFarmland || !LandlordProtection.environmentProtected(world, pos)
                || entity instanceof ServerPlayerEntity player && LandlordProtection.isAllowed(player, pos, LandAccess.BUILD)) {
            FarmlandBlock.setToDirt(entity, state, world, pos);
        }
    }
}
