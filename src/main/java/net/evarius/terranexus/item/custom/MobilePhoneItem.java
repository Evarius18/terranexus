package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.phone.PhoneScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class MobilePhoneItem extends Item {
    public MobilePhoneItem(Settings settings) { super(settings); }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld && user instanceof ServerPlayerEntity player)
            PhoneScreen.open(player);
        return ActionResult.SUCCESS;
    }
}
