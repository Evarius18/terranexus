package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;import com.mojang.serialization.codecs.RecordCodecBuilder;import net.minecraft.datafixer.DataFixTypes;import net.minecraft.server.MinecraftServer;import net.minecraft.world.PersistentState;import net.minecraft.world.PersistentStateType;import java.util.HashMap;import java.util.Map;import java.util.UUID;

public final class LandSurveyTaskState extends PersistentState{
    private static final Codec<LandSurveyTaskState> CODEC=RecordCodecBuilder.create(i->i.group(Codec.unboundedMap(Codec.STRING,LandSurveyTask.CODEC).optionalFieldOf("tasks",Map.of()).forGetter(s->s.tasks)).apply(i,LandSurveyTaskState::new));
    private static final PersistentStateType<LandSurveyTaskState> TYPE=new PersistentStateType<>("terranexus_land_survey_tasks",LandSurveyTaskState::new,CODEC,DataFixTypes.LEVEL);private final Map<String,LandSurveyTask> tasks;
    public LandSurveyTaskState(){this(new HashMap<>());}private LandSurveyTaskState(Map<String,LandSurveyTask> tasks){this.tasks=new HashMap<>(tasks);}public static LandSurveyTaskState get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}public LandSurveyTask get(UUID player){return tasks.get(player.toString());}public void put(UUID player,LandSurveyTask task){tasks.put(player.toString(),task);markDirty();}public void remove(UUID player){if(tasks.remove(player.toString())!=null)markDirty();}
}
