package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

public final class CitizenDepartureAuditState extends PersistentState {
    private static final Codec<CitizenDepartureAuditState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CitizenDepartureRecord.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(state -> state.entries)
    ).apply(instance, CitizenDepartureAuditState::new));
    private static final PersistentStateType<CitizenDepartureAuditState> TYPE = new PersistentStateType<>(
            "terranexus_citizen_departure_audit", CitizenDepartureAuditState::new, CODEC, DataFixTypes.LEVEL);
    private final List<CitizenDepartureRecord> entries;

    public CitizenDepartureAuditState() { this(new ArrayList<>()); }
    private CitizenDepartureAuditState(List<CitizenDepartureRecord> entries) { this.entries = new ArrayList<>(entries); }
    public static CitizenDepartureAuditState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }
    public List<CitizenDepartureRecord> all() { return List.copyOf(entries); }
    public void append(CitizenDepartureRecord record) {
        entries.add(record);
        int maximum = ConfigManager.immigration().maximumDepartureAuditEntries;
        if (entries.size() > maximum) entries.subList(0, entries.size() - maximum).clear();
        markDirty();
    }
}
