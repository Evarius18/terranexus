package net.evarius.terranexus.identity;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class IdentityCommands {
    private IdentityCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("identity")
                        .executes(context -> show(context.getSource(), context.getSource().getPlayerOrThrow()))
                        .then(literal("me").executes(context -> show(context.getSource(), context.getSource().getPlayerOrThrow())))
                        .then(literal("show").requires(AuthorityState::mayProcessImmigration)
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> show(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("set").requires(AuthorityState::mayProcessImmigration)
                                .then(argument("player", EntityArgumentType.player())
                                .then(argument("field", StringArgumentType.word())
                                .then(argument("value", StringArgumentType.greedyString())
                                        .executes(context -> set(context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "field"),
                                                StringArgumentType.getString(context, "value")))))))
                        .then(literal("approve").requires(AuthorityState::mayProcessImmigration)
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> approve(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("issue").requires(AuthorityState::mayProcessImmigration)
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> issue(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
        ));
    }

    private static int set(ServerCommandSource source, ServerPlayerEntity player, String field, String value) {
        IdentityState state = IdentityState.get(source.getServer());
        CitizenIdentity identity = state.get(player.getUuid());
        if (identity == null) {
            source.sendError(Text.literal("Für diesen Spieler existiert keine Identität."));
            return 0;
        }
        CitizenIdentity changed = identity.withField(field, value);
        if (changed == null) {
            source.sendError(Text.literal("Unbekanntes Feld. Erlaubt: vorname, nachname, geburtsdatum, geburtsort, geburtsland, nationalitaet, geschlecht, adresse"));
            return 0;
        }
        state.put(changed);
        RoleplayNames.apply(player);
        source.sendFeedback(() -> Text.literal("Bürgerdaten wurden aktualisiert."), true);
        return 1;
    }

    private static int show(ServerCommandSource source, ServerPlayerEntity player) {
        CitizenIdentity identity = IdentityState.get(source.getServer()).get(player.getUuid());
        if (identity == null) {
            source.sendError(Text.literal("Für diesen Spieler existiert keine Identität."));
            return 0;
        }
        if (source.getEntity() instanceof ServerPlayerEntity viewer) {
            IdentityScreen.open(viewer, identity);
        } else {
            source.sendFeedback(() -> Text.literal(identity.citizenNumber() + ": " + identity.firstName() + " " + identity.lastName()
                    + ", geboren " + identity.birthDate() + " in " + identity.birthPlace() + ", " + identity.birthCountry()), false);
        }
        return 1;
    }

    private static int issue(ServerCommandSource source, ServerPlayerEntity player) {
        IdentityState state = IdentityState.get(source.getServer());
        CitizenIdentity identity = state.get(player.getUuid());
        if (identity == null) {
            source.sendError(Text.literal("Für diesen Spieler existiert keine Identität."));
            return 0;
        }
        if (!state.isApproved(player.getUuid())) {
            source.sendError(Text.literal("Die Einreise wurde noch nicht durch eine berechtigte Stelle freigegeben."));
            return 0;
        }
        player.giveItemStack(CitizenIdCardItem.createCard(ModItems.CITIZEN_ID_CARD, identity));
        source.sendFeedback(() -> Text.literal("Personalausweis wurde an " + identity.firstName() + " " + identity.lastName() + " ausgegeben."), true);
        return 1;
    }

    private static int approve(ServerCommandSource source, ServerPlayerEntity player) {
        IdentityState state = IdentityState.get(source.getServer());
        CitizenIdentity identity = state.get(player.getUuid());
        if (identity == null) {
            source.sendError(Text.literal("Für diesen Spieler existiert keine Identität."));
            return 0;
        }
        ServerPlayerEntity officer;
        try {
            officer = source.getPlayerOrThrow();
        } catch (Exception exception) {
            source.sendError(Text.literal("Die Freigabe muss durch einen angemeldeten RP-Bediensteten erfolgen."));
            return 0;
        }
        state.approve(player.getUuid(), officer.getUuid());
        CitizenIdentity officerIdentity = state.get(officer.getUuid());
        String officerName = officerIdentity == null ? "Verwaltungsbediensteter" : officerIdentity.firstName() + " " + officerIdentity.lastName();
        source.sendFeedback(() -> Text.literal("Einreise von " + identity.firstName() + " " + identity.lastName()
                + " wurde durch " + officerName + " freigegeben."), true);
        return 1;
    }
}
