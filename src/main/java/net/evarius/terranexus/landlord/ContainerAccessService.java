package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.state.property.Properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ContainerAccessService {
    private static final Map<UUID, PendingAction> PENDING_ACTIONS = new HashMap<>();

    private ContainerAccessService() {}

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    public static boolean isContainer(ServerWorld world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof Inventory;
    }

    public static boolean isLockable(ServerWorld world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return isContainer(world, pos) || block instanceof DoorBlock
                || block instanceof TrapdoorBlock || block instanceof FenceGateBlock;
    }

    public static Result activate(ServerPlayerEntity player, Action action, ServerPlayerEntity target) {
        if ((action == Action.GRANT || action == Action.REVOKE) && target == null)
            return Result.fail("Für diese Aktion muss eine Zielperson angegeben werden.");
        if (action == Action.GRANT && target != null && target.getUuid().equals(player.getUuid()))
            return Result.fail("Der Sperrenbesitzer besitzt bereits Zugriff.");
        PENDING_ACTIONS.put(player.getUuid(), new PendingAction(action,
                target == null ? null : target.getUuidAsString()));
        return Result.ok(switch (action) {
            case LOCK -> "Sperrmodus aktiv: Klicke jetzt den gewünschten Container, die Tür, Falltür oder das Zauntor an.";
            case UNLOCK -> "Entsperrmodus aktiv: Klicke jetzt das gewünschte gesperrte Objekt an.";
            case GRANT -> "Berechtigungsmodus aktiv: Klicke das Objekt an, das für "
                    + target.getName().getString() + " freigegeben werden soll.";
            case REVOKE -> "Entzugsmodus aktiv: Klicke das Objekt an, dessen Freigabe entfernt werden soll.";
            case LIST -> "Prüfmodus aktiv: Klicke das Objekt an, dessen Berechtigungen du anzeigen möchtest.";
        });
    }

    public static Result cancel(ServerPlayerEntity player) {
        return PENDING_ACTIONS.remove(player.getUuid()) == null
                ? Result.fail("Es ist kein Sperr- oder Berechtigungsmodus aktiv.")
                : Result.ok("Sperr- und Berechtigungsmodus beendet.");
    }

    public static void clear(ServerPlayerEntity player) {
        PENDING_ACTIONS.remove(player.getUuid());
    }

    public static ActionResult handlePendingInteraction(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        PendingAction pending = PENDING_ACTIONS.get(player.getUuid());
        if (pending == null) return ActionResult.PASS;
        if (!isLockable(world, pos)) {
            player.sendMessage(Text.literal("Dieses Objekt kann nicht gesperrt werden. Der Modus bleibt aktiv.")
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }
        Result result = switch (pending.action()) {
            case LOCK -> lock(player, world, pos);
            case UNLOCK -> unlock(player, world, pos);
            case GRANT -> changeGrant(player, world, pos, pending.targetId(), true);
            case REVOKE -> changeGrant(player, world, pos, pending.targetId(), false);
            case LIST -> list(player, world, pos);
        };
        player.sendMessage(Text.literal(result.message()).formatted(result.success()
                ? Formatting.GREEN : Formatting.RED), false);
        if (result.success()) PENDING_ACTIONS.remove(player.getUuid());
        return result.success() ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    public static boolean isLocked(ServerWorld world, BlockPos pos) {
        LandManagementState state = LandManagementState.get(world.getServer());
        String dimension = dimension(world);
        return objectPositions(world, pos).stream().anyMatch(value -> state.containerLock(dimension, value) != null);
    }

    public static boolean mayAccessLocked(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (propertyManager(player, world, pos)) return true;
        LandManagementState state = LandManagementState.get(world.getServer());
        String dimension = dimension(world);
        List<ContainerLock> locks = objectPositions(world, pos).stream()
                .map(value -> state.containerLock(dimension, value)).filter(java.util.Objects::nonNull).toList();
        return !locks.isEmpty() && locks.stream().allMatch(lock -> lock.permits(player.getUuidAsString())
                || mayManageViaGrant(player, world, pos));
    }

    public static boolean mayModifyLocked(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        LandManagementState state = LandManagementState.get(world.getServer());
        String dimension = dimension(world);
        List<ContainerLock> locks = objectPositions(world, pos).stream()
                .map(value -> state.containerLock(dimension, value)).filter(java.util.Objects::nonNull).toList();
        return !locks.isEmpty() && locks.stream().allMatch(lock -> mayManageLock(player, world, pos, lock));
    }

    public static Result lock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!isLockable(world, pos)) return Result.fail("Der angeklickte Block ist kein sperrbares Objekt.");
        List<BlockPos> positions = objectPositions(world, pos);
        LandManagementState state = LandManagementState.get(player.getServer());
        String dimension = dimension(world);
        for (BlockPos part : positions) {
            ContainerLock existing = state.containerLock(dimension, part);
            if (existing != null && !mayManageLock(player, world, part, existing))
                return Result.fail("Mindestens ein Teil dieses Containers ist bereits durch eine andere Person gesperrt.");
        }
        if (!mayCreateLock(player, world, pos))
            return Result.fail("Du darfst auf diesem Grundstück keine Containersperren verwalten.");
        long now = System.currentTimeMillis();
        for (BlockPos part : positions) {
            ContainerLock existing = state.containerLock(dimension, part);
            if (existing == null)
                state.setContainerLock(new ContainerLock(dimension, part.asLong(), player.getUuidAsString(), List.of(), now));
        }
        return Result.ok("Objekt wurde gesperrt. Zugriff ist jetzt nur gezielt berechtigten Personen erlaubt.");
    }

    public static Result unlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!isLockable(world, pos)) return Result.fail("Der angeklickte Block ist kein sperrbares Objekt.");
        LandManagementState state = LandManagementState.get(player.getServer());
        String dimension = dimension(world);
        List<BlockPos> positions = objectPositions(world, pos);
        boolean found = false;
        for (BlockPos part : positions) {
            ContainerLock lock = state.containerLock(dimension, part);
            if (lock == null) continue;
            found = true;
            if (!mayManageLock(player, world, part, lock))
                return Result.fail("Du darfst diese Containersperre nicht entfernen.");
        }
        if (!found) return Result.fail("Dieser Container besitzt keine individuelle Sperre.");
        positions.forEach(part -> state.removeContainerLock(dimension, part));
        return Result.ok("Individuelle Objektsperre entfernt; es gelten wieder die Grundstücksrechte.");
    }

    public static Result grant(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ServerPlayerEntity target) {
        if (target.getUuid().equals(player.getUuid())) return Result.fail("Der Sperrenbesitzer hat bereits Zugriff.");
        return changeGrant(player, world, pos, target.getUuidAsString(), true);
    }

    public static Result revoke(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ServerPlayerEntity target) {
        return changeGrant(player, world, pos, target.getUuidAsString(), false);
    }

    public static Result list(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!isLockable(world, pos)) return Result.fail("Der angeklickte Block ist kein sperrbares Objekt.");
        LandManagementState state = LandManagementState.get(player.getServer());
        String dimension = dimension(world);
        Set<String> permitted = new LinkedHashSet<>();
        String owner = null;
        for (BlockPos part : objectPositions(world, pos)) {
            ContainerLock lock = state.containerLock(dimension, part);
            if (lock == null) continue;
            if (!mayManageLock(player, world, part, lock) && !lock.permits(player.getUuidAsString()))
                return Result.fail("Du darfst die Berechtigungsliste dieses Containers nicht einsehen.");
            if (owner == null) owner = lock.ownerId();
            permitted.addAll(lock.permittedPlayers());
        }
        if (owner == null) return Result.fail("Dieses Objekt besitzt keine individuelle Sperre.");
        String ownerLabel = citizenName(player, owner);
        String permittedLabel = permitted.stream().map(id -> citizenName(player, id))
                .collect(java.util.stream.Collectors.joining(", "));
        return Result.ok("Sperrenbesitzer: " + ownerLabel + " · Berechtigt: "
                + (permitted.isEmpty() ? "niemand" : permittedLabel));
    }

    public static void removed(ServerWorld world, BlockPos pos) {
        LandManagementState state = LandManagementState.get(world.getServer());
        String dimension = dimension(world);
        ContainerLock removed = state.containerLock(dimension, pos);
        state.removeContainerLock(dimension, pos);
        removeLinkedDoorPart(state, dimension, pos.up(), removed);
        removeLinkedDoorPart(state, dimension, pos.down(), removed);
    }

    private static Result changeGrant(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                                      String targetId, boolean grant) {
        if (!isLockable(world, pos)) return Result.fail("Der angeklickte Block ist kein sperrbares Objekt.");
        LandManagementState state = LandManagementState.get(player.getServer());
        String dimension = dimension(world);
        List<BlockPos> positions = objectPositions(world, pos);
        List<ContainerLock> locks = new ArrayList<>();
        for (BlockPos part : positions) {
            ContainerLock lock = state.containerLock(dimension, part);
            if (lock == null) return Result.fail("Der Container muss zuerst mit /lock gesperrt werden.");
            if (!mayManageLock(player, world, part, lock))
                return Result.fail("Du darfst die Berechtigungen dieses Containers nicht verändern.");
            locks.add(lock);
        }
        for (ContainerLock lock : locks) state.setContainerLock(grant ? lock.grant(targetId) : lock.revoke(targetId));
        return Result.ok(grant ? "Objektzugriff wurde erteilt." : "Objektzugriff wurde entzogen.");
    }

    private static boolean mayCreateLock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        LandProperty property = LandlordProtection.propertyAt(world, pos);
        if (property == null) return false;
        LandManagementState management = LandManagementState.get(player.getServer());
        return propertyManager(player, world, pos) || management.isTenant(property.id(), player.getUuid())
                || mayManageViaGrant(player, world, pos);
    }

    private static boolean mayManageLock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ContainerLock lock) {
        return lock.ownerId().equals(player.getUuidAsString()) || propertyManager(player, world, pos)
                || mayManageViaGrant(player, world, pos);
    }

    private static boolean mayManageViaGrant(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        LandProperty property = LandlordProtection.propertyAt(world, pos);
        return property != null && LandManagementState.get(player.getServer()).access(property.id())
                .grants().getOrDefault(player.getUuidAsString(), List.of()).contains(LandAccess.MANAGE_CONTAINERS);
    }

    private static boolean propertyManager(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        LandProperty property = LandlordProtection.propertyAt(world, pos);
        return LandPermissionService.mayExerciseOwnerRights(player, property);
    }

    private static List<BlockPos> objectPositions(ServerWorld world, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        if (!isLockable(world, pos)) return result;
        result.add(pos.toImmutable());
        var state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(state));
            if (world.getBlockState(other).isOf(state.getBlock()) && isContainer(world, other)) result.add(other.toImmutable());
        } else if (state.getBlock() instanceof DoorBlock && state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
            if (world.getBlockState(other).isOf(state.getBlock())) result.add(other.toImmutable());
        }
        return List.copyOf(result);
    }

    private static void removeLinkedDoorPart(LandManagementState state, String dimension,
                                             BlockPos pos, ContainerLock removed) {
        if (removed == null) return;
        ContainerLock neighbour = state.containerLock(dimension, pos);
        if (neighbour != null && neighbour.ownerId().equals(removed.ownerId())
                && neighbour.createdAt() == removed.createdAt())
            state.removeContainerLock(dimension, pos);
    }

    private static String dimension(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static String citizenName(ServerPlayerEntity viewer, String playerId) {
        try {
            CitizenIdentity identity = IdentityState.get(viewer.getServer()).get(java.util.UUID.fromString(playerId));
            return identity == null ? "Unbekannte Person" : identity.firstName() + " " + identity.lastName();
        } catch (IllegalArgumentException ignored) { return "Unbekannte Person"; }
    }

    public enum Action { LOCK, UNLOCK, GRANT, REVOKE, LIST }
    private record PendingAction(Action action, String targetId) {}
}
