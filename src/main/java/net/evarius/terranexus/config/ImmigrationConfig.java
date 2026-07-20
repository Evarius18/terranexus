package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.List;

public final class ImmigrationConfig {
    public String _description = "Eingabegrenzen und amtliche Auswahlwerte der Einreisebehörde. Freigaben bleiben verpflichtend.";
    public int maximumFieldLength = 80;
    public String citizenNumberPrefix = "TN-";
    public int citizenNumberDigits = 8;
    public List<String> genderOptions = new ArrayList<>(List.of("Weiblich", "Männlich", "Divers", "Keine Angabe"));

    void validate() {
        maximumFieldLength = ConfigManager.clamp(maximumFieldLength, 16, 256);
        citizenNumberPrefix = ConfigManager.text(citizenNumberPrefix, "TN-", 8).replaceAll("[^A-Za-z0-9-]", "").toUpperCase();
        citizenNumberDigits = ConfigManager.clamp(citizenNumberDigits, 6, 12);
        genderOptions = ConfigManager.uniqueText(genderOptions, 16, 32);
        if (genderOptions.isEmpty()) genderOptions = List.of("Keine Angabe");
    }
}
