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
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class EconomyScreen {
    private static final Map<UUID, String> SELECTED_ACCOUNTS = new HashMap<>();
    private EconomyScreen() {}

    public static void open(ServerPlayerEntity player) {
        EconomyState economy = EconomyState.get(player.getServer());
        String source = selectedAccount(player);
        BankAccount account = economy.ensureAccount(source);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_INGOT, accountLabel(player, source),
                (ConfigManager.desktop().showAccountNumbers ? account.accountNumber() + " · " : "")
                        + EconomyState.format(economy.balance(source)) + (account.frozen() ? " · GESPERRT" : ""));
        button(inventory, actions, 1, Items.WRITABLE_BOOK, "Kontobewegungen", "Buchungen und Salden", ignored -> history(player, source, 0));
        if (InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_VIEW_ACCOUNTS))
            button(inventory, actions, 2, Items.COMPARATOR, "Bankverwaltung", "Kontensuche, Schalter und Revision", ignored -> BankManagementScreen.open(player));
        button(inventory, actions, 7, Items.COMPASS, "Konto wechseln", "Persönliches, Institutions- oder Gebietskonto", ignored -> openAccountSelection(player, 0));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur vorherigen Übersicht", ignored -> home(player));

        if (canTransferFrom(player, source)) {
            button(inventory, actions, 3, Items.COMPASS, "Empfänger suchen", "RP-Name, Bürger- oder Kontonummer", ignored -> recipientSearch(player, source));
            int slot = 9;
            for (CitizenIdentity identity : IdentityState.get(player.getServer()).allApproved()) {
                UUID recipientId = UUID.fromString(identity.playerUuid());
                String target = EconomyState.playerAccount(recipientId);
                if (target.equals(source) || slot >= 36) continue;
                String rpName = identity.firstName() + " " + identity.lastName();
                button(inventory, actions, slot++, Items.PLAYER_HEAD, "Überweisen an " + rpName, identity.citizenNumber(),
                        ignored -> openAmountInput(player, source, target, rpName));
            }
            slot = 36;
            for (Institution institution : InstitutionState.get(player.getServer()).all()) {
                String target = EconomyState.institutionAccount(institution.id());
                if (target.equals(source) || slot >= 48) continue;
                button(inventory, actions, slot++, Items.BRICKS, "Überweisen an " + institution.name(), institution.type(),
                        ignored -> openAmountInput(player, source, target, institution.name()));
            }
            for (AdministrativeArea area : LandManagementState.get(player.getServer()).areas()) {
                String target = EconomyState.areaAccount(area.id());
                if (target.equals(source) || slot >= 54) continue;
                button(inventory, actions, slot++, Items.FILLED_MAP, "Überweisen an " + area.name(), area.type(),
                        ignored -> openAmountInput(player, source, target, area.name()));
            }
        } else {
            ManagementHubScreen.display(inventory, 22, Items.BARRIER, "Nur Lesezugriff",
                    account.frozen() ? "Das Konto ist gesperrt." : "Deine Rolle erlaubt keine ausgehenden Buchungen.");
        }
        openMenu(player, inventory, actions, "TerraNexus Bank");
    }

    public static void openInstitution(ServerPlayerEntity player, String institutionId) {
        if (!InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_FINANCES)) {
            error(player, "Keine Berechtigung für diese Institutionsfinanzen."); return;
        }
        SELECTED_ACCOUNTS.put(player.getUuid(), EconomyState.institutionAccount(institutionId));
        open(player);
    }

    private static void openAccountSelection(ServerPlayerEntity player, int requestedPage) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        EconomyState economy = EconomyState.get(player.getServer());
        List<AccountChoice> choices = new ArrayList<>();
        String personal = EconomyState.playerAccount(player.getUuid());
        choices.add(new AccountChoice(personal, "Persönliches Konto", Items.PLAYER_HEAD));
        InstitutionState institutions = InstitutionState.get(player.getServer());
        for (Institution institution : institutions.all()) {
            if (!InstitutionAccess.has(player, institution.id(), InstitutionPermission.VIEW_FINANCES)) continue;
            String account = EconomyState.institutionAccount(institution.id());
            choices.add(new AccountChoice(account, institution.name(), Items.BRICKS));
        }
        for (AdministrativeArea area : LandManagementState.get(player.getServer()).areas()) {
            if (!area.ownerId().equals(player.getUuidAsString()) && !AuthorityState.mayManageLand(player)) continue;
            String account = EconomyState.areaAccount(area.id());
            choices.add(new AccountChoice(account, area.type() + " · " + area.name(), Items.FILLED_MAP));
        }
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (choices.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        ManagementHubScreen.display(inventory, 4, Items.COMPASS, "Konten", choices.size() + " verfügbar · Seite " + (page + 1) + "/" + pages);
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> openAccountSelection(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> openAccountSelection(player, page + 1));
        int slot = 9;
        for (AccountChoice choice : choices.subList(page * pageSize, Math.min(choices.size(), (page + 1) * pageSize))) {
            button(inventory, actions, slot++, choice.item(), choice.label(), accountDetail(economy, choice.account()),
                    ignored -> select(player, choice.account()));
        }
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Bank", ignored -> open(player));
        openMenu(player, inventory, actions, "Konto auswählen");
    }

    private static void history(ServerPlayerEntity player, String account, int requestedPage) {
        if (!mayViewAccount(player, account)) { error(player, "Keine Berechtigung für diesen Buchungsverlauf."); open(player); return; }
        var entries = EconomyState.get(player.getServer()).history(account);
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Kontobewegungen", entries.size() + " Buchungen · Seite " + (page + 1) + "/" + pages);
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Kontoübersicht", ignored -> open(player));
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page, ignored -> history(player, account, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2), ignored -> history(player, account, page + 1));
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        int slot = 9;
        for (EconomyTransaction entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            boolean incoming = entry.recipient().equals(account);
            String title = (entry.successful() ? (incoming ? "+ " : "- ") : "✗ ") + EconomyState.format(entry.amount()) + " · " + entry.type();
            String other = BankManagementScreen.label(player, incoming ? entry.sender() : entry.recipient());
            ManagementHubScreen.display(inventory, slot++, entry.successful() ? Items.PAPER : Items.BARRIER, title,
                    format.format(new Date(entry.timestamp())) + " · " + other + " · " + entry.purpose());
        }
        openMenu(player, inventory, actions, "Bank · Kontobewegungen");
    }

    private static void openAmountInput(ServerPlayerEntity sender, String source, String target, String recipientName) {
        input(sender, "Betrag an " + recipientName, value -> {
            Long cents = EconomyState.parseAmount(value, false);
            if (cents == null) { error(sender, "Ungültiger Betrag. Beispiel: 125,50"); open(sender); return; }
            input(sender, "Verwendungszweck", purpose -> {
                if (!selectedAccount(sender).equals(source) || !canTransferFrom(sender, source)) {
                    error(sender, "Die Berechtigung oder der Kontostatus hat sich geändert."); open(sender); return;
                }
                String institutionId = source.startsWith("institution:") ? source.substring("institution:".length()) : "";
                boolean success = EconomyState.get(sender.getServer()).transfer(source, target, cents,
                        purpose.isBlank() ? "Überweisung" : purpose.trim(), sender.getUuidAsString(), institutionId, "TRANSFER");
                sender.sendMessage(Text.literal(success ? EconomyState.format(cents) + " wurden an " + recipientName + " überwiesen."
                        : "Überweisung abgelehnt: Kontodeckung oder Kontostatus prüfen.").formatted(success ? Formatting.GREEN : Formatting.RED), false);
                open(sender);
            });
        });
    }

    private static void recipientSearch(ServerPlayerEntity player, String source) {
        input(player, "Empfänger suchen", value -> {
            String query = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            if (query.isBlank()) { error(player, "Bitte einen Suchbegriff eingeben."); open(player); return; }
            EconomyState economy = EconomyState.get(player.getServer());
            for (CitizenIdentity identity : IdentityState.get(player.getServer()).allApproved())
                economy.ensureAccount(EconomyState.playerAccount(UUID.fromString(identity.playerUuid())));
            for (Institution institution : InstitutionState.get(player.getServer()).all())
                economy.ensureAccount(EconomyState.institutionAccount(institution.id()));
            BankAccount byNumber = economy.findByNumber(query);
            if (byNumber != null && !byNumber.accountKey().equals(source)) {
                openAmountInput(player, source, byNumber.accountKey(), BankManagementScreen.label(player, byNumber.accountKey())); return;
            }
            for (CitizenIdentity identity : IdentityState.get(player.getServer()).allApproved()) {
                String name = (identity.firstName() + " " + identity.lastName()).toLowerCase(java.util.Locale.ROOT);
                if (name.contains(query) || identity.citizenNumber().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    String target = EconomyState.playerAccount(UUID.fromString(identity.playerUuid())); economy.ensureAccount(target);
                    if (!target.equals(source)) { openAmountInput(player, source, target, identity.firstName() + " " + identity.lastName()); return; }
                }
            }
            for (Institution institution : InstitutionState.get(player.getServer()).all()) {
                if (institution.name().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    String target = EconomyState.institutionAccount(institution.id()); economy.ensureAccount(target);
                    if (!target.equals(source)) { openAmountInput(player, source, target, institution.name()); return; }
                }
            }
            error(player, "Kein Empfängerkonto gefunden."); open(player);
        });
    }

    private static boolean mayViewAccount(ServerPlayerEntity player, String account) {
        if (account.equals(EconomyState.playerAccount(player.getUuid()))) return true;
        if (account.startsWith("institution:")) return InstitutionAccess.has(player, account.substring("institution:".length()), InstitutionPermission.VIEW_FINANCES);
        if (account.startsWith("area:")) {
            AdministrativeArea area = LandManagementState.get(player.getServer()).area(account.substring("area:".length()));
            return area != null && (area.ownerId().equals(player.getUuidAsString()) || AuthorityState.mayManageLand(player));
        }
        return false;
    }
    private static boolean canTransferFrom(ServerPlayerEntity player, String account) {
        if (EconomyState.get(player.getServer()).isFrozen(account)) return false;
        if (account.equals(EconomyState.playerAccount(player.getUuid()))) return true;
        if (account.startsWith("institution:")) return InstitutionAccess.has(player, account.substring("institution:".length()), InstitutionPermission.MANAGE_FINANCES);
        if (account.startsWith("area:")) return mayViewAccount(player, account);
        return false;
    }
    private static String selectedAccount(ServerPlayerEntity player) {
        String personal = EconomyState.playerAccount(player.getUuid());
        String selected = SELECTED_ACCOUNTS.getOrDefault(player.getUuid(), personal);
        if (!mayViewAccount(player, selected)) { SELECTED_ACCOUNTS.put(player.getUuid(), personal); return personal; }
        return selected;
    }
    private static void select(ServerPlayerEntity player, String account) { SELECTED_ACCOUNTS.put(player.getUuid(), account); open(player); }
    private static String accountLabel(ServerPlayerEntity player, String account) { return "Konto · " + BankManagementScreen.label(player, account); }
    private static String accountDetail(EconomyState economy, String account) {
        BankAccount data = economy.ensureAccount(account);
        return (ConfigManager.desktop().showAccountNumbers ? data.accountNumber() + " · " : "")
                + EconomyState.format(economy.balance(account)) + (data.frozen() ? " · GESPERRT" : "");
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) -> new TextInputScreenHandler(id, inventory, done), Text.literal(title).formatted(Formatting.GOLD)));
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory,
                                 Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, ignored) -> new ActionMenuScreenHandler(id, inv, inventory, actions), Text.literal(title).formatted(Formatting.GOLD)));
    }
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
    private static void home(ServerPlayerEntity player) {
        if (AuthorityState.mayManageIdentity(player) || AuthorityState.mayUseLandOffice(player)
                || !InstitutionState.get(player.getServer()).forMember(player.getUuid()).isEmpty()) AdminDesktopScreen.open(player);
        else ManagementHubScreen.open(player);
    }

    public static void retainOnline(java.util.Set<UUID> online) { SELECTED_ACCOUNTS.keySet().retainAll(online); }

    private record AccountChoice(String account, String label, net.minecraft.item.Item item) {}
}
