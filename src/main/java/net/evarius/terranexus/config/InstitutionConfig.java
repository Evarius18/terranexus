package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InstitutionConfig {
    public String _description = "Grenzen und RP-Auswahlwerte für Institutionen. Die Rechte jeder Rolle sind nicht konfigurierbar.";
    public int maximumEmployees = 250;
    public int maximumNameLength = 80;
    public int maximumPersonnelNoteLength = 160;
    public long creationFee = 0;
    public String defaultEmployeeRole = "employee";
    public List<String> allowedRoles = new ArrayList<>(List.of("director", "manager", "auditor", "accountant", "hr", "employee"));
    public List<String> allowedTypes = new ArrayList<>(List.of("Behörde", "Unternehmen", "Bank/Finanzinstitut", "Zentralbank", "Verein", "Partei", "Bildungseinrichtung", "Rettungsorganisation", "Sonstige Institution"));
    public List<String> centralBankTypeKeywords = new ArrayList<>(List.of("Zentralbank", "Central Bank"));
    public Map<String, List<String>> emergencyOrganizationMappings = defaultEmergencyMappings();

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
        centralBankTypeKeywords = ConfigManager.uniqueText(centralBankTypeKeywords, 8, 48);
        if (centralBankTypeKeywords.isEmpty()) centralBankTypeKeywords = List.of("Zentralbank");
        LinkedHashMap<String, List<String>> emergencyMappings = new LinkedHashMap<>();
        if (emergencyOrganizationMappings != null) emergencyOrganizationMappings.forEach((key, matchers) -> {
            String normalizedKey = ConfigManager.text(key, "", 32).toUpperCase(Locale.ROOT);
            List<String> normalizedMatchers = ConfigManager.uniqueText(matchers, 16, 80);
            if (!normalizedKey.isBlank() && !normalizedMatchers.isEmpty() && emergencyMappings.size() < 32)
                emergencyMappings.putIfAbsent(normalizedKey, normalizedMatchers);
        });
        emergencyOrganizationMappings = emergencyMappings.isEmpty()
                ? defaultEmergencyMappings() : emergencyMappings;
        if (allowedTypes.stream().noneMatch(type -> type.equalsIgnoreCase("Zentralbank"))) {
            List<String> migrated = new ArrayList<>(allowedTypes);
            migrated.add("Zentralbank");
            allowedTypes = migrated;
        }
    }

    private static Map<String, List<String>> defaultEmergencyMappings() {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        result.put("FW", List.of("Feuerwehr", "Fire Department"));
        result.put("RD", List.of("Rettungsdienst", "Sanitätsdienst", "Medical", "EMS"));
        result.put("POL", List.of("Polizei", "Police"));
        return result;
    }
}
