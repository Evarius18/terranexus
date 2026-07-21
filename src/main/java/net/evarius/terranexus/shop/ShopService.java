package net.evarius.terranexus.shop;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandTradeService;
import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.management.ShopScreen;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Locale;
import java.util.UUID;

public final class ShopService {
    private ShopService() {}

    public record TradeResult(boolean success, String message) {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(world instanceof ServerWorld serverWorld)
                    || !(player instanceof ServerPlayerEntity serverPlayer) || !ConfigManager.shops().enabled)
                return ActionResult.PASS;
            String dimension = dimension(serverWorld);
            BlockPos pos = hit.getBlockPos();
            ShopState state = ShopState.get(serverWorld.getServer());
            ShopRecord shop = state.atSign(dimension, pos);
            if (shop != null) {
                ShopScreen.open(serverPlayer, shop);
                return ActionResult.SUCCESS_SERVER;
            }
            ShopRecord containerShop = state.atContainer(dimension, pos);
            if (containerShop != null && !mayManage(serverPlayer, containerShop)) {
                serverPlayer.sendMessage(Text.literal("Die Kiste ist als Shoplager versiegelt.").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            if (serverWorld.getBlockEntity(pos) instanceof SignBlockEntity sign && isShopHeader(line(sign, serverPlayer, 0))) {
                TradeResult result = registerSignShop(serverPlayer, serverWorld, pos, sign);
                serverPlayer.sendMessage(Text.literal(result.message()).formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld) || !ConfigManager.shops().enabled) return true;
            ShopState state = ShopState.get(serverWorld.getServer());
            ShopRecord shop = state.atSign(dimension(serverWorld), pos);
            if (shop == null) shop = state.atContainer(dimension(serverWorld), pos);
            if (shop == null) return true;
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !mayManage(serverPlayer, shop)) {
                player.sendMessage(Text.literal("Nur die Shopverwaltung darf diesen Shop abbauen.").formatted(Formatting.RED), true);
                return false;
            }
            state.remove(shop.id());
            serverPlayer.sendMessage(Text.literal("Shop wurde aufgehoben.").formatted(Formatting.YELLOW), false);
            return true;
        });
    }

    public static ShopRecord atSign(ServerWorld world, BlockPos pos) {
        return ShopState.get(world.getServer()).atSign(dimension(world), pos);
    }

    public static ShopRecord atContainer(ServerWorld world, BlockPos pos) {
        return ShopState.get(world.getServer()).atContainer(dimension(world), pos);
    }

    public static boolean mayManage(ServerPlayerEntity player, ShopRecord shop) {
        if (player.hasPermissionLevel(2) || AuthorityState.isTnAdmin(player)) return true;
        if (player.getWorld().getRegistryKey().getValue().toString().equals(shop.dimension())) {
            LandProperty currentProperty = LandlordProtection.propertyAt(player.getWorld(), shop.signPos());
            if (LandTradeService.mayManageCommercially(player, currentProperty)) return true;
        }
        if (shop.ownerType().equals("player")) return shop.ownerId().equals(player.getUuidAsString());
        if (shop.ownerType().equals("institution"))
            return InstitutionState.get(player.getServer()).mayManage(shop.ownerId(), player.getUuid());
        return shop.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && LandManagementState.get(player.getServer()).mayManageArea(shop.ownerId(), player);
    }

    public static int stock(ServerWorld world, ShopRecord shop) {
        Inventory inventory = inventory(world, shop);
        Item item = item(shop);
        return inventory == null || item == null ? 0 : count(inventory, item);
    }

    public static int playerStock(ServerPlayerEntity player, ShopRecord shop) {
        Item item = item(shop);
        return item == null ? 0 : count(player.getInventory(), item);
    }

    public static TradeResult buy(ServerPlayerEntity customer, ShopRecord shop, int requested) {
        ServerWorld world = customer.getWorld();
        ShopRecord latest = ShopState.get(customer.getServer()).atSign(shop.dimension(), shop.signPos());
        Item item = item(shop);
        Inventory storage = inventory(world, shop);
        int amount = Math.min(requested, ConfigManager.shops().maximumItemsPerTransaction);
        if (!nearShop(customer, shop) || !ownershipCurrent(world, shop) || latest == null || !latest.equals(shop) || item == null || storage == null
                || amount <= 0 || shop.buyPrice() <= 0 || !(world.getBlockEntity(shop.signPos()) instanceof SignBlockEntity))
            return fail("Dieser Kauf ist nicht verfügbar.");
        if (shop.account().equals(EconomyState.playerAccount(customer.getUuid()))) return fail("Eigene Shops können nicht selbst handeln.");
        if (count(storage, item) < amount) return fail("Nicht genügend Lagerbestand.");
        if (capacity(customer.getInventory(), item) < amount) return fail("Nicht genügend Platz im Inventar.");
        long total = total(shop.buyPrice(), amount);
        if (total <= 0 || total > ConfigManager.economy().maximumTransferAmount) return fail("Der Gesamtpreis überschreitet das Buchungslimit.");
        boolean booked = EconomyState.get(customer.getServer()).transferConditional(
                EconomyState.playerAccount(customer.getUuid()), shop.account(), total,
                "Shopkauf · " + shop.itemId() + " × " + amount, customer.getUuidAsString(), scope(shop), "SHOP_PURCHASE", () -> {
                    if (!latest.equals(ShopState.get(customer.getServer()).atSign(shop.dimension(), shop.signPos()))
                            || count(storage, item) < amount || capacity(customer.getInventory(), item) < amount) return false;
                    remove(storage, item, amount);
                    insert(customer.getInventory(), item, amount);
                    storage.markDirty(); customer.getInventory().markDirty();
                    return true;
                });
        return booked ? ok(amount + "× " + shop.itemId() + " gekauft für " + EconomyState.format(total) + ".")
                : fail("Kauf abgelehnt: Kontodeckung, Kontostatus oder Bestand prüfen.");
    }

    public static TradeResult sell(ServerPlayerEntity customer, ShopRecord shop, int requested) {
        if (!ConfigManager.shops().allowPlayersToSellToShops) return fail("Ankäufe sind serverseitig deaktiviert.");
        ServerWorld world = customer.getWorld();
        ShopRecord latest = ShopState.get(customer.getServer()).atSign(shop.dimension(), shop.signPos());
        Item item = item(shop);
        Inventory storage = inventory(world, shop);
        int amount = Math.min(requested, ConfigManager.shops().maximumItemsPerTransaction);
        if (!nearShop(customer, shop) || !ownershipCurrent(world, shop) || latest == null || !latest.equals(shop) || item == null || storage == null
                || amount <= 0 || shop.sellPrice() <= 0 || !(world.getBlockEntity(shop.signPos()) instanceof SignBlockEntity))
            return fail("Dieser Ankauf ist nicht verfügbar.");
        if (shop.account().equals(EconomyState.playerAccount(customer.getUuid()))) return fail("Eigene Shops können nicht selbst handeln.");
        if (count(customer.getInventory(), item) < amount) return fail("Du besitzt nicht genügend passende Items.");
        if (capacity(storage, item) < amount) return fail("Das Shoplager ist voll.");
        long total = total(shop.sellPrice(), amount);
        if (total <= 0 || total > ConfigManager.economy().maximumTransferAmount) return fail("Der Gesamtpreis überschreitet das Buchungslimit.");
        boolean booked = EconomyState.get(customer.getServer()).transferConditional(
                shop.account(), EconomyState.playerAccount(customer.getUuid()), total,
                "Shopankauf · " + shop.itemId() + " × " + amount, customer.getUuidAsString(), scope(shop), "SHOP_SALE", () -> {
                    if (!latest.equals(ShopState.get(customer.getServer()).atSign(shop.dimension(), shop.signPos()))
                            || count(customer.getInventory(), item) < amount || capacity(storage, item) < amount) return false;
                    remove(customer.getInventory(), item, amount);
                    insert(storage, item, amount);
                    storage.markDirty(); customer.getInventory().markDirty();
                    return true;
                });
        return booked ? ok(amount + "× " + shop.itemId() + " verkauft für " + EconomyState.format(total) + ".")
                : fail("Ankauf abgelehnt: Shopdeckung, Kontostatus oder Lager prüfen.");
    }

    public static boolean remove(ServerPlayerEntity player, ShopRecord shop) {
        return mayManage(player, shop) && ShopState.get(player.getServer()).remove(shop.id());
    }

    private static TradeResult registerSignShop(ServerPlayerEntity player, ServerWorld world, BlockPos signPos, SignBlockEntity sign) {
        String itemName = line(sign, player, 1).trim().toLowerCase(Locale.ROOT);
        Identifier identifier = Identifier.tryParse(itemName);
        if (identifier == null || !Registries.ITEM.containsId(identifier))
            return fail("Zeile 2 muss eine gültige Item-ID enthalten, z. B. minecraft:stone.");
        Long buy = price(line(sign, player, 2), 'k', 'b');
        Long sell = price(line(sign, player, 3), 'v', 's');
        if (buy == null || sell == null || buy < 0 || sell < 0 || buy == 0 && sell == 0
                || buy > ConfigManager.shops().maximumItemPrice || sell > ConfigManager.shops().maximumItemPrice)
            return fail("Zeile 3/4: K: Preis und V: Preis; mindestens ein Preis muss positiv sein.");
        BlockPos containerPos = findSingleChest(world, signPos);
        if (containerPos == null) return fail("Direkt neben dem Schild muss eine einzelne Kiste stehen.");
        LandProperty property = LandlordProtection.propertyAt(world, signPos);
        if (property == null && ConfigManager.shops().requireClaimedLand)
            return fail("Shops dürfen nur auf einem Grundstück erstellt werden.");
        if (property != null && (!property.contains(dimension(world), containerPos.getX(), containerPos.getY(), containerPos.getZ())
                || !LandTradeService.mayManageCommercially(player, property)))
            return fail("Schild und Kiste müssen auf einer selbst verwalteten Fläche stehen.");

        String ownerType = property == null ? "player" : property.ownerType();
        String ownerId = property == null ? player.getUuidAsString() : property.ownerId();
        String account = property == null ? EconomyState.playerAccount(player.getUuid()) : LandTradeService.ownerAccount(property);
        ShopState state = ShopState.get(player.getServer());
        if (state.ownedBy(ownerType, ownerId).size() >= ConfigManager.shops().maximumShopsPerOwner)
            return fail("Die maximale Shopanzahl dieses Eigentümers ist erreicht.");
        ShopRecord record = new ShopRecord(UUID.randomUUID().toString(), dimension(world), signPos.asLong(),
                containerPos.asLong(), identifier.toString(), buy, sell, ownerType, ownerId, account, System.currentTimeMillis());
        return state.add(record) ? ok("Shop wurde erstellt. Kiste und Schild sind jetzt geschützt.")
                : fail("An diesem Schild oder dieser Kiste existiert bereits ein Shop.");
    }

    private static String line(SignBlockEntity sign, ServerPlayerEntity player, int line) {
        boolean front = sign.isPlayerFacingFront(player);
        return sign.getText(front).getMessage(line, player.shouldFilterText()).getString();
    }
    private static boolean isShopHeader(String text) { return text.trim().equalsIgnoreCase("[Shop]"); }
    private static Long price(String text, char german, char english) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2 || value.charAt(1) != ':' || value.charAt(0) != german && value.charAt(0) != english) return null;
        return EconomyState.parseAmount(value.substring(2), true);
    }
    private static BlockPos findSingleChest(ServerWorld world, BlockPos signPos) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = signPos.offset(direction);
            BlockState state = world.getBlockState(candidate);
            if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return candidate;
        }
        return null;
    }
    private static Inventory inventory(ServerWorld world, ShopRecord shop) {
        if (!dimension(world).equals(shop.dimension())) return null;
        BlockState state = world.getBlockState(shop.containerPos());
        if (!(state.getBlock() instanceof ChestBlock chest) || state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return null;
        return ChestBlock.getInventory(chest, state, world, shop.containerPos(), true);
    }
    private static Item item(ShopRecord shop) {
        Identifier id = Identifier.tryParse(shop.itemId());
        return id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }
    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) if (isTradeStack(inventory.getStack(slot), item)) count += inventory.getStack(slot).getCount();
        return count;
    }
    private static int capacity(Inventory inventory, Item item) {
        int capacity = 0;
        ItemStack template = new ItemStack(item);
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) capacity += Math.min(template.getMaxCount(), inventory.getMaxCount(template));
            else if (isTradeStack(stack, item)) capacity += Math.max(0, Math.min(stack.getMaxCount(), inventory.getMaxCount(stack)) - stack.getCount());
        }
        return capacity;
    }
    private static void remove(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isTradeStack(stack, item)) continue;
            int take = Math.min(remaining, stack.getCount());
            inventory.removeStack(slot, take); remaining -= take;
        }
        if (remaining != 0) throw new IllegalStateException("Shopbestand wurde während der Transaktion verändert");
    }
    private static void insert(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isTradeStack(stack, item)) continue;
            int add = Math.min(remaining, Math.min(stack.getMaxCount(), inventory.getMaxCount(stack)) - stack.getCount());
            if (add > 0) { stack.increment(add); remaining -= add; }
        }
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            if (!inventory.getStack(slot).isEmpty()) continue;
            ItemStack stack = new ItemStack(item, Math.min(remaining, Math.min(item.getDefaultStack().getMaxCount(), inventory.getMaxCount(item.getDefaultStack()))));
            inventory.setStack(slot, stack); remaining -= stack.getCount();
        }
        if (remaining != 0) throw new IllegalStateException("Inventarkapazität wurde während der Transaktion verändert");
    }
    private static long total(long unit, int amount) {
        try { return Math.multiplyExact(unit, amount); }
        catch (ArithmeticException ignored) { return -1; }
    }
    private static String scope(ShopRecord shop) {
        return shop.ownerType().equals("institution") ? shop.ownerId()
                : shop.ownerType().equals(LandManagementState.AREA_OWNER_TYPE) ? "area:" + shop.ownerId() : "";
    }
    private static String dimension(ServerWorld world) { return world.getRegistryKey().getValue().toString(); }
    private static boolean nearShop(ServerPlayerEntity player, ShopRecord shop) {
        BlockPos pos = shop.signPos();
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    private static boolean ownershipCurrent(ServerWorld world, ShopRecord shop) {
        LandProperty property = LandlordProtection.propertyAt(world, shop.signPos());
        if (property == null) return !ConfigManager.shops().requireClaimedLand && shop.ownerType().equals("player");
        return property.ownerType().equals(shop.ownerType()) && property.ownerId().equals(shop.ownerId())
                && LandTradeService.ownerAccount(property).equals(shop.account())
                && property.contains(shop.dimension(), shop.containerPos().getX(), shop.containerPos().getY(), shop.containerPos().getZ());
    }
    private static boolean isTradeStack(ItemStack stack, Item item) {
        return !stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, item.getDefaultStack());
    }
    private static TradeResult ok(String message) { return new TradeResult(true, message); }
    private static TradeResult fail(String message) { return new TradeResult(false, message); }
}
