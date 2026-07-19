package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.*;

public class LandlordState extends PersistentState {
    private static final Codec<LandlordState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,LandProperty.CODEC).optionalFieldOf("properties",Map.of()).forGetter(s->s.properties)
    ).apply(i,LandlordState::new));
    private static final PersistentStateType<LandlordState> TYPE=new PersistentStateType<>("terranexus_properties",LandlordState::new,CODEC,DataFixTypes.LEVEL);
    private final Map<String,LandProperty> properties;
    public LandlordState(){this(new HashMap<>());} private LandlordState(Map<String,LandProperty> p){properties=new HashMap<>(p);}
    public static LandlordState get(MinecraftServer s){return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    public List<LandProperty> owned(UUID u){return properties.values().stream().filter(p->p.ownerType().equals("player")&&p.ownerId().equals(u.toString())).toList();}
    public LandProperty at(String dim,BlockPos p){return properties.values().stream().filter(x->x.contains(dim,p.getX(),p.getY(),p.getZ())).findFirst().orElse(null);}
    public boolean add(LandProperty p){
        for(LandProperty old:properties.values()) if(old.dimension().equals(p.dimension()) && boxesOverlap(old,p)) return false;
        properties.put(p.id(),p);markDirty();return true;
    }
    private boolean boxesOverlap(LandProperty a,LandProperty b){
        if(!(a.minX()<=b.maxX()&&a.maxX()>=b.minX()&&a.minY()<=b.maxY()&&a.maxY()>=b.minY()&&a.minZ()<=b.maxZ()&&a.maxZ()>=b.minZ()))return false;
        if(!a.regionType().equals("polygon")&&!b.regionType().equals("polygon"))return true;
        int minX=Math.max(a.minX(),b.minX()),maxX=Math.min(a.maxX(),b.maxX()),minZ=Math.max(a.minZ(),b.minZ()),maxZ=Math.min(a.maxZ(),b.maxZ());
        long cells=(long)(maxX-minX+1)*(maxZ-minZ+1);if(cells>1_000_000L)return true;
        int y=Math.max(a.minY(),b.minY());
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(a.contains(a.dimension(),x,y,z)&&b.contains(b.dimension(),x,y,z))return true;
        return false;
    }
    public static LandProperty chunk(String name,UUID owner,String dim,int cx,int cz){return new LandProperty(UUID.randomUUID().toString(),name,"player",owner.toString(),dim,"chunk",cx*16,-64,cz*16,cx*16+15,319,cz*16+15,List.of());}
    public static LandProperty cuboid(String name,UUID owner,String dim,BlockPos a,BlockPos b){return new LandProperty(UUID.randomUUID().toString(),name,"player",owner.toString(),dim,"cuboid",Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()),List.of());}
    public static LandProperty polygon(String name,UUID owner,String dim,List<BlockPos> points){int minX=points.stream().mapToInt(BlockPos::getX).min().orElse(0),maxX=points.stream().mapToInt(BlockPos::getX).max().orElse(0),minZ=points.stream().mapToInt(BlockPos::getZ).min().orElse(0),maxZ=points.stream().mapToInt(BlockPos::getZ).max().orElse(0);return new LandProperty(UUID.randomUUID().toString(),name,"player",owner.toString(),dim,"polygon",minX,-64,minZ,maxX,319,maxZ,points.stream().map(p->p.getX()+","+p.getZ()).toList());}
}
