package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.identity.RoleplayNames;
import net.evarius.terranexus.config.ConfigManager;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class CitizenRecordScreen {
    private CitizenRecordScreen() {}

    public static void open(ServerPlayerEntity officer, ServerPlayerEntity citizen) {
        open(officer, citizen.getUuid(), 0);
    }

    public static void open(ServerPlayerEntity officer, UUID citizenId, int returnPage) {
        if (!AuthorityState.mayManageIdentity(officer)) {
            officer.sendMessage(Text.literal("Keine Berechtigung zur Bearbeitung von Bürgerakten.").formatted(Formatting.RED), false);
            return;
        }
        IdentityState state = IdentityState.get(officer.getServer());
        CitizenIdentity identity = state.get(citizenId);
        if (identity == null) {
            ImmigrationScreen.open(officer, returnPage);
            return;
        }
        ServerPlayerEntity citizen = officer.getServer().getPlayerManager().getPlayer(citizenId);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.NAME_TAG, identity.firstName() + " " + identity.lastName(), identity.citizenNumber());
        addField(inventory, actions, 10, Items.PAPER, "Vorname", "vorname", identity::firstName, officer, citizenId, returnPage);
        addField(inventory, actions, 12, Items.PAPER, "Nachname", "nachname", identity::lastName, officer, citizenId, returnPage);
        addField(inventory, actions, 14, Items.CLOCK, "Geburtsdatum", "geburtsdatum", identity::birthDate, officer, citizenId, returnPage);
        addField(inventory, actions, 16, Items.COMPASS, "Geburtsort", "geburtsort", identity::birthPlace, officer, citizenId, returnPage);
        addField(inventory, actions, 28, Items.MAP, "Geburtsland", "geburtsland", identity::birthCountry, officer, citizenId, returnPage);
        addField(inventory, actions, 30, Items.WRITABLE_BOOK, "Nationalität", "nationalitaet", identity::nationality, officer, citizenId, returnPage);
        addField(inventory, actions, 32, Items.PLAYER_HEAD, "Geschlecht", "geschlecht", identity::gender, officer, citizenId, returnPage);
        actions.put(32,ignored->openGenderSelection(officer,citizenId,returnPage));
        addField(inventory, actions, 34, Items.OAK_DOOR, "Meldeadresse", "adresse", identity::address, officer, citizenId, returnPage);

        boolean approved = state.isApproved(citizenId);
        ManagementHubScreen.display(inventory, 47, approved ? Items.LIME_DYE : Items.YELLOW_DYE,
                approved ? "Einreise freigegeben" : "Einreise freigeben",
                approved ? "Klicken, um die Freigabe zurückzunehmen" : "Klicken, um die Einreise zu bestätigen");
        actions.put(47, ignored -> {
            if (!AuthorityState.mayManageIdentity(officer)) return;
            if (state.isApproved(citizenId)) state.revokeApproval(citizenId);
            else state.approve(citizenId, officer.getUuid());
            open(officer, citizenId, returnPage);
        });
        if (approved && citizen != null) {
            ManagementHubScreen.display(inventory, 49, Items.PAPER, "Personalausweis ausstellen", "Aktuellen Ausweis an den Bürger übergeben");
            actions.put(49, ignored -> {
                if (!AuthorityState.mayManageIdentity(officer) || !state.isApproved(citizenId)) return;
                CitizenIdentity current = state.get(citizenId);
                citizen.giveItemStack(CitizenIdCardItem.createCard(ModItems.CITIZEN_ID_CARD, current));
                open(officer, citizenId, returnPage);
            });
        } else if (approved) {
            ManagementHubScreen.display(inventory, 49, Items.GRAY_DYE, "Bürger offline", "Ausweis kann derzeit nicht übergeben werden");
        }
        ManagementHubScreen.display(inventory, 53, Items.ARROW, "Zurück", "Zur Einreiseübersicht");
        actions.put(53, ignored -> ImmigrationScreen.open(officer, returnPage));

        CustomGuiService.open(officer, inventory, actions,
                Text.literal("Bürgerakte bearbeiten").formatted(Formatting.DARK_AQUA));
    }

    private static void addField(SimpleInventory inventory,
                                 Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                 int slot, Item item, String label, String field, Supplier<String> getter,
                                 ServerPlayerEntity officer, UUID citizenId, int returnPage) {
        ManagementHubScreen.display(inventory, slot, item, label + " bearbeiten", getter.get());
        actions.put(slot, ignored -> openEditor(officer, citizenId, field, label, returnPage));
    }

    private static void openEditor(ServerPlayerEntity officer, UUID citizenId, String field, String label, int returnPage) {
        if (!AuthorityState.mayManageIdentity(officer)) return;
        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
                    if (!AuthorityState.mayManageIdentity(officer)) return;
                    String error = validate(field, value);
                    if (error != null) {
                        officer.sendMessage(Text.literal(error).formatted(Formatting.RED), false);
                        openEditor(officer, citizenId, field, label, returnPage);
                        return;
                    }
                    IdentityState state = IdentityState.get(officer.getServer());
                    CitizenIdentity old = state.get(citizenId);
                    if (old != null) {
                        CitizenIdentity changed = old.withField(field, value.trim());
                        if (changed != null) {
                            state.put(changed);
                            ServerPlayerEntity online = officer.getServer().getPlayerManager().getPlayer(citizenId);
                            if(online != null) RoleplayNames.apply(online);
                        }
                    }
                    open(officer, citizenId, returnPage);
                }), Text.literal(label + " ändern").formatted(Formatting.DARK_AQUA)));
    }

    private static String validate(String field, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) return "Das Feld darf nicht leer sein.";
        int maximumLength = ConfigManager.immigration().maximumFieldLength;
        if (trimmed.length() > maximumLength) return "Der Wert ist zu lang (maximal " + maximumLength + " Zeichen).";
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
    private static void openGenderSelection(ServerPlayerEntity officer,UUID citizenId,int returnPage){List<SelectionMenuScreen.Option> options=java.util.List.of(new SelectionMenuScreen.Option("Weiblich","Weiblich","Amtliche Auswahl",Items.MAGENTA_DYE),new SelectionMenuScreen.Option("Männlich","Männlich","Amtliche Auswahl",Items.BLUE_DYE),new SelectionMenuScreen.Option("Divers","Divers","Amtliche Auswahl",Items.PURPLE_DYE),new SelectionMenuScreen.Option("Keine Angabe","Keine Angabe","Ohne Geschlechtseintrag",Items.GRAY_DYE));SelectionMenuScreen.open(officer,"Geschlecht ändern",options,value->{if(!AuthorityState.mayManageIdentity(officer)){officer.sendMessage(Text.literal("Deine Verwaltungsberechtigung ist nicht mehr gültig.").formatted(Formatting.RED),false);return;}IdentityState state=IdentityState.get(officer.getServer());CitizenIdentity old=state.get(citizenId);if(old!=null)state.put(old.withField("geschlecht",value));open(officer,citizenId,returnPage);},()->open(officer,citizenId,returnPage));}
}
