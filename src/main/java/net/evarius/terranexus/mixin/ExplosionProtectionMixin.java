package net.evarius.terranexus.mixin;

import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionProtectionMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private Entity entity;

    @ModifyVariable(method = "explode", at = @At(value = "STORE"), ordinal = 0)
    private List<BlockPos> terranexus$filterProtectedBlocks(List<BlockPos> blocks) {
        if (ConfigManager.claims().protectExplosions)
            blocks.removeIf(pos -> !LandlordProtection.explosionMayChange(world, entity, pos));
        return blocks;
    }
}
