package net.evarius.terranexus.config;

public final class EconomyConfig {
    public String _description = "Währung, Anfangsguthaben und Überweisungsgrenzen. Geldbeträge werden in der kleinsten Währungseinheit gespeichert.";
    public String currencyName = "Nexus";
    public String currencySymbol = "N";
    public int currencyDecimals = 2;
    public long playerStartBalance = 0;
    public long maximumTransferAmount = 100_000_000L;
    public int transferFeeBasisPoints = 0;
    public String accountNumberPrefix = "TN";
    public int accountNumberDigits = 12;
    public boolean citizenAllowanceEnabled = true;
    public long citizenAllowanceAmount = 10_000L;
    public int citizenAllowanceIntervalMinutes = 60;
    public boolean citizenAllowanceNotifyPlayer = true;

    void validate() {
        // Migrate the former shipped defaults while preserving genuinely customized currencies.
        if ("TerraNexus Euro".equalsIgnoreCase(currencyName)) currencyName = "Nexus";
        if (currencySymbol != null && (currencySymbol.equals("TN€") || currencySymbol.equals("TNâ‚¬")))
            currencySymbol = "N";
        currencyName = ConfigManager.text(currencyName, "Nexus", 48);
        currencySymbol = ConfigManager.text(currencySymbol, "N", 8);
        currencyDecimals = ConfigManager.clamp(currencyDecimals, 0, 2);
        playerStartBalance = Math.max(0, playerStartBalance);
        maximumTransferAmount = Math.max(1, maximumTransferAmount);
        transferFeeBasisPoints = ConfigManager.clamp(transferFeeBasisPoints, 0, 10_000);
        accountNumberPrefix = ConfigManager.text(accountNumberPrefix, "TN", 6).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (accountNumberPrefix.isBlank()) accountNumberPrefix = "TN";
        accountNumberDigits = ConfigManager.clamp(accountNumberDigits, 8, 16);
        citizenAllowanceAmount = Math.max(0, citizenAllowanceAmount);
        citizenAllowanceIntervalMinutes = ConfigManager.clamp(citizenAllowanceIntervalMinutes, 5, 10_080);
    }
}
