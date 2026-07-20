package net.evarius.terranexus.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class EconomyCommands {
    private EconomyCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("economy").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("deposit").then(argument("player", EntityArgumentType.player())
                                .then(argument("cents", LongArgumentType.longArg(1)).executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    long cents = LongArgumentType.getLong(context, "cents");
                                    EconomyState.get(context.getSource().getServer()).adjust(EconomyState.playerAccount(player.getUuid()), cents,
                                            "TNAdmin-Einzahlung", context.getSource().getEntity() == null ? "CONSOLE" : context.getSource().getEntity().getUuidAsString(), "", "ADMIN_DEPOSIT");
                                    CitizenIdentity identity = IdentityState.get(context.getSource().getServer()).get(player.getUuid());
                                    String name = identity == null ? "Unregistrierter Bürger" : identity.firstName() + " " + identity.lastName();
                                    context.getSource().sendFeedback(() -> Text.literal(EconomyState.format(cents) + " wurden dem Konto von " + name + " gutgeschrieben."), true);
                                    return 1;
                                }))))
        ));
    }
}
