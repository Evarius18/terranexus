package net.evarius.terranexus.landlord;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ContainerAccessCommands {
    private ContainerAccessCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("lock").executes(context -> activate(context.getSource(),
                    ContainerAccessService.Action.LOCK, null)));
            dispatcher.register(literal("unlock").executes(context -> activate(context.getSource(),
                    ContainerAccessService.Action.UNLOCK, null)));
            dispatcher.register(literal("berechtigung")
                    .then(literal("erteilen").then(argument("spieler", EntityArgumentType.player())
                            .executes(context -> activate(context.getSource(), ContainerAccessService.Action.GRANT,
                                    EntityArgumentType.getPlayer(context, "spieler")))))
                    .then(literal("entziehen").then(argument("spieler", EntityArgumentType.player())
                            .executes(context -> activate(context.getSource(), ContainerAccessService.Action.REVOKE,
                                    EntityArgumentType.getPlayer(context, "spieler")))))
                    .then(literal("liste").executes(context -> activate(context.getSource(),
                            ContainerAccessService.Action.LIST, null)))
                    .then(literal("abbrechen").executes(context -> cancel(context.getSource()))));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ContainerAccessService.clear(handler.player));
    }

    private static int activate(ServerCommandSource source, ContainerAccessService.Action action,
                                ServerPlayerEntity target) {
        ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); }
        catch (Exception exception) { source.sendError(Text.literal("Dieser Befehl kann nur im Spiel verwendet werden.")); return 0; }
        ContainerAccessService.Result result = ContainerAccessService.activate(player, action, target);
        if (result.success()) source.sendFeedback(() -> Text.literal(result.message()), false);
        else source.sendError(Text.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int cancel(ServerCommandSource source) {
        ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); }
        catch (Exception exception) { source.sendError(Text.literal("Dieser Befehl kann nur im Spiel verwendet werden.")); return 0; }
        ContainerAccessService.Result result = ContainerAccessService.cancel(player);
        if (result.success()) source.sendFeedback(() -> Text.literal(result.message()), false);
        else source.sendError(Text.literal(result.message()));
        return result.success() ? 1 : 0;
    }
}
