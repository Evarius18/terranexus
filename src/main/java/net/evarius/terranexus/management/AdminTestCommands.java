package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandResolution;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.identity.RoleplayNames;
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
                        .then(literal("reload-config").executes(context -> {
                            ConfigManager.reload();
                            LandlordState.get(context.getSource().getServer()).refreshRuntimeIndexes();
                            LandManagementState.get(context.getSource().getServer()).refreshConfiguredHierarchy();
                            context.getSource().sendFeedback(() -> Text.literal("TerraNexus-Konfiguration neu geladen: " + ConfigManager.directory()), true);
                            return 1;
                        }))
                        .then(literal("land-info").executes(context -> landInfo(context.getSource().getPlayerOrThrow())))
        ));
    }

    private static int landInfo(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        LandProperty property = LandlordState.get(player.getServer()).at(dimension, player.getBlockPos());
        LandManagementState management = LandManagementState.get(player.getServer());
        LandResolution resolution = management.resolve(property);
        String explicit = property == null ? "Wilderness (virtuell)" : property.name() + " · " + property.id();
        player.sendMessage(Text.literal("Fläche: " + explicit).formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("Zuständigkeit: " + resolution.jurisdiction().name()
                + " · Eigentümer: " + resolution.ownerType() + ':' + resolution.ownerId()
                + " · Nutzung: " + resolution.landUse()).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int enable(ServerPlayerEntity player) {
        IdentityState identities=IdentityState.get(player.getServer());
        if(identities.get(player.getUuid())==null)identities.create(player.getUuid(),"Test",player.getName().getString(),"01.01.2000","Teststadt","Deutschland","Deutsch");
        net.evarius.terranexus.economy.EconomyState.get(player.getServer())
                .ensureAccount(net.evarius.terranexus.economy.EconomyState.playerAccount(player.getUuid()));
        if(!identities.isApproved(player.getUuid()))identities.approve(player.getUuid(),player.getUuid());
        RoleplayNames.apply(player);
        AuthorityState authority = AuthorityState.get(player.getServer());
        authority.grant(player.getUuid(), AuthorityState.CIVIL_REGISTRAR);
        authority.grant(player.getUuid(), AuthorityState.IMMIGRATION_OFFICER);
        authority.grant(player.getUuid(), AuthorityState.SUPPORTER);
        authority.grant(player.getUuid(), AuthorityState.LAND_REGISTRAR);
        authority.grant(player.getUuid(), AuthorityState.LAND_SURVEYOR);
        authority.grant(player.getUuid(), AuthorityState.LAND_CLERK);
        authority.grant(player.getUuid(), AuthorityState.LAND_ADMINISTRATOR);
        authority.grant(player.getUuid(), AuthorityState.TN_ADMIN_TEST);
        player.giveItemStack(new ItemStack(ModItems.MANAGEMENT_TABLET));
        player.giveItemStack(new ItemStack(ModItems.BUILDING_AUTHORITY_TABLET));
        player.giveItemStack(new ItemStack(ModItems.LAND_REGISTRY_EXTRACT));
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
        authority.revoke(player.getUuid(), AuthorityState.LAND_SURVEYOR);
        authority.revoke(player.getUuid(), AuthorityState.LAND_CLERK);
        authority.revoke(player.getUuid(), AuthorityState.LAND_ADMINISTRATOR);
        authority.revoke(player.getUuid(), AuthorityState.TN_ADMIN_TEST);
        player.sendMessage(Text.literal("TerraNexus-Testzugriff wurde entfernt.").formatted(Formatting.YELLOW), false);
        return 1;
    }
}
