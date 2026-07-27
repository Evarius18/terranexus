package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResidentialBuilding(String id, String name, String createdBy, long createdAt) {
    public static final Codec<ResidentialBuilding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ResidentialBuilding::id),
            Codec.STRING.fieldOf("name").forGetter(ResidentialBuilding::name),
            Codec.STRING.fieldOf("created_by").forGetter(ResidentialBuilding::createdBy),
            Codec.LONG.fieldOf("created_at").forGetter(ResidentialBuilding::createdAt)
    ).apply(instance, ResidentialBuilding::new));
}
