package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.landlord.LandLease;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.minecraft.inventory.SimpleInventory;
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
import java.util.UUID;
import java.util.function.Consumer;

public final class LandlordLeaseScreen {
    private LandlordLeaseScreen() {}

    public static void openPersonal(ServerPlayerEntity player) { open(player, Context.PERSONAL, player.getUuidAsString(), 0); }
    public static void openInstitution(ServerPlayerEntity player, String institutionId) { open(player, Context.INSTITUTION, institutionId, 0); }
    public static void openArea(ServerPlayerEntity player, String areaId) { open(player, Context.AREA, areaId, 0); }

    private static void open(ServerPlayerEntity player, Context context, String ownerId, int requestedPage) {
        if (!authorized(player, context, ownerId)) { denied(player); return; }
        LandlordState lands = LandlordState.get(player.getServer());
        LandManagementState management = LandManagementState.get(player.getServer());
        List<Entry> entries = new ArrayList<>();
        for (LandLease lease : management.leases()) {
            LandProperty property = lands.get(lease.propertyId());
            if (property != null && belongs(property, context, ownerId)) entries.add(new Entry(property, lease));
        }
        entries.sort(Comparator.comparing(entry -> entry.property().name(), String.CASE_INSENSITIVE_ORDER));
        int active = (int) entries.stream().filter(entry -> entry.lease().active()).count();
        int pageSize = Math.min(36, ConfigManager.desktop().standardEntriesPerPage);
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITTEN_BOOK, "Vermieterübersicht",
                active + " aktiv · " + (entries.size() - active) + " ausstehend · Seite " + (page + 1) + "/" + pages);
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> open(player, context, ownerId, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> open(player, context, ownerId, page + 1));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Grundstücksübersicht",
                ignored -> back(player, context, ownerId));
        int slot = 9;
        for (Entry entry : entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize))) {
            LandLease lease = entry.lease();
            String status = lease.active() ? "Aktiv" : "Annahme ausstehend";
            String term = lease.termPayments() <= 0 ? "unbefristet"
                    : lease.paymentsCompleted() + "/" + lease.termPayments() + " Zahlungen";
            String detail = tenantName(player, lease.tenantId()) + " · " + status + " · "
                    + EconomyState.format(lease.rent()) + " alle " + lease.periodDays() + " Tag(e) · " + term;
            button(inventory, actions, slot++, lease.active() ? Items.LIME_DYE : Items.YELLOW_DYE,
                    entry.property().name(), detail, ignored -> details(player, context, ownerId, entry.property().id(), page));
        }
        menu(player, inventory, actions, "Grundbuch · Vermietungen");
    }

    private static void details(ServerPlayerEntity player, Context context, String ownerId,
                                String propertyId, int returnPage) {
        if (!authorized(player, context, ownerId)) { denied(player); return; }
        LandProperty property = LandlordState.get(player.getServer()).get(propertyId);
        LandLease lease = LandManagementState.get(player.getServer()).lease(propertyId);
        if (property == null || lease == null || !belongs(property, context, ownerId)) {
            open(player, context, ownerId, returnPage);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITTEN_BOOK, property.name(),
                lease.active() ? "Aktiver Mietvertrag" : "Mietangebot wartet auf Annahme");
        ManagementHubScreen.display(inventory, 19, Items.PLAYER_HEAD, "Mieter", tenantName(player, lease.tenantId()));
        ManagementHubScreen.display(inventory, 21, Items.GOLD_INGOT, "Miete und Kaution",
                EconomyState.format(lease.rent()) + " · Kaution " + EconomyState.format(lease.deposit()));
        ManagementHubScreen.display(inventory, 23, Items.CLOCK, "Laufzeit",
                lease.active() ? "Beginn " + date(lease.startedAt()) + " · Ende " + endDate(lease)
                        : "Beginnt nach Annahme · " + (lease.termPayments() <= 0 ? "unbefristet" : lease.termPayments() + " Zahlungen"));
        ManagementHubScreen.display(inventory, 25, lease.missedPayments() > 0 ? Items.RED_DYE : Items.LIME_DYE,
                "Zahlungsstatus", lease.missedPayments() + " Fehlzahlung(en) · nächste Fälligkeit "
                        + (lease.nextDueAt() > 0 ? date(lease.nextDueAt()) : "nach Vertragsannahme"));
        var claimConfig = ConfigManager.claims();
        ManagementHubScreen.display(inventory, 29, Items.OAK_DOOR, "Automatische Mieterrechte",
                "Interaktion " + yesNo(claimConfig.tenantInteractionAllowed)
                        + " · Container " + yesNo(claimConfig.tenantContainerAccess)
                        + " · Redstone " + yesNo(claimConfig.tenantRedstoneAccess)
                        + " · Bauen " + yesNo(claimConfig.tenantBuildingAllowed));
        ManagementHubScreen.display(inventory, 31, Items.COMPARATOR, "Verlängerung",
                lease.autoRenew() ? "Automatisch" : "Vertrag endet nach der Laufzeit");
        button(inventory, actions, 33, Items.FILLED_MAP, "Grundstücksakte", "Verträge und Rechte verwalten",
                ignored -> PropertyFinanceScreen.open(player, property));
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zur Vermieterübersicht",
                ignored -> open(player, context, ownerId, returnPage));
        menu(player, inventory, actions, "Mietvertrag · Details");
    }

    private static boolean authorized(ServerPlayerEntity player, Context context, String ownerId) {
        return switch (context) {
            case PERSONAL -> ownerId.equals(player.getUuidAsString());
            case INSTITUTION -> InstitutionAccess.has(player, ownerId, InstitutionPermission.MANAGE_PROPERTY);
            case AREA -> LandManagementState.get(player.getServer()).mayManageAreaFinances(ownerId, player);
        };
    }

    private static boolean belongs(LandProperty property, Context context, String ownerId) {
        return switch (context) {
            case PERSONAL -> property.ownerType().equals("player") && property.ownerId().equals(ownerId);
            case INSTITUTION -> property.ownerType().equals("institution") && property.ownerId().equals(ownerId);
            case AREA -> property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE) && property.ownerId().equals(ownerId);
        };
    }

    private static void back(ServerPlayerEntity player, Context context, String ownerId) {
        switch (context) {
            case PERSONAL -> LandRegistryScreen.open(player);
            case INSTITUTION -> InstitutionManagementScreen.open(player, ownerId);
            case AREA -> AreaFinanceScreen.openArea(player, ownerId);
        }
    }

    private static String tenantName(ServerPlayerEntity player, String id) {
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(id));
            return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName();
        } catch (IllegalArgumentException ignored) { return "Ungültige Bürger-ID"; }
    }

    private static String endDate(LandLease lease) {
        return lease.endsAt() <= 0 ? "unbefristet" : date(lease.endsAt());
    }
    private static String yesNo(boolean value) { return value ? "erlaubt" : "gesperrt"; }
    private static String date(long timestamp) {
        return timestamp <= 0 ? "nicht festgelegt" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }
    private static void denied(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("Keine Berechtigung für diese Vermieterübersicht.").formatted(Formatting.RED), false);
    }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String name, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
        actions.put(slot, action);
    }
    private static void menu(ServerPlayerEntity player, SimpleInventory inventory,
                             Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) {
        CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN));
    }

    private enum Context { PERSONAL, INSTITUTION, AREA }
    private record Entry(LandProperty property, LandLease lease) {}
}
