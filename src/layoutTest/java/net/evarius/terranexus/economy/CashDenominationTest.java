package net.evarius.terranexus.economy;

import java.util.List;

public final class CashDenominationTest {
    private CashDenominationTest() {}

    public static void run() {
        List<Long> expected = List.of(50_000L, 20_000L, 10_000L, 5_000L, 2_000L, 1_000L,
                500L, 200L, 100L, 50L, 20L, 10L, 5L, 2L, 1L);
        List<Long> actual = CashDenomination.descending().stream().map(CashDenomination::cents).toList();
        if (!actual.equals(expected)) throw new AssertionError("Unexpected Nexus cash denominations: " + actual);
        if (CashDenomination.descending().getLast().cents() != 1)
            throw new AssertionError("Cash system cannot represent arbitrary cent amounts");
    }
}
