package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PistonHandler.class)
public abstract class PistonProtectionMixin {
    @Shadow @Final private World world;
    @Shadow @Final private BlockPos posFrom;
    @Shadow @Final private BlockPos posTo;
    @Shadow @Final private Direction motionDirection;
    @Shadow @Final private List<BlockPos> movedBlocks;
    @Shadow @Final private List<BlockPos> brokenBlocks;

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void terranexus$preventBoundaryCrossing(CallbackInfoReturnable<Boolean> result) {
        if (!ConfigManager.claims().protectPistons || !result.getReturnValue() || world.isClient()) return;
        List<BlockPos> affected = new ArrayList<>();
        affected.add(posTo);
        affected.addAll(brokenBlocks);
        for (BlockPos moved : movedBlocks) {
            affected.add(moved);
            affected.add(moved.offset(motionDirection));
        }
        if (!LandlordProtection.remainsInSameProtectionArea(world, posFrom, affected)) result.setReturnValue(false);
    }
}
