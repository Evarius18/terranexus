package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.management.PropertyScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class BuildingAuthorityTabletItem extends Item {
    public BuildingAuthorityTabletItem(Settings settings){super(settings);}
    @Override public ActionResult use(World world, PlayerEntity user, Hand hand){if(world instanceof ServerWorld&&user instanceof ServerPlayerEntity player){if(!AuthorityState.mayUseLandOffice(player)){player.sendMessage(Text.literal("Zugriff verweigert: Dieses Gerät ist ausschließlich für Bauamtsmitarbeiter freigeschaltet.").formatted(Formatting.RED),false);}else PropertyScreen.open(player);}return ActionResult.SUCCESS;}
}
