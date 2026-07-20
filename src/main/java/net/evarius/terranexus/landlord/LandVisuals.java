package net.evarius.terranexus.landlord;

import net.minecraft.particle.ParticleTypes;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public final class LandVisuals {
    private LandVisuals() {}
    public static void preview(ServerPlayerEntity player,List<BlockPos> points){if(points==null||points.isEmpty())return;for(BlockPos point:points)player.getWorld().spawnParticles(player,ParticleTypes.END_ROD,true,false,point.getX()+.5,point.getY()+1.15,point.getZ()+.5,2,.08,.18,.08,0);var config=ConfigManager.performance();int edges=points.size()>2?points.size():points.size()-1,budget=config.maximumParticleBudgetPerPlayer;for(int i=0;i<edges&&budget>0;i++){BlockPos a=points.get(i),b=points.get((i+1)%points.size());double dx=b.getX()-a.getX(),dy=b.getY()-a.getY(),dz=b.getZ()-a.getZ();int samples=Math.min(Math.min(config.maximumParticleSamplesPerEdge,budget),Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*2)));budget-=samples;for(int sample=0;sample<=samples;sample++){double ratio=sample/(double)samples;player.getWorld().spawnParticles(player,ParticleTypes.HAPPY_VILLAGER,true,false,a.getX()+.5+dx*ratio,a.getY()+1.05+dy*ratio,a.getZ()+.5+dz*ratio,1,0,0,0,0);}}}
}
