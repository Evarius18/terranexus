package net.evarius.terranexus.election;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.IdentityState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ElectionCommands {
    private ElectionCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = literal("election").requires(ElectionCommands::mayManage);
            var createTitle = argument("title", StringArgumentType.greedyString()).executes(context -> create(
                    context.getSource(), StringArgumentType.getString(context, "id"),
                    StringArgumentType.getString(context, "type"), StringArgumentType.getString(context, "area"),
                    StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "title")));
            var createRole = argument("role", StringArgumentType.word()); createRole.then(createTitle);
            var createArea = argument("area", StringArgumentType.word()); createArea.then(createRole);
            var createType = argument("type", StringArgumentType.word()); createType.then(createArea);
            var createId = argument("id", StringArgumentType.word()); createId.then(createType);
            root.then(literal("create").then(createId));
            root.then(literal("option").then(argument("id", StringArgumentType.word())
                    .then(argument("label", StringArgumentType.greedyString()).executes(context -> option(
                            context.getSource(), StringArgumentType.getString(context, "id"),
                            StringArgumentType.getString(context, "label"), "")))));
            root.then(literal("candidate").then(argument("id", StringArgumentType.word())
                    .then(argument("player", EntityArgumentType.player()).executes(context -> candidate(
                            context.getSource(), StringArgumentType.getString(context, "id"),
                            EntityArgumentType.getPlayer(context, "player"))))));
            var open = literal("open").then(argument("id", StringArgumentType.word())
                    .executes(context -> open(context.getSource(), StringArgumentType.getString(context, "id"),
                            ConfigManager.elections().defaultDurationMinutes))
                    .then(argument("minutes", IntegerArgumentType.integer(5, 525600)).executes(context -> open(
                            context.getSource(), StringArgumentType.getString(context, "id"),
                            IntegerArgumentType.getInteger(context, "minutes")))));
            root.then(open);
            root.then(literal("close").then(argument("id", StringArgumentType.word()).executes(context ->
                    close(context.getSource(), StringArgumentType.getString(context, "id")))));
            dispatcher.register(root);
        });
    }

    private static int create(ServerCommandSource source, String id, String type, String area, String role, String title) {
        if (role.equalsIgnoreCase("none")) role = "";
        if (area.equalsIgnoreCase("global")) area = "";
        Election created = ElectionState.get(source.getServer()).create(actor(source), id,
                type, title, area, role);
        if (created == null) { source.sendError(Text.literal("Wahl konnte nicht angelegt werden.")); return 0; }
        source.sendFeedback(() -> Text.literal("Wahl " + created.id() + " angelegt."), true);
        return 1;
    }

    private static int candidate(ServerCommandSource source, String id, ServerPlayerEntity candidate) {
        var identity = IdentityState.get(source.getServer()).get(candidate.getUuid());
        String label = identity == null ? candidate.getName().getString() : identity.firstName() + " " + identity.lastName();
        return option(source, id, label, candidate.getUuidAsString());
    }

    private static int option(ServerCommandSource source, String id, String label, String candidate) {
        boolean success = ElectionState.get(source.getServer()).addOption(actor(source), id, label, candidate);
        if (!success) source.sendError(Text.literal("Option konnte nicht ergänzt werden."));
        else source.sendFeedback(() -> Text.literal("Option ergänzt."), true);
        return success ? 1 : 0;
    }

    private static int open(ServerCommandSource source, String id, int minutes) {
        boolean success = ElectionState.get(source.getServer()).open(actor(source), id, minutes);
        if (!success) source.sendError(Text.literal("Wahl benötigt mindestens zwei Optionen und muss im Entwurf sein."));
        else source.sendFeedback(() -> Text.literal("Wahl geöffnet."), true);
        return success ? 1 : 0;
    }

    private static int close(ServerCommandSource source, String id) {
        ElectionState.CloseResult result = ElectionState.get(source.getServer()).close(actor(source), id);
        if (!result.success()) { source.sendError(Text.literal(result.message())); return 0; }
        source.sendFeedback(() -> Text.literal(result.message() + " Ergebnis: " + result.winner()), true);
        return 1;
    }

    private static boolean mayManage(ServerCommandSource source) {
        return source.hasPermissionLevel(2) || source.getEntity() instanceof ServerPlayerEntity player
                && AuthorityState.mayManageLandHierarchy(player);
    }
    private static java.util.UUID actor(ServerCommandSource source){return source.getEntity()==null?new java.util.UUID(0,0):source.getEntity().getUuid();}
}
