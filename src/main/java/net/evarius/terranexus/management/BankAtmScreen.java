package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.CashService;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class BankAtmScreen {
    private BankAtmScreen() {}

    public static void open(ServerPlayerEntity player) {
        if (IdentityState.get(player.getServer()).get(player.getUuid()) == null
                || !IdentityState.get(player.getServer()).isApproved(player.getUuid())) {
            player.sendMessage(Text.literal("Für den Bankautomaten ist eine freigeschaltete Bürgerakte erforderlich.")
                    .formatted(Formatting.RED), false);
            return;
        }
        EconomyState economy = EconomyState.get(player.getServer());
        String account = EconomyState.playerAccount(player.getUuid());
        economy.ensureAccount(account);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_BLOCK, "Nexus Bankautomat",
                "Kontostand: " + EconomyState.format(economy.balance(account)));
        ManagementHubScreen.display(inventory, 20, Items.CHEST, "Bargeld einzahlen",
                "Im Inventar: " + EconomyState.format(CashService.carriedValue(player)));
        actions.put(20, ignored -> {
            boolean success = CashService.depositAll(player);
            player.sendMessage(Text.literal(success ? "Bargeld wurde dem Konto gutgeschrieben."
                    : "Einzahlung nicht möglich: kein Bargeld vorhanden oder Konto gesperrt.")
                    .formatted(success ? Formatting.GREEN : Formatting.RED), false);
            open(player);
        });
        ManagementHubScreen.display(inventory, 24, Items.PAPER, "Bargeld auszahlen",
                "Nur Geldscheine · Betrag in Vielfachen von 5 Nexus");
        actions.put(24, ignored -> withdrawPrompt(player));
        ManagementHubScreen.display(inventory, 31, Items.WRITABLE_BOOK, "Bankkonto",
                "Kontostand, Überweisungen und Kontobewegungen");
        actions.put(31, ignored -> EconomyScreen.open(player));
        CustomGuiService.open(player, inventory, actions, Text.literal("Nexus · Bankautomat").formatted(Formatting.GOLD));
    }

    private static void withdrawPrompt(ServerPlayerEntity player) {
        TextPromptService.open(player, "Bankautomat · Auszahlung", "", value -> {
            Long amount = EconomyState.parseAmount(value, true);
            boolean valid = amount != null && amount <= ConfigManager.economy().maximumTransferAmount
                    && CashService.isBanknoteAmount(amount);
            boolean success = valid && CashService.withdrawBanknotes(player, amount);
            player.sendMessage(Text.literal(success ? "Bargeld wurde ausgezahlt."
                    : "Auszahlung nicht möglich: Nur Scheine und Beträge ab 5 Nexus in 5-Nexus-Schritten sind zulässig; bitte auch Kontostand und Inventarplatz prüfen.")
                    .formatted(success ? Formatting.GREEN : Formatting.RED), false);
            open(player);
        }, () -> open(player));
    }
}
