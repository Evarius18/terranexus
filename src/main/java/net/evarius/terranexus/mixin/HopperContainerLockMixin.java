package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.ContainerAccessService;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperContainerLockMixin {
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private static void terranexus$preventInsertIntoLockedContainer(World world, BlockPos pos,
                                                                    HopperBlockEntity hopper,
                                                                    CallbackInfoReturnable<Boolean> result) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        BlockPos target = pos.offset(world.getBlockState(pos).get(HopperBlock.FACING));
        if (ContainerAccessService.isLocked(serverWorld, target)) result.setReturnValue(false);
    }

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void terranexus$preventExtractFromLockedContainer(World world, Hopper hopper,
                                                                     CallbackInfoReturnable<Boolean> result) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        BlockPos target = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ContainerAccessService.isLocked(serverWorld, target)) result.setReturnValue(false);
    }
}
