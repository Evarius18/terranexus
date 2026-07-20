package net.evarius.terranexus.config;

public final class GeneralConfig {
    public String _description = "Allgemeine Darstellung. Interne IDs und Sicherheitslogik sind absichtlich nicht konfigurierbar.";
    public int configVersion = 1;
    public String serverDisplayName = "TerraNexus";

    void validate() {
        configVersion = Math.max(1, configVersion);
        serverDisplayName = ConfigManager.text(serverDisplayName, "TerraNexus", 48);
    }
}
