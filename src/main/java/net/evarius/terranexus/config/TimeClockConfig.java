package net.evarius.terranexus.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TimeClockConfig {
    public String _description = "Stempeluhr, Dienstzeithistorie und erweiterbare Besetzungsschwellen.";
    public boolean enabled = true;
    public boolean showDutyActionbar = true;
    public boolean clockOutOnDisconnect = false;
    public int statusRefreshTicks = 40;
    public int maximumSessionsPerEmployee = 128;
    public Map<String, TimeClockRuleConfig> rules = defaultRules();

    void validate() {
        statusRefreshTicks = ConfigManager.clamp(statusRefreshTicks, 20, 1_200);
        maximumSessionsPerEmployee = ConfigManager.clamp(maximumSessionsPerEmployee, 1, 2_048);
        LinkedHashMap<String, TimeClockRuleConfig> validated = new LinkedHashMap<>();
        if (rules != null) rules.forEach((id, rule) -> {
            String cleanId = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]", "_");
            if (cleanId.isBlank() || cleanId.length() > 64 || rule == null || validated.size() >= 64) return;
            rule.validate();
            validated.putIfAbsent(cleanId, rule);
        });
        if (validated.isEmpty()) validated.putAll(defaultRules());
        rules = validated;
    }

    private static Map<String, TimeClockRuleConfig> defaultRules() {
        LinkedHashMap<String, TimeClockRuleConfig> result = new LinkedHashMap<>();
        result.put("minimum_staffing", new TimeClockRuleConfig(
                "Allgemeine Mindestbesetzung", "Warnt, wenn weniger Mitarbeiter als benötigt im Dienst sind.",
                List.of(), 1, "AT_LEAST"));
        result.put("fire_department_staffing", new TimeClockRuleConfig(
                "Feuerwehr-Einsatzbereitschaft", "Kann von Feuer- und Simulationsmechaniken als Besetzungsregel abgefragt werden.",
                List.of("Feuerwehr", "Fire Department"), 3, "AT_LEAST"));
        result.put("fire_spread_staffing", new TimeClockRuleConfig(
                "Feuerausbreitung nach Personalstärke",
                "Erweiterbare Gameplay-Bedingung: aktiv, wenn mehr als X Feuerwehrleute im Dienst sind.",
                List.of("Feuerwehr", "Fire Department"), 3, "MORE_THAN", false));
        return result;
    }
}
