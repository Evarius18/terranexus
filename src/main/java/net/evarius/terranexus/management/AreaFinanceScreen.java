package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.economy.EconomyTransaction;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.AreaEmployment;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.logging.AuditLogger;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class AreaFinanceScreen {
    private AreaFinanceScreen() {}

    public static boolean hasManagedArea(ServerPlayerEntity player) {
        LandManagementState state = LandManagementState.get(player.getServer());
        return state.areas().stream().anyMatch(area -> !area.id().equals(LandManagementState.ROOT_AREA_ID)
                && state.mayManageAreaFinances(area.id(), player));
    }

    public static void open(ServerPlayerEntity player) { open(player, 0); }

    private static void open(ServerPlayerEntity player, int requestedPage) {
        if (!hasManagedArea(player)) { denied(player); return; }
        LandManagementState state = LandManagementState.get(player.getServer());
        List<AdministrativeArea> areas = state.areas().stream()
                .filter(area -> !area.id().equals(LandManagementState.ROOT_AREA_ID) && state.mayManageAreaFinances(area.id(), player)).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (areas.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.FILLED_MAP, "Verwaltungsfinanzen", areas.size() + " Einheiten · Seite " + (page + 1) + "/" + pages);
        if (page > 0) displayButton(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> open(player, page - 1));
        if (page + 1 < pages) displayButton(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> open(player, page + 1));
        int slot = 9;
        for (AdministrativeArea area : areas.subList(page * pageSize, Math.min(areas.size(), (page + 1) * pageSize))) {
            String account = EconomyState.areaAccount(area.id());
            displayButton(inventory, actions, slot++, Items.MAP, area.name(), area.type() + " · "
                    + EconomyState.format(EconomyState.get(player.getServer()).balance(account)), ignored -> area(player, area.id(), 0));
        }
        displayButton(inventory, actions, 49, Items.ARROW, "Zurück", "Zum Admin-Desktop", ignored -> AdminDesktopScreen.open(player));
        menu(player, inventory, actions, "Verwaltung · Finanzen");
    }

    public static void openArea(ServerPlayerEntity player, String areaId) { area(player, areaId, 0); }

    private static void area(ServerPlayerEntity player, String areaId, int employeePage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        AdministrativeArea area = state.area(areaId);
        if (area == null || !state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        EconomyState economy = EconomyState.get(player.getServer());
        String account = EconomyState.areaAccount(areaId);
        var bankAccount = economy.ensureAccount(account);
        List<AreaEmployment> employees = state.areaEmployees(areaId);
        int pageSize = Math.min(36, ConfigManager.desktop().standardEntriesPerPage);
        int pages = Math.max(1, (employees.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(employeePage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.GOLD_BLOCK, area.name(), bankAccount.accountNumber() + " · " + EconomyState.format(economy.balance(account)));
        displayButton(inventory, actions, 1, Items.PLAYER_HEAD, "Mitarbeiter einstellen", "Freigeschaltete Bürgerakte auswählen",
                ignored -> candidates(player, areaId, 0));
        displayButton(inventory, actions, 3, Items.WRITABLE_BOOK, "Gehaltsjournal", "Auszahlungen und fehlgeschlagene Läufe",
                ignored -> payrollHistory(player, areaId, 0));
        displayButton(inventory, actions, 5, Items.FILLED_MAP, "Grundstücke und Gebäude", "Verkaufen und vermieten",
                ignored -> properties(player, areaId, 0));
        displayButton(inventory, actions, 6, Items.GOLD_INGOT, "Zahlungsverkehr", "Überweisungen vom Verwaltungskonto",
                ignored -> EconomyScreen.openArea(player, areaId));
        displayButton(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Gebietsauswahl", ignored -> open(player));
        if (page > 0) displayButton(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> area(player, areaId, page - 1));
        if (page + 1 < pages) displayButton(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> area(player, areaId, page + 1));
        int slot = 9;
        for (AreaEmployment employee : employees.subList(page * pageSize, Math.min(employees.size(), (page + 1) * pageSize))) {
            UUID id = parse(employee.playerUuid());
            String name = id == null ? employee.playerUuid() : citizenName(player, id);
            displayButton(inventory, actions, slot++, Items.PAPER, name,
                    employee.salaryGroup() + " · " + EconomyState.format(employee.salary()) + " · fällig " + date(employee.nextPayAt()),
                    ignored -> employee(player, areaId, employee.playerUuid(), page));
        }
        menu(player, inventory, actions, "Verwaltung · " + area.name());
    }

    private static void properties(ServerPlayerEntity player, String areaId, int requestedPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        AdministrativeArea area = state.area(areaId);
        if (area == null || !state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        List<LandProperty> properties = LandlordState.get(player.getServer()).all().stream()
                .filter(property -> property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                        && property.ownerId().equals(areaId)).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (properties.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.FILLED_MAP, area.name() + " · Flächen", properties.size() + " Einträge · Seite " + (page + 1) + "/" + pages);
        if (page > 0) displayButton(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> properties(player, areaId, page - 1));
        if (page + 1 < pages) displayButton(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> properties(player, areaId, page + 1));
        displayButton(inventory, actions, 8, Items.ARROW, "Zurück", "Zu den Verwaltungsfinanzen", ignored -> area(player, areaId, 0));
        int slot = 9;
        for (LandProperty property : properties.subList(page * pageSize, Math.min(properties.size(), (page + 1) * pageSize)))
            displayButton(inventory, actions, slot++, Items.PAPER, property.name(), state.address(property.id()) + " · " + state.landUse(property.id()),
                    ignored -> PropertyFinanceScreen.open(player, property));
        menu(player, inventory, actions, "Verwaltung · Flächenhandel");
    }

    private static void employee(ServerPlayerEntity player, String areaId, String playerUuid, int returnPage) {
        UUID id = parse(playerUuid);
        LandManagementState state = LandManagementState.get(player.getServer());
        AreaEmployment employee = id == null ? null : state.areaEmployee(areaId, id);
        if (employee == null || !state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 13, Items.PLAYER_HEAD, citizenName(player, id), "Beschäftigt seit " + date(employee.joinedAt()));
        display(inventory, 22, Items.GOLD_INGOT, employee.salaryGroup(), EconomyState.format(employee.salary()));
        displayButton(inventory, actions, 30, Items.COMPARATOR, "Gehaltsgruppe ändern", "Aus konfigurierten Gruppen auswählen",
                ignored -> salaryGroups(player, areaId, id, returnPage, false));
        displayButton(inventory, actions, 32, Items.BARRIER, "Beschäftigung beenden", "Entfernt den Mitarbeiter aus dieser Verwaltung", ignored -> {
            boolean removed = LandManagementState.get(player.getServer()).dismissAreaEmployee(player, areaId, id);
            player.sendMessage(Text.literal(removed ? "Beschäftigung wurde beendet." : "Änderung wurde abgelehnt.")
                    .formatted(removed ? Formatting.YELLOW : Formatting.RED), false);
            area(player, areaId, returnPage);
        });
        displayButton(inventory, actions, 49, Items.ARROW, "Zurück", "Zur Mitarbeiterliste", ignored -> area(player, areaId, returnPage));
        menu(player, inventory, actions, "Verwaltung · Personalakte");
    }

    private static void candidates(ServerPlayerEntity player, String areaId, int requestedPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        if (!state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        List<CitizenIdentity> candidates = IdentityState.get(player.getServer()).allApproved().stream()
                .filter(identity -> {
                    UUID id = parse(identity.playerUuid()); return id != null && state.areaEmployee(areaId, id) == null;
                }).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.PLAYER_HEAD, "Mitarbeiter einstellen", candidates.size() + " verfügbare Bürger · Seite " + (page + 1) + "/" + pages);
        if (page > 0) displayButton(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> candidates(player, areaId, page - 1));
        if (page + 1 < pages) displayButton(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> candidates(player, areaId, page + 1));
        displayButton(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Verwaltung", ignored -> area(player, areaId, 0));
        int slot = 9;
        for (CitizenIdentity candidate : candidates.subList(page * pageSize, Math.min(candidates.size(), (page + 1) * pageSize))) {
            UUID id = parse(candidate.playerUuid());
            displayButton(inventory, actions, slot++, Items.PAPER, candidate.firstName() + " " + candidate.lastName(), candidate.citizenNumber(),
                    ignored -> salaryGroups(player, areaId, id, 0, true));
        }
        menu(player, inventory, actions, "Verwaltung · Bürgerauswahl");
    }

    private static void salaryGroups(ServerPlayerEntity player, String areaId, UUID employeeId, int returnPage, boolean hiring) {
        LandManagementState state = LandManagementState.get(player.getServer());
        if (employeeId == null || !state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.GOLD_INGOT, "Gehaltsgruppe wählen", citizenName(player, employeeId));
        int slot = 18;
        for (Map.Entry<String, Long> group : ConfigManager.salary().administrationSalaryGroups.entrySet()) {
            displayButton(inventory, actions, slot++, Items.PAPER, group.getKey(), EconomyState.format(group.getValue()), ignored -> {
                LandManagementState current = LandManagementState.get(player.getServer());
                boolean success = hiring
                        ? current.hireAreaEmployee(player, areaId, employeeId, group.getKey())
                        : current.setAreaSalaryGroup(player, areaId, employeeId, group.getKey());
                player.sendMessage(Text.literal(success ? "Personal- und Gehaltsdaten wurden gespeichert." : "Änderung wurde abgelehnt.")
                        .formatted(success ? Formatting.GREEN : Formatting.RED), false);
                area(player, areaId, returnPage);
            });
        }
        displayButton(inventory, actions, 49, Items.ARROW, "Zurück", "Abbrechen",
                ignored -> { if (hiring) candidates(player, areaId, 0); else employee(player, areaId, employeeId.toString(), returnPage); });
        menu(player, inventory, actions, "Verwaltung · Gehaltsgruppen");
    }

    private static void payrollHistory(ServerPlayerEntity player, String areaId, int requestedPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        if (!state.mayManageAreaFinances(areaId, player)) { denied(player); return; }
        List<EconomyTransaction> entries = EconomyState.get(player.getServer()).history(EconomyState.areaAccount(areaId)).stream()
                .filter(entry -> entry.type().equals("ADMIN_SALARY")).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.WRITABLE_BOOK, "Gehaltsjournal", entries.size() + " Läufe · Seite " + (page + 1) + "/" + pages);
        if (page > 0) displayButton(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> payrollHistory(player, areaId, page - 1));
        if (page + 1 < pages) displayButton(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> payrollHistory(player, areaId, page + 1));
        displayButton(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Verwaltung", ignored -> area(player, areaId, 0));
        int slot = 9;
        for (EconomyTransaction entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize)))
            display(inventory, slot++, entry.successful() ? Items.PAPER : Items.BARRIER,
                    (entry.successful() ? "✓ " : "✗ ") + EconomyState.format(entry.amount()),
                    date(entry.timestamp()) + " · " + BankManagementScreen.label(player, entry.recipient()));
        menu(player, inventory, actions, "Verwaltung · Gehaltsjournal");
    }

    private static String citizenName(ServerPlayerEntity player, UUID id) {
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(id);
        return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName();
    }
    private static UUID parse(String value) { try { return UUID.fromString(value); } catch (RuntimeException ignored) { return null; } }
    private static String date(long timestamp) { return timestamp <= 0 ? "offen" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp)); }
    private static void denied(ServerPlayerEntity player) {
        AuditLogger.denied(player, "administration_finance", "access");
        player.sendMessage(Text.literal("Keine Berechtigung für diese Verwaltungsfinanzen.").formatted(Formatting.RED), false);
    }
    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
    }
    private static void displayButton(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                      int slot, Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, ignored) ->
                new ActionMenuScreenHandler(id, inv, inventory, actions), Text.literal(title).formatted(Formatting.GOLD)));
    }
}
