package net.evarius.terranexus.landlord;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandlordScheduler {
    private static int ticks;
    private static final Map<UUID,String> LAST_PROPERTY=new HashMap<>();
    private LandlordScheduler() {}
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{ticks++;if(ticks%20==0)for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){LandSelectionState selections=LandSelectionState.get(server);LandSelection selection=selections.get(player.getUuid());String dimension=player.getWorld().getRegistryKey().getValue().toString();if(selection!=null&&selection.dimension().equals(dimension))LandVisuals.preview(player,selections.points(player.getUuid()));LandMarkerService.tick(player);notifyProperty(player,dimension);}if(ticks>=1200){ticks=0;var players=server.getPlayerManager().getPlayerList();Set<UUID> online=new HashSet<>();for(ServerPlayerEntity player:players)online.add(player.getUuid());LAST_PROPERTY.keySet().retainAll(online);LandMarkerService.retainOnline(players);LandManagementState.get(server).processRents(server,System.currentTimeMillis());}});}
    private static void notifyProperty(ServerPlayerEntity player,String dimension){LandProperty property=LandlordState.get(player.getServer()).at(dimension,player.getBlockPos());String current=property==null?"":property.id(),previous=LAST_PROPERTY.getOrDefault(player.getUuid(),"");if(current.equals(previous))return;LAST_PROPERTY.put(player.getUuid(),current);if(property!=null)player.sendMessage(Text.literal("Grundstück: ").formatted(Formatting.GOLD).append(Text.literal(property.name()).formatted(Formatting.WHITE)),true);else if(!previous.isBlank())player.sendMessage(Text.literal("Grundstück verlassen").formatted(Formatting.GRAY),true);}
}
