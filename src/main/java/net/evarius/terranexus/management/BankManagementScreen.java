package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.BankAccount;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.economy.EconomyTransaction;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.logging.AuditLogger;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BankManagementScreen {
    private BankManagementScreen() {}
    private static int pageSize() { return ConfigManager.desktop().standardEntriesPerPage; }

    public static void open(ServerPlayerEntity player) { open(player, "", 0); }

    private static void open(ServerPlayerEntity player, String query, int requestedPage) {
        if (!mayView(player)) { denied(player); return; }
        EconomyState economy = EconomyState.get(player.getServer());
        ensureKnownAccounts(player, economy);
        List<BankAccount> accounts = economy.allAccounts().stream()
                .filter(account -> matches(player, account, query))
                .sorted(Comparator.comparing(account -> label(player, account.accountKey())))
                .toList();
        int pageSize = pageSize();
        int pages = Math.max(1, (accounts.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_BLOCK, "Bankverwaltung",
                accounts.size() + " Konten · Seite " + (page + 1) + "/" + pages + (query.isBlank() ? "" : " · Suche: " + query));
        button(inventory, actions, 1, Items.COMPASS, "Konten suchen", "RP-Name, Bürger- oder Kontonummer", ignored -> search(player));
        button(inventory, actions, 3, Items.WRITABLE_BOOK, "Alle Kontobewegungen", "Revisionsjournal", ignored -> history(player, null, 0, true));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Bank", ignored -> EconomyScreen.open(player));
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page, ignored -> open(player, query, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2), ignored -> open(player, query, page + 1));
        int slot = 9;
        for (BankAccount account : accounts.subList(page * pageSize, Math.min(accounts.size(), (page + 1) * pageSize))) {
            String detail = (ConfigManager.desktop().showAccountNumbers ? account.accountNumber() + " · " : "") + EconomyState.format(economy.balance(account.accountKey()))
                    + " · " + accountType(account.accountKey()) + " · " + (account.frozen() ? "GESPERRT" : "AKTIV");
            button(inventory, actions, slot++, account.frozen() ? Items.BARRIER : Items.PAPER,
                    label(player, account.accountKey()), detail, ignored -> details(player, account.accountKey(), query, page));
        }
        menu(player, inventory, actions, "Bank · Kontenübersicht");
    }

    private static void details(ServerPlayerEntity player, String accountKey, String query, int returnPage) {
        if (!mayView(player)) { denied(player); return; }
        EconomyState economy = EconomyState.get(player.getServer());
        BankAccount account = economy.account(accountKey);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.PAPER, label(player, accountKey), account.accountNumber());
        ManagementHubScreen.display(inventory, 13, account.frozen() ? Items.BARRIER : Items.GOLD_INGOT,
                "Kontostand", EconomyState.format(economy.balance(accountKey)) + (account.frozen() ? " · Konto gesperrt" : ""));
        button(inventory, actions, 20, Items.WRITABLE_BOOK, "Kontobewegungen", "Vollständiger Buchungsverlauf",
                ignored -> history(player, accountKey, 0, false, query, returnPage));
        if (InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_CASH_OPERATIONS)) {
            button(inventory, actions, 22, Items.LIME_DYE, "Einzahlung", "Bankschalter-Gutschrift", ignored -> cash(player, accountKey, true));
            button(inventory, actions, 24, Items.RED_DYE, "Auszahlung", "Bankschalter-Belastung", ignored -> cash(player, accountKey, false));
        }
        if (!accountKey.startsWith("system:") && (ConfigManager.bank().accountFreezingEnabled || account.frozen())
                && InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_FREEZE_ACCOUNTS)) {
            button(inventory, actions, 31, account.frozen() ? Items.LIME_DYE : Items.BARRIER,
                    account.frozen() ? "Konto entsperren" : "Konto sperren", "Statusänderung wird protokolliert", ignored -> {
                        EconomyState current = EconomyState.get(player.getServer());
                        if ((!ConfigManager.bank().accountFreezingEnabled && !current.isFrozen(accountKey))
                                || !InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_FREEZE_ACCOUNTS)) { denied(player); return; }
                        current.setFrozen(accountKey, !current.isFrozen(accountKey), player.getUuidAsString(),
                                authorizingBank(player, InstitutionPermission.BANK_FREEZE_ACCOUNTS));
                        details(player, accountKey, query, returnPage);
                    });
        }
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Kontenübersicht", ignored -> open(player, query, returnPage));
        menu(player, inventory, actions, "Bank · Kontoakte");
    }

    public static void history(ServerPlayerEntity player, String accountKey, int requestedPage, boolean global) {
        history(player, accountKey, requestedPage, global, "", 0);
    }

    private static void history(ServerPlayerEntity player, String accountKey, int requestedPage, boolean global,
                                String returnQuery, int returnPage) {
        if (!mayView(player)) { denied(player); return; }
        List<EconomyTransaction> entries = accountKey == null
                ? EconomyState.get(player.getServer()).transactions()
                : EconomyState.get(player.getServer()).history(accountKey);
        int pageSize = pageSize();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, global ? "Revisionsjournal" : "Kontobewegungen",
                entries.size() + " Buchungen · Seite " + (page + 1) + "/" + pages);
        Runnable back = global ? () -> open(player) : () -> details(player, accountKey, returnQuery, returnPage);
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Vorherige Ansicht", ignored -> back.run());
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> history(player, accountKey, page - 1, global, returnQuery, returnPage));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> history(player, accountKey, page + 1, global, returnQuery, returnPage));
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        int slot = 9;
        for (EconomyTransaction entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            String title = (entry.successful() ? "✓ " : "✗ ") + entry.type() + " · " + EconomyState.format(entry.amount());
            String detail = format.format(new Date(entry.timestamp())) + " · " + shortLabel(player, entry.sender())
                    + " → " + shortLabel(player, entry.recipient()) + " · " + entry.purpose()
                    + " · Ausgeführt: " + actorLabel(player, entry.actorUuid());
            ManagementHubScreen.display(inventory, slot++, entry.successful() ? Items.PAPER : Items.BARRIER, title, detail);
        }
        menu(player, inventory, actions, "Bank · Buchungsverlauf");
    }

    private static void search(ServerPlayerEntity player) {
        CustomSearchService.open(player, "Bank · Kontosuche", "RP-Name, Bürger- oder Kontonummer", "",
                ConfigManager.bank().searchMinimumCharacters, 64, value -> {
            String query = value == null ? "" : value.trim();
            if (query.length() < ConfigManager.bank().searchMinimumCharacters) {
                error(player, "Der Suchbegriff ist zu kurz."); open(player); return;
            }
            open(player, query, 0);
        }, () -> open(player));
    }

    private static void cash(ServerPlayerEntity player, String account, boolean deposit) {
        if (!InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_CASH_OPERATIONS)) { denied(player); return; }
        input(player, deposit ? "Einzahlungsbetrag" : "Auszahlungsbetrag", value -> {
            Long cents = EconomyState.parseAmount(value, false);
            if (cents == null || cents > ConfigManager.bank().maximumCashOperation) {
                error(player, "Ungültiger Betrag oder konfiguriertes Schalterlimit überschritten."); details(player, account, "", 0); return;
            }
            input(player, "Verwendungszweck", purpose -> {
                if (!InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_CASH_OPERATIONS)) { denied(player); return; }
                boolean success = EconomyState.get(player.getServer()).adjust(account, deposit ? cents : -cents,
                        purpose.isBlank() ? (deposit ? "Bankschalter-Einzahlung" : "Bankschalter-Auszahlung") : purpose.trim(),
                        player.getUuidAsString(), authorizingBank(player, InstitutionPermission.BANK_CASH_OPERATIONS),
                        deposit ? "CASH_DEPOSIT" : "CASH_WITHDRAWAL");
                player.sendMessage(Text.literal(success ? "Buchung erfolgreich." : "Buchung abgelehnt: Kontodeckung oder Kontostatus prüfen.")
                        .formatted(success ? Formatting.GREEN : Formatting.RED), false);
                details(player, account, "", 0);
            });
        });
    }

    private static boolean matches(ServerPlayerEntity player, BankAccount account, String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.toLowerCase(Locale.ROOT).replace(" ", "");
        return account.accountNumber().toLowerCase(Locale.ROOT).contains(needle)
                || label(player, account.accountKey()).toLowerCase(Locale.ROOT).replace(" ", "").contains(needle)
                || account.accountKey().toLowerCase(Locale.ROOT).contains(needle);
    }

    static void ensureKnownAccounts(ServerPlayerEntity player, EconomyState economy) {
        for (CitizenIdentity identity : IdentityState.get(player.getServer()).all()) {
            try { economy.ensureAccount(EconomyState.playerAccount(UUID.fromString(identity.playerUuid()))); }
            catch (IllegalArgumentException ignored) {}
        }
        for (Institution institution : InstitutionState.get(player.getServer()).all()) economy.ensureAccount(EconomyState.institutionAccount(institution.id()));
        for (AdministrativeArea area : LandManagementState.get(player.getServer()).areas()) economy.ensureAccount(EconomyState.areaAccount(area.id()));
    }

    static String label(ServerPlayerEntity player, String account) {
        if (account.equals("CASH")) return "Bargeld/Schalter";
        if (account.startsWith("institution:")) {
            Institution institution = InstitutionState.get(player.getServer()).get(account.substring("institution:".length()));
            return institution == null ? "Unbekannte Institution" : institution.name();
        }
        if (account.startsWith("area:")) {
            AdministrativeArea area = LandManagementState.get(player.getServer()).area(account.substring("area:".length()));
            return area == null ? "Unbekanntes Gebiet" : area.name();
        }
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(account));
            return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName() + " · " + identity.citizenNumber();
        } catch (IllegalArgumentException ignored) { return account; }
    }
    private static String accountType(String account) {
        if (account.startsWith("institution:")) return "INSTITUTION";
        if (account.startsWith("area:")) return "VERWALTUNG";
        if (account.startsWith("system:")) return "SYSTEM";
        return "PRIVAT";
    }
    private static String shortLabel(ServerPlayerEntity player, String account) {
        String value = label(player, account);
        return value.length() > 24 ? value.substring(0, 24) : value;
    }
    private static String actorLabel(ServerPlayerEntity player, String actor) {
        if (actor == null || actor.isBlank()) return "nicht dokumentiert";
        if (actor.equals("SYSTEM") || actor.equals("CONSOLE") || actor.equals("TNADMIN_TEST")) return actor;
        try { return label(player, EconomyState.playerAccount(UUID.fromString(actor))); }
        catch (IllegalArgumentException ignored) { return actor; }
    }
    private static String authorizingBank(ServerPlayerEntity player, InstitutionPermission permission) {
        for (Institution institution : InstitutionState.get(player.getServer()).forMember(player.getUuid())) {
            String type = institution.type().toLowerCase(Locale.ROOT);
            if ((type.contains("bank") || type.contains("finanz"))
                    && InstitutionAccess.has(player, institution.id(), permission)) return institution.id();
        }
        return AuthorityState.isTnAdmin(player) ? "TNADMIN_TEST" : "";
    }
    private static boolean mayView(ServerPlayerEntity player) {
        return InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_VIEW_ACCOUNTS)
                || InstitutionAccess.hasCentralBankPermission(player, InstitutionPermission.CENTRAL_BANK_VIEW);
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) ->
                new TextInputScreenHandler(id, inventory, done), Text.literal(title).formatted(Formatting.GOLD)));
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String name, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.GOLD));
    }
    private static void denied(ServerPlayerEntity player) { AuditLogger.denied(player,"bank","management");error(player, "Keine Berechtigung für die Bankverwaltung."); }
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
}
