package net.evarius.terranexus.landlord;

import net.minecraft.server.network.ServerPlayerEntity;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public final class LandMarkerService {
    private static final Map<UUID,Marker> ACTIVE=new HashMap<>();
    private LandMarkerService() {}
    public static boolean toggle(ServerPlayerEntity player,LandProperty property){Marker current=ACTIVE.get(player.getUuid());if(current!=null&&current.propertyId.equals(property.id())){ACTIVE.remove(player.getUuid());player.sendMessage(Text.literal("Grundstücksmarkierung deaktiviert.").formatted(Formatting.YELLOW),false);return false;}if(!property.isOwnedBy(player.getUuid()))return false;int seconds=ConfigManager.claims().markerDurationSeconds;ACTIVE.put(player.getUuid(),new Marker(property.id(),System.currentTimeMillis()+seconds*1000L));player.sendMessage(Text.literal("Private Grundstücksmarkierung für "+seconds+" Sekunden aktiviert.").formatted(Formatting.GREEN),false);return true;}
    public static boolean active(UUID player,String propertyId){Marker marker=ACTIVE.get(player);return marker!=null&&marker.propertyId.equals(propertyId)&&marker.expiresAt>System.currentTimeMillis();}
    public static long remainingSeconds(UUID player,String propertyId){Marker marker=ACTIVE.get(player);return marker==null||!marker.propertyId.equals(propertyId)?0:Math.max(0,(marker.expiresAt-System.currentTimeMillis())/1000);}
    public static void tick(ServerPlayerEntity player){Marker marker=ACTIVE.get(player.getUuid());if(marker==null)return;if(marker.expiresAt<=System.currentTimeMillis()){ACTIVE.remove(player.getUuid());player.sendMessage(Text.literal("Grundstücksmarkierung automatisch deaktiviert.").formatted(Formatting.GRAY),true);return;}LandProperty property=LandlordState.get(player.getServer()).get(marker.propertyId);if(property==null||!property.isOwnedBy(player.getUuid())){ACTIVE.remove(player.getUuid());return;}String dimension=player.getWorld().getRegistryKey().getValue().toString();if(!property.dimension().equals(dimension))return;LandVisuals.preview(player,border(property,player.getBlockY()));}
    public static void retainOnline(Collection<ServerPlayerEntity> players){Set<UUID> online=new HashSet<>();for(ServerPlayerEntity player:players)online.add(player.getUuid());ACTIVE.keySet().retainAll(online);}
    public static void retainOnlineIds(Set<UUID> online){ACTIVE.keySet().retainAll(online);}
    private static List<BlockPos> border(LandProperty property,int y){if(property.regionType().equals("polygon")){List<BlockPos> points=new ArrayList<>();for(String value:property.polygonPoints()){String[] pair=value.split(",");if(pair.length!=2)continue;try{points.add(new BlockPos(Integer.parseInt(pair[0]),y,Integer.parseInt(pair[1])));}catch(NumberFormatException ignored){}}return points;}return List.of(new BlockPos(property.minX(),y,property.minZ()),new BlockPos(property.maxX(),y,property.minZ()),new BlockPos(property.maxX(),y,property.maxZ()),new BlockPos(property.minX(),y,property.maxZ()));}
    private record Marker(String propertyId,long expiresAt){}
}
