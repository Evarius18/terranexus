package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LandTransferState extends PersistentState {
    private static final Codec<LandTransferState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, LandTransferRequest.CODEC).optionalFieldOf("requests", Map.of())
                    .forGetter(state -> state.requests),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pending_extracts", Map.of())
                    .forGetter(state -> state.pendingExtracts)
    ).apply(instance, LandTransferState::new));
    private static final PersistentStateType<LandTransferState> TYPE = new PersistentStateType<>(
            "terranexus_land_transfers", LandTransferState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, LandTransferRequest> requests;
    private final Map<String, Integer> pendingExtracts;

    public LandTransferState() { this(new HashMap<>(), new HashMap<>()); }
    private LandTransferState(Map<String, LandTransferRequest> requests, Map<String, Integer> pendingExtracts) {
        this.requests = new HashMap<>(requests);
        this.pendingExtracts = new HashMap<>(pendingExtracts);
    }

    public static LandTransferState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public LandTransferRequest get(String id) { return requests.get(id); }
    public LandTransferRequest forProperty(String propertyId) {
        return requests.values().stream().filter(request -> request.propertyId().equals(propertyId)).findFirst().orElse(null);
    }
    public List<LandTransferRequest> all() { return List.copyOf(requests.values()); }
    public void put(LandTransferRequest request) { requests.put(request.id(), request); markDirty(); }
    public void remove(String id) { if (requests.remove(id) != null) markDirty(); }

    public void removeForProperty(String propertyId) {
        if (requests.values().removeIf(request -> request.propertyId().equals(propertyId))) markDirty();
    }

    public void removeForCitizen(String citizenId) {
        boolean changed = requests.values().removeIf(request -> request.oldOwnerId().equals(citizenId)
                || request.newOwnerId().equals(citizenId) || request.initiatorId().equals(citizenId));
        changed |= pendingExtracts.remove(citizenId) != null;
        if (changed) markDirty();
    }

    public void purgeExpired(long oldestAllowed) {
        if (requests.values().removeIf(request -> request.createdAt() < oldestAllowed)) markDirty();
    }

    public void queueExtract(String playerId) {
        pendingExtracts.merge(playerId, 1, Integer::sum);
        markDirty();
    }

    public int takeExtracts(String playerId) {
        Integer count = pendingExtracts.remove(playerId);
        if (count != null) markDirty();
        return count == null ? 0 : Math.max(0, count);
    }
}
