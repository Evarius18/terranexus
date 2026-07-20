package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public abstract class DispenserProtectionMixin {
    @Inject(method = "dispense", at = @At("HEAD"), cancellable = true)
    private void terranexus$preventDispensingAcrossBoundary(ServerWorld world, BlockState state,
                                                             BlockPos pos, CallbackInfo info) {
        BlockPos target = pos.offset(state.get(DispenserBlock.FACING));
        if (ConfigManager.claims().protectAutomation && !LandlordProtection.remainsInSameProtectionArea(world, pos, target)) info.cancel();
    }
}
