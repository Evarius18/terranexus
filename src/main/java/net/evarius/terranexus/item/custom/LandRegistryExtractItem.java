package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.management.LandRegistryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class LandRegistryExtractItem extends Item {
    public LandRegistryExtractItem(Settings settings){super(settings);}
    @Override public ActionResult use(World world,PlayerEntity user,Hand hand){if(world instanceof ServerWorld&&user instanceof ServerPlayerEntity player){if(IdentityState.get(player.getServer()).get(player.getUuid())==null)player.sendMessage(Text.literal("Der Grundbuchauszug ist keiner Bürgerakte zugeordnet.").formatted(Formatting.RED),false);else LandRegistryScreen.open(player);}return ActionResult.SUCCESS;}
}
