package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.BankAccount;
import net.evarius.terranexus.economy.EconomyMetrics;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.economy.EconomyTransaction;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.logging.AuditLogger;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class CentralBankScreen {
    private CentralBankScreen() {}
    private enum JournalFilter { ALL, PROPERTY, RENT, SHOP, SALARY, MONETARY }

    public static void open(ServerPlayerEntity player) {
        if (!mayView(player)) { denied(player); return; }
        EconomyState economy = EconomyState.get(player.getServer());
        BankManagementScreen.ensureKnownAccounts(player, economy);
        EconomyMetrics metrics = economy.metrics();
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.BEACON, "Zentralbank",
                "Geldmenge: " + EconomyState.format(metrics.totalMoneySupply()) + " · " + metrics.accountCount() + " Konten");
        display(inventory, 11, Items.PLAYER_HEAD, "Privatvermögen", EconomyState.format(metrics.playerMoney()));
        display(inventory, 13, Items.BRICKS, "Institutionen", EconomyState.format(metrics.institutionMoney()));
        display(inventory, 15, Items.FILLED_MAP, "Verwaltungen", EconomyState.format(metrics.administrationMoney()));
        display(inventory, 20, Items.WRITABLE_BOOK, "Buchungsstatistik",
                metrics.successfulTransactions() + " erfolgreich · " + metrics.rejectedTransactions() + " abgelehnt");
        display(inventory, 22, Items.GOLD_BLOCK, "Emittiert / eingezogen",
                EconomyState.format(metrics.issuedMoney()) + " / " + EconomyState.format(metrics.retiredMoney()));
        display(inventory, 24, Items.HOPPER, "Buchungsvolumen", EconomyState.format(metrics.transferredVolume()));
        display(inventory, 26, Items.COMPARATOR, "Wirtschaftsparameter",
                "Limit " + EconomyState.format(ConfigManager.economy().maximumTransferAmount)
                        + " · Gebühr " + ConfigManager.economy().transferFeeBasisPoints + " Basispunkte");
        button(inventory, actions, 29, Items.PAPER, "Kontenübersicht", "Suche, Salden und Kontobewegungen",
                ignored -> BankManagementScreen.open(player));
        button(inventory, actions, 31, Items.COMPARATOR, "Wirtschaftsjournal", "Filterbare Geldflüsse",
                ignored -> journalFilters(player));
        if (mayPolicy(player)) {
            button(inventory, actions, 33, Items.LIME_DYE, "Geld emittieren", "Kritische Aktion mit Bestätigung",
                    ignored -> searchAccount(player, true));
            button(inventory, actions, 35, Items.RED_DYE, "Geld einziehen", "Nur vorhandenes Guthaben",
                    ignored -> searchAccount(player, false));
        }
        button(inventory, actions, 49, Items.ARROW, "Zurück", "Zum Admin-Desktop", ignored -> AdminDesktopScreen.open(player));
        menu(player, inventory, actions, "Zentralbank · Übersicht");
    }

    private static void journalFilters(ServerPlayerEntity player) {
        if (!mayView(player)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.COMPARATOR, "Wirtschaftsjournal", "Transaktionen nach Fachbereich filtern");
        filterButton(inventory, actions, player, 19, Items.WRITABLE_BOOK, "Alle Buchungen", JournalFilter.ALL);
        filterButton(inventory, actions, player, 21, Items.FILLED_MAP, "Grundstückskäufe", JournalFilter.PROPERTY);
        filterButton(inventory, actions, player, 23, Items.OAK_DOOR, "Mieten", JournalFilter.RENT);
        filterButton(inventory, actions, player, 25, Items.CHEST, "Shops", JournalFilter.SHOP);
        filterButton(inventory, actions, player, 29, Items.EMERALD, "Gehälter", JournalFilter.SALARY);
        filterButton(inventory, actions, player, 31, Items.BEACON, "Geldpolitik", JournalFilter.MONETARY);
        button(inventory, actions, 49, Items.ARROW, "Zurück", "Zur Zentralbank", ignored -> open(player));
        menu(player, inventory, actions, "Zentralbank · Journalfilter");
    }

    private static void journal(ServerPlayerEntity player, JournalFilter filter, int requestedPage) {
        if (!mayView(player)) { denied(player); return; }
        List<EconomyTransaction> entries = entries(EconomyState.get(player.getServer()), filter);
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.WRITABLE_BOOK, label(filter), entries.size() + " Buchungen · Seite " + (page + 1) + "/" + pages);
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> journal(player, filter, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> journal(player, filter, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zu den Filtern", ignored -> journalFilters(player));
        SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        int slot = 9;
        for (EconomyTransaction entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            display(inventory, slot++, entry.successful() ? Items.PAPER : Items.BARRIER,
                    (entry.successful() ? "✓ " : "✗ ") + entry.type() + " · " + EconomyState.format(entry.amount()),
                    date.format(new Date(entry.timestamp())) + " · " + BankManagementScreen.label(player, entry.sender())
                            + " → " + BankManagementScreen.label(player, entry.recipient()) + " · " + entry.purpose());
        }
        menu(player, inventory, actions, "Zentralbank · " + label(filter));
    }

    private static void searchAccount(ServerPlayerEntity player, boolean issue) {
        if (!mayPolicy(player)) { denied(player); return; }
        input(player, "Konto suchen", query -> accountResults(player, query.trim(), issue, 0));
    }

    private static void accountResults(ServerPlayerEntity player, String query, boolean issue, int requestedPage) {
        if (!mayPolicy(player)) { denied(player); return; }
        if (query.isBlank()) { message(player, "Bitte RP-Name, Kontonummer oder Kontoschlüssel angeben.", false); open(player); return; }
        EconomyState economy = EconomyState.get(player.getServer());
        BankManagementScreen.ensureKnownAccounts(player, economy);
        String needle = query.toLowerCase(Locale.ROOT).replace(" ", "");
        List<BankAccount> accounts = economy.allAccounts().stream()
                .filter(account -> !account.accountKey().startsWith("system:"))
                .filter(account -> account.accountNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || account.accountKey().toLowerCase(Locale.ROOT).contains(needle)
                        || BankManagementScreen.label(player, account.accountKey()).toLowerCase(Locale.ROOT).replace(" ", "").contains(needle))
                .sorted(Comparator.comparing(account -> BankManagementScreen.label(player, account.accountKey()))).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (accounts.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, issue ? Items.LIME_DYE : Items.RED_DYE, issue ? "Emissionsziel" : "Einziehungsziel",
                accounts.size() + " Treffer · Suche: " + query);
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> accountResults(player, query, issue, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> accountResults(player, query, issue, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Zentralbank", ignored -> open(player));
        int slot = 9;
        for (BankAccount account : accounts.subList(page * pageSize, Math.min(accounts.size(), (page + 1) * pageSize))) {
            button(inventory, actions, slot++, Items.PAPER, BankManagementScreen.label(player, account.accountKey()),
                    account.accountNumber() + " · " + EconomyState.format(economy.balance(account.accountKey())),
                    ignored -> amount(player, account.accountKey(), issue));
        }
        menu(player, inventory, actions, "Zentralbank · Konto wählen");
    }

    private static void amount(ServerPlayerEntity player, String account, boolean issue) {
        input(player, issue ? "Emissionsbetrag" : "Einziehungsbetrag", value -> {
            Long cents = EconomyState.parseAmount(value, false);
            if (cents == null || cents > ConfigManager.economy().maximumTransferAmount) {
                message(player, "Ungültiger Betrag oder konfiguriertes Buchungslimit überschritten.", false); open(player); return;
            }
            input(player, "Begründung", purpose -> confirm(player, account, cents, purpose.trim(), issue));
        });
    }

    private static void confirm(ServerPlayerEntity player, String account, long amount, String purpose, boolean issue) {
        if (!mayPolicy(player)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 13, issue ? Items.LIME_DYE : Items.RED_DYE,
                issue ? "Geld emittieren" : "Geld einziehen",
                BankManagementScreen.label(player, account) + " · " + EconomyState.format(amount));
        display(inventory, 22, Items.WRITABLE_BOOK, "Begründung", purpose.isBlank() ? "Keine Begründung" : purpose);
        button(inventory, actions, 30, Items.BARRIER, "Abbrechen", "Keine Buchung", ignored -> open(player));
        button(inventory, actions, 32, Items.LIME_CONCRETE, "Verbindlich bestätigen", "Aktion wird dauerhaft protokolliert", ignored -> {
            if (!mayPolicy(player)) { denied(player); return; }
            EconomyState economy = EconomyState.get(player.getServer());
            boolean success = issue
                    ? economy.issueMoney(account, amount, purpose.isBlank() ? "Zentralbank-Emission" : purpose, player.getUuidAsString())
                    : economy.retireMoney(account, amount, purpose.isBlank() ? "Zentralbank-Einziehung" : purpose, player.getUuidAsString());
            message(player, success ? "Geldpolitische Buchung wurde ausgeführt und protokolliert."
                    : "Buchung abgelehnt: Kontodeckung oder Kontostatus prüfen.", success);
            open(player);
        });
        menu(player, inventory, actions, "Zentralbank · Sicherheitsbestätigung");
    }

    private static List<EconomyTransaction> entries(EconomyState economy, JournalFilter filter) {
        return switch (filter) {
            case ALL -> economy.transactions();
            case PROPERTY -> economy.transactionsByTypes(Set.of("PROPERTY_SALE"));
            case RENT -> economy.transactionsByTypes(Set.of("RENT", "LEASE_DEPOSIT", "LEASE_TERMINATION",
                    "LEASE_EXPIRED", "LEASE_DEFAULTED", "LEASE_INVALIDATED"));
            case SHOP -> economy.transactionsByTypes(Set.of("SHOP_PURCHASE", "SHOP_SALE"));
            case SALARY -> economy.transactionsByTypes(Set.of("SALARY", "ADMIN_SALARY", "PAYROLL"));
            case MONETARY -> economy.transactionsByTypes(Set.of("CENTRAL_BANK_ISSUE", "CENTRAL_BANK_RETIRE"));
        };
    }
    private static String label(JournalFilter filter) {
        return switch (filter) {
            case ALL -> "Alle Buchungen"; case PROPERTY -> "Grundstückskäufe"; case RENT -> "Mieten";
            case SHOP -> "Shops"; case SALARY -> "Gehälter"; case MONETARY -> "Geldpolitik";
        };
    }
    private static void filterButton(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                     ServerPlayerEntity player, int slot, Item item, String name, JournalFilter filter) {
        button(inventory, actions, slot, item, name, "Journal öffnen", ignored -> journal(player, filter, 0));
    }
    private static boolean mayView(ServerPlayerEntity player) {
        return InstitutionAccess.hasCentralBankPermission(player, InstitutionPermission.CENTRAL_BANK_VIEW);
    }
    private static boolean mayPolicy(ServerPlayerEntity player) {
        return InstitutionAccess.hasCentralBankPermission(player, InstitutionPermission.CENTRAL_BANK_MONETARY_POLICY);
    }
    private static void denied(ServerPlayerEntity player) {
        AuditLogger.denied(player, "central_bank", "management");
        message(player, "Keine Berechtigung für die Zentralbank.", false);
    }
    private static void message(ServerPlayerEntity player, String text, boolean success) {
        player.sendMessage(Text.literal(text).formatted(success ? Formatting.GREEN : Formatting.RED), false);
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) ->
                new TextInputScreenHandler(id, inventory, done), Text.literal(title).formatted(Formatting.GOLD)));
    }
    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, ignored) ->
                new ActionMenuScreenHandler(id, inv, inventory, actions), Text.literal(title).formatted(Formatting.GOLD)));
    }
}
