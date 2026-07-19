package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.identity.RoleplayNames;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class CitizenRecordScreen {
    private CitizenRecordScreen() {}

    public static void open(ServerPlayerEntity officer, ServerPlayerEntity citizen) {
        if (!AuthorityState.mayManageIdentity(officer)) {
            officer.sendMessage(Text.literal("Keine Berechtigung zur Bearbeitung von Bürgerakten.").formatted(Formatting.RED), false);
            return;
        }
        IdentityState state = IdentityState.get(officer.getServer());
        CitizenIdentity identity = state.get(citizen.getUuid());
        if (identity == null) {
            ImmigrationScreen.open(officer);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.NAME_TAG, identity.firstName() + " " + identity.lastName(), identity.citizenNumber());
        addField(inventory, actions, 10, Items.PAPER, "Vorname", "vorname", identity::firstName, officer, citizen);
        addField(inventory, actions, 12, Items.PAPER, "Nachname", "nachname", identity::lastName, officer, citizen);
        addField(inventory, actions, 14, Items.CLOCK, "Geburtsdatum", "geburtsdatum", identity::birthDate, officer, citizen);
        addField(inventory, actions, 16, Items.COMPASS, "Geburtsort", "geburtsort", identity::birthPlace, officer, citizen);
        addField(inventory, actions, 28, Items.MAP, "Geburtsland", "geburtsland", identity::birthCountry, officer, citizen);
        addField(inventory, actions, 30, Items.WRITABLE_BOOK, "Nationalität", "nationalitaet", identity::nationality, officer, citizen);
        addField(inventory, actions, 32, Items.PLAYER_HEAD, "Geschlecht", "geschlecht", identity::gender, officer, citizen);
        addField(inventory, actions, 34, Items.OAK_DOOR, "Meldeadresse", "adresse", identity::address, officer, citizen);

        boolean approved = state.isApproved(citizen.getUuid());
        ManagementHubScreen.display(inventory, 47, approved ? Items.LIME_DYE : Items.YELLOW_DYE,
                approved ? "Einreise freigegeben" : "Einreise freigeben",
                approved ? "Klicken, um die Freigabe zurückzunehmen" : "Klicken, um die Einreise zu bestätigen");
        actions.put(47, ignored -> {
            if (!AuthorityState.mayManageIdentity(officer)) return;
            if (state.isApproved(citizen.getUuid())) state.revokeApproval(citizen.getUuid());
            else state.approve(citizen.getUuid(), officer.getUuid());
            open(officer, citizen);
        });
        if (approved) {
            ManagementHubScreen.display(inventory, 49, Items.PAPER, "Personalausweis ausstellen", "Aktuellen Ausweis an den Bürger übergeben");
            actions.put(49, ignored -> {
                if (!AuthorityState.mayManageIdentity(officer) || !state.isApproved(citizen.getUuid())) return;
                CitizenIdentity current = state.get(citizen.getUuid());
                citizen.giveItemStack(CitizenIdCardItem.createCard(ModItems.CITIZEN_ID_CARD, current));
                open(officer, citizen);
            });
        }
        ManagementHubScreen.display(inventory, 53, Items.ARROW, "Zurück", "Zur Einreiseübersicht");
        actions.put(53, ignored -> ImmigrationScreen.open(officer));

        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new ActionMenuScreenHandler(syncId, playerInventory, inventory, actions),
                Text.literal("Bürgerakte bearbeiten").formatted(Formatting.DARK_AQUA)));
    }

    private static void addField(SimpleInventory inventory,
                                 Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                 int slot, Item item, String label, String field, Supplier<String> getter,
                                 ServerPlayerEntity officer, ServerPlayerEntity citizen) {
        CitizenIdentity current = IdentityState.get(officer.getServer()).get(citizen.getUuid());
        ManagementHubScreen.display(inventory, slot, item, label + " bearbeiten", getter.get());
        actions.put(slot, ignored -> openEditor(officer, citizen, field, label));
    }

    private static void openEditor(ServerPlayerEntity officer, ServerPlayerEntity citizen, String field, String label) {
        if (!AuthorityState.mayManageIdentity(officer)) return;
        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
                    if (!AuthorityState.mayManageIdentity(officer)) return;
                    String error = validate(field, value);
                    if (error != null) {
                        officer.sendMessage(Text.literal(error).formatted(Formatting.RED), false);
                        openEditor(officer, citizen, field, label);
                        return;
                    }
                    IdentityState state = IdentityState.get(officer.getServer());
                    CitizenIdentity old = state.get(citizen.getUuid());
                    if (old != null) {
                        CitizenIdentity changed = old.withField(field, value.trim());
                        if (changed != null) {
                            state.put(changed);
                            RoleplayNames.apply(citizen);
                        }
                    }
                    open(officer, citizen);
                }), Text.literal(label + " ändern").formatted(Formatting.DARK_AQUA)));
    }

    private static String validate(String field, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) return "Das Feld darf nicht leer sein.";
        if (trimmed.length() > 80) return "Der Wert ist zu lang (maximal 80 Zeichen).";
        if (field.equals("geburtsdatum")) {
            try {
                LocalDate date = LocalDate.parse(trimmed,
                        DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT));
                if (date.isAfter(LocalDate.now())) return "Das Geburtsdatum darf nicht in der Zukunft liegen.";
            } catch (DateTimeParseException exception) {
                return "Bitte das Geburtsdatum als TT.MM.JJJJ eingeben.";
            }
        }
        return null;
    }
}
