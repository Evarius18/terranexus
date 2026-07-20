package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.BankAccount;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.economy.EconomyTransaction;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionEmployee;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionRole;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.logging.AuditLogger;
import net.minecraft.inventory.SimpleInventory;
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

public final class InstitutionManagementScreen {
    private InstitutionManagementScreen() {}

    private static int pageSize() { return ConfigManager.desktop().standardEntriesPerPage; }

    public static void open(ServerPlayerEntity player, String institutionId) {
        Institution institution = current(player, institutionId);
        if (institution == null || !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        InstitutionState state = InstitutionState.get(player.getServer());
        EconomyState economy = EconomyState.get(player.getServer());
        String accountKey = EconomyState.institutionAccount(institutionId);
        BankAccount account = economy.ensureAccount(accountKey);
        InstitutionEmployee ownRecord = state.employee(institutionId, player.getUuid());
        String ownRole = ownRecord == null ? "TNAdmin-Testzugriff" : ownRecord.institutionRole().label();
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.BRICKS, institution.name(), institution.type() + " · " + ownRole);
        ManagementHubScreen.display(inventory, 13, Items.GOLD_BLOCK, "Institutionskonto",
                (ConfigManager.desktop().showAccountNumbers ? account.accountNumber() + " · " : "")
                        + EconomyState.format(economy.balance(accountKey)));
        ManagementHubScreen.display(inventory, 15, Items.NAME_TAG, "Eigentümer", citizenName(player, institution.ownerUuid()));
        button(inventory, actions, 19, Items.PLAYER_HEAD, "Mitarbeiterverwaltung",
                state.employees(institutionId).size() + " Beschäftigte", ignored -> employees(player, institutionId, 0));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_FINANCES))
            button(inventory, actions, 21, Items.GOLD_INGOT, "Finanzverwaltung", "Kontostand, Überweisungen und Buchungsverlauf",
                    ignored -> EconomyScreen.openInstitution(player, institutionId));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_FINANCES))
            button(inventory, actions, 23, Items.CLOCK, "Gehaltsverwaltung", "Gehälter, nächste Läufe und Auszahlungshistorie",
                    ignored -> payroll(player, institutionId, 0));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_SETTINGS)
                || InstitutionAccess.has(player, institutionId, InstitutionPermission.TRANSFER_OWNERSHIP))
            button(inventory, actions, 25, Items.COMPARATOR, "Institutionseinstellungen", "Eigentum und Stammdaten",
                    ignored -> settings(player, institutionId));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Institutionsübersicht", ignored -> InstitutionScreen.open(player));
        menu(player, inventory, actions, "Institution · Desktop");
    }

    private static void employees(ServerPlayerEntity player, String institutionId, int requestedPage) {
        Institution institution = current(player, institutionId);
        if (institution == null || !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        List<InstitutionEmployee> employees = InstitutionState.get(player.getServer()).employees(institutionId);
        int pageSize = pageSize();
        int pages = Math.max(1, (employees.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.PLAYER_HEAD, "Mitarbeiterliste",
                employees.size() + " Beschäftigte · Seite " + (page + 1) + "/" + pages);
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_EMPLOYEES))
            button(inventory, actions, 1, Items.EMERALD, "Mitarbeiter einstellen", "Registrierte Bürger auswählen", ignored -> hireCandidates(player, institutionId, 0));
        navigation(inventory, actions, player, page, pages, () -> employees(player, institutionId, page - 1),
                () -> employees(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zum Institutionsdesktop", ignored -> open(player, institutionId));
        int slot = 9;
        for (InstitutionEmployee employee : employees.subList(page * pageSize, Math.min(employees.size(), (page + 1) * pageSize))) {
            String detail = employee.institutionRole().label() + " · Eintritt " + date(employee.joinedAt())
                    + " · Gehalt " + EconomyState.format(employee.salary());
            button(inventory, actions, slot++, Items.PLAYER_HEAD, citizenName(player, employee.playerUuid()), detail,
                    ignored -> employeeDetails(player, institutionId, UUID.fromString(employee.playerUuid()), page));
        }
        menu(player, inventory, actions, "Institution · Personal");
    }

    private static void employeeDetails(ServerPlayerEntity player, String institutionId, UUID employeeId, int returnPage) {
        Institution institution = current(player, institutionId);
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, employeeId);
        if (institution == null || employee == null || !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.PLAYER_HEAD, citizenName(player, employee.playerUuid()), employee.institutionRole().label());
        ManagementHubScreen.display(inventory, 13, Items.CLOCK, "Beschäftigungsdaten", "Eintritt " + date(employee.joinedAt()) + " · nächste Zahlung " + date(employee.nextPayAt()));
        ManagementHubScreen.display(inventory, 15, Items.GOLD_INGOT, "Gehalt", EconomyState.format(employee.salary()));
        ManagementHubScreen.display(inventory, 17, Items.WRITABLE_BOOK, "Personalvermerk",
                employee.personnelNote().isBlank() ? "Kein Vermerk" : employee.personnelNote());
        if (employee.institutionRole() != InstitutionRole.OWNER
                && InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_ROLES))
            button(inventory, actions, 20, Items.NAME_TAG, "Rolle ändern", "Aktuell: " + employee.institutionRole().label(),
                    ignored -> selectRole(player, institutionId, employeeId, returnPage));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_SALARIES))
            button(inventory, actions, 22, Items.GOLD_NUGGET, "Gehalt ändern", "Auszahlung je Gehaltsperiode",
                    ignored -> salaryInput(player, institutionId, employeeId, returnPage));
        if (employee.institutionRole() != InstitutionRole.OWNER
                && InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_EMPLOYEES))
            button(inventory, actions, 24, Items.BARRIER, "Mitarbeiter entlassen", "Beschäftigungsverhältnis beenden", ignored -> {
                confirmFire(player, institutionId, employeeId, returnPage);
            });
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_EMPLOYEES))
            button(inventory, actions, 26, Items.WRITABLE_BOOK, "Personalvermerk bearbeiten", "Interner HR-Vermerk", ignored ->
                    input(player, "Personalvermerk", value -> {
                        if (!InstitutionState.get(player.getServer()).setPersonnelNote(player, institutionId, employeeId, value))
                            error(player, "Vermerk ungültig oder keine Berechtigung.");
                        employeeDetails(player, institutionId, employeeId, returnPage);
                    }));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Mitarbeiterliste", ignored -> employees(player, institutionId, returnPage));
        menu(player, inventory, actions, "Institution · Personalakte");
    }

    private static void hireCandidates(ServerPlayerEntity player, String institutionId, int requestedPage) {
        if (!InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_EMPLOYEES)) { denied(player); return; }
        Institution institution = current(player, institutionId);
        if (institution == null) return;
        List<CitizenIdentity> candidates = IdentityState.get(player.getServer()).allApproved().stream()
                .filter(identity -> !institution.employees().containsKey(identity.playerUuid())).toList();
        int pageSize = pageSize();
        int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.EMERALD, "Einstellung", candidates.size() + " verfügbare Bürger");
        navigation(inventory, actions, player, page, pages, () -> hireCandidates(player, institutionId, page - 1),
                () -> hireCandidates(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Mitarbeiterliste", ignored -> employees(player, institutionId, 0));
        int slot = 9;
        for (CitizenIdentity candidate : candidates.subList(page * pageSize, Math.min(candidates.size(), (page + 1) * pageSize))) {
            UUID target = UUID.fromString(candidate.playerUuid());
            button(inventory, actions, slot++, Items.PLAYER_HEAD, candidate.firstName() + " " + candidate.lastName(), candidate.citizenNumber(), ignored -> {
                if (!InstitutionState.get(player.getServer()).hire(player, institutionId, target, InstitutionState.configuredDefaultRole())) error(player, "Einstellung nicht zulässig oder Mitarbeitergrenze erreicht.");
                employees(player, institutionId, 0);
            });
        }
        menu(player, inventory, actions, "Institution · Einstellung");
    }

    private static void selectRole(ServerPlayerEntity player, String institutionId, UUID employeeId, int returnPage) {
        InstitutionState state = InstitutionState.get(player.getServer());
        List<SelectionMenuScreen.Option> options = java.util.Arrays.stream(InstitutionRole.values())
                .filter(InstitutionState::isRoleEnabled)
                .filter(role -> state.canAssignRole(player, institutionId, employeeId, role))
                .map(role -> new SelectionMenuScreen.Option(role.id(), role.label(), roleDescription(role), Items.NAME_TAG)).toList();
        SelectionMenuScreen.open(player, "Rolle vergeben", options, value -> {
            if (!InstitutionState.get(player.getServer()).setRole(player, institutionId, employeeId, InstitutionRole.fromId(value)))
                error(player, "Diese Rollenzuweisung ist aufgrund der Hierarchie nicht zulässig.");
            employeeDetails(player, institutionId, employeeId, returnPage);
        }, () -> employeeDetails(player, institutionId, employeeId, returnPage));
    }

    private static void salaryInput(ServerPlayerEntity player, String institutionId, UUID employeeId, int returnPage) {
        input(player, "Gehalt je Periode", value -> {
            Long salary = EconomyState.parseAmount(value, true);
            if (salary == null || !InstitutionState.get(player.getServer()).setSalary(player, institutionId, employeeId, salary))
                error(player, "Gehalt ungültig oder keine Berechtigung.");
            employeeDetails(player, institutionId, employeeId, returnPage);
        });
    }

    private static void payroll(ServerPlayerEntity player, String institutionId, int requestedPage) {
        if (!InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_FINANCES)) { denied(player); return; }
        List<InstitutionEmployee> entries = InstitutionState.get(player.getServer()).employees(institutionId);
        int pageSize = pageSize();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        long payroll = entries.stream().mapToLong(InstitutionEmployee::salary).sum();
        ManagementHubScreen.display(inventory, 4, Items.CLOCK, "Gehaltslauf", "Gesamtsumme je Periode: " + EconomyState.format(payroll));
        button(inventory, actions, 1, Items.WRITABLE_BOOK, "Auszahlungshistorie", "Erfolgreiche und fehlgeschlagene Gehaltsbuchungen",
                ignored -> payrollHistory(player, institutionId, 0));
        navigation(inventory, actions, player, page, pages, () -> payroll(player, institutionId, page - 1), () -> payroll(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zum Institutionsdesktop", ignored -> open(player, institutionId));
        int slot = 9;
        for (InstitutionEmployee employee : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            String detail = EconomyState.format(employee.salary()) + " · nächste Zahlung " + date(employee.nextPayAt());
            if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_SALARIES))
                button(inventory, actions, slot++, Items.GOLD_NUGGET, citizenName(player, employee.playerUuid()), detail,
                        ignored -> salaryInput(player, institutionId, UUID.fromString(employee.playerUuid()), page));
            else ManagementHubScreen.display(inventory, slot++, Items.GOLD_NUGGET, citizenName(player, employee.playerUuid()), detail);
        }
        menu(player, inventory, actions, "Institution · Gehälter");
    }

    private static void payrollHistory(ServerPlayerEntity player, String institutionId, int requestedPage) {
        if (!InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_FINANCES)) { denied(player); return; }
        List<EconomyTransaction> entries = EconomyState.get(player.getServer()).institutionHistory(institutionId).stream()
                .filter(entry -> entry.type().equals("SALARY")).toList();
        int pageSize = pageSize();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Gehaltsbuchungen",
                entries.size() + " Buchungen · Seite " + (page + 1) + "/" + pages);
        navigation(inventory, actions, player, page, pages, () -> payrollHistory(player, institutionId, page - 1),
                () -> payrollHistory(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Gehaltsverwaltung", ignored -> payroll(player, institutionId, 0));
        int slot = 9;
        for (EconomyTransaction entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            String target = entry.recipient().startsWith("institution:") ? entry.recipient()
                    : citizenName(player, entry.recipient());
            ManagementHubScreen.display(inventory, slot++, entry.successful() ? Items.GOLD_NUGGET : Items.BARRIER,
                    (entry.successful() ? "Ausgezahlt · " : "Fehlgeschlagen · ") + EconomyState.format(entry.amount()),
                    date(entry.timestamp()) + " · " + target + " · " + entry.purpose());
        }
        menu(player, inventory, actions, "Institution · Gehaltshistorie");
    }

    private static void settings(ServerPlayerEntity player, String institutionId) {
        Institution institution = current(player, institutionId);
        if (institution == null) return;
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.COMPARATOR, "Institutionseinstellungen", institution.name() + " · " + institution.type());
        ManagementHubScreen.display(inventory, 13, Items.NAME_TAG, "Aktueller Eigentümer", citizenName(player, institution.ownerUuid()));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.TRANSFER_OWNERSHIP))
            button(inventory, actions, 22, Items.WRITABLE_BOOK, "Eigentum übertragen", "Neuen Eigentümer aus Beschäftigten wählen", ignored -> ownerCandidates(player, institutionId, 0));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zum Institutionsdesktop", ignored -> open(player, institutionId));
        menu(player, inventory, actions, "Institution · Einstellungen");
    }

    private static void ownerCandidates(ServerPlayerEntity player, String institutionId, int requestedPage) {
        Institution institution = current(player, institutionId);
        if (institution == null || !InstitutionAccess.has(player, institutionId, InstitutionPermission.TRANSFER_OWNERSHIP)) { denied(player); return; }
        List<InstitutionEmployee> candidates = InstitutionState.get(player.getServer()).employees(institutionId).stream()
                .filter(employee -> !employee.playerUuid().equals(institution.ownerUuid())).toList();
        int pageSize = pageSize();
        int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Eigentum übertragen",
                candidates.size() + " mögliche Personen · Seite " + (page + 1) + "/" + pages);
        navigation(inventory, actions, player, page, pages, () -> ownerCandidates(player, institutionId, page - 1),
                () -> ownerCandidates(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zu den Einstellungen", ignored -> settings(player, institutionId));
        int slot = 9;
        for (InstitutionEmployee employee : candidates.subList(page * pageSize, Math.min(candidates.size(), (page + 1) * pageSize))) {
            UUID target = UUID.fromString(employee.playerUuid());
            button(inventory, actions, slot++, Items.PLAYER_HEAD, citizenName(player, employee.playerUuid()), employee.institutionRole().label(),
                    ignored -> confirmOwnership(player, institutionId, target, page));
        }
        menu(player, inventory, actions, "Institution · Eigentumswechsel");
    }

    private static void confirmOwnership(ServerPlayerEntity player, String institutionId, UUID target, int returnPage) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Eigentumsübertragung bestätigen", citizenName(player, target.toString()));
        button(inventory, actions, 20, Items.LIME_DYE, "Verbindlich übertragen", "Der bisherige Owner wird Director", ignored -> {
            if (!InstitutionState.get(player.getServer()).transferOwnership(player, institutionId, target)) error(player, "Eigentumsübertragung abgelehnt.");
            open(player, institutionId);
        });
        button(inventory, actions, 24, Items.RED_DYE, "Abbrechen", "Keine Änderung durchführen", ignored -> ownerCandidates(player, institutionId, returnPage));
        menu(player, inventory, actions, "Institution · Bestätigung");
    }

    private static void confirmFire(ServerPlayerEntity player, String institutionId, UUID target, int returnPage) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.BARRIER, "Entlassung bestätigen", citizenName(player, target.toString()));
        button(inventory, actions, 20, Items.RED_DYE, "Verbindlich entlassen", "Rolle und Gehaltsplan werden entfernt", ignored -> {
            if (!InstitutionState.get(player.getServer()).fire(player, institutionId, target)) error(player, "Entlassung nicht zulässig.");
            employees(player, institutionId, returnPage);
        });
        button(inventory, actions, 24, Items.LIME_DYE, "Abbrechen", "Personalakte unverändert lassen", ignored -> employeeDetails(player, institutionId, target, returnPage));
        menu(player, inventory, actions, "Institution · Bestätigung");
    }

    private static Institution current(ServerPlayerEntity player, String id) { return InstitutionState.get(player.getServer()).get(id); }
    private static String citizenName(ServerPlayerEntity player, String uuid) {
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(uuid));
            return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName() + " · " + identity.citizenNumber();
        } catch (IllegalArgumentException ignored) { return "Ungültige Bürger-ID"; }
    }
    private static String date(long timestamp) { return timestamp <= 0 ? "nicht dokumentiert" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp)); }
    private static String roleDescription(InstitutionRole role) {
        return switch (role) {
            case DIRECTOR -> "Personal, Rollen, Finanzen und Einstellungen";
            case MANAGER -> "Mitarbeiter und operative Organisation";
            case AUDITOR -> "Buchungen und Berichte ausschließlich lesen";
            case ACCOUNTANT -> "Überweisungen, Buchhaltung und Gehälter";
            case HR -> "Einstellungen, Entlassungen und Rollen";
            case EMPLOYEE -> "Standardzugriff ohne Verwaltung";
            case OWNER -> "Vollzugriff";
        };
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) -> new TextInputScreenHandler(id, inventory, done), Text.literal(title).formatted(Formatting.DARK_AQUA)));
    }
    private static void navigation(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                   ServerPlayerEntity player, int page, int pages, Runnable previous, Runnable next) {
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page, ignored -> previous.run());
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2), ignored -> next.run());
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, ignored) -> new ActionMenuScreenHandler(id, inv, inventory, actions), Text.literal(title).formatted(Formatting.DARK_AQUA)));
    }
    private static void denied(ServerPlayerEntity player) { AuditLogger.denied(player,"institutions","management");error(player, "Keine Berechtigung für diese Institutionsfunktion."); }
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
}
