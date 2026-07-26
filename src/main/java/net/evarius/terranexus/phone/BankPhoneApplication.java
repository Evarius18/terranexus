package net.evarius.terranexus.phone;

import net.evarius.terranexus.api.economy.AccountSnapshot;
import net.evarius.terranexus.api.economy.TerraNexusEconomyApi;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.management.CustomGuiService;
import net.evarius.terranexus.management.ManagementHubScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class BankPhoneApplication implements PhoneApplication {
    @Override public String id() { return "terranexus:bank"; }
    @Override public String title() { return "Bankkonto"; }
    @Override public String description() { return "Kontostand und letzte Buchungen"; }
    @Override public Item icon() { return Items.GOLD_INGOT; }

    @Override
    public void open(ServerPlayerEntity player) {
        AccountSnapshot snapshot = TerraNexusEconomyApi.accountSnapshot(player);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_INGOT, EconomyState.format(snapshot.balance()),
                snapshot.frozen() ? "Konto gesperrt" : "Konto aktiv");
        ManagementHubScreen.display(inventory, 13, Items.NAME_TAG, "Kontonummer", snapshot.accountNumber());
        int slot = 20;
        SimpleDateFormat format = new SimpleDateFormat("dd.MM. HH:mm");
        for (var transaction : snapshot.recentTransactions()) {
            String direction = transaction.recipient().equals(EconomyState.playerAccount(player.getUuid())) ? "+" : "−";
            ManagementHubScreen.display(inventory, slot++, Items.PAPER,
                    direction + EconomyState.format(transaction.amount()),
                    format.format(new Date(transaction.timestamp())) + " · " + transaction.purpose());
        }
        ManagementHubScreen.display(inventory, 49, Items.ARROW, "Zurück", "Zum Handy");
        actions.put(49, ignored -> PhoneScreen.open(player));
        CustomGuiService.open(player, inventory, actions,
                Text.literal("TN-Handy · Bankkonto").formatted(Formatting.DARK_AQUA));
    }
}
