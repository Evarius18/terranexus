package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InstitutionState extends PersistentState {
    private static final Codec<InstitutionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Institution.CODEC).optionalFieldOf("institutions", Map.of()).forGetter(state -> state.institutions)
    ).apply(instance, InstitutionState::new));
    private static final PersistentStateType<InstitutionState> TYPE =
            new PersistentStateType<>("terranexus_institutions", InstitutionState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, Institution> institutions;

    public InstitutionState() { this(new HashMap<>()); }
    private InstitutionState(Map<String, Institution> institutions) { this.institutions = new HashMap<>(institutions); }
    public static InstitutionState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }

    public Institution create(String name, String type, UUID owner) {
        String id = UUID.randomUUID().toString();
        Institution institution = new Institution(id, name, type, owner.toString(),
                new HashMap<>(Map.of(owner.toString(), new ArrayList<>(List.of("owner", "manager")))));
        institutions.put(id, institution); markDirty(); return institution;
    }

    public List<Institution> forMember(UUID player) {
        return institutions.values().stream().filter(i -> i.members().containsKey(player.toString())).toList();
    }

    public void addMember(String institutionId, UUID player, String role) {
        Institution old = institutions.get(institutionId); if (old == null) return;
        Map<String,List<String>> members = new HashMap<>(old.members());
        List<String> roles = new ArrayList<>(members.getOrDefault(player.toString(), List.of()));
        if (!roles.contains(role)) roles.add(role); members.put(player.toString(), roles);
        institutions.put(institutionId, new Institution(old.id(), old.name(), old.type(), old.ownerUuid(), members)); markDirty();
    }
}
