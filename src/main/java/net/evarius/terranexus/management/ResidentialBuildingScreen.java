package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.landlord.LandLease;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandPermissionService;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.ResidentialBuilding;
import net.evarius.terranexus.landlord.ResidentialUnit;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ResidentialBuildingScreen {
    private ResidentialBuildingScreen() {}

    public static void open(ServerPlayerEntity player, LandProperty property) {
        LandProperty current = latest(player, property.id());
        if (!mayManage(player, current)) { denied(player); return; }
        ResidentialUnit unit = LandManagementState.get(player.getServer()).residentialUnit(current.id());
        if (unit == null) selectBuilding(player, current, 0);
        else details(player, current, unit);
    }

    private static void selectBuilding(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        if (!mayManage(player, latest(player, property.id()))) { denied(player); return; }
        LandManagementState state = LandManagementState.get(player.getServer());
        List<ResidentialBuilding> buildings = state.residentialBuildings().stream()
                .filter(building -> building.propertyId().isBlank() || building.propertyId().equals(property.id()))
                .filter(building -> state.mayManageResidentialBuilding(player, building.id())).toList();
        int pageSize = Math.min(36, ConfigManager.desktop().standardEntriesPerPage);
        int pages = Math.max(1, (buildings.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.BRICKS, "Mehrfamilienhaus zuordnen",
                property.name() + " · " + buildings.size() + " verwaltbare Gebäude");
        button(inventory, actions, 1, Items.EMERALD, "Neues Mehrfamilienhaus",
                "Neue Gebäudeakte anlegen", ignored -> createBuilding(player, property));
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> selectBuilding(player, property, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> selectBuilding(player, property, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Grundstücksakte",
                ignored -> PropertyFinanceScreen.open(player, latest(player, property.id())));
        int slot = 9;
        for (ResidentialBuilding building : buildings.subList(page * pageSize,
                Math.min(buildings.size(), (page + 1) * pageSize))) {
            List<net.evarius.terranexus.landlord.PropertySubarea> internal =
                    net.evarius.terranexus.landlord.PropertySubareaState.get(player.getServer()).forBuilding(building.id());
            long apartments = state.units(building.id()).stream().filter(unit -> !unit.commonArea()).count()
                    + internal.stream().filter(unit -> !isCommonArea(unit.type())).count();
            long commonAreas = state.units(building.id()).size() + internal.size() - apartments;
            button(inventory, actions, slot++, Items.BRICKS, building.name(),
                    apartments + " Wohnung(en) · " + commonAreas + " Gemeinschaftsbereich(e)",
                    ignored -> assignmentType(player, property, building));
        }
        menu(player, inventory, actions, "Wohnungsverwaltung · Gebäude");
    }

    private static void createBuilding(ServerPlayerEntity player, LandProperty property) {
        input(player, "Name des Mehrfamilienhauses", value -> {
            LandProperty current = latest(player, property.id());
            if (!mayManage(player, current)) { denied(player); return; }
            ResidentialBuilding created = LandManagementState.get(player.getServer())
                    .createResidentialBuilding(player, value, property.id());
            if (created == null) {
                error(player, "Gebäudename ist ungültig oder bereits vergeben.");
                selectBuilding(player, current, 0);
                return;
            }
            assignmentType(player, current, created);
        });
    }

    private static void assignmentType(ServerPlayerEntity player, LandProperty property,
                                       ResidentialBuilding building) {
        LandManagementState state = LandManagementState.get(player.getServer());
        LandProperty current = latest(player, property.id());
        if (!mayManage(player, current) || !state.mayManageResidentialBuilding(player, building.id())
                || state.residentialUnit(current.id()) != null) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.BRICKS, building.name(), "Nutzung von „" + current.name() + "“ festlegen");
        button(inventory, actions, 20, Items.OAK_DOOR, "Als Wohnung",
                "Eigener Mieter, Vertrag und Grundstücksschutz",
                ignored -> unitName(player, current, building, false));
        button(inventory, actions, 24, Items.IRON_DOOR, "Als Gemeinschaftsbereich",
                "Treppenhaus, Flur oder gemeinsamer Zugang; nicht einzeln vermietbar",
                ignored -> unitName(player, current, building, true));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Gebäudeauswahl",
                ignored -> selectBuilding(player, current, 0));
        if (state.units(building.id()).isEmpty()) button(inventory, actions, 6, Items.BARRIER,
                "Leere Gebäudeakte verwerfen", "Entfernt nur die noch unbenutzte Gruppierung", ignored -> {
                    if (!state.removeResidentialBuilding(player, building.id()))
                        error(player, "Gebäudeakte konnte nicht entfernt werden.");
                    selectBuilding(player, current, 0);
                });
        menu(player, inventory, actions, "Wohnungsverwaltung · Nutzung");
    }

    private static void unitName(ServerPlayerEntity player, LandProperty property,
                                 ResidentialBuilding building, boolean commonArea) {
        input(player, commonArea ? "Pfad des Gemeinschaftsbereichs (z.B. Haus A / Flur 1)"
                : "Untergliederung (z.B. Haus A / Flur 1 / Wohnung 7)", value -> {
            LandManagementState state = LandManagementState.get(player.getServer());
            LandProperty current = latest(player, property.id());
            if (current == null) {
                error(player, "Das Grundstück existiert nicht mehr.");
                PropertyScreen.open(player);
                return;
            }
            List<String> path = parseUnitPath(value);
            String name = path.isEmpty() ? "" : path.getLast();
            if (!mayManage(player, current) || !state.assignResidentialUnit(
                    player, building.id(), current.id(), name, commonArea, path)) {
                error(player, commonArea && state.lease(current.id()) != null
                        ? "Ein vermietetes Grundstück kann kein Gemeinschaftsbereich werden."
                        : "Zuordnung fehlgeschlagen. Namen, Rechte und bestehende Zuordnung prüfen.");
                open(player, current);
                return;
            }
            open(player, current);
        });
    }

    private static void details(ServerPlayerEntity player, LandProperty property, ResidentialUnit unit) {
        LandManagementState state = LandManagementState.get(player.getServer());
        ResidentialBuilding building = state.residentialBuilding(unit.buildingId());
        if (building == null || !mayManage(player, property)) { denied(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, unit.commonArea() ? Items.IRON_DOOR : Items.OAK_DOOR,
                building.name(), unit.typeLabel() + " · " + unit.pathLabel());
        LandLease lease = state.lease(property.id());
        display(inventory, 20, lease == null ? Items.GRAY_DYE : lease.active() ? Items.LIME_DYE : Items.YELLOW_DYE,
                "Mietstatus", unit.commonArea() ? "Nicht einzeln vermietbar"
                        : lease == null ? "Frei" : lease.active() ? "Aktiv vermietet" : "Mietangebot offen");
        long apartments = state.units(building.id()).stream().filter(value -> !value.commonArea()).count();
        long commonAreas = state.units(building.id()).size() - apartments;
        display(inventory, 22, Items.WRITABLE_BOOK, "Gebäudestruktur",
                apartments + " Wohnung(en) · " + commonAreas + " Gemeinschaftsbereich(e)");
        button(inventory, actions, 24, Items.BOOK, "Alle Einheiten",
                "Wohnungen und Gemeinschaftsbereiche anzeigen",
                ignored -> units(player, property, building.id(), 0));
        button(inventory, actions, 31, Items.BARRIER, "Zuordnung entfernen",
                "Mietvertrag und Grundstück bleiben erhalten", ignored -> {
                    if (!state.removeResidentialUnit(player, property.id()))
                        error(player, "Zuordnung konnte nicht entfernt werden.");
                    PropertyFinanceScreen.open(player, latest(player, property.id()));
                });
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Grundstücksakte",
                ignored -> PropertyFinanceScreen.open(player, latest(player, property.id())));
        menu(player, inventory, actions, "Wohnungsverwaltung · Einheit");
    }

    private static void units(ServerPlayerEntity player, LandProperty returnProperty,
                              String buildingId, int requestedPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        ResidentialBuilding building = state.residentialBuilding(buildingId);
        if (building == null || !state.mayManageResidentialBuilding(player, buildingId)) { denied(player); return; }
        List<ResidentialUnit> units = state.units(buildingId);
        int pageSize = Math.min(36, ConfigManager.desktop().standardEntriesPerPage);
        int pages = Math.max(1, (units.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.BRICKS, building.name(),
                units.size() + " Einheiten · Seite " + (page + 1) + "/" + pages);
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> units(player, returnProperty, buildingId, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> units(player, returnProperty, buildingId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Einheit",
                ignored -> open(player, latest(player, returnProperty.id())));
        int slot = 9;
        for (ResidentialUnit unit : units.subList(page * pageSize, Math.min(units.size(), (page + 1) * pageSize))) {
            LandProperty property = latest(player, unit.propertyId());
            LandLease lease = state.lease(unit.propertyId());
            String detail = unit.typeLabel() + " · " + (property == null ? "Grundstück fehlt"
                    : lease == null ? "frei" : lease.active() ? "aktiv vermietet" : "Angebot offen");
            if (property != null && mayManage(player, property))
                button(inventory, actions, slot++, unit.commonArea() ? Items.IRON_DOOR : Items.OAK_DOOR,
                        unit.pathLabel(), detail, ignored -> open(player, property));
            else display(inventory, slot++, unit.commonArea() ? Items.IRON_DOOR : Items.OAK_DOOR,
                    unit.pathLabel(), detail);
        }
        menu(player, inventory, actions, "Wohnungsverwaltung · Einheiten");
    }

    private static LandProperty latest(ServerPlayerEntity player, String propertyId) {
        return LandlordState.get(player.getServer()).get(propertyId);
    }

    private static List<String> parseUnitPath(String value) {
        if (value == null) return List.of();
        return java.util.Arrays.stream(value.split("[/>]"))
                .map(String::trim).filter(segment -> !segment.isBlank()).limit(8).toList();
    }
    private static boolean isCommonArea(String type) {
        String normalized = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("gemeinschaft") || normalized.contains("flur")
                || normalized.contains("treppen") || normalized.contains("tiefgarage");
    }
    private static boolean mayManage(ServerPlayerEntity player, LandProperty property) {
        return LandPermissionService.mayExerciseOwnerRights(player, property);
    }
    private static void input(ServerPlayerEntity player, String title, Consumer<String> done) {
        TextPromptService.open(player, title, done);
    }
    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
    }
    private static void button(SimpleInventory inventory,
                               Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, Item item, String name, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        display(inventory, slot, item, name, detail);
        actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN));
    }
    private static void denied(ServerPlayerEntity player) {
        error(player, "Keine Eigentümer- oder Verwaltungsberechtigung für diese Wohnungsakte.");
    }
    private static void error(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
    }
}
