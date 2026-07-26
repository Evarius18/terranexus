package net.evarius.terranexus.api.economy;

import net.evarius.terranexus.economy.BankAccount;
import net.evarius.terranexus.economy.EconomyState;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stable read-only entry point for companion mods. Mutations remain inside the
 * server-authoritative economy services and are intentionally not exposed here.
 */
public final class TerraNexusEconomyApi {
    private TerraNexusEconomyApi() {}

    public static AccountSnapshot accountSnapshot(ServerPlayerEntity player) {
        EconomyState economy = EconomyState.get(player.getServer());
        String accountKey = EconomyState.playerAccount(player.getUuid());
        BankAccount account = economy.account(accountKey);
        return new AccountSnapshot(account.accountNumber(), economy.balance(accountKey), account.frozen(),
                economy.history(accountKey).stream().limit(5).toList());
    }
}
