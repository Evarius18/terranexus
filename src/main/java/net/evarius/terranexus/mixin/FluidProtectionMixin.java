package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowableFluid.class)
public abstract class FluidProtectionMixin {
    @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
    private void terranexus$preventFlowAcrossBoundary(WorldAccess world, BlockPos target, BlockState state,
                                                       Direction direction, FluidState fluidState, CallbackInfo info) {
        if (ConfigManager.claims().protectFluids && world instanceof ServerWorld serverWorld) {
            BlockPos source = target.offset(direction.getOpposite());
            if (!LandlordProtection.remainsInSameProtectionArea(serverWorld, source, target)) info.cancel();
        }
    }
}
