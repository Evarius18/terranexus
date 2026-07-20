package net.evarius.terranexus.config;

public final class LoggingConfig {
    public String _description = "Diagnoseausgaben. Persistente Audit- und Buchhaltungsdaten werden aus Integritätsgründen nie deaktiviert.";
    public boolean debugMode = false;
    public boolean logConfigLoading = true;
    public boolean logTransactions = false;
    public boolean logDeniedActions = false;
    public int maximumLandAuditEntries = 2_000;

    void validate() {
        maximumLandAuditEntries = ConfigManager.clamp(maximumLandAuditEntries, 100, 100_000);
    }
}
