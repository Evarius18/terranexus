package net.evarius.terranexus.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SalaryConfig {
    public String _description = "Automatische Gehaltsläufe und Gehaltsgrenzen.";
    public boolean automaticPaymentsEnabled = true;
    public int paymentIntervalMinutes = 10_080;
    public long defaultSalary = 0;
    public long maximumSalary = 100_000_000L;
    public boolean notifyEmployees = true;
    public boolean notifyResponsibleStaffOnFailure = true;
    public int maximumAdministrationEmployees = 500;
    public Map<String, Long> administrationSalaryGroups = new LinkedHashMap<>(Map.of(
            "Angestellte", 0L, "Sachbearbeitung", 0L, "Leitung", 0L));

    void validate() {
        paymentIntervalMinutes = ConfigManager.clamp(paymentIntervalMinutes, 1, 525_600);
        maximumSalary = Math.max(0, maximumSalary);
        defaultSalary = ConfigManager.clamp(defaultSalary, 0, maximumSalary);
        maximumAdministrationEmployees = ConfigManager.clamp(maximumAdministrationEmployees, 1, 10_000);
        LinkedHashMap<String, Long> groups = new LinkedHashMap<>();
        if (administrationSalaryGroups != null) administrationSalaryGroups.forEach((name, salary) -> {
            String clean = ConfigManager.text(name, "", 48);
            if (!clean.isBlank() && groups.size() < 32)
                groups.putIfAbsent(clean, ConfigManager.clamp(salary == null ? 0 : salary, 0, maximumSalary));
        });
        if (groups.isEmpty()) groups.put("Angestellte", defaultSalary);
        administrationSalaryGroups = groups;
    }
}
