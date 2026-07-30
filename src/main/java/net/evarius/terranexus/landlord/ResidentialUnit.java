package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record ResidentialUnit(String propertyId, String buildingId, String name, boolean commonArea,
                              List<String> pathSegments) {
    public static final Codec<ResidentialUnit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(ResidentialUnit::propertyId),
            Codec.STRING.fieldOf("building_id").forGetter(ResidentialUnit::buildingId),
            Codec.STRING.fieldOf("name").forGetter(ResidentialUnit::name),
            Codec.BOOL.optionalFieldOf("common_area", false).forGetter(ResidentialUnit::commonArea),
            Codec.STRING.listOf().optionalFieldOf("path_segments", List.of())
                    .forGetter(ResidentialUnit::pathSegments)
    ).apply(instance, ResidentialUnit::new));

    public ResidentialUnit {
        pathSegments = pathSegments == null || pathSegments.isEmpty() ? List.of(name)
                : pathSegments.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).limit(8).toList();
    }

    public ResidentialUnit(String propertyId, String buildingId, String name, boolean commonArea) {
        this(propertyId, buildingId, name, commonArea, List.of(name));
    }

    public String typeLabel() {
        return commonArea ? "Gemeinschaftsbereich" : "Wohnung";
    }

    public String pathLabel() {
        return String.join(" · ", pathSegments);
    }
}
