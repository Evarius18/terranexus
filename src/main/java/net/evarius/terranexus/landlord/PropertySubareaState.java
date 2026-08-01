package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PropertySubareaState extends PersistentState {
    private static final Codec<PropertySubareaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PropertySubarea.CODEC.listOf().optionalFieldOf("subareas", List.of()).forGetter(state -> List.copyOf(state.values.values()))
    ).apply(instance, PropertySubareaState::new));
    private static final PersistentStateType<PropertySubareaState> TYPE = new PersistentStateType<>(
            "terranexus_property_subareas", PropertySubareaState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, PropertySubarea> values = new HashMap<>();
    public PropertySubareaState() {}
    private PropertySubareaState(List<PropertySubarea> values) { values.forEach(value -> this.values.put(value.id(), value)); }
    public static PropertySubareaState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }
    public List<PropertySubarea> forProperty(String propertyId) { return values.values().stream().filter(value -> value.propertyId().equals(propertyId)).sorted(Comparator.comparing(PropertySubarea::name)).toList(); }
    public PropertySubarea create(LandProperty parent, String name, String type, List<BlockPos> points, UUID actor) {
        String cleanName=name==null?"":name.trim();
        if(parent==null||cleanName.isBlank()||cleanName.length()>ConfigManager.claims().maximumPropertyNameLength
                ||!ConfigManager.claims().subareaTypes.contains(type)||forProperty(parent.id()).size()>=ConfigManager.claims().maximumSubareasPerProperty
                ||LandGeometry.validatePolygon(points)!=null||!LandlordState.selectionInside(parent,points))return null;
        List<String> polygon=LandlordState.encode(points);int minY=points.stream().mapToInt(BlockPos::getY).min().orElse(parent.minY()),maxY=points.stream().mapToInt(BlockPos::getY).max().orElse(parent.maxY());
        for(PropertySubarea existing:forProperty(parent.id()))if(overlaps(existing,polygon,points,minY,maxY))return null;
        PropertySubarea created=new PropertySubarea(UUID.randomUUID().toString(),parent.id(),"",cleanName,type,polygon,
                minY,maxY,System.currentTimeMillis(),actor.toString());
        values.put(created.id(),created);markDirty();return created;
    }
    public PropertySubarea get(String id){return values.get(id);}
    public boolean rename(String id,String name){PropertySubarea old=values.get(id);String clean=name==null?"":name.trim();if(old==null||clean.isBlank()||clean.length()>ConfigManager.claims().maximumPropertyNameLength)return false;values.put(id,new PropertySubarea(old.id(),old.propertyId(),old.buildingId(),clean,old.type(),old.polygon(),old.minY(),old.maxY(),old.createdAt(),old.createdBy()));markDirty();return true;}
    public boolean assignBuilding(String id,String buildingId){PropertySubarea old=values.get(id);String clean=buildingId==null?"":buildingId.trim();if(old==null)return false;values.put(id,new PropertySubarea(old.id(),old.propertyId(),clean,old.name(),old.type(),old.polygon(),old.minY(),old.maxY(),old.createdAt(),old.createdBy()));markDirty();return true;}
    public List<PropertySubarea> forBuilding(String buildingId){return values.values().stream().filter(value->value.buildingId().equals(buildingId)).sorted(Comparator.comparing(PropertySubarea::name)).toList();}
    public boolean remove(String id){if(values.remove(id)==null)return false;markDirty();return true;}
    public void removeForProperty(String propertyId){if(values.values().removeIf(value->value.propertyId().equals(propertyId)))markDirty();}
    public boolean allInside(LandProperty property){for(PropertySubarea value:forProperty(property.id())){List<BlockPos> points=decode(value.polygon(),value.minY());if(!LandlordState.selectionInside(property,points))return false;}return true;}
    private static boolean overlaps(PropertySubarea existing,List<String> candidate,List<BlockPos> points,int minY,int maxY){if(existing.maxY()<minY||existing.minY()>maxY)return false;int minX=points.stream().mapToInt(BlockPos::getX).min().orElse(0),maxX=points.stream().mapToInt(BlockPos::getX).max().orElse(0),minZ=points.stream().mapToInt(BlockPos::getZ).min().orElse(0),maxZ=points.stream().mapToInt(BlockPos::getZ).max().orElse(0);for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(LandGeometry.containsMarkedBlock(candidate,x,z)&&LandGeometry.containsMarkedBlock(existing.polygon(),x,z))return true;return false;}
    private static List<BlockPos> decode(List<String> encoded,int y){List<BlockPos> result=new ArrayList<>();for(String value:encoded){String[] pair=value.split(",");if(pair.length==2)try{result.add(new BlockPos(Integer.parseInt(pair[0]),y,Integer.parseInt(pair[1])));}catch(NumberFormatException ignored){}}return result;}
}
