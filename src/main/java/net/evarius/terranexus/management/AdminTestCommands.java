package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.block.ModBlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminTestCommands {
    private AdminTestCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("tnadmin").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("test-access")
                                .executes(context -> enable(context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> enable(EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("remove-test-access")
                                .executes(context -> disable(context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> disable(EntityArgumentType.getPlayer(context, "player")))))
        ));
    }

    private static int enable(ServerPlayerEntity player) {
        AuthorityState authority = AuthorityState.get(player.getServer());
        authority.grant(player.getUuid(), AuthorityState.CIVIL_REGISTRAR);
        authority.grant(player.getUuid(), AuthorityState.IMMIGRATION_OFFICER);
        authority.grant(player.getUuid(), AuthorityState.SUPPORTER);
        authority.grant(player.getUuid(), AuthorityState.LAND_REGISTRAR);
        player.giveItemStack(new ItemStack(ModItems.MANAGEMENT_TABLET));
        player.giveItemStack(new ItemStack(ModBlocks.MANAGEMENT_COMPUTER));
        player.sendMessage(Text.literal("TerraNexus-Testzugriff aktiviert. Rechtsklicke das Verwaltungsgerät, um die GUI zu öffnen.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int disable(ServerPlayerEntity player) {
        AuthorityState authority = AuthorityState.get(player.getServer());
        authority.revoke(player.getUuid(), AuthorityState.CIVIL_REGISTRAR);
        authority.revoke(player.getUuid(), AuthorityState.IMMIGRATION_OFFICER);
        authority.revoke(player.getUuid(), AuthorityState.SUPPORTER);
        authority.revoke(player.getUuid(), AuthorityState.LAND_REGISTRAR);
        player.sendMessage(Text.literal("TerraNexus-Testzugriff wurde entfernt.").formatted(Formatting.YELLOW), false);
        return 1;
    }
}
