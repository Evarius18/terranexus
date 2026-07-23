package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.config.TimeClockRuleConfig;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.DutyRecord;
import net.evarius.terranexus.institution.DutySession;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionEmployee;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.institution.TimeClockService;
import net.evarius.terranexus.institution.TimeClockState;
import net.evarius.terranexus.logging.AuditLogger;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class TimeClockScreen {
    private TimeClockScreen() {}

    public static void open(ServerPlayerEntity player, String institutionId) {
        Institution institution = institution(player, institutionId);
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid());
        if (institution == null || employee == null && !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        TimeClockState state = TimeClockState.get(player.getServer());
        List<TimeClockService.RuleStatus> rules = TimeClockService.statuses(player.getServer(), institutionId);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        refreshSummary(player, institutionId, inventory);

        if (employee != null) {
            DutyRecord own = state.record(institutionId, player.getUuid());
            display(inventory, 20, own.onDuty() ? Items.LIME_DYE : Items.GRAY_DYE,
                    own.onDuty() ? "Im Dienst" : "Außer Dienst",
                    own.onDuty() ? "Seit " + dateTime(own.clockedInAt()) : lastClockOut(own));
            button(inventory, actions, 22, own.onDuty() ? Items.RED_DYE : Items.LIME_DYE,
                    own.onDuty() ? "Ausstempeln" : "Einstempeln",
                    own.onDuty() ? "Aktuelle Schicht beenden" : "Dienst jetzt beginnen", ignored -> toggle(player, institutionId));
            button(inventory, actions, 24, Items.WRITABLE_BOOK, "Meine Dienstzeiten",
                    "Gesamt: " + duration(own.workedMillis(System.currentTimeMillis())),
                    ignored -> employeeDetails(player, institutionId, player.getUuid(), 0));
        } else {
            display(inventory, 22, Items.BARRIER, "Nur Verwaltungszugriff", "Du bist hier nicht als Mitarbeiter geführt");
        }
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_TIME_CLOCK))
            button(inventory, actions, 29, Items.PLAYER_HEAD, "Dienstübersicht",
                    "Status aller Mitarbeiter anzeigen", ignored -> employees(player, institutionId, 0));
        button(inventory, actions, 31, Items.COMPARATOR, "Besetzungsregeln",
                rules.size() + " anwendbare Schwellenwerte", ignored -> thresholds(player, institutionId, 0));
        button(inventory, actions, 33, Items.COMPASS, "Jetzt aktualisieren",
                "Zähler und Warnstatus neu laden", ignored -> open(player, institutionId));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zum Institutions-Desktop",
                ignored -> InstitutionManagementScreen.open(player, institutionId));
        liveMenu(player, inventory, actions, "Stempeluhr · " + institution.name(),
                () -> refreshSummary(player, institutionId, inventory));
    }

    private static void toggle(ServerPlayerEntity player, String institutionId) {
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid());
        if (employee == null) { denied(player); return; }
        DutyRecord current = TimeClockState.get(player.getServer()).record(institutionId, player.getUuid());
        TimeClockState.ChangeResult result = current.onDuty()
                ? TimeClockService.clockOut(player, institutionId) : TimeClockService.clockIn(player, institutionId);
        player.sendMessage(Text.literal(result.message()).formatted(result.success()
                ? result.onDuty() ? Formatting.GREEN : Formatting.YELLOW : Formatting.RED), false);
        open(player, institutionId);
    }

    private static void employees(ServerPlayerEntity player, String institutionId, int requestedPage) {
        Institution institution = institution(player, institutionId);
        if (institution == null || !InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_TIME_CLOCK)) { denied(player); return; }
        TimeClockState clock = TimeClockState.get(player.getServer());
        List<InstitutionEmployee> employees = new ArrayList<>(InstitutionState.get(player.getServer()).employees(institutionId));
        employees.sort(Comparator
                .comparing((InstitutionEmployee employee) -> !clock.record(institutionId, parse(employee.playerUuid())).onDuty())
                .thenComparing(employee -> citizenName(player, employee.playerUuid()), String.CASE_INSENSITIVE_ORDER));
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (employees.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.PLAYER_HEAD, "Dienstübersicht",
                clock.onDutyCount(institutionId) + " im Dienst · Seite " + (page + 1) + "/" + pages);
        navigation(inventory, actions, page, pages, () -> employees(player, institutionId, page - 1),
                () -> employees(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Stempeluhr", ignored -> open(player, institutionId));
        button(inventory, actions, 6, Items.COMPASS, "Aktualisieren", "Aktuellen Dienststatus laden",
                ignored -> employees(player, institutionId, page));
        int slot = 9;
        for (InstitutionEmployee employee : employees.subList(page * pageSize, Math.min(employees.size(), (page + 1) * pageSize))) {
            UUID id = parse(employee.playerUuid());
            DutyRecord record = clock.record(institutionId, id);
            String detail = (record.onDuty() ? "Im Dienst seit " + dateTime(record.clockedInAt()) : "Außer Dienst · " + lastClockOut(record))
                    + " · " + employee.institutionRole().label();
            button(inventory, actions, slot++, record.onDuty() ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE,
                    citizenName(player, employee.playerUuid()), detail,
                    ignored -> employeeDetails(player, institutionId, id, page));
        }
        menu(player, inventory, actions, "Stempeluhr · Mitarbeiter");
    }

    private static void employeeDetails(ServerPlayerEntity player, String institutionId, UUID employeeId, int returnPage) {
        boolean self = player.getUuid().equals(employeeId);
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, employeeId);
        if (employee == null || !self && !InstitutionAccess.has(player, institutionId, InstitutionPermission.VIEW_TIME_CLOCK)) {
            denied(player); return;
        }
        DutyRecord record = TimeClockState.get(player.getServer()).record(institutionId, employeeId);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, record.onDuty() ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE,
                citizenName(player, employeeId.toString()), record.onDuty() ? "Im Dienst" : "Außer Dienst");
        display(inventory, 11, Items.CLOCK, "Dienstbeginn",
                record.onDuty() ? dateTime(record.clockedInAt()) : "Keine aktive Schicht");
        display(inventory, 13, Items.REDSTONE_TORCH, "Letztes Dienstende", lastClockOut(record));
        display(inventory, 15, Items.WRITABLE_BOOK, "Gesamtdienstzeit",
                duration(record.workedMillis(System.currentTimeMillis())) + " · " + record.sessions().size() + " gespeicherte Schichten");
        List<DutySession> sessions = new ArrayList<>(record.sessions());
        sessions.sort(Comparator.comparingLong(DutySession::startedAt).reversed());
        int slot = 27;
        for (DutySession session : sessions.stream().limit(18).toList())
            display(inventory, slot++, Items.PAPER, dateTime(session.startedAt()) + " – " + dateTime(session.endedAt()),
                    "Dauer: " + duration(session.durationMillis()));
        button(inventory, actions, 8, Items.ARROW, "Zurück", self ? "Zur eigenen Stempeluhr" : "Zur Dienstübersicht",
                ignored -> { if (self) open(player, institutionId); else employees(player, institutionId, returnPage); });
        menu(player, inventory, actions, "Stempeluhr · Dienstakte");
    }

    private static void thresholds(ServerPlayerEntity player, String institutionId, int requestedPage) {
        Institution institution = institution(player, institutionId);
        if (institution == null || !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        List<TimeClockService.RuleStatus> rules = TimeClockService.statuses(player.getServer(), institutionId);
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (rules.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        long warnings = rules.stream().filter(rule -> rule.warningEnabled() && !rule.satisfied()).count();
        display(inventory, 4, warnings == 0 ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                "Besetzungsregeln", warnings == 0 ? "Alle Regeln erfüllt" : warnings + " Warnung(en)");
        navigation(inventory, actions, page, pages, () -> thresholds(player, institutionId, page - 1),
                () -> thresholds(player, institutionId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Stempeluhr", ignored -> open(player, institutionId));
        int slot = 9;
        for (TimeClockService.RuleStatus rule : rules.subList(page * pageSize, Math.min(rules.size(), (page + 1) * pageSize))) {
            String detail = "Ist " + rule.onDuty() + " · Regel: "
                    + TimeClockService.comparisonLabel(rule.comparison(), rule.threshold())
                    + (rule.overridden() ? " · institutionsspezifisch" : " · Standardwert")
                    + (rule.warningEnabled() ? " · Warnregel" : " · Gameplay-Bedingung");
            button(inventory, actions, slot++, rule.satisfied() ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                    rule.label(), detail, ignored -> thresholdDetails(player, institutionId, rule.ruleId(), page));
        }
        menu(player, inventory, actions, "Stempeluhr · Regeln");
    }

    private static void thresholdDetails(ServerPlayerEntity player, String institutionId, String ruleId, int returnPage) {
        TimeClockRuleConfig config = ConfigManager.timeClock().rules.get(ruleId);
        TimeClockService.RuleStatus status = TimeClockService.statuses(player.getServer(), institutionId).stream()
                .filter(rule -> rule.ruleId().equals(ruleId)).findFirst().orElse(null);
        if (config == null || status == null || !InstitutionAccess.mayView(player, institutionId)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 13, status.satisfied() ? Items.LIME_CONCRETE : Items.RED_CONCRETE, status.label(),
                status.description());
        display(inventory, 22, Items.COMPARATOR, "Aktueller Status", "Ist " + status.onDuty() + " · "
                + TimeClockService.comparisonLabel(status.comparison(), status.threshold()));
        if (InstitutionAccess.has(player, institutionId, InstitutionPermission.MANAGE_TIME_CLOCK_SETTINGS)) {
            button(inventory, actions, 29, Items.WRITABLE_BOOK, "Schwellenwert ändern", "Aktuell: " + status.threshold(),
                    ignored -> thresholdInput(player, institutionId, ruleId, returnPage));
            if (status.overridden()) button(inventory, actions, 33, Items.BARRIER, "Standardwert wiederherstellen",
                    "Standard: " + config.defaultThreshold, ignored -> {
                        if (!TimeClockState.get(player.getServer()).resetThreshold(player, institutionId, ruleId))
                            error(player, "Schwellenwert konnte nicht zurückgesetzt werden.");
                        thresholdDetails(player, institutionId, ruleId, returnPage);
                    });
        }
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zu den Besetzungsregeln",
                ignored -> thresholds(player, institutionId, returnPage));
        menu(player, inventory, actions, "Stempeluhr · Regeleinstellung");
    }

    private static void thresholdInput(ServerPlayerEntity player, String institutionId, String ruleId, int returnPage) {
        input(player, "Schwellenwert (0–10000)", value -> {
            int threshold;
            try { threshold = Integer.parseInt(value.trim()); }
            catch (RuntimeException ignored) { error(player, "Bitte eine ganze Zahl zwischen 0 und 10000 eingeben."); thresholdDetails(player, institutionId, ruleId, returnPage); return; }
            if (!TimeClockState.get(player.getServer()).setThreshold(player, institutionId, ruleId, threshold))
                error(player, "Ungültiger Wert oder keine Berechtigung.");
            thresholdDetails(player, institutionId, ruleId, returnPage);
        });
    }

    private static Institution institution(ServerPlayerEntity player, String institutionId) {
        return InstitutionState.get(player.getServer()).get(institutionId);
    }
    private static UUID parse(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException ignored) { return new UUID(0L, 0L); }
    }
    private static String citizenName(ServerPlayerEntity player, String playerId) {
        UUID id = parse(playerId);
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(id);
        return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName();
    }
    private static String lastClockOut(DutyRecord record) {
        return record.lastClockOutAt() <= 0 ? "Noch kein Dienstende gespeichert" : "Zuletzt " + dateTime(record.lastClockOutAt());
    }
    private static String dateTime(long timestamp) {
        return timestamp <= 0 ? "–" : new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp));
    }
    private static String duration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remaining = seconds % 60L;
        return String.format("%d:%02d:%02d h", hours, minutes, remaining);
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) ->
                new TextInputScreenHandler(id, inventory, done), Text.literal(title).formatted(Formatting.DARK_AQUA)));
    }
    private static void navigation(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                   int page, int pages, Runnable previous, Runnable next) {
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page, ignored -> previous.run());
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2), ignored -> next.run());
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
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_AQUA));
    }
    private static void liveMenu(ServerPlayerEntity player, SimpleInventory inventory,
                                 Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                 String title, Runnable refresh) {
        CustomGuiService.openLive(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_AQUA),
                refresh, ConfigManager.timeClock().statusRefreshTicks);
    }
    private static void refreshSummary(ServerPlayerEntity player, String institutionId, SimpleInventory inventory) {
        Institution institution = InstitutionState.get(player.getServer()).get(institutionId);
        if (institution == null) return;
        int onDuty = TimeClockState.get(player.getServer()).onDutyCount(institutionId);
        TimeClockService.RuleStatus warning = TimeClockService.statuses(player.getServer(), institutionId).stream()
                .filter(status -> status.warningEnabled() && !status.satisfied()).findFirst().orElse(null);
        display(inventory, 4, Items.CLOCK, institution.name() + " · Stempeluhr",
                onDuty + " von " + institution.employees().size() + " Mitarbeitern im Dienst");
        display(inventory, 13, warning == null ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                warning == null ? "Einsatzstatus: ausreichend" : "Warnung: Besetzungsschwelle",
                warning == null ? "Alle Warnregeln sind erfüllt"
                        : warning.label() + " · Ist " + warning.onDuty() + ", benötigt "
                        + TimeClockService.comparisonLabel(warning.comparison(), warning.threshold()));
    }
    private static void denied(ServerPlayerEntity player) {
        AuditLogger.denied(player, "time_clock", "access");
        error(player, "Keine Berechtigung für diese Stempeluhr.");
    }
    private static void error(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
    }
}
