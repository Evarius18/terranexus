package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.LandAuditState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LandAdministrationScreen {
    private static final int PAGE_SIZE = 36;

    private LandAdministrationScreen() {}

    public static void open(ServerPlayerEntity player) { open(player, 0); }

    private static void open(ServerPlayerEntity player, int requestedPage) {
        if (!AuthorityState.mayAdministerLand(player)) {
            player.sendMessage(Text.literal("Bauamtsleitung erforderlich.").formatted(Formatting.RED), false);
            return;
        }
        LandManagementState state = LandManagementState.get(player.getServer());
        List<AdministrativeArea> areas = state.areas();
        int pages = Math.max(1, (areas.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();

        AdministrativeArea root = state.rootArea();
        display(inventory, 4, Items.RECOVERY_COMPASS, root.name(),
                root.type() + " · lückenlose Standardzuständigkeit");
        button(inventory, actions, 6, Items.EMERALD, "Verwaltungseinheit anlegen",
                "Ebene und übergeordnete Einheit auswählen", ignored -> selectLevel(player));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Grundstücksverwaltung", ignored -> PropertyScreen.open(player));

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, areas.size()); index++) {
            AdministrativeArea area = areas.get(index);
            int slot = 9 + index - start;
            String parent = area.parentId().isBlank() ? "oberste Ebene" : parentName(state, area.parentId());
            String detail = state.levelName(area.level()) + " · übergeordnet: " + parent;
            button(inventory, actions, slot, area.id().equals(LandManagementState.ROOT_AREA_ID)
                    ? Items.RECOVERY_COMPASS : Items.FILLED_MAP, area.name(), detail,
                    ignored -> areaDetails(player, area.id(), page));
        }
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite", page + "/" + pages,
                ignored -> open(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 53, Items.ARROW, "Nächste Seite", (page + 2) + "/" + pages,
                ignored -> open(player, page + 1));
        openMenu(player, inventory, actions, "Gebietsverwaltung");
    }

    private static void selectLevel(ServerPlayerEntity player) {
        if (!AuthorityState.mayAdministerLand(player)) { open(player); return; }
        LandManagementState state = LandManagementState.get(player.getServer());
        List<String> levels = ConfigManager.administration().hierarchyLevels;
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.COMPASS, "Ebene auswählen", "Bezeichnungen stammen aus administration.json");
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Gebietsverwaltung", ignored -> open(player));
        for (int index = 0; index < levels.size() && index < 45; index++) {
            int level = index;
            button(inventory, actions, 9 + index, Items.MAP, levels.get(index) + " anlegen",
                    index == levels.size() - 1 ? "Unterhalb von " + state.rootArea().name()
                            : "Benötigt eine übergeordnete Einheit", ignored -> selectParent(player, level));
        }
        openMenu(player, inventory, actions, "Verwaltungsebene auswählen");
    }

    private static void selectParent(ServerPlayerEntity player, int childLevel) { selectParent(player, childLevel, 0); }

    private static void selectParent(ServerPlayerEntity player, int childLevel, int requestedPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        int topLevel = ConfigManager.administration().hierarchyLevels.size() - 1;
        if (childLevel == topLevel) {
            askName(player, childLevel, LandManagementState.ROOT_AREA_ID);
            return;
        }
        List<AdministrativeArea> parents = state.areasAtLevel(childLevel + 1);
        int pages = Math.max(1, (parents.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.COMPASS, "Übergeordnete Einheit",
                state.levelName(childLevel + 1) + " für neue Einheit auswählen");
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Ebenenauswahl", ignored -> selectLevel(player));
        if (parents.isEmpty()) display(inventory, 22, Items.BARRIER, "Keine passende Einheit vorhanden",
                "Lege zuerst eine Einheit der Ebene „" + state.levelName(childLevel + 1) + "“ an.");
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, parents.size()); index++) {
            AdministrativeArea parent = parents.get(index);
            int slot = 9 + index - start;
            String parentId = parent.id();
            button(inventory, actions, slot, Items.FILLED_MAP, parent.name(), state.levelName(parent.level()),
                    ignored -> askName(player, childLevel, parentId));
        }
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite", page + "/" + pages,
                ignored -> selectParent(player, childLevel, page - 1));
        if (page + 1 < pages) button(inventory, actions, 53, Items.ARROW, "Nächste Seite", (page + 2) + "/" + pages,
                ignored -> selectParent(player, childLevel, page + 1));
        openMenu(player, inventory, actions, "Überordnung wählen");
    }

    private static void askName(ServerPlayerEntity player, int level, String parentId) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) ->
                new TextInputScreenHandler(id, inventory, value -> {
                    String name = value == null ? "" : value.trim();
                    LandManagementState state = LandManagementState.get(player.getServer());
                    AdministrativeArea created = state.createArea(name, level, parentId, "player", player.getUuidAsString());
                    if (created == null) {
                        player.sendMessage(Text.literal("Einheit konnte nicht angelegt werden: Name, Ebene, Überordnung oder Limit ist ungültig.")
                                .formatted(Formatting.RED), false);
                    } else {
                        EconomyState.get(player.getServer()).ensureAccount(EconomyState.areaAccount(created.id()));
                        player.sendMessage(Text.literal(state.levelName(level) + " „" + created.name() + "“ wurde angelegt.")
                                .formatted(Formatting.GREEN), false);
                    }
                    open(player);
                }), Text.literal(stateTitle(level))));
    }

    private static void areaDetails(ServerPlayerEntity player, String areaId, int backPage) {
        LandManagementState state = LandManagementState.get(player.getServer());
        AdministrativeArea area = state.area(areaId);
        if (area == null) { open(player, backPage); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        String parent = area.parentId().isBlank() ? "Keine" : parentName(state, area.parentId());
        display(inventory, 4, Items.FILLED_MAP, area.name(), state.levelName(area.level()));
        display(inventory, 20, Items.COMPASS, "Übergeordnet", parent);
        display(inventory, 22, Items.CHEST, "Gebietskonto",
                EconomyState.format(EconomyState.get(player.getServer()).balance(EconomyState.areaAccount(area.id()))));
        display(inventory, 24, Items.PAPER, "Untergeordnete Einheiten", String.valueOf(state.children(area.id()).size()));
        display(inventory, 31, Items.NAME_TAG, "Verantwortung",
                areaOwnerLabel(player, area));
        if (!area.id().equals(LandManagementState.ROOT_AREA_ID))
            button(inventory, actions, 33, Items.WRITABLE_BOOK, "Verantwortung übertragen",
                    "Bürger oder Institution auswählen", ignored -> selectAreaOwnerType(player, area.id(), backPage));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Gebietsverwaltung", ignored -> open(player, backPage));
        openMenu(player, inventory, actions, "Verwaltungseinheit");
    }

    private static void selectAreaOwnerType(ServerPlayerEntity player, String areaId, int backPage) {
        if (!AuthorityState.mayAdministerLand(player)) { open(player, backPage); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.NAME_TAG, "Neue Verantwortung", "Eigentümerart auswählen");
        button(inventory, actions, 20, Items.PLAYER_HEAD, "Bürger", "Freigeschaltete Bürgerakte",
                ignored -> areaPlayerOwners(player, areaId, backPage, 0));
        button(inventory, actions, 24, Items.BRICKS, "Institution", "Behörde, Unternehmen oder Organisation",
                ignored -> areaInstitutionOwners(player, areaId, backPage, 0));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Verwaltungseinheit",
                ignored -> areaDetails(player, areaId, backPage));
        openMenu(player, inventory, actions, "Verantwortung übertragen");
    }

    private static void areaPlayerOwners(ServerPlayerEntity player, String areaId, int backPage, int requestedPage) {
        List<CitizenIdentity> values = IdentityState.get(player.getServer()).allApproved();
        int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, values.size()); index++) {
            CitizenIdentity identity = values.get(index); String name = identity.firstName() + " " + identity.lastName();
            button(inventory, actions, 9 + index - start, Items.PLAYER_HEAD, name, identity.citizenNumber(),
                    ignored -> assignAreaOwner(player, areaId, "player", identity.playerUuid(), backPage));
        }
        ownerListNavigation(player, areaId, backPage, page, pages, inventory, actions,
                target -> areaPlayerOwners(player, areaId, backPage, target));
        openMenu(player, inventory, actions, "Bürger auswählen");
    }

    private static void areaInstitutionOwners(ServerPlayerEntity player, String areaId, int backPage, int requestedPage) {
        List<Institution> values = InstitutionState.get(player.getServer()).all();
        int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, values.size()); index++) {
            Institution institution = values.get(index);
            button(inventory, actions, 9 + index - start, Items.BRICKS, institution.name(), institution.type(),
                    ignored -> assignAreaOwner(player, areaId, "institution", institution.id(), backPage));
        }
        ownerListNavigation(player, areaId, backPage, page, pages, inventory, actions,
                target -> areaInstitutionOwners(player, areaId, backPage, target));
        openMenu(player, inventory, actions, "Institution auswählen");
    }

    private static void ownerListNavigation(ServerPlayerEntity player, String areaId, int backPage, int page, int pages,
                                            SimpleInventory inventory,
                                            Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                            java.util.function.IntConsumer opener) {
        button(inventory, actions, 49, Items.ARROW, "Eigentümerart", "Zurück",
                ignored -> selectAreaOwnerType(player, areaId, backPage));
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite", page + "/" + pages,
                ignored -> opener.accept(page - 1));
        if (page + 1 < pages) button(inventory, actions, 53, Items.ARROW, "Nächste Seite", (page + 2) + "/" + pages,
                ignored -> opener.accept(page + 1));
    }

    private static void assignAreaOwner(ServerPlayerEntity player, String areaId, String ownerType, String ownerId, int backPage) {
        if (!AuthorityState.mayAdministerLand(player)) { open(player, backPage); return; }
        if (!LandManagementState.get(player.getServer()).setAreaOwner(player.getServer(), areaId, ownerType, ownerId))
            player.sendMessage(Text.literal("Verantwortung konnte nicht übertragen werden.").formatted(Formatting.RED), false);
        areaDetails(player, areaId, backPage);
    }

    private static String areaOwnerLabel(ServerPlayerEntity player, AdministrativeArea area) {
        if (area.id().equals(LandManagementState.ROOT_AREA_ID)) return "System / Bauamtsleitung";
        if (area.ownerType().equals("institution")) {
            Institution institution = InstitutionState.get(player.getServer()).get(area.ownerId());
            return institution == null ? "Unbekannte Institution" : institution.name();
        }
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(java.util.UUID.fromString(area.ownerId()));
            return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName();
        } catch (IllegalArgumentException ignored) { return "Unbekannt"; }
    }

    public static void selectForProperty(ServerPlayerEntity player, LandProperty property) { selectForProperty(player, property, 0); }

    private static void selectForProperty(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        if (!AuthorityState.mayAdministerLand(player)) { PropertyFinanceScreen.open(player, property); return; }
        LandManagementState state = LandManagementState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.MAP, "Zuständigkeit zuweisen", property.name());
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Verträge und Rechte",
                ignored -> PropertyFinanceScreen.open(player, property));
        List<AdministrativeArea> areas = state.areas();
        int pages = Math.max(1, (areas.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, areas.size()); index++) {
            AdministrativeArea area = areas.get(index);
            int slot = 9 + index - start;
            String id = area.id();
            button(inventory, actions, slot, Items.FILLED_MAP, area.name(), state.levelName(area.level()), ignored -> {
                LandProperty current = net.evarius.terranexus.landlord.LandlordState.get(player.getServer()).get(property.id());
                if (current == null || !AuthorityState.mayAdministerLand(player)) return;
                state.assignArea(current.id(), id);
                LandAuditState.get(player.getServer()).log(player.getUuid(), "JURISDICTION", current, id);
                PropertyFinanceScreen.open(player, current);
            });
        }
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite", page + "/" + pages,
                ignored -> selectForProperty(player, property, page - 1));
        if (page + 1 < pages) button(inventory, actions, 53, Items.ARROW, "Nächste Seite", (page + 2) + "/" + pages,
                ignored -> selectForProperty(player, property, page + 1));
        openMenu(player, inventory, actions, "Zuständigkeit wählen");
    }

    public static void selectLandUse(ServerPlayerEntity player, LandProperty property) {
        if (!AuthorityState.mayProcessLandRecords(player)) { PropertyFinanceScreen.open(player, property); return; }
        LandManagementState state = LandManagementState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.OAK_SIGN, "Flächennutzung", property.name());
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Verträge und Rechte",
                ignored -> PropertyFinanceScreen.open(player, property));
        int slot = 9;
        for (String use : ConfigManager.administration().landUseTypes) {
            if (slot >= 54) break;
            boolean publicUse = ConfigManager.administration().publicLandUseTypes.contains(use);
            button(inventory, actions, slot++, publicUse ? Items.GRASS_BLOCK : Items.BRICKS, use,
                    publicUse ? "Öffentliche Fläche · Standardrechte werden gesetzt" : "Private Nutzung", ignored -> {
                        LandProperty current = net.evarius.terranexus.landlord.LandlordState.get(player.getServer()).get(property.id());
                        if (current == null || !AuthorityState.mayProcessLandRecords(player)) return;
                        state.setLandUse(current.id(), use);
                        LandAuditState.get(player.getServer()).log(player.getUuid(), "LAND_USE", current, use);
                        PropertyFinanceScreen.open(player, current);
                    });
        }
        openMenu(player, inventory, actions, "Flächennutzung wählen");
    }

    private static String stateTitle(int level) {
        List<String> levels = ConfigManager.administration().hierarchyLevels;
        return level >= 0 && level < levels.size() ? levels.get(level) + " benennen" : "Einheit benennen";
    }

    private static String parentName(LandManagementState state, String id) {
        AdministrativeArea parent = state.area(id);
        return parent == null ? state.rootArea().name() : parent.name();
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

    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory,
                                 Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                 String title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (id, playerInventory, ignored) -> new ActionMenuScreenHandler(id, playerInventory, inventory, actions),
                Text.literal(title).formatted(Formatting.DARK_GREEN)));
    }
}
