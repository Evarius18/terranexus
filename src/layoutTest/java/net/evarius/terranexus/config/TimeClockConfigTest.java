package net.evarius.terranexus.config;

public final class TimeClockConfigTest {
    private TimeClockConfigTest() {}

    public static void run() {
        TimeClockConfig config = new TimeClockConfig();
        require(config.rules.containsKey("fire"), "Feuerwehrregel fehlt");
        require(config.rules.containsKey("medical"), "Rettungsdienstregel fehlt");
        require(config.rules.containsKey("police"), "Polizeiregel fehlt");
        require(config.rules.get("fire").enabled
                        && "LESS_THAN".equals(config.rules.get("fire").comparison)
                        && config.rules.get("fire").defaultThreshold == 3,
                "Feuerausbreitungsregel besitzt falsche Standardsemantik");
        require(config.rules.get("medical").defaultThreshold == 2,
                "Rettungsdienst-Schwellenwert ist falsch");
        require(config.rules.get("police").defaultThreshold == 4,
                "Polizei-Schwellenwert ist falsch");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
