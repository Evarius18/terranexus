package net.evarius.terranexus.client.gui;

import java.util.Locale;

public enum GuiPageType {
    ADMIN_DESKTOP,
    BANK_ACCOUNTS,
    AUDIT_LOG,
    CENTRAL_BANK,
    LAND_TOOL,
    LIST,
    DETAIL,
    HUB;

    public static GuiPageType detect(String title) {
        if (AdminDesktopLayout.applies(title)) return ADMIN_DESKTOP;
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (normalized.contains("bank") && normalized.contains("kontenübersicht")) return BANK_ACCOUNTS;
        if (normalized.equals("audit-log") || normalized.contains("grundstücksprotokoll")) return AUDIT_LOG;
        if (normalized.contains("zentralbank") && normalized.contains("übersicht")) return CENTRAL_BANK;
        if (normalized.contains("terranexus grundstücke")) return LAND_TOOL;
        if (containsAny(normalized, "übersicht", "verlauf", "journal", "protokoll", "suche", "liste", "markt",
                "eigentum", "mietverträge", "buchungen", "mitarbeiter", "bürger", "konto wählen"))
            return LIST;
        if (containsAny(normalized, "akte", "details", "bearbeiten", "einstellungen", "bestätigen", "verwaltung"))
            return DETAIL;
        return HUB;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
