package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.evarius.terranexus.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

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
    private final Set<String> citizenNumbers = new HashSet<>();
    private List<CitizenIdentity> cachedAll;
    private List<CitizenIdentity> cachedApproved;
    private static final SecureRandom RANDOM = new SecureRandom();

    public IdentityState() {
        this(new HashMap<>(), new HashMap<>());
    }

    private IdentityState(Map<String, CitizenIdentity> identities, Map<String, ApprovalRecord> approvals) {
        this.identities = new HashMap<>(identities);
        this.approvals = new HashMap<>(approvals);
        this.identities.values().forEach(identity -> citizenNumbers.add(identity.citizenNumber()));
    }

    public static IdentityState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public CitizenIdentity get(UUID playerUuid) {
        return identities.get(playerUuid.toString());
    }

    public List<CitizenIdentity> allApproved() {
        if (cachedApproved == null) cachedApproved = identities.values().stream()
                .filter(identity -> approvals.containsKey(identity.playerUuid()))
                .sorted(java.util.Comparator.comparing(CitizenIdentity::lastName).thenComparing(CitizenIdentity::firstName)).toList();
        return cachedApproved;
    }

    public List<CitizenIdentity> all() {
        if (cachedAll == null) cachedAll = identities.values().stream()
                .sorted(java.util.Comparator.comparing(CitizenIdentity::lastName)
                        .thenComparing(CitizenIdentity::firstName)
                        .thenComparing(CitizenIdentity::citizenNumber)).toList();
        return cachedAll;
    }

    public CitizenIdentity create(UUID playerUuid, String firstName, String lastName, String birthDate,
                                  String birthPlace, String birthCountry, String nationality) {
        CitizenIdentity identity = new CitizenIdentity(playerUuid.toString(),
                createRandomCitizenNumber(), firstName, lastName, birthDate,
                birthPlace, birthCountry, nationality, "Nicht angegeben", "Nicht gemeldet");
        identities.put(playerUuid.toString(), identity);
        citizenNumbers.add(identity.citizenNumber());
        invalidateCaches();
        markDirty();
        return identity;
    }

    private String createRandomCitizenNumber() {
        while (true) {
            var config = ConfigManager.immigration();
            long bound = 1;
            for (int index = 0; index < config.citizenNumberDigits; index++) bound *= 10;
            String number = config.citizenNumberPrefix + String.format("%0" + config.citizenNumberDigits + "d", RANDOM.nextLong(bound));
            if (!citizenNumbers.contains(number)) return number;
        }
    }

    public void put(CitizenIdentity identity) {
        CitizenIdentity old = identities.put(identity.playerUuid(), identity);
        if (old != null) citizenNumbers.remove(old.citizenNumber());
        citizenNumbers.add(identity.citizenNumber());
        invalidateCaches();
        markDirty();
    }

    public void approve(UUID citizen, UUID officer) {
        approvals.put(citizen.toString(), new ApprovalRecord(officer.toString(), System.currentTimeMillis()));
        cachedApproved = null;
        markDirty();
    }

    public boolean isApproved(UUID citizen) {
        return approvals.containsKey(citizen.toString());
    }

    public ApprovalRecord approval(UUID citizen) {
        return approvals.get(citizen.toString());
    }

    public void revokeApproval(UUID citizen) {
        if (approvals.remove(citizen.toString()) != null) { cachedApproved = null; markDirty(); }
    }

    private void invalidateCaches() { cachedAll = null; cachedApproved = null; }

    public record ApprovalRecord(String approvedBy, long approvedAt) {}
}
