package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstitutionScreen {
    private InstitutionScreen() {}

    public static void open(ServerPlayerEntity player) {
        open(player, 0);
    }

    private static void open(ServerPlayerEntity player, int requestedPage) {
        InstitutionState state = InstitutionState.get(player.getServer());
        List<Institution> visible = AuthorityState.isTnAdmin(player) ? state.all() : state.forMember(player.getUuid());
        int pageSize = ConfigManager.desktop().standardEntriesPerPage;
        int pages = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.BRICKS, "Institutionen", visible.size() + " Organisationen · Seite " + (page + 1) + "/" + pages);
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Verwaltungsübersicht");
        actions.put(8, ignored -> home(player));
        if (page > 0) { ManagementHubScreen.display(inventory, 0, Items.ARROW, "Vorherige Seite", "Seite " + page); actions.put(0, ignored -> open(player, page - 1)); }
        if (page + 1 < pages) { ManagementHubScreen.display(inventory, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2)); actions.put(7, ignored -> open(player, page + 1)); }
        int slot = 9;
        for (Institution institution : visible.subList(page * pageSize, Math.min(visible.size(), (page + 1) * pageSize))) {
            String role = state.employee(institution.id(), player.getUuid()) == null ? "TNAdmin-Testzugriff"
                    : state.employee(institution.id(), player.getUuid()).institutionRole().label();
            ManagementHubScreen.display(inventory, slot, Items.BRICKS, institution.name(), institution.type() + " · " + role);
            actions.put(slot++, ignored -> InstitutionManagementScreen.open(player, institution.id()));
        }
        if (mayCreate(player)) {
            ManagementHubScreen.display(inventory, 49, Items.EMERALD, "Institution gründen", "Organisation mit Eigentümer und Konto anlegen");
            actions.put(49, ignored -> createStep(player, new ArrayList<>(), 0));
        }
        CustomGuiService.open(player, inventory, actions,
                Text.literal("TerraNexus Institutionen").formatted(Formatting.DARK_AQUA));
    }

    private static void createStep(ServerPlayerEntity player, List<String> values, int step) {
        if (!mayCreate(player)) { denied(player); home(player); return; }
        if (step == 1) { selectType(player, values); return; }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, ignored) ->
                new TextInputScreenHandler(syncId, inv, value -> {
                    value = value == null ? "" : value.trim();
                    int maximumLength = ConfigManager.institutions().maximumNameLength;
                    if (value.isBlank() || value.length() > maximumLength || !InstitutionState.get(player.getServer()).isNameAvailable(value)) {
                        player.sendMessage(Text.literal(value.isBlank() ? "Der Institutionsname darf nicht leer sein."
                                : value.length() > maximumLength ? "Der Institutionsname darf maximal " + maximumLength + " Zeichen lang sein."
                                : "Dieser Institutionsname wird bereits verwendet.").formatted(Formatting.RED), false);
                        createStep(player, new ArrayList<>(), 0); return;
                    }
                    values.add(value); createStep(player, values, 1);
                }), Text.literal("Name der Institution").formatted(Formatting.DARK_AQUA)));
    }

    private static void selectType(ServerPlayerEntity player, List<String> values) {
        List<SelectionMenuScreen.Option> options = ConfigManager.institutions().allowedTypes.stream()
                .map(type -> new SelectionMenuScreen.Option(type, type, "Konfigurierte Institutionsart", iconFor(type))).toList();
        SelectionMenuScreen.open(player, "Institutionsart auswählen", options, type -> {
            if (!mayCreate(player) || values.isEmpty()) { denied(player); home(player); return; }
            InstitutionState state = InstitutionState.get(player.getServer());
            if (!state.isNameAvailable(values.get(0)) || !ConfigManager.institutions().allowedTypes.contains(type)) {
                player.sendMessage(Text.literal("Name oder Institutionsart ist nicht mehr verfügbar.").formatted(Formatting.RED), false);
                open(player); return;
            }
            long fee = ConfigManager.institutions().creationFee;
            Institution[] created = new Institution[1];
            boolean success = fee == 0 ? (created[0] = state.create(values.get(0), type, player.getUuid())) != null
                    : EconomyState.get(player.getServer()).transferConditional(EconomyState.playerAccount(player.getUuid()),
                    "system:institution_fees", fee, "Gründungsgebühr · " + values.get(0), player.getUuidAsString(), "",
                    "INSTITUTION_CREATION_FEE", () -> mayCreate(player)
                            && (created[0] = state.create(values.get(0), type, player.getUuid())) != null);
            if (!success || created[0] == null) {
                player.sendMessage(Text.literal("Die Institution konnte nicht angelegt oder die Gründungsgebühr nicht bezahlt werden.").formatted(Formatting.RED), false);
                open(player); return;
            }
            Institution institution = created[0];
            EconomyState.get(player.getServer()).ensureAccount(EconomyState.institutionAccount(institution.id()));
            InstitutionManagementScreen.open(player, institution.id());
        }, () -> createStep(player, new ArrayList<>(), 0));
    }

    private static boolean mayCreate(ServerPlayerEntity player) {
        return AuthorityState.isTnAdmin(player) || player.hasPermissionLevel(2)
                || AuthorityState.get(player.getServer()).has(player.getUuid(), AuthorityState.CIVIL_REGISTRAR);
    }
    private static void denied(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("Keine Berechtigung zur Institutionsgründung.").formatted(Formatting.RED), false);
    }
    private static net.minecraft.item.Item iconFor(String type) {
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("bank") || normalized.contains("finanz")) return Items.GOLD_BLOCK;
        if (normalized.contains("behörde")) return Items.IRON_BLOCK;
        if (normalized.contains("bildung")) return Items.BOOKSHELF;
        if (normalized.contains("rettung")) return Items.RED_DYE;
        if (normalized.contains("verein")) return Items.OAK_SIGN;
        return Items.BRICKS;
    }
    private static void home(ServerPlayerEntity player) {
        if (AuthorityState.mayManageIdentity(player) || AuthorityState.mayUseLandOffice(player)
                || !InstitutionState.get(player.getServer()).forMember(player.getUuid()).isEmpty()) AdminDesktopScreen.open(player);
        else ManagementHubScreen.open(player);
    }
}
