package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.*;

public class LandSelectionState extends PersistentState {
    private static final Codec<LandSelectionState> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.unboundedMap(Codec.STRING,LandSelection.CODEC).optionalFieldOf("selections",Map.of()).forGetter(state->state.selections)
    ).apply(instance,LandSelectionState::new));
    private static final PersistentStateType<LandSelectionState> TYPE=new PersistentStateType<>("terranexus_land_selections",LandSelectionState::new,CODEC,DataFixTypes.LEVEL);
    private final Map<String,LandSelection> selections;
    public LandSelectionState(){this(new HashMap<>());}private LandSelectionState(Map<String,LandSelection> selections){this.selections=new HashMap<>(selections);}
    public static LandSelectionState get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    public LandSelection get(UUID player){return selections.get(player.toString());}
    public void start(UUID player,String dimension){selections.put(player.toString(),new LandSelection(dimension,List.of()));markDirty();}
    public boolean add(UUID player,String dimension,BlockPos pos){LandSelection old=get(player);if(old==null||!old.dimension().equals(dimension))return false;List<String> points=new ArrayList<>(old.points());String encoded=encode(pos);if(points.isEmpty()||!points.get(points.size()-1).equals(encoded))points.add(encoded);selections.put(player.toString(),new LandSelection(dimension,points));markDirty();return true;}
    public void undo(UUID player){LandSelection old=get(player);if(old==null||old.points().isEmpty())return;List<String> points=new ArrayList<>(old.points());points.remove(points.size()-1);selections.put(player.toString(),new LandSelection(old.dimension(),points));markDirty();}
    public void clear(UUID player){if(selections.remove(player.toString())!=null)markDirty();}
    public List<BlockPos> points(UUID player){
        LandSelection selection=get(player);
        if(selection==null)return List.of();
        List<BlockPos> result=new ArrayList<>();
        for(String value:selection.points()){
            String[] part=value.split(",");
            if(part.length!=3)continue;
            try{result.add(new BlockPos(Integer.parseInt(part[0]),Integer.parseInt(part[1]),Integer.parseInt(part[2])));}
            catch(NumberFormatException ignored){}
        }
        return result;
    }
    private static String encode(BlockPos pos){return pos.getX()+","+pos.getY()+","+pos.getZ();}
}
