package net.evarius.terranexus.config;

public final class PhoneConfig {
    public String _description = "TerraNexus-Handy: Synchronisierung und maximale Anzahl übertragener RP-VCA-Historieneinträge.";
    public int synchronizationIntervalTicks = 10;
    public int historyLimit = 50;
    public int maximumDialLength = 64;

    void validate() {
        synchronizationIntervalTicks = ConfigManager.clamp(synchronizationIntervalTicks, 2, 100);
        historyLimit = ConfigManager.clamp(historyLimit, 5, 250);
        maximumDialLength = ConfigManager.clamp(maximumDialLength, 8, 128);
    }
}
