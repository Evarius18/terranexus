package net.evarius.terranexus.client.gui;

import java.util.Locale;

public enum GuiPageType {
    ADMIN_DESKTOP,
    BANK_ACCOUNTS,
    AUDIT_LOG,
    CENTRAL_BANK,
    LAND_TOOL,
    DOCUMENT_LIST,
    DOCUMENT_DETAIL,
    IDENTITY_CARD,
    SHOP_DIALOG,
    LIST,
    DETAIL,
    HUB;

    public static GuiPageType detect(String title) {
        if (AdminDesktopLayout.applies(title)) return ADMIN_DESKTOP;
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (normalized.contains("bank") && (normalized.contains("kontenübersicht")
                || normalized.contains("account overview"))) return BANK_ACCOUNTS;
        if (normalized.equals("audit-log") || normalized.contains("grundstücksprotokoll")
                || normalized.contains("property audit")) return AUDIT_LOG;
        if ((normalized.contains("zentralbank") || normalized.contains("central bank"))
                && (normalized.contains("übersicht") || normalized.contains("overview"))) return CENTRAL_BANK;
        if (normalized.contains("terranexus grundstücke") || normalized.contains("terranexus properties")) return LAND_TOOL;
        if (normalized.contains("grundbuchauszug") || normalized.contains("land registry extract")) return DOCUMENT_LIST;
        if (normalized.contains("grundstücksakte") || normalized.contains("property file")) return DOCUMENT_DETAIL;
        if (normalized.contains("bürgerinformation") || normalized.contains("citizen information")) return IDENTITY_CARD;
        if (normalized.equals("terranexus shop")) return SHOP_DIALOG;
        if (containsAny(normalized, "übersicht", "verlauf", "journal", "protokoll", "suche", "liste", "markt",
                "eigentum", "mietverträge", "buchungen", "mitarbeiter", "bürger", "konto wählen",
                "übertragung", "umschreibung", "overview", "history", "journal", "audit", "search", "list",
                "market", "ownership", "leases", "transactions", "employees", "citizens", "transfer"))
            return LIST;
        if (containsAny(normalized, "akte", "details", "bearbeiten", "einstellungen", "bestätigen", "verwaltung",
                "file", "edit", "settings", "confirm", "administration", "management"))
            return DETAIL;
        return HUB;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
