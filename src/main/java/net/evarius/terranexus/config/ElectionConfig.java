package net.evarius.terranexus.config;

public final class ElectionConfig {
    public String _description="Wahlen und Abstimmungen: Laufzeiten, Größenlimits und Aufbewahrung abgeschlossener Vorgänge.";
    public int defaultDurationMinutes=1440;
    public int maximumElections=100;
    public int maximumOptions=32;
    public int completedRetentionDays=365;
    void validate(){defaultDurationMinutes=ConfigManager.clamp(defaultDurationMinutes,5,525600);maximumElections=ConfigManager.clamp(maximumElections,10,1000);maximumOptions=ConfigManager.clamp(maximumOptions,2,100);completedRetentionDays=ConfigManager.clamp(completedRetentionDays,30,3650);}
}
