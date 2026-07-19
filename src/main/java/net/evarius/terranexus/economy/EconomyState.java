package net.evarius.terranexus.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.evarius.terranexus.config.TerraNexusConfig;

public class EconomyState extends PersistentState {
    private static final Codec<EconomyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("balances", Map.of()).forGetter(state -> state.balances)
    ).apply(instance, EconomyState::new));
    private static final PersistentStateType<EconomyState> TYPE =
            new PersistentStateType<>("terranexus_economy", EconomyState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Long> balances;

    public EconomyState() { this(new HashMap<>()); }
    private EconomyState(Map<String, Long> balances) { this.balances = new HashMap<>(balances); }

    public static EconomyState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public long balance(UUID owner) { return balances.getOrDefault(owner.toString(), 0L); }

    public void deposit(UUID owner, long cents) {
        if (cents < 0) throw new IllegalArgumentException("Negative deposit");
        balances.merge(owner.toString(), cents, Math::addExact);
        markDirty();
    }

    public boolean withdraw(UUID owner, long cents) {
        if (cents < 0 || balance(owner) < cents) return false;
        balances.put(owner.toString(), balance(owner) - cents);
        markDirty();
        return true;
    }

    public boolean transfer(UUID from, UUID to, long cents) {
        if (!withdraw(from, cents)) return false;
        deposit(to, cents);
        return true;
    }

    public static String format(long cents) {
        TerraNexusConfig config = TerraNexusConfig.get();
        if (config.currencyDecimals == 0) return String.format("%,d %s", cents, config.currencySymbol);
        return String.format("%,d.%02d %s", cents / 100, Math.abs(cents % 100), config.currencySymbol);
    }
}
