package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AdministrativeArea(String id, String name, String type, String parentId, String ownerType, String ownerId) {
    public static final Codec<AdministrativeArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(AdministrativeArea::id), Codec.STRING.fieldOf("name").forGetter(AdministrativeArea::name),
            Codec.STRING.fieldOf("type").forGetter(AdministrativeArea::type), Codec.STRING.optionalFieldOf("parent_id", "").forGetter(AdministrativeArea::parentId),
            Codec.STRING.fieldOf("owner_type").forGetter(AdministrativeArea::ownerType), Codec.STRING.fieldOf("owner_id").forGetter(AdministrativeArea::ownerId)
    ).apply(instance, AdministrativeArea::new));
}
