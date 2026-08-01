package net.evarius.terranexus.economy;

import java.util.List;

public enum CashDenomination {
    CENT_1("cash_1_cent", 1), CENT_2("cash_2_cent", 2), CENT_5("cash_5_cent", 5),
    CENT_10("cash_10_cent", 10), CENT_20("cash_20_cent", 20), CENT_50("cash_50_cent", 50),
    NEXUS_1("cash_1_nexus", 100), NEXUS_2("cash_2_nexus", 200), NEXUS_5("cash_5_nexus", 500),
    NEXUS_10("cash_10_nexus", 1_000), NEXUS_20("cash_20_nexus", 2_000),
    NEXUS_50("cash_50_nexus", 5_000), NEXUS_100("cash_100_nexus", 10_000),
    NEXUS_200("cash_200_nexus", 20_000), NEXUS_500("cash_500_nexus", 50_000);

    private final String id;
    private final long cents;

    CashDenomination(String id, long cents) { this.id = id; this.cents = cents; }
    public String id() { return id; }
    public long cents() { return cents; }
    public boolean coin() { return cents < 500; }

    public static List<CashDenomination> descending() {
        return java.util.Arrays.stream(values()).sorted(java.util.Comparator.comparingLong(CashDenomination::cents).reversed()).toList();
    }
}
