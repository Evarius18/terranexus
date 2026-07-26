package net.evarius.terranexus.api.economy;

import net.evarius.terranexus.economy.EconomyTransaction;

import java.util.List;

/** Immutable, server-created account view for optional RP integrations such as a phone addon. */
public record AccountSnapshot(String accountNumber, long balance, boolean frozen,
                              List<EconomyTransaction> recentTransactions) {
    public AccountSnapshot {
        recentTransactions = List.copyOf(recentTransactions);
    }
}
