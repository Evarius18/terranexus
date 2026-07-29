package net.evarius.terranexus.phone.history;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PhoneHistoryState extends PersistentState {
    private static final Codec<Map<String, List<PhoneHistoryEntry>>> ENTRIES_CODEC =
            Codec.unboundedMap(Codec.STRING, PhoneHistoryEntry.CODEC.listOf());
    private static final Codec<PhoneHistoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRIES_CODEC.optionalFieldOf("players", Map.of()).forGetter(state -> state.entries)
    ).apply(instance, PhoneHistoryState::new));
    private static final PersistentStateType<PhoneHistoryState> TYPE = new PersistentStateType<>(
            "terranexus_phone_history", PhoneHistoryState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, List<PhoneHistoryEntry>> entries;

    public PhoneHistoryState() { entries = new HashMap<>(); }

    private PhoneHistoryState(Map<String, List<PhoneHistoryEntry>> stored) {
        entries = new HashMap<>();
        stored.forEach((player, history) -> entries.put(player, new ArrayList<>(history)));
    }

    public static PhoneHistoryState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<PhoneHistoryEntry> entries(UUID playerId) {
        List<PhoneHistoryEntry> result = entries.get(playerId.toString());
        return result == null ? List.of() : List.copyOf(result);
    }

    public void add(UUID playerId, PhoneHistoryEntry entry) {
        List<PhoneHistoryEntry> history = entries.computeIfAbsent(playerId.toString(), ignored -> new ArrayList<>());
        history.add(0, entry);
        int maximum = ConfigManager.phone().historyLimit;
        if (history.size() > maximum) history.subList(maximum, history.size()).clear();
        markDirty();
    }
}
