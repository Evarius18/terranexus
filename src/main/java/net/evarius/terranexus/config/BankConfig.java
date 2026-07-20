package net.evarius.terranexus.config;

public final class BankConfig {
    public String _description = "Bedien- und Betragsgrenzen der Bankverwaltung. Rollen- und Eigentumsprüfungen bleiben fest im Code.";
    public long maximumCashOperation = 10_000_000L;
    public boolean accountFreezingEnabled = true;
    public int searchMinimumCharacters = 1;

    void validate() {
        maximumCashOperation = Math.max(1, maximumCashOperation);
        searchMinimumCharacters = ConfigManager.clamp(searchMinimumCharacters, 1, 16);
    }
}
