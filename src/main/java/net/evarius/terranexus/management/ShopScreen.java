package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.shop.ShopRecord;
import net.evarius.terranexus.shop.ShopService;
import net.evarius.terranexus.shop.ShopState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopScreen {
    private ShopScreen() {}

    public static void open(ServerPlayerEntity player, ShopRecord requested) {
        ShopRecord shop = ShopState.get(player.getServer()).atSign(requested.dimension(), requested.signPos());
        if (shop == null || !player.getWorld().getRegistryKey().getValue().toString().equals(shop.dimension())) {
            player.sendMessage(Text.literal("Dieser Shop ist nicht mehr verfügbar.").formatted(Formatting.RED), false);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        var id = Identifier.tryParse(shop.itemId());
        ItemStack product = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : new ItemStack(Items.BARRIER);
        product.set(DataComponentTypes.CUSTOM_NAME, Text.literal(shop.itemId()).formatted(Formatting.AQUA));
        product.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Lagerbestand: " + ShopService.stock(player.getWorld(), shop)).formatted(Formatting.GRAY),
                Text.literal("Dein Bestand: " + ShopService.playerStock(player, shop)).formatted(Formatting.GRAY),
                Text.literal("Betreiber: " + BankManagementScreen.label(player, shop.account())).formatted(Formatting.DARK_GRAY))));
        inventory.setStack(13, product);

        if (shop.buyPrice() > 0) {
            ManagementHubScreen.display(inventory, 29, Items.LIME_DYE, "1 kaufen", EconomyState.format(shop.buyPrice()));
            actions.put(29, ignored -> trade(player, shop, true, 1));
            int amount = Math.min(64, Math.max(1, ShopService.stock(player.getWorld(), shop)));
            ManagementHubScreen.display(inventory, 38, Items.EMERALD, amount + " kaufen", EconomyState.format(shop.buyPrice() * amount));
            actions.put(38, ignored -> trade(player, shop, true, amount));
        }
        if (shop.sellPrice() > 0) {
            ManagementHubScreen.display(inventory, 33, Items.ORANGE_DYE, "1 verkaufen", EconomyState.format(shop.sellPrice()));
            actions.put(33, ignored -> trade(player, shop, false, 1));
            int amount = Math.min(64, Math.max(1, ShopService.playerStock(player, shop)));
            ManagementHubScreen.display(inventory, 42, Items.GOLD_INGOT, amount + " verkaufen", EconomyState.format(shop.sellPrice() * amount));
            actions.put(42, ignored -> trade(player, shop, false, amount));
        }
        if (ShopService.mayManage(player, shop)) {
            ManagementHubScreen.display(inventory, 49, Items.BARRIER, "Shop aufheben", "Kiste und Schild werden wieder freigegeben");
            actions.put(49, ignored -> {
                boolean removed = ShopService.remove(player, shop);
                player.closeHandledScreen();
                player.sendMessage(Text.literal(removed ? "Shop wurde aufgehoben." : "Shop konnte nicht aufgehoben werden.")
                        .formatted(removed ? Formatting.YELLOW : Formatting.RED), false);
            });
        }
        CustomGuiService.open(player, inventory, actions,
                Text.literal("TerraNexus Shop").formatted(Formatting.DARK_GREEN));
    }

    private static void trade(ServerPlayerEntity player, ShopRecord shop, boolean buy, int amount) {
        ShopService.TradeResult result = buy ? ShopService.buy(player, shop, amount) : ShopService.sell(player, shop, amount);
        player.sendMessage(Text.literal(result.message()).formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
        if (ShopState.get(player.getServer()).atSign(shop.dimension(), shop.signPos()) != null) open(player, shop);
        else player.closeHandledScreen();
    }
}
