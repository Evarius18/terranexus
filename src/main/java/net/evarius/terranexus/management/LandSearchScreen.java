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

    public static void ask(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) ->
                new TextInputScreenHandler(id, inventory, query -> results(player,
                        query == null ? "" : query.trim().toLowerCase(Locale.ROOT), 0)),
                Text.literal("ID, Name, Adresse oder Besitzer")));
    }

    private static void results(ServerPlayerEntity player, String query, int requestedPage) {
        IdentityState identities = IdentityState.get(player.getServer());
        LandManagementState management = LandManagementState.get(player.getServer());
        List<SearchResult> matches = LandlordState.get(player.getServer()).all().stream().map(property ->
                        new SearchResult(property, ownerName(identities, property), management.address(property.id())))
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
                    result.address + " · " + result.property.id() + " · " + result.owner);
            actions.put(slot++, ignored -> PropertyFinanceScreen.open(player, result.property));
        }
        openMenu(player, inventory, actions, "Grundstückssuche");
    }

    public static void audit(ServerPlayerEntity player) {
        if (!AuthorityState.mayAdministerLand(player)) return;
        List<LandAuditEntry> entries = LandAuditState.get(player.getServer()).recent();
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Grundstücksprotokoll",
                "Letzte " + entries.size() + " Vorgänge · persistent");
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Bauamt");
        actions.put(8, ignored -> PropertyScreen.open(player));
        int slot = 9;
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (LandAuditEntry entry : entries)
            ManagementHubScreen.display(inventory, slot++, Items.PAPER, entry.action() + " · " + entry.propertyId(),
                    format.format(new Date(entry.timestamp())) + " · " + entry.details());
        openMenu(player, inventory, actions, "Audit-Log");
    }

    private static boolean matches(SearchResult result, String query) {
        return result.property.id().toLowerCase(Locale.ROOT).contains(query)
                || result.property.name().toLowerCase(Locale.ROOT).contains(query)
                || result.owner.toLowerCase(Locale.ROOT).contains(query)
                || result.address.toLowerCase(Locale.ROOT).contains(query);
    }
    private static String ownerName(IdentityState identities, LandProperty property) {
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
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory1, ignored) ->
                new ActionMenuScreenHandler(id, inventory1, inventory, actions), Text.literal(title).formatted(Formatting.DARK_GREEN)));
    }
    private record SearchResult(LandProperty property, String owner, String address) {}
}
