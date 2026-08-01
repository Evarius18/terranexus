package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.shop.ShopRecord;
import net.evarius.terranexus.shop.ShopService;
import net.evarius.terranexus.shop.ShopState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Guided, server-authoritative setup flow for sign shops. */
public final class ShopSetupScreen {
    private ShopSetupScreen() {}

    public static void begin(ServerPlayerEntity player, ServerWorld world, BlockPos signPos) {
        if (!ShopService.isShopAdministrator(player)) {
            error(player, "Nur Administratoren dürfen ein [Shop]-Schild einrichten.");
            return;
        }
        ShopRecord existing = ShopState.get(player.getServer())
                .atSign(world.getRegistryKey().getValue().toString(), signPos);
        if (existing != null) {
            edit(player, existing);
            return;
        }
        ItemStack held = player.getMainHandStack();
        String suggested = held.isEmpty() ? "" : Registries.ITEM.getId(held.getItem()).toString();
        askItem(player, new Draft(world, signPos.toImmutable(), suggested, 0, 0, true, true, null));
    }

    public static void edit(ServerPlayerEntity player, ShopRecord requested) {
        ShopRecord shop = ShopState.get(player.getServer()).atSign(requested.dimension(), requested.signPos());
        if (shop == null || !ShopService.mayConfigure(player, shop)
                || !(player.getWorld() instanceof ServerWorld world)
                || !world.getRegistryKey().getValue().toString().equals(shop.dimension())) {
            error(player, "Dieser Shop darf nicht bearbeitet werden.");
            return;
        }
        showSettings(player, new Draft(world, shop.signPos(), shop.itemId(), shop.buyPrice(),
                shop.sellPrice(), shop.salesEnabled(), shop.purchasesEnabled(), shop));
    }

    private static void askItem(ServerPlayerEntity player, Draft draft) {
        input(player, "Shop · Item-ID", draft.itemId(),
                value -> {
                    String itemId = value.isBlank() ? draft.itemId()
                            : value.trim().toLowerCase(java.util.Locale.ROOT);
                    Identifier id = Identifier.tryParse(itemId);
                    if (id == null || !Registries.ITEM.containsId(id)) {
                        error(player, "Ungültige Item-ID. Beispiel: minecraft:stone");
                        askItem(player, draft);
                        return;
                    }
                    askPrice(player, draft.withItem(itemId), true);
                });
    }

    private static void askPrice(ServerPlayerEntity player, Draft draft, boolean sale) {
        String title = sale ? "Shop · Verkaufspreis (V)" : "Shop · Ankaufspreis (K)";
        input(player, title, value -> {
            Long amount = EconomyState.parseAmount(value, true);
            if (amount == null || amount < 0 || amount > ConfigManager.shops().maximumItemPrice) {
                error(player, "Bitte einen Preis von 0 bis "
                        + EconomyState.format(ConfigManager.shops().maximumItemPrice) + " eingeben.");
                askPrice(player, draft, sale);
                return;
            }
            Draft updated = sale ? draft.withBuyPrice(amount) : draft.withSellPrice(amount);
            if (sale) askPrice(player, updated, false);
            else showSettings(player, updated.withDirections(
                    updated.buyPrice() > 0 && updated.salesEnabled(),
                    updated.sellPrice() > 0 && updated.purchasesEnabled()));
        });
    }

    private static void showSettings(ServerPlayerEntity player, Draft draft) {
        if (!stillValid(player, draft)) return;
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();

        ManagementHubScreen.display(inventory, 4, Items.OAK_SIGN,
                draft.existing() == null ? "Shop einrichten" : "Shop bearbeiten",
                draft.itemId() + "\nAlle Änderungen werden serverseitig geprüft");
        Identifier id = Identifier.tryParse(draft.itemId());
        ItemStack product = id != null && Registries.ITEM.containsId(id)
                ? new ItemStack(Registries.ITEM.get(id)) : new ItemStack(Items.BARRIER);
        inventory.setStack(13, product);

        String saleStatus = draft.salesEnabled()
                ? "AKTIV · " + EconomyState.format(draft.buyPrice()) : "DEAKTIVIERT";
        ManagementHubScreen.display(inventory, 20, draft.salesEnabled() ? Items.LIME_DYE : Items.GRAY_DYE,
                "Verkauf (V)", saleStatus);
        actions.put(20, ignored -> toggleSales(player, draft));

        String purchaseStatus = draft.purchasesEnabled()
                ? "AKTIV · " + EconomyState.format(draft.sellPrice()) : "DEAKTIVIERT";
        ManagementHubScreen.display(inventory, 22, draft.purchasesEnabled() ? Items.LIME_DYE : Items.GRAY_DYE,
                "Ankauf (K)", purchaseStatus);
        actions.put(22, ignored -> togglePurchases(player, draft));

        ManagementHubScreen.display(inventory, 24, Items.COMPARATOR, "Artikel / Preise ändern",
                "Geführte Eingabe erneut starten");
        actions.put(24, ignored -> askItem(player, draft));

        ManagementHubScreen.display(inventory, 31, Items.EMERALD, "Speichern",
                "Vorschau bestätigen und Shopdaten dauerhaft speichern");
        actions.put(31, ignored -> save(player, draft));
        ManagementHubScreen.display(inventory, 33, Items.BARRIER, "Abbrechen",
                "Keine Änderungen speichern");
        actions.put(33, ignored -> player.closeHandledScreen());

        CustomGuiService.open(player, inventory, actions,
                Text.literal("TerraNexus Shop").formatted(Formatting.DARK_GREEN));
    }

    private static void toggleSales(ServerPlayerEntity player, Draft draft) {
        if (!draft.salesEnabled() && draft.buyPrice() <= 0) {
            error(player, "Für den Verkauf muss zuerst ein positiver Verkaufspreis festgelegt werden.");
            showSettings(player, draft);
            return;
        }
        showSettings(player, draft.withDirections(!draft.salesEnabled(), draft.purchasesEnabled()));
    }

    private static void togglePurchases(ServerPlayerEntity player, Draft draft) {
        if (!draft.purchasesEnabled() && draft.sellPrice() <= 0) {
            error(player, "Für den Ankauf muss zuerst ein positiver Ankaufspreis festgelegt werden.");
            showSettings(player, draft);
            return;
        }
        showSettings(player, draft.withDirections(draft.salesEnabled(), !draft.purchasesEnabled()));
    }

    private static void save(ServerPlayerEntity player, Draft draft) {
        if (!stillValid(player, draft)) return;
        ShopService.TradeResult result = ShopService.saveSetup(player, draft.world(), draft.signPos(),
                draft.itemId(), draft.buyPrice(), draft.sellPrice(), draft.salesEnabled(),
                draft.purchasesEnabled(), draft.existing());
        player.sendMessage(Text.literal(result.message())
                .formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
        if (result.success()) {
            ShopRecord saved = ShopState.get(player.getServer()).atSign(
                    draft.world().getRegistryKey().getValue().toString(), draft.signPos());
            if (saved != null) ShopScreen.open(player, saved);
        } else {
            showSettings(player, draft);
        }
    }

    private static boolean stillValid(ServerPlayerEntity player, Draft draft) {
        if (player.getWorld() != draft.world()
                || player.squaredDistanceTo(draft.signPos().getX() + 0.5,
                draft.signPos().getY() + 0.5, draft.signPos().getZ() + 0.5) > 64.0) {
            error(player, "Die Shop-Einrichtung wurde beendet: Du bist nicht mehr am Shop.");
            player.closeHandledScreen();
            return false;
        }
        return true;
    }

    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        input(player, title, "", done);
    }

    private static void input(ServerPlayerEntity player, String title, String initialValue,
                              Consumer<String> done) {
        TextPromptService.open(player, title, initialValue, done, () -> {});
    }

    private static void error(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
    }

    private record Draft(ServerWorld world, BlockPos signPos, String itemId,
                         long buyPrice, long sellPrice, boolean salesEnabled,
                         boolean purchasesEnabled, ShopRecord existing) {
        private Draft withItem(String value) {
            return new Draft(world, signPos, value, buyPrice, sellPrice,
                    salesEnabled, purchasesEnabled, existing);
        }

        private Draft withBuyPrice(long value) {
            return new Draft(world, signPos, itemId, value, sellPrice,
                    salesEnabled, purchasesEnabled, existing);
        }

        private Draft withSellPrice(long value) {
            return new Draft(world, signPos, itemId, buyPrice, value,
                    salesEnabled, purchasesEnabled, existing);
        }

        private Draft withDirections(boolean sale, boolean purchase) {
            return new Draft(world, signPos, itemId, buyPrice, sellPrice, sale, purchase, existing);
        }
    }
}
