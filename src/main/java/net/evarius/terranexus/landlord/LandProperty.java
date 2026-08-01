package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

public record LandProperty(String id, String name, String ownerType, String ownerId, String dimension,
                           String regionType, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                           List<String> polygonPoints, List<List<String>> excludedAreas) {
    public static final Codec<LandProperty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(LandProperty::id), Codec.STRING.fieldOf("name").forGetter(LandProperty::name),
            Codec.STRING.fieldOf("owner_type").forGetter(LandProperty::ownerType), Codec.STRING.fieldOf("owner_id").forGetter(LandProperty::ownerId),
            Codec.STRING.fieldOf("dimension").forGetter(LandProperty::dimension), Codec.STRING.fieldOf("region_type").forGetter(LandProperty::regionType),
            Codec.INT.fieldOf("min_x").forGetter(LandProperty::minX), Codec.INT.fieldOf("min_y").forGetter(LandProperty::minY),
            Codec.INT.fieldOf("min_z").forGetter(LandProperty::minZ), Codec.INT.fieldOf("max_x").forGetter(LandProperty::maxX),
            Codec.INT.fieldOf("max_y").forGetter(LandProperty::maxY), Codec.INT.fieldOf("max_z").forGetter(LandProperty::maxZ),
            Codec.STRING.listOf().optionalFieldOf("polygon", List.of()).forGetter(LandProperty::polygonPoints),
            Codec.STRING.listOf().listOf().optionalFieldOf("excluded_areas", List.of()).forGetter(LandProperty::excludedAreas)
    ).apply(instance, LandProperty::new));

    public LandProperty(String id, String name, String ownerType, String ownerId, String dimension,
                        String regionType, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                        List<String> polygonPoints) {
        this(id, name, ownerType, ownerId, dimension, regionType, minX, minY, minZ, maxX, maxY, maxZ,
                polygonPoints, List.of());
    }

    public boolean contains(String world, int x, int y, int z) {
        if (!dimension.equals(world) || y < minY || y > maxY) return false;
        boolean outer = regionType.equals("chunk") || regionType.equals("cuboid")
                ? x >= minX && x <= maxX && z >= minZ && z <= maxZ
                : x >= minX && x <= maxX && z >= minZ && z <= maxZ && polygonPoints.size() >= 3
                && LandGeometry.containsMarkedBlock(polygonPoints, x, z);
        return outer && excludedAreas.stream().noneMatch(area -> LandGeometry.containsMarkedBlock(area, x, z));
    }

    public boolean containsColumn(String world,int x,int z){return contains(world,x,minY,z);}

    public boolean isOwnedBy(UUID player) {
        return ownerType.equals("player") && ownerId.equals(player.toString());
    }

    public LandProperty withName(String newName) {
        return new LandProperty(id, newName, ownerType, ownerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints, excludedAreas);
    }

    public LandProperty withOwner(String newOwnerType, String newOwnerId) {
        return new LandProperty(id, name, newOwnerType, newOwnerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints, excludedAreas);
    }

    public LandProperty withExcludedArea(List<String> area) {
        List<List<String>> changed = new java.util.ArrayList<>(excludedAreas);
        changed.add(List.copyOf(area));
        return new LandProperty(id, name, ownerType, ownerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints, List.copyOf(changed));
    }

    public LandProperty withExcludedAreas(List<List<String>> areas) {
        return new LandProperty(id, name, ownerType, ownerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints,
                areas == null ? List.of() : areas.stream().map(List::copyOf).toList());
    }
}
