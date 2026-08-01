package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Management-PC view for record and trade actions; surveying remains tablet-only. */
public final class LandOfficeOverviewScreen {
    private LandOfficeOverviewScreen() {}

    public static void open(ServerPlayerEntity player) { open(player, "", 0); }

    private static void open(ServerPlayerEntity player, String query, int requestedPage) {
        if (!AuthorityState.mayUseLandOffice(player)) { deny(player); return; }
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        LandManagementState management = LandManagementState.get(player.getServer());
        List<LandProperty> properties = LandlordState.get(player.getServer()).all().stream()
                .filter(property -> matches(player, management, property, normalized)).toList();
        int pageSize = Math.max(1, Math.min(36, ConfigManager.desktop().standardEntriesPerPage));
        int pages = Math.max(1, (properties.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP,
                Text.translatable("gui.terranexus.land_office_overview.title"),
                Text.translatable("gui.terranexus.land_office_overview.summary", properties.size(), page + 1, pages));
        ManagementHubScreen.display(inventory, 1, Items.COMPASS,
                Text.translatable("gui.terranexus.action.search"),
                Text.literal(normalized.isBlank() ? "ID, Name, Adresse oder Eigentümer" : query));
        actions.put(1, ignored -> search(player, query));
        ManagementHubScreen.display(inventory, 8, Items.ARROW,
                Text.translatable("gui.terranexus.action.back"), Text.literal("Admin-Desktop"));
        actions.put(8, ignored -> AdminDesktopScreen.open(player));
        if (page > 0) navigation(inventory, actions, 0, "Vorherige Seite", ignored -> open(player, query, page - 1));
        if (page + 1 < pages) navigation(inventory, actions, 7, "Nächste Seite", ignored -> open(player, query, page + 1));
        int slot = 9;
        for (LandProperty property : properties.subList(page * pageSize, Math.min(properties.size(), (page + 1) * pageSize))) {
            LandSaleOffer sale = management.sale(property.id());
            String detail = management.address(property.id()) + " · " + ownerLabel(player, management, property)
                    + (sale == null ? "" : " · Verkauf " + EconomyState.format(sale.price()));
            ManagementHubScreen.display(inventory, slot, Items.PAPER, property.name(), detail);
            actions.put(slot++, ignored -> details(player, property.id()));
        }
        CustomGuiService.open(player, inventory, actions,
                Text.translatable("gui.terranexus.land_office_overview.title").formatted(Formatting.DARK_GREEN));
    }

    static void details(ServerPlayerEntity player, String propertyId) {
        if (!AuthorityState.mayUseLandOffice(player)) { deny(player); return; }
        LandProperty property = LandlordState.get(player.getServer()).get(propertyId);
        if (property == null) { player.sendMessage(Text.translatable("message.terranexus.property.missing").formatted(Formatting.RED), false); open(player); return; }
        LandManagementState management = LandManagementState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, property.name(),
                management.address(property.id()) + " · " + ownerLabel(player, management, property));
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "GS-Übersicht");
        actions.put(8, ignored -> open(player));
        if (LandTransferService.mayInitiate(player)) action(inventory, actions, 20, Items.PLAYER_HEAD,
                "Besitzerübertragung", "Zustimmungspflichtige Umschreibung starten",
                ignored -> PropertyScreen.openOwnerSelectionFromDesktop(player, property));
        LandSaleOffer sale = management.sale(property.id());
        if (AuthorityState.mayAdministerLand(player)) {
            if (sale == null) action(inventory, actions, 22, Items.EMERALD, "Zum Verkauf freigeben",
                    "Amtlichen Verkaufspreis festlegen", ignored -> askSalePrice(player, property));
            else action(inventory, actions, 22, Items.REDSTONE, "Verkaufsfreigabe aufheben",
                    EconomyState.format(sale.price()), ignored -> {
                        show(player, LandTradeService.cancelSaleByLandOffice(player, property.id()));
                        details(player, property.id());
                    });
        }
        action(inventory, actions, 24, Items.WRITTEN_BOOK, "Verträge und Rechte",
                "Miete, Zugriff und Eigentumsinformationen", ignored -> PropertyFinanceScreen.open(player, property));
        if (AuthorityState.mayProcessLandRecords(player)) action(inventory, actions, 26, Items.OAK_SIGN,
                "Flächennutzung", management.landUse(property.id()),
                ignored -> LandAdministrationScreen.selectLandUse(player, property));
        CustomGuiService.open(player, inventory, actions,
                Text.literal("Grundstücksverwaltung · Details").formatted(Formatting.DARK_GREEN));
    }

    private static void askSalePrice(ServerPlayerEntity player, LandProperty property) {
        CustomSearchService.open(player, "Bauamt · Verkaufspreis", "Betrag in " + ConfigManager.economy().currencySymbol,
                "", 1, 24, value -> {
                    Long price = EconomyState.parseAmount(value, true);
                    if (price == null) player.sendMessage(Text.literal("Ungültiger Geldbetrag.").formatted(Formatting.RED), false);
                    else show(player, LandTradeService.offerSaleByLandOffice(player, property.id(), price));
                    details(player, property.id());
                }, () -> details(player, property.id()));
    }

    private static void search(ServerPlayerEntity player, String current) {
        CustomSearchService.open(player, "GS-Übersicht · Suche", "ID, Name, Adresse oder Eigentümer", current,
                0, 64, value -> open(player, value, 0), () -> open(player, current, 0));
    }

    private static boolean matches(ServerPlayerEntity player, LandManagementState management,
                                   LandProperty property, String query) {
        return query.isBlank() || property.id().toLowerCase(Locale.ROOT).contains(query)
                || property.name().toLowerCase(Locale.ROOT).contains(query)
                || management.address(property.id()).toLowerCase(Locale.ROOT).contains(query)
                || ownerLabel(player, management, property).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String ownerLabel(ServerPlayerEntity player, LandManagementState management, LandProperty property) {
        if (property.ownerType().equals("institution")) {
            Institution institution = InstitutionState.get(player.getServer()).get(property.ownerId());
            return institution == null ? property.ownerId() : institution.name();
        }
        if (property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)) {
            AdministrativeArea area = management.area(property.ownerId());
            return area == null ? ConfigManager.administration().wildernessName : area.name();
        }
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(property.ownerId()));
            return identity == null ? property.ownerId() : identity.firstName() + " " + identity.lastName();
        } catch (IllegalArgumentException ignored) { return property.ownerId(); }
    }

    private static void action(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String label, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> handler) {
        ManagementHubScreen.display(inventory, slot, item, label, detail);
        actions.put(slot, handler);
    }

    private static void navigation(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                   int slot, String label, Consumer<net.minecraft.entity.player.PlayerEntity> handler) {
        action(inventory, actions, slot, Items.ARROW, label, "Weitere Grundstücke", handler);
    }

    private static void show(ServerPlayerEntity player, LandTradeService.Result result) {
        player.sendMessage(Text.literal(result.message()).formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
    }

    private static void deny(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.terranexus.land_office_overview.denied").formatted(Formatting.RED), false);
    }
}
