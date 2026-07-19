package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;

public class IdentityState extends PersistentState {
    private static final Codec<Map<String, CitizenIdentity>> IDENTITIES_CODEC = Codec.unboundedMap(Codec.STRING, CitizenIdentity.CODEC);
    private static final Codec<ApprovalRecord> APPROVAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("approved_by").forGetter(ApprovalRecord::approvedBy),
            Codec.LONG.fieldOf("approved_at").forGetter(ApprovalRecord::approvedAt)
    ).apply(instance, ApprovalRecord::new));
    private static final Codec<Map<String, ApprovalRecord>> APPROVALS_CODEC = Codec.unboundedMap(Codec.STRING, APPROVAL_CODEC);
    private static final Codec<IdentityState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IDENTITIES_CODEC.optionalFieldOf("identities", Map.of()).forGetter(state -> state.identities),
            APPROVALS_CODEC.optionalFieldOf("approvals", Map.of()).forGetter(state -> state.approvals)
    ).apply(instance, IdentityState::new));
    private static final PersistentStateType<IdentityState> TYPE =
            new PersistentStateType<>("terranexus_identities", IdentityState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, CitizenIdentity> identities;
    private final Map<String, ApprovalRecord> approvals;
    private static final SecureRandom RANDOM = new SecureRandom();

    public IdentityState() {
        this(new HashMap<>(), new HashMap<>());
    }

    private IdentityState(Map<String, CitizenIdentity> identities, Map<String, ApprovalRecord> approvals) {
        this.identities = new HashMap<>(identities);
        this.approvals = new HashMap<>(approvals);
    }

    public static IdentityState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public CitizenIdentity get(UUID playerUuid) {
        return identities.get(playerUuid.toString());
    }

    public CitizenIdentity create(UUID playerUuid, String firstName, String lastName, String birthDate,
                                  String birthPlace, String birthCountry, String nationality) {
        CitizenIdentity identity = new CitizenIdentity(playerUuid.toString(),
                createRandomCitizenNumber(), firstName, lastName, birthDate,
                birthPlace, birthCountry, nationality, "Nicht angegeben", "Nicht gemeldet");
        identities.put(playerUuid.toString(), identity);
        markDirty();
        return identity;
    }

    private String createRandomCitizenNumber() {
        while (true) {
            String number = "TN-" + String.format("%08d", RANDOM.nextInt(100_000_000));
            boolean exists = identities.values().stream()
                    .anyMatch(identity -> identity.citizenNumber().equals(number));
            if (!exists) return number;
        }
    }

    public void put(CitizenIdentity identity) {
        identities.put(identity.playerUuid(), identity);
        markDirty();
    }

    public void approve(UUID citizen, UUID officer) {
        approvals.put(citizen.toString(), new ApprovalRecord(officer.toString(), System.currentTimeMillis()));
        markDirty();
    }

    public boolean isApproved(UUID citizen) {
        return approvals.containsKey(citizen.toString());
    }

    public ApprovalRecord approval(UUID citizen) {
        return approvals.get(citizen.toString());
    }

    public record ApprovalRecord(String approvedBy, long approvedAt) {}
}
