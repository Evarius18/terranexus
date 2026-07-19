package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record Institution(String id, String name, String type, String ownerUuid, Map<String, List<String>> members) {
    public static final Codec<Institution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Institution::id),
            Codec.STRING.fieldOf("name").forGetter(Institution::name),
            Codec.STRING.fieldOf("type").forGetter(Institution::type),
            Codec.STRING.fieldOf("owner_uuid").forGetter(Institution::ownerUuid),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).fieldOf("members").forGetter(Institution::members)
    ).apply(instance, Institution::new));
}
