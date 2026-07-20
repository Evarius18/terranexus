package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.RoleplayNames;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class IdentityCreationWizard {
    private static final List<String> LABELS = List.of(
            "Vorname", "Nachname", "Geburtsdatum (TT.MM.JJJJ)", "Geburtsort",
            "Geburtsland", "Nationalität", "Geschlecht", "Meldeadresse");

    private IdentityCreationWizard() {}

    public static void start(ServerPlayerEntity officer, ServerPlayerEntity citizen) {
        if (!AuthorityState.mayManageIdentity(officer)) {
            officer.sendMessage(Text.literal("Du bist nicht zur Anlage von Bürgerakten berechtigt.").formatted(Formatting.RED), false);
            return;
        }
        IdentityState state = IdentityState.get(officer.getServer());
        if (state.get(citizen.getUuid()) != null) {
            officer.sendMessage(Text.literal("Für diese Person existiert bereits eine Bürgerakte.").formatted(Formatting.RED), false);
            return;
        }
        openStep(officer, citizen, new ArrayList<>(), 0);
    }

    private static void openStep(ServerPlayerEntity officer, ServerPlayerEntity citizen, List<String> values, int step) {
        if (!AuthorityState.mayManageIdentity(officer)) {
            officer.sendMessage(Text.literal("Deine Verwaltungsberechtigung ist nicht mehr gültig.").formatted(Formatting.RED), false);
            return;
        }
        if(step==6){openGenderSelection(officer,citizen,values);return;}
        String label = LABELS.get(step);
        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
                    if(!AuthorityState.mayManageIdentity(officer)){officer.sendMessage(Text.literal("Deine Verwaltungsberechtigung ist nicht mehr gültig.").formatted(Formatting.RED),false);return;}
                    value=value==null?"":value.trim();
                    int maximumLength=ConfigManager.immigration().maximumFieldLength;
                    if(value.isBlank()||value.length()>maximumLength){officer.sendMessage(Text.literal(value.isBlank()?"Das Feld darf nicht leer sein.":"Der Wert ist zu lang (maximal "+maximumLength+" Zeichen).").formatted(Formatting.RED),false);openStep(officer,citizen,values,step);return;}
                    if (step == 2 && !isValidBirthDate(value)) {
                        officer.sendMessage(Text.literal("Ungültiges Geburtsdatum. Bitte im Format TT.MM.JJJJ eingeben, z. B. 23.05.1990.")
                                .formatted(Formatting.RED), false);
                        openStep(officer, citizen, values, step);
                        return;
                    }
                    values.add(value);
                    if (step + 1 < LABELS.size()) {
                        openStep(officer, citizen, values, step + 1);
                    } else {
                        IdentityState state = IdentityState.get(officer.getServer());
                        if(state.get(citizen.getUuid())!=null){officer.sendMessage(Text.literal("Für diese Person existiert inzwischen bereits eine Bürgerakte.").formatted(Formatting.RED),false);ImmigrationScreen.open(officer);return;}
                        CitizenIdentity identity = state.create(citizen.getUuid(), values.get(0), values.get(1),
                                values.get(2), values.get(3), values.get(4), values.get(5));
                        net.evarius.terranexus.economy.EconomyState.get(officer.getServer())
                                .ensureAccount(net.evarius.terranexus.economy.EconomyState.playerAccount(citizen.getUuid()));
                        state.put(identity.withField("geschlecht", values.get(6)).withField("adresse", values.get(7)));
                        RoleplayNames.apply(citizen);
                        officer.sendMessage(Text.literal("Bürgerakte " + identity.citizenNumber()
                                + " wurde angelegt und wartet auf die Einreisefreigabe.").formatted(Formatting.GREEN), false);
                        ImmigrationScreen.open(officer);
                    }
                }), Text.literal("Bürgerakte: " + label).formatted(Formatting.DARK_AQUA)));
    }

    private static void openGenderSelection(ServerPlayerEntity officer,ServerPlayerEntity citizen,List<String> values){List<SelectionMenuScreen.Option> options=ConfigManager.immigration().genderOptions.stream().map(value->new SelectionMenuScreen.Option(value,value,"Amtliche Auswahl",Items.NAME_TAG)).toList();SelectionMenuScreen.open(officer,"Geschlecht auswählen",options,value->{if(!AuthorityState.mayManageIdentity(officer)){officer.sendMessage(Text.literal("Deine Verwaltungsberechtigung ist nicht mehr gültig.").formatted(Formatting.RED),false);return;}values.add(value);openStep(officer,citizen,values,7);},()->{List<String> previous=new ArrayList<>(values);if(!previous.isEmpty())previous.remove(previous.size()-1);openStep(officer,citizen,previous,5);});}

    private static boolean isValidBirthDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value,
                    DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT));
            return !date.isAfter(LocalDate.now());
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
