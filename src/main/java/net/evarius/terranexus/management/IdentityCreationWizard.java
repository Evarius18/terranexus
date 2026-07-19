package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class IdentityCreationWizard {
    private static final List<String> LABELS = List.of(
            "Vorname", "Nachname", "Geburtsdatum (JJJJ-MM-TT)", "Geburtsort",
            "Geburtsland", "Nationalität", "Geschlecht", "Meldeadresse");

    private IdentityCreationWizard() {}

    public static void start(ServerPlayerEntity officer, ServerPlayerEntity citizen) {
        IdentityState state = IdentityState.get(officer.getServer());
        if (state.get(citizen.getUuid()) != null) {
            officer.sendMessage(Text.literal("Für diese Person existiert bereits eine Bürgerakte.").formatted(Formatting.RED), false);
            return;
        }
        openStep(officer, citizen, new ArrayList<>(), 0);
    }

    private static void openStep(ServerPlayerEntity officer, ServerPlayerEntity citizen, List<String> values, int step) {
        String label = LABELS.get(step);
        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
                    values.add(value);
                    if (step + 1 < LABELS.size()) {
                        openStep(officer, citizen, values, step + 1);
                    } else {
                        IdentityState state = IdentityState.get(officer.getServer());
                        CitizenIdentity identity = state.create(citizen.getUuid(), values.get(0), values.get(1),
                                values.get(2), values.get(3), values.get(4), values.get(5));
                        state.put(identity.withField("geschlecht", values.get(6)).withField("adresse", values.get(7)));
                        officer.sendMessage(Text.literal("Bürgerakte " + identity.citizenNumber()
                                + " wurde angelegt und wartet auf die Einreisefreigabe.").formatted(Formatting.GREEN), false);
                        ImmigrationScreen.open(officer);
                    }
                }), Text.literal("Bürgerakte: " + label).formatted(Formatting.DARK_AQUA)));
    }
}
