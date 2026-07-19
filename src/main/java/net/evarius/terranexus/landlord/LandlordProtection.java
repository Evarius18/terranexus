package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.institution.InstitutionState;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class LandlordProtection {
    private LandlordProtection() {}
    public static void register(){PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!(player instanceof ServerPlayerEntity serverPlayer)||allowed(serverPlayer,pos,LandAccess.BUILD));UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(world.isClient()||!(player instanceof ServerPlayerEntity serverPlayer))return ActionResult.PASS;boolean placement=player.getStackInHand(hand).getItem() instanceof BlockItem;BlockPos pos=placement?hit.getBlockPos().offset(hit.getSide()):hit.getBlockPos();String permission=placement?LandAccess.BUILD:(isRedstone(world.getBlockState(hit.getBlockPos()).getBlock())?LandAccess.REDSTONE:(world.getBlockEntity(hit.getBlockPos())!=null?LandAccess.CONTAINERS:LandAccess.INTERACT));return allowed(serverPlayer,pos,permission)?ActionResult.PASS:ActionResult.FAIL;});}
    private static boolean isRedstone(net.minecraft.block.Block block){return block==Blocks.LEVER||block==Blocks.STONE_BUTTON||block==Blocks.OAK_BUTTON||block==Blocks.REPEATER||block==Blocks.COMPARATOR||block==Blocks.REDSTONE_WIRE||block==Blocks.DAYLIGHT_DETECTOR;}
    private static boolean allowed(ServerPlayerEntity player,BlockPos pos,String permission){if(player.hasPermissionLevel(2)||AuthorityState.mayManageLand(player))return true;String dimension=player.getWorld().getRegistryKey().getValue().toString();LandProperty property=LandlordState.get(player.getServer()).at(dimension,pos);if(property==null||property.isOwnedBy(player.getUuid()))return true;if(property.ownerType().equals("institution")&&InstitutionState.get(player.getServer()).mayManage(property.ownerId(),player.getUuid()))return true;LandManagementState management=LandManagementState.get(player.getServer());if(management.isTenant(property.id(),player.getUuid())||management.access(property.id()).permits(player.getUuidAsString(),permission))return true;player.sendMessage(Text.literal("Keine Berechtigung für „"+property.name()+"“.").formatted(Formatting.RED),true);return false;}
}
