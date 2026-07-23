package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GuiPageModel {
    private static final Set<Integer> CENTRAL_METRICS = Set.of(11, 13, 15, 20, 22, 24, 26);
    private static final Set<Integer> CENTRAL_ACTIONS = Set.of(29, 31, 33, 35);
    private static final Set<Integer> LAND_TOOLBAR = Set.of(6, 7, 8, 10, 12, 13, 14, 15, 16, 17);
    private static final Set<Integer> LAND_CHUNKS = Set.of(20, 21, 22, 29, 30, 31, 38, 39, 40);

    private final GuiPageType type;
    private final Map<GuiElementRole, List<GuiMenuElement>> elements;

    private GuiPageModel(GuiPageType type, Map<GuiElementRole, List<GuiMenuElement>> elements) {
        this.type = type;
        EnumMap<GuiElementRole, List<GuiMenuElement>> immutable = new EnumMap<>(GuiElementRole.class);
        elements.forEach((role, values) -> immutable.put(role, List.copyOf(values)));
        this.elements = Map.copyOf(immutable);
    }

    public static GuiPageModel create(String title, List<GuiMenuElement> source) {
        GuiPageType type = GuiPageType.detect(title);
        EnumMap<GuiElementRole, List<GuiMenuElement>> grouped = new EnumMap<>(GuiElementRole.class);
        for (GuiMenuElement element : source) {
            GuiElementRole role = classify(type, element);
            grouped.computeIfAbsent(role, ignored -> new ArrayList<>()).add(element);
        }
        return new GuiPageModel(type, grouped);
    }

    public GuiPageType type() { return type; }
    public List<GuiMenuElement> elements(GuiElementRole role) {
        return elements.getOrDefault(role, List.of());
    }
    public GuiMenuElement header() {
        List<GuiMenuElement> headers = elements(GuiElementRole.HEADER);
        return headers.isEmpty() ? null : headers.getFirst();
    }

    private static GuiElementRole classify(GuiPageType type, GuiMenuElement element) {
        if (element.id() == 4 && !element.enabled()) return GuiElementRole.HEADER;
        if (isNavigation(element)) return GuiElementRole.NAVIGATION;
        return switch (type) {
            case CENTRAL_BANK -> CENTRAL_METRICS.contains(element.id()) ? GuiElementRole.METRIC
                    : CENTRAL_ACTIONS.contains(element.id()) ? GuiElementRole.ACTION : GuiElementRole.DETAIL_INFO;
            case LAND_TOOL -> LAND_TOOLBAR.contains(element.id()) ? GuiElementRole.TOOLBAR
                    : LAND_CHUNKS.contains(element.id()) ? GuiElementRole.GRID_CELL
                    : element.id() >= 45 ? GuiElementRole.LIST_ROW : GuiElementRole.DETAIL_INFO;
            case BANK_ACCOUNTS, AUDIT_LOG, LIST -> element.id() < 9
                    ? GuiElementRole.TOOLBAR : GuiElementRole.LIST_ROW;
            case DETAIL -> element.enabled() ? GuiElementRole.ACTION : GuiElementRole.DETAIL_INFO;
            case HUB, ADMIN_DESKTOP -> element.enabled() ? GuiElementRole.MODULE : GuiElementRole.DETAIL_INFO;
        };
    }

    private static boolean isNavigation(GuiMenuElement element) {
        String label = element.label().toLowerCase(Locale.ROOT);
        return label.startsWith("zurück") || label.startsWith("vorherige seite")
                || label.startsWith("nächste seite") || element.id() == 49 || element.id() == 53;
    }
}
