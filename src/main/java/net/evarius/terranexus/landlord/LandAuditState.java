package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LandAuditState extends PersistentState {
    private static final Codec<LandAuditState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LandAuditEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(state -> state.entries),
            OwnershipChange.CODEC.listOf().optionalFieldOf("ownership", List.of()).forGetter(state -> state.ownership)
    ).apply(instance, LandAuditState::new));
    private static final PersistentStateType<LandAuditState> TYPE =
            new PersistentStateType<>("terranexus_land_audit", LandAuditState::new, CODEC, DataFixTypes.LEVEL);
    private final List<LandAuditEntry> entries;
    private final List<OwnershipChange> ownership;

    public LandAuditState() { this(new ArrayList<>(), new ArrayList<>()); }
    private LandAuditState(List<LandAuditEntry> entries, List<OwnershipChange> ownership) {
        this.entries = new ArrayList<>(entries);
        this.ownership = new ArrayList<>(ownership);
    }
    public static LandAuditState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }
    public void log(UUID actor, String action, LandProperty property, String details) {
        entries.add(new LandAuditEntry(System.currentTimeMillis(), actor.toString(), action, property.id(), details));
        trim(entries);
        markDirty();
    }
    public void owner(UUID actor, LandProperty old, String newType, String newId) {
        ownership.add(new OwnershipChange(System.currentTimeMillis(), actor.toString(), old.id(), old.ownerType(), old.ownerId(), newType, newId));
        trim(ownership);
        log(actor, "OWNER_CHANGE", old, old.ownerType() + ':' + old.ownerId() + " -> " + newType + ':' + newId);
    }
    public List<LandAuditEntry> recent() {
        int maximum = Math.min(ConfigManager.desktop().standardEntriesPerPage, entries.size());
        List<LandAuditEntry> result = new ArrayList<>(maximum);
        for (int index = entries.size() - 1; index >= entries.size() - maximum; index--) result.add(entries.get(index));
        return List.copyOf(result);
    }
    public List<OwnershipChange> history(String propertyId) {
        List<OwnershipChange> result = new ArrayList<>();
        for (int index = ownership.size() - 1; index >= 0; index--) {
            OwnershipChange change = ownership.get(index);
            if (change.propertyId().equals(propertyId)) result.add(change);
        }
        return List.copyOf(result);
    }
    private static void trim(List<?> list) {
        int maximum = ConfigManager.logging().maximumLandAuditEntries;
        if (list.size() > maximum) list.subList(0, list.size() - maximum).clear();
    }
}
