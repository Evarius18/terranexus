package net.evarius.terranexus.phone;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.landlord.LandLease;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandSaleOffer;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.management.CustomGuiService;
import net.evarius.terranexus.management.ManagementHubScreen;
import net.evarius.terranexus.management.PropertyFinanceScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Mobile entry point for the existing land sale and lease services. */
public final class RealEstatePhoneApplication implements PhoneApplication {
    @Override public String id() { return "terranexus:real_estate"; }
    @Override public String title() { return "Immobilien"; }
    @Override public String description() { return "Kaufen, mieten und verwalten"; }
    @Override public Item icon() { return Items.OAK_DOOR; }

    @Override
    public void open(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.OAK_DOOR, "Immobilien",
                "Grundstücke, Häuser und Gewerbeobjekte");
        button(inventory, actions, 20, Items.EMERALD, "Markt", "Verfügbare Kaufobjekte",
                ignored -> market(player));
        button(inventory, actions, 22, Items.FILLED_MAP, "Mein Eigentum", "Eigene Immobilien und Grundstücke",
                ignored -> personal(player, false));
        button(inventory, actions, 24, Items.WRITABLE_BOOK, "Meine Mieten", "Aktive Mietverhältnisse",
                ignored -> personal(player, true));
        button(inventory, actions, 26, Items.CLOCK, "Wohnung & Hotel", "Öffentliche Miet- und Self-Check-in-Angebote",
                ignored -> rentalMarket(player));
        back(inventory, actions, player);
        menu(player, inventory, actions, "TN-Handy · Immobilien");
    }

    private static void market(ServerPlayerEntity player) {
        LandManagementState management = LandManagementState.get(player.getServer());
        List<Map.Entry<LandProperty, LandSaleOffer>> entries = new ArrayList<>();
        for (LandSaleOffer offer : management.sales()) {
            LandProperty property = LandlordState.get(player.getServer()).get(offer.propertyId());
            if (property != null) entries.add(Map.entry(property, offer));
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.EMERALD, "Immobilienmarkt",
                entries.size() + " verfügbare Objekte");
        int slot = 9;
        for (Map.Entry<LandProperty, LandSaleOffer> entry : entries.stream().limit(36).toList()) {
            LandProperty property = entry.getKey();
            String detail = management.landUse(property.id()) + " · "
                    + management.address(property.id()) + " · " + EconomyState.format(entry.getValue().price());
            button(inventory, actions, slot++, Items.OAK_DOOR, property.name(), detail,
                    ignored -> PropertyFinanceScreen.open(player, property));
        }
        phoneBack(inventory, actions, player);
        menu(player, inventory, actions, "TN-Handy · Immobilienmarkt");
    }

    private static void personal(ServerPlayerEntity player, boolean rentals) {
        LandManagementState management = LandManagementState.get(player.getServer());
        List<LandProperty> entries = LandlordState.get(player.getServer()).all().stream().filter(property -> {
            if (rentals) {
                LandLease lease = management.lease(property.id());
                return lease != null && lease.tenantId().equals(player.getUuidAsString());
            }
            return property.isOwnedBy(player.getUuid());
        }).toList();
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, rentals ? Items.WRITABLE_BOOK : Items.FILLED_MAP,
                rentals ? "Meine Mieten" : "Mein Eigentum", entries.size() + " Objekte");
        int slot = 9;
        for (LandProperty property : entries.stream().limit(36).toList()) {
            String detail = management.landUse(property.id()) + " · " + management.address(property.id());
            button(inventory, actions, slot++, Items.OAK_DOOR, property.name(), detail,
                    ignored -> PropertyFinanceScreen.open(player, property));
        }
        phoneBack(inventory, actions, player);
        menu(player, inventory, actions, rentals
                ? "TN-Handy · Mietobjekte" : "TN-Handy · Eigentum");
    }

    private static void rentalMarket(ServerPlayerEntity player) {
        LandManagementState management = LandManagementState.get(player.getServer());
        List<Map.Entry<LandProperty, LandLease>> entries = new ArrayList<>();
        for (LandLease lease : management.leases()) {
            if (!lease.publicOffer()) continue;
            LandProperty property = LandlordState.get(player.getServer()).get(lease.propertyId());
            if (property != null) entries.add(Map.entry(property, lease));
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.CLOCK, "Wohnung & Hotel",
                entries.size() + " direkt buchbare Objekte");
        int slot = 9;
        for (Map.Entry<LandProperty, LandLease> entry : entries.stream().limit(36).toList()) {
            LandProperty property = entry.getKey();
            LandLease lease = entry.getValue();
            String term = lease.termPayments() <= 0 ? "unbefristet"
                    : lease.termPayments() + " Zeitraum/Zeiträume";
            String detail = management.landUse(property.id()) + " · "
                    + EconomyState.format(lease.rent()) + " alle " + lease.periodDays()
                    + " Tag(e) · " + term;
            button(inventory, actions, slot++, Items.OAK_DOOR, property.name(), detail,
                    ignored -> PropertyFinanceScreen.open(player, property));
        }
        phoneBack(inventory, actions, player);
        menu(player, inventory, actions, "TN-Handy · Buchungsmarkt");
    }

    private static void back(SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                             ServerPlayerEntity player) {
        ManagementHubScreen.display(inventory, 49, Items.ARROW, "Zurück", "Zum Startbildschirm");
        actions.put(49, ignored -> PhoneScreen.open(player));
    }

    private static void phoneBack(SimpleInventory inventory,
                                  Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                  ServerPlayerEntity player) {
        ManagementHubScreen.display(inventory, 49, Items.ARROW, "Zurück", "Zur Immobilien-App");
        actions.put(49, ignored -> new RealEstatePhoneApplication().open(player));
    }

    private static void button(SimpleInventory inventory,
                               Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, Item item, String name, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
        actions.put(slot, action);
    }

    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                             String title) {
        CustomGuiService.open(player, inventory, actions,
                Text.literal(title).formatted(Formatting.DARK_AQUA));
    }
}
