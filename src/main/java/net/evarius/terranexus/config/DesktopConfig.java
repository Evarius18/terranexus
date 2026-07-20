package net.evarius.terranexus.config;

public final class DesktopConfig {
    public String _description = "Seiten- und Darstellungsgrößen serverseitiger Verwaltungsoberflächen.";
    public int standardEntriesPerPage = 36;
    public int immigrationEntriesPerPage = 15;
    public boolean showAccountNumbers = true;

    void validate() {
        standardEntriesPerPage = ConfigManager.clamp(standardEntriesPerPage, 9, 36);
        immigrationEntriesPerPage = ConfigManager.clamp(immigrationEntriesPerPage, 3, 15);
    }
}
