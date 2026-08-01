package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A classified internal area. Ownership and protection always resolve through the parent property. */
public record PropertySubarea(String id, String propertyId, String buildingId, String name, String type, List<String> polygon,
                              int minY, int maxY, long createdAt, String createdBy) {
    public static final Codec<PropertySubarea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(PropertySubarea::id),
            Codec.STRING.fieldOf("property_id").forGetter(PropertySubarea::propertyId),
            Codec.STRING.optionalFieldOf("building_id", "").forGetter(PropertySubarea::buildingId),
            Codec.STRING.fieldOf("name").forGetter(PropertySubarea::name),
            Codec.STRING.fieldOf("type").forGetter(PropertySubarea::type),
            Codec.STRING.listOf().fieldOf("polygon").forGetter(PropertySubarea::polygon),
            Codec.INT.fieldOf("min_y").forGetter(PropertySubarea::minY),
            Codec.INT.fieldOf("max_y").forGetter(PropertySubarea::maxY),
            Codec.LONG.fieldOf("created_at").forGetter(PropertySubarea::createdAt),
            Codec.STRING.fieldOf("created_by").forGetter(PropertySubarea::createdBy)
    ).apply(instance, PropertySubarea::new));

    public PropertySubarea(String id, String propertyId, String name, String type, List<String> polygon,
                           int minY, int maxY, long createdAt, String createdBy) {
        this(id, propertyId, "", name, type, polygon, minY, maxY, createdAt, createdBy);
    }
}
