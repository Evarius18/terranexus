package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LandSurveyTask(String propertyId, String mode, String name, String type) {
    public static final String RESHAPE="reshape", DETACH="detach", SUBAREA="subarea";
    public static final Codec<LandSurveyTask> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(LandSurveyTask::propertyId),Codec.STRING.fieldOf("mode").forGetter(LandSurveyTask::mode),
            Codec.STRING.optionalFieldOf("name","").forGetter(LandSurveyTask::name),Codec.STRING.optionalFieldOf("type","").forGetter(LandSurveyTask::type)
    ).apply(instance,LandSurveyTask::new));
}
