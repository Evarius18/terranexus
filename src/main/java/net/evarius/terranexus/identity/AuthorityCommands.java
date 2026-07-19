package net.evarius.terranexus.identity;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AuthorityCommands {
    private AuthorityCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("authority").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("grant").then(argument("player", EntityArgumentType.player())
                                .then(argument("role", StringArgumentType.word()).executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    String role = StringArgumentType.getString(context, "role").toLowerCase();
                                    if (!AuthorityState.isKnownRole(role)) {
                                        context.getSource().sendError(Text.literal("Rollen: civil_registrar, immigration_officer, supporter, land_surveyor, land_clerk, land_administrator (land_registrar bleibt kompatibel)"));
                                        return 0;
                                    }
                                    AuthorityState.get(context.getSource().getServer()).grant(player.getUuid(), role);
                                    CitizenIdentity identity = IdentityState.get(context.getSource().getServer()).get(player.getUuid());
                                    String name = identity == null ? "Unregistrierter Bürger" : identity.firstName() + " " + identity.lastName();
                                    String roleLabel = AuthorityState.roleLabel(role);
                                    String scope=role.startsWith("land_")?"für Aufgaben des Bauamts":"für Einreisen und Bürgerakten";
                                    context.getSource().sendFeedback(() -> Text.literal("Verwaltungsberechtigung erteilt: "
                                            + name + " ist ab sofort als „" + roleLabel + "“ "+scope+" berechtigt."), true);
                                    player.sendMessage(Text.literal("Du wurdest als „" + roleLabel
                                            + "“ für die TerraNexus-Verwaltung autorisiert."), false);
                                    return 1;
                                }))))
                        .then(literal("revoke").then(argument("player", EntityArgumentType.player())
                                .then(argument("role", StringArgumentType.word()).executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    String role = StringArgumentType.getString(context, "role").toLowerCase();
                                    AuthorityState.get(context.getSource().getServer()).revoke(player.getUuid(), role);
                                    String roleLabel = AuthorityState.roleLabel(role);
                                    context.getSource().sendFeedback(() -> Text.literal("Verwaltungsberechtigung entzogen: „"
                                            + roleLabel + "“."), true);
                                    player.sendMessage(Text.literal("Deine Verwaltungsberechtigung „" + roleLabel
                                            + "“ wurde aufgehoben."), false);
                                    return 1;
                                }))))
        ));
    }
}
