package net.evarius.terranexus.economy;

public record EconomyMetrics(long totalMoneySupply, long playerMoney, long institutionMoney,
                             long administrationMoney, long systemMoney, int accountCount,
                             int frozenAccountCount, long successfulTransactions,
                             long rejectedTransactions, long transferredVolume,
                             long issuedMoney, long retiredMoney) {
}
