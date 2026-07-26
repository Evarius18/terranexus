package net.evarius.terranexus.landlord;

import net.evarius.terranexus.management.LandTransferScreen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class LandTransferCommands {
    private LandTransferCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("uebertragungen").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (LandTransferService.pendingFor(player).isEmpty() && !LandTransferService.mayInitiate(player)) {
                        context.getSource().sendError(Text.literal("Du hast keine offenen Grundstücksübertragungen."));
                        return 0;
                    }
                    if (LandTransferService.mayInitiate(player)) LandTransferScreen.openLandOffice(player);
                    else LandTransferScreen.openFromCommand(player);
                    return 1;
                })));
    }
}
