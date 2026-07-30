package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandTransferRequest;
import net.evarius.terranexus.landlord.LandTransferService;
import net.evarius.terranexus.landlord.LandlordState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class LandTransferScreen {
    private LandTransferScreen() {}

    public static void openRegistry(ServerPlayerEntity player) { open(player, false, true, 0); }
    public static void openFromCommand(ServerPlayerEntity player) { open(player, false, false, 0); }
    public static void openLandOffice(ServerPlayerEntity player) { open(player, true, false, 0); }

    private static void open(ServerPlayerEntity player, boolean landOffice, boolean returnToRegistry, int requestedPage) {
        List<LandTransferRequest> requests = LandTransferService.pendingFor(player);
        int pageSize = 36;
        int pages = Math.max(1, (requests.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Grundstücksübertragungen",
                requests.size() + " offene Vorgänge · Seite " + (page + 1) + "/" + pages);
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück",
                landOffice ? "Zur Grundstücksverwaltung" : returnToRegistry ? "Zum Grundbuchauszug" : "Fenster schließen");
        actions.put(8, ignored -> {
            if (landOffice) PropertyScreen.open(player);
            else if (returnToRegistry) LandRegistryScreen.open(player);
            else player.closeHandledScreen();
        });
        if (landOffice && LandTransferService.mayInitiate(player))
            button(inventory, actions, 6, Items.PLAYER_HEAD, "Neue Umschreibung",
                    "Grundstück und neuen Eigentümer auswählen",
                    ignored -> selectProperty(player, 0));
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite", "Seite " + page,
                ignored -> open(player, landOffice, returnToRegistry, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2),
                ignored -> open(player, landOffice, returnToRegistry, page + 1));

        int slot = 9;
        for (LandTransferRequest request : requests.subList(page * pageSize, Math.min(requests.size(), (page + 1) * pageSize))) {
            LandProperty property = LandlordState.get(player.getServer()).get(request.propertyId());
            String propertyName = property == null ? "Nicht mehr vorhanden" : property.name();
            String detail = ownerLabel(player, request.oldOwnerType(), request.oldOwnerId()) + " → "
                    + ownerLabel(player, request.newOwnerType(), request.newOwnerId()) + " · " + approvalStatus(request);
            if (LandTransferService.canApprove(player, request)) {
                button(inventory, actions, slot++, Items.WRITTEN_BOOK, propertyName,
                        detail + " · " + Text.translatable("gui.terranexus.transfer.open_extract").getString(),
                        ignored -> review(player, request.id(), landOffice, returnToRegistry, page));
            } else ManagementHubScreen.display(inventory, slot++, Items.PAPER, propertyName, detail);
        }
        CustomGuiService.open(player, inventory, actions, Text.literal("Grundbuch · Übertragungen").formatted(Formatting.DARK_GREEN));
    }

    private static void selectProperty(ServerPlayerEntity player, int requestedPage) {
        if (!LandTransferService.mayInitiate(player)) {
            player.sendMessage(Text.literal(
                    net.evarius.terranexus.landlord.LandPermissionService.transferDenial(player))
                    .formatted(Formatting.RED), false);
            openLandOffice(player);
            return;
        }
        List<LandProperty> properties = LandlordState.get(player.getServer()).all();
        int pageSize = Math.min(36, ConfigManager.desktop().standardEntriesPerPage);
        int pages = Math.max(1, (properties.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, "Grundstück auswählen",
                properties.size() + " Grundstücke · Seite " + (page + 1) + "/" + pages);
        button(inventory, actions, 8, Items.ARROW, "Zurück", "Zu den Übertragungsvorgängen",
                ignored -> openLandOffice(player));
        if (page > 0) button(inventory, actions, 0, Items.ARROW, "Vorherige Seite",
                "Seite " + page, ignored -> selectProperty(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 7, Items.ARROW, "Nächste Seite",
                "Seite " + (page + 2), ignored -> selectProperty(player, page + 1));
        int slot = 9;
        for (LandProperty property : properties.subList(page * pageSize,
                Math.min(properties.size(), (page + 1) * pageSize))) {
            String detail = ownerLabel(player, property.ownerType(), property.ownerId())
                    + " · " + property.regionType() + " · ID " + property.id();
            button(inventory, actions, slot++, Items.PAPER, property.name(), detail,
                    ignored -> PropertyScreen.openOwnerSelection(player, property));
        }
        CustomGuiService.open(player, inventory, actions,
                Text.literal("Grundbuch · Neue Umschreibung").formatted(Formatting.DARK_GREEN));
    }

    private static void review(ServerPlayerEntity player, String requestId, boolean landOffice,
                               boolean returnToRegistry, int returnPage) {
        LandTransferRequest request = LandTransferService.request(player.getServer(), requestId);
        if (request == null || !LandTransferService.canApprove(player, request)) {
            player.sendMessage(Text.translatable("message.terranexus.transfer.review_unavailable")
                    .formatted(Formatting.RED), false);
            open(player, landOffice, returnToRegistry, returnPage);
            return;
        }
        LandProperty property = LandlordState.get(player.getServer()).get(request.propertyId());
        if (property == null) {
            player.sendMessage(Text.translatable("message.terranexus.transfer.property_missing")
                    .formatted(Formatting.RED), false);
            open(player, landOffice, returnToRegistry, returnPage);
            return;
        }

        LandManagementState management = LandManagementState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITTEN_BOOK,
                Text.translatable("gui.terranexus.transfer.extract.title"),
                Text.translatable("gui.terranexus.transfer.extract.subtitle", property.name()));
        ManagementHubScreen.display(inventory, 10, Items.OAK_SIGN,
                Text.translatable("gui.terranexus.transfer.extract.address"),
                Text.literal(management.address(property.id())));
        ManagementHubScreen.display(inventory, 12, Items.COMPASS,
                Text.translatable("gui.terranexus.transfer.extract.location"),
                Text.translatable("gui.terranexus.transfer.extract.coordinates", property.dimension(),
                        property.minX(), property.minZ(), property.maxX(), property.maxZ()));
        ManagementHubScreen.display(inventory, 14, Items.PLAYER_HEAD,
                Text.translatable("gui.terranexus.transfer.extract.current_owner"),
                Text.literal(ownerLabel(player, request.oldOwnerType(), request.oldOwnerId())));
        ManagementHubScreen.display(inventory, 16, Items.EMERALD,
                Text.translatable("gui.terranexus.transfer.extract.new_owner"),
                Text.literal(ownerLabel(player, request.newOwnerType(), request.newOwnerId())));
        ManagementHubScreen.display(inventory, 28, Items.CLOCK,
                Text.translatable("gui.terranexus.transfer.extract.status"),
                Text.literal(approvalStatus(request)));
        button(inventory, actions, 45, Items.ARROW,
                Text.translatable("gui.terranexus.back"),
                Text.translatable("gui.terranexus.transfer.back_to_requests"),
                ignored -> open(player, landOffice, returnToRegistry, returnPage));
        button(inventory, actions, 49, Items.LIME_DYE,
                Text.translatable("gui.terranexus.transfer.approve_explicit"),
                Text.translatable("gui.terranexus.transfer.approve_warning"),
                ignored -> {
                    LandTransferService.Result result = LandTransferService.approve(player, request.id());
                    player.sendMessage(Text.literal(result.message())
                            .formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
                    open(player, landOffice, returnToRegistry, returnPage);
                });
        CustomGuiService.open(player, inventory, actions,
                Text.translatable("gui.terranexus.transfer.extract.screen_title").formatted(Formatting.DARK_GREEN));
    }

    private static String approvalStatus(LandTransferRequest request) {
        String source = request.oldOwnerType().equals("institution")
                ? (request.institutionApproved() ? "Institution ✓" : "Institution offen")
                : (request.ownerApproved() ? "Eigentümer ✓" : "Eigentümer offen");
        String recipient = !request.newOwnerType().equals("institution")
                ? (request.recipientApproved() ? "Empfänger ✓" : "Empfänger offen") : "Empfänger entfällt";
        return source + " · " + recipient;
    }

    private static String ownerLabel(ServerPlayerEntity player, String type, String id) {
        if (type.equals("institution")) {
            Institution institution = InstitutionState.get(player.getServer()).get(id);
            return institution == null ? "Unbekannte Institution" : institution.name();
        }
        if (type.equals(LandManagementState.AREA_OWNER_TYPE)) {
            AdministrativeArea area = LandManagementState.get(player.getServer()).area(id);
            return area == null ? "Unbekannte Verwaltung" : area.name();
        }
        try {
            CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(id));
            return identity == null ? "Unbekannter Bürger" : identity.firstName() + " " + identity.lastName();
        } catch (IllegalArgumentException ignored) { return "Unbekannter Bürger"; }
    }

    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, String name, String detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
        actions.put(slot, action);
    }

    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                               int slot, net.minecraft.item.Item item, Text name, Text detail,
                               Consumer<net.minecraft.entity.player.PlayerEntity> action) {
        ManagementHubScreen.display(inventory, slot, item, name, detail);
        actions.put(slot, action);
    }
}
