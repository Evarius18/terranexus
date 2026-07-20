package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InstitutionConfig {
    public String _description = "Grenzen und RP-Auswahlwerte für Institutionen. Die Rechte jeder Rolle sind nicht konfigurierbar.";
    public int maximumEmployees = 250;
    public int maximumNameLength = 80;
    public int maximumPersonnelNoteLength = 160;
    public long creationFee = 0;
    public String defaultEmployeeRole = "employee";
    public List<String> allowedRoles = new ArrayList<>(List.of("director", "manager", "auditor", "accountant", "hr", "employee"));
    public List<String> allowedTypes = new ArrayList<>(List.of("Behörde", "Unternehmen", "Bank/Finanzinstitut", "Verein", "Partei", "Bildungseinrichtung", "Rettungsorganisation", "Sonstige Institution"));

    void validate() {
        maximumEmployees = ConfigManager.clamp(maximumEmployees, 1, 10_000);
        maximumNameLength = ConfigManager.clamp(maximumNameLength, 3, 128);
        maximumPersonnelNoteLength = ConfigManager.clamp(maximumPersonnelNoteLength, 0, 2_000);
        creationFee = Math.max(0, creationFee);
        allowedRoles = ConfigManager.uniqueText(allowedRoles, 16, 32).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.equals("owner")).toList();
        if (allowedRoles.isEmpty()) allowedRoles = List.of("employee");
        defaultEmployeeRole = ConfigManager.text(defaultEmployeeRole, "employee", 32).toLowerCase(Locale.ROOT);
        if (!allowedRoles.contains(defaultEmployeeRole)) defaultEmployeeRole = allowedRoles.contains("employee") ? "employee" : allowedRoles.getFirst();
        allowedTypes = ConfigManager.uniqueText(allowedTypes, 24, 64);
        if (allowedTypes.isEmpty()) allowedTypes = List.of("Sonstige Institution");
    }
}
