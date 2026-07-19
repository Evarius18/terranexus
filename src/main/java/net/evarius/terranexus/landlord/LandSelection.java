package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record LandSelection(String dimension, List<String> points) {
    public static final Codec<LandSelection> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(LandSelection::dimension),
            Codec.STRING.listOf().optionalFieldOf("points",List.of()).forGetter(LandSelection::points)
    ).apply(instance,LandSelection::new));
}
