package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.logging.AuditLogger;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Ownable;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Objects;

public final class LandlordProtection {
    private LandlordProtection() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
                !(player instanceof ServerPlayerEntity serverPlayer) || check(serverPlayer, pos, LandAccess.BUILD));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            Item held = player.getStackInHand(hand).getItem();
            BlockPos target = hit.getBlockPos();
            if (isBlockMutatingItem(held)) {
                if (!check(serverPlayer, target, LandAccess.BUILD)) return ActionResult.FAIL;
                BlockPos affected = held instanceof BlockItem
                        ? new ItemPlacementContext(player, hand, player.getStackInHand(hand), hit).getBlockPos()
                        : target.offset(hit.getSide());
                if (!affected.equals(target) && !check(serverPlayer, affected, LandAccess.BUILD)) return ActionResult.FAIL;
                return ActionResult.PASS;
            }

            String permission = isRedstone(world.getBlockState(target).getBlock())
                    ? LandAccess.REDSTONE
                    : world.getBlockEntity(target) != null ? LandAccess.CONTAINERS : LandAccess.INTERACT;
            return check(serverPlayer, target, permission) ? ActionResult.PASS : ActionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(player.getStackInHand(hand).getItem() instanceof BucketItem)) return ActionResult.PASS;
            HitResult result = player.raycast(player.getBlockInteractionRange(), 1.0F, true);
            if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) return ActionResult.PASS;
            BlockPos hitPos = hit.getBlockPos();
            if (!check(serverPlayer, hitPos, LandAccess.BUILD)) return ActionResult.FAIL;
            BlockPos adjacent = hitPos.offset(hit.getSide());
            return check(serverPlayer, adjacent, LandAccess.BUILD) ? ActionResult.PASS : ActionResult.FAIL;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (!ConfigManager.claims().preventPvpInsideClaims || !(target instanceof ServerPlayerEntity victim)) return true;
            ServerPlayerEntity attacker = responsiblePlayer(source.getAttacker());
            if (attacker == null || attacker == victim || attacker.hasPermissionLevel(2)) return true;
            return propertyAt(victim.getWorld(), victim.getBlockPos()) == null;
        });
    }

    public static boolean isAllowed(ServerPlayerEntity player, BlockPos pos, String permission) {
        return isAllowed(player, player.getWorld(), pos, permission);
    }

    public static boolean isAllowed(ServerPlayerEntity player, World world, BlockPos pos, String permission) {
        if (player.hasPermissionLevel(2) || AuthorityState.mayAdministerLand(player)) return true;
        LandProperty property = propertyAt(world, pos);
        if (property == null || property.isOwnedBy(player.getUuid())) return true;
        if (!protectionEnabled(permission)) return true;
        if (property.ownerType().equals("institution")
                && InstitutionState.get(player.getServer()).mayManage(property.ownerId(), player.getUuid())) return true;
        LandManagementState management = LandManagementState.get(player.getServer());
        return management.isTenant(property.id(), player.getUuid())
                || management.access(property.id()).permits(player.getUuidAsString(), permission);
    }

    public static LandProperty propertyAt(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return null;
        String dimension = world.getRegistryKey().getValue().toString();
        return LandlordState.get(serverWorld.getServer()).at(dimension, pos);
    }

    public static boolean remainsInSameProtectionArea(World world, BlockPos origin, Collection<BlockPos> affected) {
        String originId = propertyId(propertyAt(world, origin));
        for (BlockPos pos : affected) {
            if (!Objects.equals(originId, propertyId(propertyAt(world, pos)))) return false;
        }
        return true;
    }

    public static boolean remainsInSameProtectionArea(World world, BlockPos from, BlockPos to) {
        return Objects.equals(propertyId(propertyAt(world, from)), propertyId(propertyAt(world, to)));
    }

    public static boolean explosionMayChange(ServerWorld world, Entity cause, BlockPos pos) {
        LandProperty property = propertyAt(world, pos);
        if (property == null) return true;
        ServerPlayerEntity responsible = responsiblePlayer(cause);
        return responsible != null && isAllowed(responsible, world, pos, LandAccess.BUILD);
    }

    private static boolean check(ServerPlayerEntity player, BlockPos pos, String permission) {
        if (isAllowed(player, pos, permission)) return true;
        LandProperty property = propertyAt(player.getWorld(), pos);
        if (property != null) {
            AuditLogger.denied(player, "claims", permission + '@' + property.id());
            player.sendMessage(Text.literal("Keine Berechtigung für „" + property.name() + "“.").formatted(Formatting.RED), true);
        }
        return false;
    }

    private static boolean protectionEnabled(String permission) {
        var config = ConfigManager.claims();
        return switch (permission) {
            case LandAccess.INTERACT -> config.protectInteractions;
            case LandAccess.CONTAINERS -> config.protectContainers;
            case LandAccess.REDSTONE -> config.protectRedstone;
            default -> true;
        };
    }

    private static ServerPlayerEntity responsiblePlayer(Entity entity) {
        Entity current = entity;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (current instanceof ServerPlayerEntity player) return player;
            if (!(current instanceof Ownable ownable)) return null;
            Entity owner = ownable.getOwner();
            if (owner == current) return null;
            current = owner;
        }
        return null;
    }

    private static String propertyId(LandProperty property) {
        return property == null ? "" : property.id();
    }

    private static boolean isBlockMutatingItem(Item item) {
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof AxeItem
                || item instanceof HoeItem || item instanceof ShovelItem
                || item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE || item == Items.BONE_MEAL
                || item == Items.SHEARS || item == Items.HONEYCOMB || item == Items.GLASS_BOTTLE;
    }

    private static boolean isRedstone(Block block) {
        return block instanceof LeverBlock || block instanceof ButtonBlock
                || block instanceof RepeaterBlock || block instanceof ComparatorBlock
                || block instanceof RedstoneWireBlock || block instanceof DaylightDetectorBlock;
    }
}
