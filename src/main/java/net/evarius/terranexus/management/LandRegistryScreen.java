package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.landlord.LandAuditState;
import net.evarius.terranexus.landlord.LandLease;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandMarkerService;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandSaleOffer;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.OwnershipChange;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
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
import java.util.function.Consumer;

public final class LandRegistryScreen {
    private LandRegistryScreen() {}

    public static void open(ServerPlayerEntity player) { owned(player, 0); }

    private static void owned(ServerPlayerEntity player, int requestedPage) {
        LandlordState lands = LandlordState.get(player.getServer());
        LandManagementState management = LandManagementState.get(player.getServer());
        List<LandProperty> properties = lands.owned(player.getUuid());
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (properties.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.WRITTEN_BOOK,
                Text.translatable("gui.terranexus.registry.personal"),
                Text.translatable("gui.terranexus.registry.owned_page", properties.size(), page + 1, pages));
        button(inventory, actions, 1, Items.EMERALD,
                Text.translatable("gui.terranexus.registry.market"),
                Text.translatable("gui.terranexus.registry.market.description"), ignored -> market(player, 0));
        button(inventory, actions, 3, Items.WRITABLE_BOOK,
                Text.translatable("gui.terranexus.registry.my_leases"),
                Text.translatable("gui.terranexus.registry.my_leases.description"), ignored -> leases(player, 0));
        int pendingTransfers = net.evarius.terranexus.landlord.LandTransferService.pendingFor(player).size();
        button(inventory, actions, 5, pendingTransfers > 0 ? Items.LIME_DYE : Items.PAPER,
                Text.translatable("gui.terranexus.registry.transfers"),
                Text.translatable("gui.terranexus.registry.open_processes", pendingTransfers),
                ignored -> LandTransferScreen.openRegistry(player));
        button(inventory, actions, 6, Items.WRITTEN_BOOK,
                Text.translatable("gui.terranexus.registry.landlord_overview"),
                Text.translatable("gui.terranexus.registry.landlord_overview.description"),
                ignored -> LandlordLeaseScreen.openPersonal(player));
        if (page > 0) button(inventory, actions, 0, Items.ARROW,
                Text.translatable("gui.terranexus.previous_page"),
                Text.translatable("gui.terranexus.page", page), ignored -> owned(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW,
                Text.translatable("gui.terranexus.next_page"),
                Text.translatable("gui.terranexus.page", page + 2), ignored -> owned(player, page + 1));
        int slot = 9;
        for (LandProperty property : properties.subList(page * pageSize, Math.min(properties.size(), (page + 1) * pageSize))) {
            String marker = LandMarkerService.active(player.getUuid(), property.id())
                    ? " · Markierung " + LandMarkerService.remainingSeconds(player.getUuid(), property.id()) + "s" : "";
            button(inventory, actions, slot++, Items.PAPER, property.name(), management.address(property.id()) + marker,
                    ignored -> details(player, property.id(), page));
        }
        menu(player, inventory, actions, Text.translatable("gui.terranexus.registry.ownership_title"));
    }

    private static void market(ServerPlayerEntity player, int requestedPage) {
        LandlordState lands = LandlordState.get(player.getServer());
        List<Map.Entry<LandProperty, LandSaleOffer>> offers = new ArrayList<>();
        for (LandSaleOffer offer : LandManagementState.get(player.getServer()).sales()) {
            LandProperty property = lands.get(offer.propertyId());
            if (property != null && !property.isOwnedBy(player.getUuid())) offers.add(Map.entry(property, offer));
        }
        offers.sort(Comparator.comparing(entry -> entry.getKey().name(), String.CASE_INSENSITIVE_ORDER));
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (offers.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.EMERALD, Text.translatable("gui.terranexus.registry.market"),
                Text.translatable("gui.terranexus.registry.offers_page", offers.size(), page + 1, pages));
        if (page > 0) button(inventory, actions, 0, Items.ARROW,
                Text.translatable("gui.terranexus.previous_page"), Text.translatable("gui.terranexus.page", page),
                ignored -> market(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW,
                Text.translatable("gui.terranexus.next_page"), Text.translatable("gui.terranexus.page", page + 2),
                ignored -> market(player, page + 1));
        button(inventory, actions, 8, Items.ARROW, Text.translatable("gui.terranexus.back"),
                Text.translatable("gui.terranexus.registry.back_to_extract"), ignored -> open(player));
        int slot = 9;
        for (Map.Entry<LandProperty, LandSaleOffer> entry : offers.subList(page * pageSize, Math.min(offers.size(), (page + 1) * pageSize))) {
            LandProperty property = entry.getKey(); LandSaleOffer offer = entry.getValue();
            button(inventory, actions, slot++, Items.EMERALD, property.name(), EconomyState.format(offer.price()) + " · "
                    + LandManagementState.get(player.getServer()).address(property.id()), ignored -> PropertyFinanceScreen.open(player, property));
        }
        menu(player, inventory, actions, Text.translatable("gui.terranexus.registry.market_title"));
    }

    private static void leases(ServerPlayerEntity player, int requestedPage) {
        LandlordState lands = LandlordState.get(player.getServer());
        List<Map.Entry<LandProperty, LandLease>> contracts = new ArrayList<>();
        for (LandLease lease : LandManagementState.get(player.getServer()).leases()) {
            if (!lease.tenantId().equals(player.getUuidAsString())) continue;
            LandProperty property = lands.get(lease.propertyId());
            if (property != null) contracts.add(Map.entry(property, lease));
        }
        contracts.sort(Comparator.comparing(entry -> entry.getKey().name(), String.CASE_INSENSITIVE_ORDER));
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (contracts.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.WRITABLE_BOOK, Text.translatable("gui.terranexus.registry.my_leases"),
                Text.translatable("gui.terranexus.registry.contracts_page", contracts.size(), page + 1, pages));
        if (page > 0) button(inventory, actions, 0, Items.ARROW,
                Text.translatable("gui.terranexus.previous_page"), Text.translatable("gui.terranexus.page", page),
                ignored -> leases(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW,
                Text.translatable("gui.terranexus.next_page"), Text.translatable("gui.terranexus.page", page + 2),
                ignored -> leases(player, page + 1));
        button(inventory, actions, 8, Items.ARROW, Text.translatable("gui.terranexus.back"),
                Text.translatable("gui.terranexus.registry.back_to_extract"), ignored -> open(player));
        int slot = 9;
        for (Map.Entry<LandProperty, LandLease> entry : contracts.subList(page * pageSize, Math.min(contracts.size(), (page + 1) * pageSize))) {
            LandProperty property = entry.getKey(); LandLease lease = entry.getValue();
            String detail = (lease.active() ? "Aktiv" : "Annahme erforderlich") + " · " + EconomyState.format(lease.rent())
                    + " alle " + lease.periodDays() + " Tag(e)";
            button(inventory, actions, slot++, lease.active() ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK,
                    property.name(), detail, ignored -> PropertyFinanceScreen.open(player, property));
        }
        menu(player, inventory, actions, Text.translatable("gui.terranexus.registry.leases_title"));
    }

    private static void details(ServerPlayerEntity player, String propertyId, int returnPage) {
        LandProperty property = LandlordState.get(player.getServer()).get(propertyId);
        if (property == null || !property.isOwnedBy(player.getUuid())) { open(player); return; }
        LandManagementState management = LandManagementState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.PAPER, property.name(), "ID: " + property.id() + " · " + management.address(property.id()));
        display(inventory, 20, Items.COMPASS, Text.translatable("gui.terranexus.registry.location"),
                Text.translatable("gui.terranexus.registry.location_value", property.dimension(),
                        property.minX(), property.minZ(), property.maxX(), property.maxZ()));
        boolean active = LandMarkerService.active(player.getUuid(), property.id());
        button(inventory, actions, 22, active ? Items.RED_DYE : Items.LIME_DYE,
                Text.translatable(active ? "gui.terranexus.registry.marker.disable" : "gui.terranexus.registry.marker.enable"),
                Text.translatable(active ? "gui.terranexus.registry.marker.remaining"
                                : "gui.terranexus.registry.marker.automatic",
                        active ? LandMarkerService.remainingSeconds(player.getUuid(), property.id())
                                : ConfigManager.claims().markerDurationSeconds),
                ignored -> { LandMarkerService.toggle(player, property); details(player, propertyId, returnPage); });
        button(inventory, actions, 24, Items.GOLD_INGOT,
                Text.translatable("gui.terranexus.registry.contracts_rights"),
                Text.translatable("gui.terranexus.registry.contracts_rights.description"),
                ignored -> PropertyFinanceScreen.open(player, property));
        List<OwnershipChange> history = LandAuditState.get(player.getServer()).history(property.id());
        String historyText = history.isEmpty() ? "Erste Eigentümereintragung" : history.size() + " Wechsel · zuletzt "
                + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(history.getFirst().timestamp()));
        display(inventory, 26, Items.CLOCK, Text.translatable("gui.terranexus.registry.owner_history"),
                Text.literal(historyText));
        button(inventory, actions, 8, Items.ARROW, Text.translatable("gui.terranexus.back"),
                Text.translatable("gui.terranexus.registry.back_to_extract"), ignored -> owned(player, returnPage));
        button(inventory, actions, 53, Items.BARRIER, Text.translatable("gui.terranexus.registry.close_file"),
                Text.translatable("gui.terranexus.registry.close_file.description"), ignored -> { });
        menu(player, inventory, actions, Text.translatable("gui.terranexus.registry.file_title"));
    }

    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
    }
    private static void display(SimpleInventory inventory, int slot, Item item, Text name, Text detail) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, Item item, Text name, Text detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        display(inventory, slot, item, name, detail); actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN));
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, Text title) {
        CustomGuiService.open(player, inventory, actions, title.copy().formatted(Formatting.DARK_GREEN));
    }
}
