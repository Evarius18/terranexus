package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResidentialUnit(String propertyId, String buildingId, String name, boolean commonArea) {
    public static final Codec<ResidentialUnit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(ResidentialUnit::propertyId),
            Codec.STRING.fieldOf("building_id").forGetter(ResidentialUnit::buildingId),
            Codec.STRING.fieldOf("name").forGetter(ResidentialUnit::name),
            Codec.BOOL.optionalFieldOf("common_area", false).forGetter(ResidentialUnit::commonArea)
    ).apply(instance, ResidentialUnit::new));

    public String typeLabel() {
        return commonArea ? "Gemeinschaftsbereich" : "Wohnung";
    }
}
