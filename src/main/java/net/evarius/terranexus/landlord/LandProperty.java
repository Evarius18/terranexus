package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

public record LandProperty(String id, String name, String ownerType, String ownerId, String dimension,
                           String regionType, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                           List<String> polygonPoints) {
    public static final Codec<LandProperty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(LandProperty::id), Codec.STRING.fieldOf("name").forGetter(LandProperty::name),
            Codec.STRING.fieldOf("owner_type").forGetter(LandProperty::ownerType), Codec.STRING.fieldOf("owner_id").forGetter(LandProperty::ownerId),
            Codec.STRING.fieldOf("dimension").forGetter(LandProperty::dimension), Codec.STRING.fieldOf("region_type").forGetter(LandProperty::regionType),
            Codec.INT.fieldOf("min_x").forGetter(LandProperty::minX), Codec.INT.fieldOf("min_y").forGetter(LandProperty::minY),
            Codec.INT.fieldOf("min_z").forGetter(LandProperty::minZ), Codec.INT.fieldOf("max_x").forGetter(LandProperty::maxX),
            Codec.INT.fieldOf("max_y").forGetter(LandProperty::maxY), Codec.INT.fieldOf("max_z").forGetter(LandProperty::maxZ),
            Codec.STRING.listOf().optionalFieldOf("polygon", List.of()).forGetter(LandProperty::polygonPoints)
    ).apply(instance, LandProperty::new));

    public boolean contains(String world, int x, int y, int z) {
        if (!dimension.equals(world) || y < minY || y > maxY) return false;
        if (regionType.equals("chunk") || regionType.equals("cuboid"))
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        if (x < minX || x > maxX || z < minZ || z > maxZ || polygonPoints.size() < 3) return false;
        boolean inside=false;
        for(int i=0,j=polygonPoints.size()-1;i<polygonPoints.size();j=i++){
            String[] a=polygonPoints.get(i).split(","), b=polygonPoints.get(j).split(",");
            double xi=Double.parseDouble(a[0]),zi=Double.parseDouble(a[1]),xj=Double.parseDouble(b[0]),zj=Double.parseDouble(b[1]);
            if(((zi>z)!=(zj>z)) && x < (xj-xi)*(z-zi)/(zj-zi)+xi) inside=!inside;
        }
        return inside;
    }

    public boolean containsColumn(String world,int x,int z){return contains(world,x,minY,z);}

    public boolean isOwnedBy(UUID player) {
        return ownerType.equals("player") && ownerId.equals(player.toString());
    }

    public LandProperty withName(String newName) {
        return new LandProperty(id, newName, ownerType, ownerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints);
    }

    public LandProperty withOwner(String newOwnerType, String newOwnerId) {
        return new LandProperty(id, name, newOwnerType, newOwnerId, dimension, regionType,
                minX, minY, minZ, maxX, maxY, maxZ, polygonPoints);
    }
}
