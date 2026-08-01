package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.List;

public final class ClaimsConfig {
    public String _description = "Grundstücksgrenzen, RP-Markierungen und Schutzarten. Eigentums- und Berechtigungsprüfungen bleiben immer aktiv.";
    public int defaultMinimumY = -64;
    public int defaultMaximumY = 319;
    public int maximumPolygonPoints = 128;
    public int maximumPropertyNameLength = 80;
    public int maximumSubareasPerProperty = 128;
    public int maximumSubareaColumns = 100_000;
    public List<String> subareaTypes = new ArrayList<>(List.of(
            "Wohnung", "Gemeinschaftsbereich", "Keller", "Garage", "Gewerbeeinheit", "Sonstiger Bereich"));
    public int markerDurationSeconds = 300;
    public int transferRequestLifetimeHours = 168;
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
    public boolean tenantInteractionAllowed = true;
    public boolean tenantContainerAccess = true;
    public boolean tenantRedstoneAccess = true;
    public boolean tenantBuildingAllowed = true;
    public boolean allowPublicPropertySales = false;
    public boolean allowSpecialPropertySales = true;

    void validate() {
        defaultMinimumY = ConfigManager.clamp(defaultMinimumY, -2_032, 2_031);
        defaultMaximumY = ConfigManager.clamp(defaultMaximumY, defaultMinimumY, 2_031);
        maximumPolygonPoints = ConfigManager.clamp(maximumPolygonPoints, 3, 2_048);
        maximumPropertyNameLength = ConfigManager.clamp(maximumPropertyNameLength, 3, 128);
        maximumSubareasPerProperty = ConfigManager.clamp(maximumSubareasPerProperty, 1, 1000);
        maximumSubareaColumns = ConfigManager.clamp(maximumSubareaColumns, 256, 2_000_000);
        subareaTypes = ConfigManager.uniqueText(subareaTypes, 32, 64);
        if (subareaTypes.isEmpty()) subareaTypes = List.of("Sonstiger Bereich");
        markerDurationSeconds = ConfigManager.clamp(markerDurationSeconds, 10, 3_600);
        transferRequestLifetimeHours = ConfigManager.clamp(transferRequestLifetimeHours, 1, 8_760);
        rentDayDurationMinutes = ConfigManager.clamp(rentDayDurationMinutes, 1, 525_600);
        maximumMissedRentPayments = ConfigManager.clamp(maximumMissedRentPayments, 1, 24);
    }
}
