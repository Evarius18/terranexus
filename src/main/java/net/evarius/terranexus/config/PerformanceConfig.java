package net.evarius.terranexus.config;

public final class PerformanceConfig {
    public String _description = "Intervalle und Obergrenzen für Scheduler, Partikel und Grundstücksindizes.";
    public int visualRefreshTicks = 20;
    public int propertyEntryCheckTicks = 10;
    public int maintenanceIntervalTicks = 1_200;
    public int maximumParticleBudgetPerPlayer = 2_048;
    public int maximumParticleSamplesPerEdge = 128;
    public int maximumIndexedChunksPerProperty = 4_096;
    public int maximumExactOverlapColumns = 1_000_000;

    void validate() {
        visualRefreshTicks = ConfigManager.clamp(visualRefreshTicks, 5, 200);
        propertyEntryCheckTicks = ConfigManager.clamp(propertyEntryCheckTicks, 5, 200);
        maintenanceIntervalTicks = ConfigManager.clamp(maintenanceIntervalTicks, 20, 72_000);
        maximumParticleBudgetPerPlayer = ConfigManager.clamp(maximumParticleBudgetPerPlayer, 64, 16_384);
        maximumParticleSamplesPerEdge = ConfigManager.clamp(maximumParticleSamplesPerEdge, 8, 512);
        maximumIndexedChunksPerProperty = ConfigManager.clamp(maximumIndexedChunksPerProperty, 64, 65_536);
        maximumExactOverlapColumns = ConfigManager.clamp(maximumExactOverlapColumns, 10_000, 10_000_000);
    }
}
