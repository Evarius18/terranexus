package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeamModeState extends PersistentState {
    private static final Codec<TeamModeState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,TeamModeSnapshot.CODEC).optionalFieldOf("active",Map.of()).forGetter(s->s.active),
            TeamModeAuditEntry.CODEC.listOf().optionalFieldOf("audit",List.of()).forGetter(s->s.audit)
    ).apply(i,TeamModeState::new));
    private static final PersistentStateType<TeamModeState> TYPE=new PersistentStateType<>("terranexus_team_modes",TeamModeState::new,CODEC,DataFixTypes.LEVEL);
    private final Map<String,TeamModeSnapshot> active;private final List<TeamModeAuditEntry> audit;
    public TeamModeState(){this(new HashMap<>(),new ArrayList<>());}private TeamModeState(Map<String,TeamModeSnapshot> active,List<TeamModeAuditEntry> audit){this.active=new HashMap<>(active);this.audit=new ArrayList<>(audit);}
    public static TeamModeState get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    public boolean isActive(UUID player,TeamModeType mode){TeamModeSnapshot snapshot=active.get(player.toString());return snapshot!=null&&snapshot.mode().equals(mode.id());}
    public TeamModeSnapshot active(UUID player){return active.get(player.toString());}
    public synchronized Result activate(ServerPlayerEntity player,TeamModeType mode,String reason){
        if(mode==null)return new Result(false,"Unbekannter Teammodus.");if(active.containsKey(player.getUuidAsString()))return new Result(false,"Es ist bereits ein Teammodus aktiv.");
        if(!AuthorityState.get(player.getServer()).has(player.getUuid(),mode.requiredRole())&&!AuthorityState.isAdministrator(player))return new Result(false,"Die erforderliche Teamrolle fehlt.");
        List<ItemStack> items=new ArrayList<>();for(int slot=0;slot<player.getInventory().size();slot++)items.add(player.getInventory().getStack(slot).copy());
        List<StoredStatusEffect> effects=player.getStatusEffects().stream().map(effect->new StoredStatusEffect(
                Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString(),effect.getDuration(),effect.getAmplifier(),effect.isAmbient(),effect.shouldShowParticles(),effect.shouldShowIcon())).toList();
        long now=System.currentTimeMillis();TeamModeSnapshot snapshot=new TeamModeSnapshot(mode.id(),mode.requiredRole(),now,List.copyOf(items),player.getHealth(),player.getHungerManager().getFoodLevel(),player.getHungerManager().getSaturationLevel(),player.experienceLevel,player.totalExperience,player.experienceProgress,effects);
        active.put(player.getUuidAsString(),snapshot);for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,ItemStack.EMPTY);player.clearStatusEffects();player.getInventory().markDirty();audit(player,snapshot.role(),"rp",mode.id(),0,reason);markDirty();return new Result(true,"Teammodus "+mode.id()+" aktiviert.");
    }
    public synchronized Result deactivate(ServerPlayerEntity player,String reason){
        TeamModeSnapshot snapshot=active.get(player.getUuidAsString());if(snapshot==null)return new Result(false,"Es ist kein Teammodus aktiv.");
        for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,ItemStack.EMPTY);for(int slot=0;slot<Math.min(player.getInventory().size(),snapshot.inventory().size());slot++)player.getInventory().setStack(slot,snapshot.inventory().get(slot).copy());
        player.setHealth(Math.min(player.getMaxHealth(),Math.max(0.5F,snapshot.health())));player.getHungerManager().setFoodLevel(snapshot.food());player.getHungerManager().setSaturationLevel(snapshot.saturation());player.experienceLevel=snapshot.experienceLevel();player.totalExperience=snapshot.totalExperience();player.experienceProgress=snapshot.experienceProgress();player.clearStatusEffects();
        for(StoredStatusEffect effect:snapshot.effects())Registries.STATUS_EFFECT.getEntry(Identifier.of(effect.id())).ifPresent(entry->player.addStatusEffect(new StatusEffectInstance(entry,effect.duration(),effect.amplifier(),effect.ambient(),effect.particles(),effect.icon())));
        player.getInventory().markDirty();active.remove(player.getUuidAsString());long duration=Math.max(0,System.currentTimeMillis()-snapshot.activatedAt());audit(player,snapshot.role(),snapshot.mode(),"rp",duration,reason);markDirty();return new Result(true,"Teammodus beendet; RP-Zustand wiederhergestellt.");
    }
    public List<TeamModeAuditEntry> recent(){List<TeamModeAuditEntry> copy=new ArrayList<>(audit);java.util.Collections.reverse(copy);return List.copyOf(copy);}
    private void audit(ServerPlayerEntity player,String role,String oldMode,String newMode,long duration,String reason){audit.add(new TeamModeAuditEntry(System.currentTimeMillis(),player.getUuidAsString(),role,oldMode,newMode,duration,reason==null?"":reason.trim()));int max=ConfigManager.logging().maximumLandAuditEntries;if(audit.size()>max)audit.subList(0,audit.size()-max).clear();}
    public record Result(boolean success,String message){}
}
