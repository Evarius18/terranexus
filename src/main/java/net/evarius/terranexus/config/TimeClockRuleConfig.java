package net.evarius.terranexus.config;

import java.util.ArrayList;
import java.util.List;

public final class TimeClockRuleConfig {
    public String label = "Mindestbesetzung";
    public String description = "Statusregel auf Basis der aktuell eingestempelten Mitarbeiter.";
    public List<String> institutionTypeKeywords = new ArrayList<>();
    public int defaultThreshold = 1;
    public String comparison = "AT_LEAST";
    public boolean warnWhenUnsatisfied = true;

    public TimeClockRuleConfig() {}

    public TimeClockRuleConfig(String label, String description, List<String> institutionTypeKeywords,
                               int defaultThreshold, String comparison) {
        this(label, description, institutionTypeKeywords, defaultThreshold, comparison, true);
    }

    public TimeClockRuleConfig(String label, String description, List<String> institutionTypeKeywords,
                               int defaultThreshold, String comparison, boolean warnWhenUnsatisfied) {
        this.label = label;
        this.description = description;
        this.institutionTypeKeywords = new ArrayList<>(institutionTypeKeywords);
        this.defaultThreshold = defaultThreshold;
        this.comparison = comparison;
        this.warnWhenUnsatisfied = warnWhenUnsatisfied;
    }

    void validate() {
        label = ConfigManager.text(label, "Mindestbesetzung", 64);
        description = ConfigManager.text(description, "Dienststatusregel", 160);
        institutionTypeKeywords = ConfigManager.uniqueText(institutionTypeKeywords, 16, 64);
        defaultThreshold = ConfigManager.clamp(defaultThreshold, 0, 10_000);
        comparison = comparison == null ? "AT_LEAST" : comparison.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("AT_LEAST", "MORE_THAN", "AT_MOST", "LESS_THAN").contains(comparison)) comparison = "AT_LEAST";
    }
}
