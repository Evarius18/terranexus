package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record ContainerLock(String dimension, long position, String ownerId,
                            List<String> permittedPlayers, long createdAt) {
    public static final Codec<ContainerLock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(ContainerLock::dimension),
            Codec.LONG.fieldOf("position").forGetter(ContainerLock::position),
            Codec.STRING.fieldOf("owner_id").forGetter(ContainerLock::ownerId),
            Codec.STRING.listOf().optionalFieldOf("permitted_players", List.of()).forGetter(ContainerLock::permittedPlayers),
            Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(ContainerLock::createdAt)
    ).apply(instance, ContainerLock::new));

    public boolean permits(String playerId) {
        return ownerId.equals(playerId) || permittedPlayers.contains(playerId);
    }

    public ContainerLock grant(String playerId) {
        if (permits(playerId)) return this;
        List<String> changed = new ArrayList<>(permittedPlayers);
        changed.add(playerId);
        return new ContainerLock(dimension, position, ownerId, List.copyOf(changed), createdAt);
    }

    public ContainerLock revoke(String playerId) {
        if (!permittedPlayers.contains(playerId)) return this;
        List<String> changed = new ArrayList<>(permittedPlayers);
        changed.remove(playerId);
        return new ContainerLock(dimension, position, ownerId, List.copyOf(changed), createdAt);
    }
}
