package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record LandAccess(String propertyId, Map<String, List<String>> grants, Map<String, Boolean> publicRules) {
    public static final String BUILD = "build", INTERACT = "interact", CONTAINERS = "containers", REDSTONE = "redstone";
    public static final Codec<LandAccess> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(LandAccess::propertyId),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("grants", Map.of()).forGetter(LandAccess::grants),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("public_rules", Map.of()).forGetter(LandAccess::publicRules)
    ).apply(instance, LandAccess::new));
    public boolean permits(String playerId, String permission) { return publicRules.getOrDefault(permission, false) || grants.getOrDefault(playerId, List.of()).contains(permission); }
}
