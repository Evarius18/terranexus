package net.evarius.terranexus.identity;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TeamModeCommands {
    private TeamModeCommands(){}
    public static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            var off=literal("off").executes(context->deactivate(context.getSource().getPlayerOrThrow(),""));
            off.then(argument("reason",StringArgumentType.greedyString()).executes(context->
                    deactivate(context.getSource().getPlayerOrThrow(),StringArgumentType.getString(context,"reason"))));
            var root=literal("teammode");
            root.then(mode("support",TeamModeType.SUPPORT));
            root.then(mode("builder",TeamModeType.BUILDER));
            root.then(mode("moderation",TeamModeType.MODERATION));
            root.then(off);
            dispatcher.register(root);
        });
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> mode(String name,TeamModeType mode){
        return literal(name).executes(context->activate(context.getSource().getPlayerOrThrow(),mode,""))
                .then(argument("reason",StringArgumentType.greedyString()).executes(context->
                        activate(context.getSource().getPlayerOrThrow(),mode,StringArgumentType.getString(context,"reason"))));
    }
    private static int activate(ServerPlayerEntity player,TeamModeType mode,String reason){return result(player,TeamModeState.get(player.getServer()).activate(player,mode,reason));}
    private static int deactivate(ServerPlayerEntity player,String reason){return result(player,TeamModeState.get(player.getServer()).deactivate(player,reason));}
    private static int result(ServerPlayerEntity player,TeamModeState.Result result){player.sendMessage(Text.literal(result.message()).formatted(result.success()?Formatting.GREEN:Formatting.RED),false);return result.success()?1:0;}
}
