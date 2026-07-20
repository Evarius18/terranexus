package net.evarius.terranexus.config;

public final class ClaimsConfig {
    public String _description = "Grundstücksgrenzen, RP-Markierungen und Schutzarten. Eigentums- und Berechtigungsprüfungen bleiben immer aktiv.";
    public int defaultMinimumY = -64;
    public int defaultMaximumY = 319;
    public int maximumPolygonPoints = 128;
    public int maximumPropertyNameLength = 80;
    public int markerDurationSeconds = 300;
    public int rentDayDurationMinutes = 1_440;
    public int maximumMissedRentPayments = 3;
    public boolean showPropertyEntryMessage = true;
    public boolean protectInteractions = true;
    public boolean protectContainers = true;
    public boolean protectRedstone = true;
    public boolean protectExplosions = true;
    public boolean protectPistons = true;
    public boolean protectAutomation = true;
    public boolean protectFluids = true;
    public boolean protectFire = true;
    public boolean protectFarmland = true;
    public boolean preventPvpInsideClaims = false;

    void validate() {
        defaultMinimumY = ConfigManager.clamp(defaultMinimumY, -2_032, 2_031);
        defaultMaximumY = ConfigManager.clamp(defaultMaximumY, defaultMinimumY, 2_031);
        maximumPolygonPoints = ConfigManager.clamp(maximumPolygonPoints, 3, 2_048);
        maximumPropertyNameLength = ConfigManager.clamp(maximumPropertyNameLength, 3, 128);
        markerDurationSeconds = ConfigManager.clamp(markerDurationSeconds, 10, 3_600);
        rentDayDurationMinutes = ConfigManager.clamp(rentDayDurationMinutes, 1, 525_600);
        maximumMissedRentPayments = ConfigManager.clamp(maximumMissedRentPayments, 1, 24);
    }
}
