package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.landlord.LandAuditEntry;
import net.evarius.terranexus.landlord.LandAuditState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class LandSearchScreen {
    private LandSearchScreen() {}
    private enum AuditRange {
        ALL("Gesamter Zeitraum", 0L),
        DAY("Letzte 24 Stunden", 86_400_000L),
        WEEK("Letzte 7 Tage", 604_800_000L),
        MONTH("Letzte 30 Tage", 2_592_000_000L);

        private final String label;
        private final long milliseconds;
        AuditRange(String label, long milliseconds) { this.label = label; this.milliseconds = milliseconds; }
    }

    public static void ask(ServerPlayerEntity player) {
        CustomSearchService.open(player, "Bauamt · Grundstückssuche", "ID, Name, Adresse oder Besitzer", "",
                1, 64, query -> results(player, query.trim().toLowerCase(Locale.ROOT), 0),
                () -> PropertyScreen.open(player));
    }

    private static void results(ServerPlayerEntity player, String query, int requestedPage) {
        IdentityState identities = IdentityState.get(player.getServer());
        LandManagementState management = LandManagementState.get(player.getServer());
        List<SearchResult> matches = LandlordState.get(player.getServer()).all().stream().map(property -> {
                    AdministrativeArea area = management.jurisdiction(property);
                    return new SearchResult(property, ownerName(player, identities, management, property),
                            management.address(property.id()), management.landUse(property.id()),
                            area == null ? "" : area.name());
                })
                .filter(result -> matches(result, query)).toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.COMPASS, "Grundstückssuche",
                matches.size() + " Treffer · Seite " + (page + 1) + '/' + pages);
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Bauamt");
        actions.put(8, ignored -> PropertyScreen.open(player));
        if (page > 0) button(inventory, actions, 0, "Vorherige Seite", ignored -> results(player, query, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, "Nächste Seite", ignored -> results(player, query, page + 1));
        int slot = 9;
        for (SearchResult result : matches.subList(page * pageSize, Math.min(matches.size(), (page + 1) * pageSize))) {
            ManagementHubScreen.display(inventory, slot, Items.PAPER, result.property.name(),
                    result.address + " · " + result.use + " · " + result.jurisdiction + " · " + result.owner);
            actions.put(slot++, ignored -> PropertyFinanceScreen.open(player, result.property));
        }
        openMenu(player, inventory, actions, "Grundstückssuche");
    }

    public static void audit(ServerPlayerEntity player) {
        if (!AuthorityState.mayAdministerLand(player)) return;
        audit(player, "", "ALLE", AuditRange.ALL, 0);
    }

    private static void audit(ServerPlayerEntity player, String query, String category,
                              AuditRange range, int requestedPage) {
        if (!AuthorityState.mayAdministerLand(player)) return;
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        long earliest = range.milliseconds == 0 ? Long.MIN_VALUE : System.currentTimeMillis() - range.milliseconds;
        List<LandAuditEntry> entries = LandAuditState.get(player.getServer()).allRecent().stream()
                .filter(entry -> entry.timestamp() >= earliest)
                .filter(entry -> category.equals("ALLE") || auditCategory(entry.action()).equals(category))
                .filter(entry -> auditMatches(entry, normalizedQuery))
                .toList();
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Grundstücksprotokoll",
                entries.size() + " Vorgänge · Seite " + (page + 1) + "/" + pages + " · persistent");
        ManagementHubScreen.display(inventory, 1, Items.COMPASS, "Suche",
                normalizedQuery.isBlank() ? "Aktion, Objekt, Nutzer oder Beschreibung" : query);
        actions.put(1, ignored -> auditSearch(player, query, category, range));
        ManagementHubScreen.display(inventory, 3, Items.COMPARATOR, "Kategorie: " + category,
                "Protokolltyp filtern");
        actions.put(3, ignored -> auditCategorySelection(player, query, category, range));
        ManagementHubScreen.display(inventory, 5, Items.CLOCK, "Zeitraum: " + range.label,
                "Zeitfenster filtern");
        actions.put(5, ignored -> auditRangeSelection(player, query, category, range));
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Bauamt");
        actions.put(8, ignored -> PropertyScreen.open(player));
        if (page > 0) button(inventory, actions, 0, "Vorherige Seite",
                ignored -> audit(player, query, category, range, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, "Nächste Seite",
                ignored -> audit(player, query, category, range, page + 1));
        int slot = 9;
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (LandAuditEntry entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize)))
            ManagementHubScreen.display(inventory, slot++, Items.PAPER, entry.action() + " · " + entry.propertyId(),
                    format.format(new Date(entry.timestamp())) + " · " + entry.actorId() + " · "
                            + auditCategory(entry.action()) + " · " + entry.details());
        openMenu(player, inventory, actions, "Audit-Log");
    }

    private static void auditSearch(ServerPlayerEntity player, String query, String category, AuditRange range) {
        CustomSearchService.open(player, "Audit-Log · Suche", "Aktion, Objekt, Nutzer oder Beschreibung",
                query, 0, 64, value -> audit(player, value.trim(), category, range, 0),
                () -> audit(player, query, category, range, 0));
    }

    private static void auditCategorySelection(ServerPlayerEntity player, String query,
                                               String current, AuditRange range) {
        List<SelectionMenuScreen.Option> options = List.of(
                new SelectionMenuScreen.Option("ALLE", "Alle Kategorien", "Keine Einschränkung", Items.WRITABLE_BOOK),
                new SelectionMenuScreen.Option("ERSTELLUNG", "Erstellung", "Neue Grundstücke", Items.LIME_DYE),
                new SelectionMenuScreen.Option("EIGENTUM", "Eigentum", "Besitz- und Rechtewechsel", Items.PLAYER_HEAD),
                new SelectionMenuScreen.Option("BEARBEITUNG", "Bearbeitung", "Form, Name und Adresse", Items.COMPASS),
                new SelectionMenuScreen.Option("LÖSCHUNG", "Löschung", "Entfernte Grundstücke", Items.BARRIER),
                new SelectionMenuScreen.Option("SONSTIGES", "Sonstiges", "Weitere Vorgänge", Items.PAPER));
        SelectionMenuScreen.open(player, "Audit-Kategorie", options,
                value -> audit(player, query, value, range, 0),
                () -> audit(player, query, current, range, 0));
    }

    private static void auditRangeSelection(ServerPlayerEntity player, String query,
                                            String category, AuditRange current) {
        List<SelectionMenuScreen.Option> options = java.util.Arrays.stream(AuditRange.values())
                .map(value -> new SelectionMenuScreen.Option(value.name(), value.label,
                        "Zeitfilter für das Grundstücksprotokoll", Items.CLOCK)).toList();
        SelectionMenuScreen.open(player, "Audit-Zeitraum", options,
                value -> audit(player, query, category, AuditRange.valueOf(value), 0),
                () -> audit(player, query, category, current, 0));
    }

    private static boolean auditMatches(LandAuditEntry entry, String query) {
        if (query.isBlank()) return true;
        return entry.action().toLowerCase(Locale.ROOT).contains(query)
                || entry.propertyId().toLowerCase(Locale.ROOT).contains(query)
                || entry.actorId().toLowerCase(Locale.ROOT).contains(query)
                || entry.details().toLowerCase(Locale.ROOT).contains(query);
    }

    private static String auditCategory(String action) {
        String normalized = action.toUpperCase(Locale.ROOT);
        if (normalized.contains("CREATE")) return "ERSTELLUNG";
        if (normalized.contains("OWNER") || normalized.contains("TRUST") || normalized.contains("RIGHT")) return "EIGENTUM";
        if (normalized.contains("DELETE")) return "LÖSCHUNG";
        if (normalized.contains("EDIT") || normalized.contains("RENAME") || normalized.contains("ADDRESS")
                || normalized.contains("GEOMETRY") || normalized.contains("RESIZE")) return "BEARBEITUNG";
        return "SONSTIGES";
    }

    private static boolean matches(SearchResult result, String query) {
        return result.property.id().toLowerCase(Locale.ROOT).contains(query)
                || result.property.name().toLowerCase(Locale.ROOT).contains(query)
                || result.owner.toLowerCase(Locale.ROOT).contains(query)
                || result.address.toLowerCase(Locale.ROOT).contains(query)
                || result.use.toLowerCase(Locale.ROOT).contains(query)
                || result.jurisdiction.toLowerCase(Locale.ROOT).contains(query);
    }
    private static String ownerName(ServerPlayerEntity player, IdentityState identities,
                                    LandManagementState management, LandProperty property) {
        if (property.ownerType().equals("institution")) {
            Institution institution = InstitutionState.get(player.getServer()).get(property.ownerId());
            return institution == null ? property.ownerId() : institution.name();
        }
        if (property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)) {
            AdministrativeArea area = management.area(property.ownerId());
            return area == null ? ConfigManager.administration().wildernessName : area.name();
        }
        if (!property.ownerType().equals("player")) return property.ownerType() + ':' + property.ownerId();
        try {
            CitizenIdentity identity = identities.get(UUID.fromString(property.ownerId()));
            return identity == null ? property.ownerId() : identity.firstName() + ' ' + identity.lastName();
        } catch (IllegalArgumentException ignored) { return property.ownerId(); }
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, String label, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, Items.ARROW, label, "Weitere Suchergebnisse");
        actions.put(slot, action);
    }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory,
                                 Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN));
    }
    private record SearchResult(LandProperty property, String owner, String address, String use, String jurisdiction) {}
}
