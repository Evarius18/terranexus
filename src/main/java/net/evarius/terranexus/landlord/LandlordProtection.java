package net.evarius.terranexus.landlord;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.logging.AuditLogger;
import net.evarius.terranexus.shop.ShopRecord;
import net.evarius.terranexus.shop.ShopService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            return ContainerAccessService.handlePendingInteraction(serverPlayer, serverWorld, pos);
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (world instanceof ServerWorld serverWorld && ContainerAccessService.isLocked(serverWorld, pos)
                    && !ContainerAccessService.mayModifyLocked(serverPlayer, serverWorld, pos)) {
                AuditLogger.denied(serverPlayer, "locked_object", "break@" + pos.toShortString());
                serverPlayer.sendMessage(Text.literal("Du darfst dieses gesperrte Objekt nicht abbauen.")
                        .formatted(Formatting.RED), true);
                return false;
            }
            return check(serverPlayer, pos, LandAccess.BUILD);
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world instanceof ServerWorld serverWorld) ContainerAccessService.removed(serverWorld, pos);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            Item held = player.getStackInHand(hand).getItem();
            BlockPos target = hit.getBlockPos();
            if (world instanceof ServerWorld serverWorld) {
                ShopRecord signShop = ShopService.atSign(serverWorld, target);
                if (signShop != null) return ActionResult.PASS;
                ShopRecord containerShop = ShopService.atContainer(serverWorld, target);
                if (containerShop != null)
                    return ShopService.mayManage(serverPlayer, containerShop) ? ActionResult.PASS : ActionResult.FAIL;
                ActionResult pendingResult = ContainerAccessService.handlePendingInteraction(serverPlayer, serverWorld, target);
                if (pendingResult != ActionResult.PASS) return pendingResult;
                if (ContainerAccessService.isLockable(serverWorld, target)
                        && ContainerAccessService.isLocked(serverWorld, target))
                    return checkLockedContainer(serverPlayer, serverWorld, target)
                            ? ActionResult.PASS : ActionResult.FAIL;
            }
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
                    : world instanceof ServerWorld serverWorld && ContainerAccessService.isContainer(serverWorld, target)
                    ? LandAccess.CONTAINERS : LandAccess.INTERACT;
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
            return propertyAt(victim.getWorld(), victim.getBlockPos()) == null
                    && !ConfigManager.administration().wildernessPreventPvp;
        });
    }

    public static boolean isAllowed(ServerPlayerEntity player, BlockPos pos, String permission) {
        return isAllowed(player, player.getWorld(), pos, permission);
    }

    public static boolean isAllowed(ServerPlayerEntity player, World world, BlockPos pos, String permission) {
        if (LandPermissionService.isSystemAdministrator(player)) return true;
        if (LandAccess.BUILD.equals(permission)
                && net.evarius.terranexus.identity.AuthorityState.isBuilder(player)) return true;
        if (!protectionEnabled(permission)) return true;
        LandProperty property = propertyAt(world, pos);
        LandManagementState management = LandManagementState.get(player.getServer());
        LandResolution resolution = management.resolve(property);
        if (resolution.wilderness()) {
            return LandPermissionService.mayExerciseAreaOwnerRights(player, LandManagementState.ROOT_AREA_ID)
                    || management.wildernessPermits(permission);
        }
        if (property.isOwnedBy(player.getUuid())) return true;
        if (resolution.ownerType().equals("institution")
                && InstitutionState.get(player.getServer()).mayManage(resolution.ownerId(), player.getUuid())) return true;
        if (resolution.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && LandPermissionService.mayExerciseAreaOwnerRights(player, resolution.ownerId())) return true;
        return management.tenantPermits(property.id(), player.getUuid(), permission)
                || management.access(property.id()).permits(player.getUuidAsString(), permission);
    }

    public static LandProperty propertyAt(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return null;
        String dimension = world.getRegistryKey().getValue().toString();
        return LandlordState.get(serverWorld.getServer()).at(dimension, pos);
    }

    public static boolean remainsInSameProtectionArea(World world, BlockPos origin, Collection<BlockPos> affected) {
        String originId = protectionAreaId(propertyAt(world, origin));
        for (BlockPos pos : affected) {
            if (!Objects.equals(originId, protectionAreaId(propertyAt(world, pos)))) return false;
        }
        return true;
    }

    public static boolean remainsInSameProtectionArea(World world, BlockPos from, BlockPos to) {
        return Objects.equals(protectionAreaId(propertyAt(world, from)), protectionAreaId(propertyAt(world, to)));
    }

    public static boolean explosionMayChange(ServerWorld world, Entity cause, BlockPos pos) {
        LandProperty property = propertyAt(world, pos);
        if (property == null) return !ConfigManager.administration().wildernessEnvironmentalProtection;
        ServerPlayerEntity responsible = responsiblePlayer(cause);
        return responsible != null && isAllowed(responsible, world, pos, LandAccess.BUILD);
    }

    public static boolean environmentProtected(World world, BlockPos pos) {
        return propertyAt(world, pos) != null || ConfigManager.administration().wildernessEnvironmentalProtection;
    }

    private static boolean check(ServerPlayerEntity player, BlockPos pos, String permission) {
        if (isAllowed(player, pos, permission)) return true;
        LandProperty property = propertyAt(player.getWorld(), pos);
        String targetId = property == null ? LandManagementState.ROOT_AREA_ID : property.id();
        String targetName = property == null ? ConfigManager.administration().wildernessName : property.name();
        AuditLogger.denied(player, "claims", permission + '@' + targetId);
        player.sendMessage(Text.literal("Keine Berechtigung für „" + targetName + "“.").formatted(Formatting.RED), true);
        return false;
    }

    private static boolean checkLockedContainer(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (ContainerAccessService.mayAccessLocked(player, world, pos)) return true;
        AuditLogger.denied(player, "locked_object", "use@" + pos.toShortString());
        player.sendMessage(Text.literal("Dieses Objekt ist individuell gesperrt.").formatted(Formatting.RED), true);
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

    private static String protectionAreaId(LandProperty property) {
        return property == null ? LandManagementState.ROOT_AREA_ID : property.id();
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
