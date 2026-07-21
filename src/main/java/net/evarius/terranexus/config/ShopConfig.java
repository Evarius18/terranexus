package net.evarius.terranexus.config;

public final class ShopConfig {
    public String _description = "Kisten-Shops, Preisgrenzen und Transaktionsmengen.";
    public boolean enabled = true;
    public boolean requireClaimedLand = true;
    public boolean allowPlayersToSellToShops = true;
    public int maximumShopsPerOwner = 64;
    public int maximumItemsPerTransaction = 64;
    public long maximumItemPrice = 100_000_000L;

    void validate() {
        maximumShopsPerOwner = ConfigManager.clamp(maximumShopsPerOwner, 1, 10_000);
        maximumItemsPerTransaction = ConfigManager.clamp(maximumItemsPerTransaction, 1, 64);
        maximumItemPrice = ConfigManager.clamp(maximumItemPrice, 1L, Long.MAX_VALUE / 128L);
    }
}
