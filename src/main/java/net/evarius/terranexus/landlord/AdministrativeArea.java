package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AdministrativeArea(String id, String name, String type, String parentId,
                                 String ownerType, String ownerId, int level) {
    public static final Codec<AdministrativeArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(AdministrativeArea::id), Codec.STRING.fieldOf("name").forGetter(AdministrativeArea::name),
            Codec.STRING.fieldOf("type").forGetter(AdministrativeArea::type), Codec.STRING.optionalFieldOf("parent_id", "").forGetter(AdministrativeArea::parentId),
            Codec.STRING.fieldOf("owner_type").forGetter(AdministrativeArea::ownerType), Codec.STRING.fieldOf("owner_id").forGetter(AdministrativeArea::ownerId),
            Codec.INT.optionalFieldOf("level", -1).forGetter(AdministrativeArea::level)
    ).apply(instance, AdministrativeArea::new));

    public AdministrativeArea(String id, String name, String type, String parentId, String ownerType, String ownerId) {
        this(id, name, type, parentId, ownerType, ownerId, -1);
    }

    public AdministrativeArea withHierarchy(String newType, String newParentId, int newLevel) {
        return new AdministrativeArea(id, name, newType, newParentId, ownerType, ownerId, newLevel);
    }

    public AdministrativeArea withOwner(String newOwnerType, String newOwnerId) {
        return new AdministrativeArea(id, name, type, parentId, newOwnerType, newOwnerId, level);
    }
}
