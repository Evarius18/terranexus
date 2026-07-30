package net.evarius.terranexus.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WelfareState extends PersistentState {
    private static final Codec<WelfareState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("next_payment_at", Map.of())
                    .forGetter(state -> state.nextPaymentAt),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("eligible", Map.of())
                    .forGetter(state -> state.eligible)
    ).apply(instance, WelfareState::new));
    private static final PersistentStateType<WelfareState> TYPE =
            new PersistentStateType<>("terranexus_welfare", WelfareState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Long> nextPaymentAt;
    private final Map<String, Boolean> eligible;

    public WelfareState() {
        this(new HashMap<>(), new HashMap<>());
    }

    private WelfareState(Map<String, Long> nextPaymentAt, Map<String, Boolean> eligible) {
        this.nextPaymentAt = new HashMap<>(nextPaymentAt);
        this.eligible = new HashMap<>(eligible);
    }

    public static WelfareState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public synchronized void process(MinecraftServer server, long now) {
        var config = ConfigManager.economy();
        if (!config.citizenAllowanceEnabled || config.citizenAllowanceAmount <= 0) return;
        long interval = (long) config.citizenAllowanceIntervalMinutes * 60_000L;
        InstitutionState institutions = InstitutionState.get(server);
        LandManagementState administrations = LandManagementState.get(server);
        EconomyState economy = EconomyState.get(server);
        boolean changed = false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            String key = playerId.toString();
            boolean employed = institutions.isEmployed(playerId) || administrations.isEmployed(playerId);
            if (employed) {
                if (eligible.getOrDefault(key, true)) {
                    eligible.put(key, false);
                    nextPaymentAt.put(key, now + interval);
                    changed = true;
                }
                continue;
            }
            if (!eligible.getOrDefault(key, false)) {
                eligible.put(key, true);
                nextPaymentAt.put(key, now + interval);
                changed = true;
                continue;
            }
            long due = nextPaymentAt.getOrDefault(key, 0L);
            if (due <= 0L) {
                nextPaymentAt.put(key, now + interval);
                changed = true;
                continue;
            }
            if (now < due) continue;
            boolean paid = economy.issueMoney(EconomyState.playerAccount(playerId), config.citizenAllowanceAmount,
                    "Bürgergeld", "SYSTEM");
            nextPaymentAt.put(key, now + interval);
            changed = true;
            if (paid && config.citizenAllowanceNotifyPlayer)
                player.sendMessage(Text.literal("Bürgergeld ausgezahlt: " + EconomyState.format(config.citizenAllowanceAmount))
                        .formatted(Formatting.GREEN), false);
        }
        if (changed) markDirty();
    }
}
