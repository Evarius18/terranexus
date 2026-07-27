package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.TimeClockService;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireProtectionMixin {
    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void terranexus$protectClaimedBlocks(World world, BlockPos pos, int spreadFactor,
                                                  Random random, int currentAge, CallbackInfo info) {
        if (world instanceof ServerWorld serverWorld
                && !TimeClockService.gameplayRuleActive(serverWorld.getServer(), "fire")) {
            info.cancel();
            return;
        }
        if (ConfigManager.claims().protectFire && LandlordProtection.environmentProtected(world, pos)) info.cancel();
    }

    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean terranexus$preventNewFireInsideClaim(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        if (state.isOf(Blocks.FIRE) && world.getBlockState(pos).isAir()
                && !TimeClockService.gameplayRuleActive(world.getServer(), "fire")) return false;
        if (ConfigManager.claims().protectFire && state.isOf(Blocks.FIRE) && world.getBlockState(pos).isAir()
                && LandlordProtection.environmentProtected(world, pos)) return false;
        return world.setBlockState(pos, state, flags);
    }
}
