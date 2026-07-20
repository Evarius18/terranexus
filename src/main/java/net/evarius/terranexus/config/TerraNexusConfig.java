package net.evarius.terranexus.config;

/** Compatibility facade for the original single-file configuration API. */
@Deprecated(forRemoval = false)
public final class TerraNexusConfig {
    private static TerraNexusConfig INSTANCE = new TerraNexusConfig();

    public String currencyName = "TerraNexus Euro";
    public String currencySymbol = "TN€";
    public int currencyDecimals = 2;
    public int rentDayDurationMinutes = 1440;
    public int salaryPeriodMinutes = 10080;

    public static TerraNexusConfig get() {
        EconomyConfig economy = ConfigManager.economy();
        INSTANCE.currencyName = economy.currencyName;
        INSTANCE.currencySymbol = economy.currencySymbol;
        INSTANCE.currencyDecimals = economy.currencyDecimals;
        INSTANCE.rentDayDurationMinutes = ConfigManager.claims().rentDayDurationMinutes;
        INSTANCE.salaryPeriodMinutes = ConfigManager.salary().paymentIntervalMinutes;
        return INSTANCE;
    }

    public static void load() {
        ConfigManager.load();
        get();
    }
}
