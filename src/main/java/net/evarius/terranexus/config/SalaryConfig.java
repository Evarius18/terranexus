package net.evarius.terranexus.config;

public final class SalaryConfig {
    public String _description = "Automatische Gehaltsläufe und Gehaltsgrenzen.";
    public boolean automaticPaymentsEnabled = true;
    public int paymentIntervalMinutes = 10_080;
    public long defaultSalary = 0;
    public long maximumSalary = 100_000_000L;
    public boolean notifyEmployees = true;
    public boolean notifyResponsibleStaffOnFailure = true;

    void validate() {
        paymentIntervalMinutes = ConfigManager.clamp(paymentIntervalMinutes, 1, 525_600);
        maximumSalary = Math.max(0, maximumSalary);
        defaultSalary = ConfigManager.clamp(defaultSalary, 0, maximumSalary);
    }
}
