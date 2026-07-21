package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Server-facing names and defaults for the land administration hierarchy. */
public final class AdministrationConfig {
    public String _description = "Frei benennbare Verwaltungshierarchie (von klein nach groß), Wilderness und öffentliche Flächennutzungen.";
    public String wildernessName = "Wilderness";
    public String wildernessLevelName = "Oberste Verwaltung";
    public List<String> hierarchyLevels = new ArrayList<>(List.of(
            "Ort / Stadt", "Gemeinde", "Landkreis", "Region", "Bundesland", "Staat"
    ));
    public int maximumAdministrativeAreas = 10_000;
    public int maximumAreaNameLength = 80;

    public String privateLandUse = "Privatgrundstück";
    public List<String> landUseTypes = new ArrayList<>(List.of(
            "Privatgrundstück", "Straße", "Weg", "Park", "Öffentlicher Platz", "Gewässer",
            "Verwaltungsfläche", "Sonstige öffentliche Fläche"
    ));
    public List<String> publicLandUseTypes = new ArrayList<>(List.of(
            "Straße", "Weg", "Park", "Öffentlicher Platz", "Gewässer",
            "Verwaltungsfläche", "Sonstige öffentliche Fläche"
    ));
    public boolean defaultPublicInteractionAllowed = true;
    public boolean defaultPublicContainerAccess = false;
    public boolean defaultPublicRedstoneAccess = false;
    public boolean defaultPublicBuildingAllowed = false;

    public boolean wildernessPublicInteractionAllowed = true;
    public boolean wildernessPublicContainerAccess = false;
    public boolean wildernessPublicRedstoneAccess = false;
    public boolean wildernessPublicBuildingAllowed = false;
    public boolean wildernessPreventPvp = false;
    public boolean wildernessEnvironmentalProtection = true;

    void validate() {
        wildernessName = ConfigManager.text(wildernessName, "Wilderness", 80);
        wildernessLevelName = ConfigManager.text(wildernessLevelName, "Oberste Verwaltung", 80);
        hierarchyLevels = ConfigManager.uniqueText(hierarchyLevels, 16, 80);
        if (hierarchyLevels.isEmpty()) hierarchyLevels = new ArrayList<>(List.of("Staat"));
        maximumAdministrativeAreas = ConfigManager.clamp(maximumAdministrativeAreas, 1, 1_000_000);
        maximumAreaNameLength = ConfigManager.clamp(maximumAreaNameLength, 3, 128);

        privateLandUse = ConfigManager.text(privateLandUse, "Privatgrundstück", 80);
        LinkedHashSet<String> uses = new LinkedHashSet<>(ConfigManager.uniqueText(landUseTypes, 64, 80));
        uses.add(privateLandUse);
        landUseTypes = new ArrayList<>(uses);
        LinkedHashSet<String> publicUses = new LinkedHashSet<>(ConfigManager.uniqueText(publicLandUseTypes, 63, 80));
        publicUses.remove(privateLandUse);
        publicUses.retainAll(uses);
        publicLandUseTypes = new ArrayList<>(publicUses);
    }
}
