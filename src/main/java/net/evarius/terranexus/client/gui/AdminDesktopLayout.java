package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminDesktopLayout {
    public static final int DESIGN_WIDTH = 660;
    public static final int DESIGN_HEIGHT = 428;
    public static final int SCREEN_MARGIN = 12;
    public static final int CONTENT_PADDING = 38;
    public static final int TITLE_X = 24;
    public static final int TITLE_Y = 20;
    public static final int STATUS_X = 24;
    public static final int STATUS_BOTTOM = 18;
    public static final int TOP_BUTTON_Y = 70;
    public static final int MAIN_ROW_Y = 174;
    public static final int BUTTON_WIDTH = 132;
    public static final int BUTTON_HEIGHT = 58;
    public static final int ROW_GAP = 20;
    public static final int SECONDARY_ROW_Y = MAIN_ROW_Y + BUTTON_HEIGHT + ROW_GAP;
    public static final int TOP_BUTTON_WIDTH = 140;
    public static final int COLUMN_GAP = 18;
    public static final int CLOSE_SIZE = 24;
    public static final float MAX_SCALE = 2.0F;

    private static final int MIN_BUTTON_WIDTH = 28;
    private static final int MIN_BUTTON_HEIGHT = 26;
    private static final int MIN_COLUMN_GAP = 2;
    private static final int MIN_CONTENT_PADDING = 6;
    private static final int CLOSE_INSET = 12;
    private static final int MIN_CLOSE_SIZE = 18;
    private static final int STATUS_TEXT_HEIGHT = 9;
    private static final int STATUS_BOTTOM_PADDING = 2;
    private static final int RESPONSIVE_PADDING_DIVISOR = 30;
    private static final int RESPONSIVE_GAP_DIVISOR = 36;

    private static final List<Integer> MAIN_ROW = List.of(19, 21, 23, 25);
    private static final List<Integer> SECONDARY_ROW = List.of(31, 33, 35);

    private final GuiBounds panel;
    private final GuiBounds close;
    private final Map<Integer, GuiBounds> elementBounds;
    private final float scale;

    private AdminDesktopLayout(GuiBounds panel, GuiBounds close,
                               Map<Integer, GuiBounds> elementBounds, float scale) {
        this.panel = panel;
        this.close = close;
        this.elementBounds = Map.copyOf(elementBounds);
        this.scale = scale;
    }

    public static boolean applies(String title) {
        return title != null && title.endsWith("Admin-Desktop");
    }

    public static AdminDesktopLayout calculate(int screenWidth, int screenHeight,
                                               List<GuiMenuElement> elements) {
        return calculateForIds(screenWidth, screenHeight,
                elements.stream().map(GuiMenuElement::id).collect(Collectors.toSet()));
    }

    public static AdminDesktopLayout calculateForIds(int screenWidth, int screenHeight, Set<Integer> elementIds) {
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - SCREEN_MARGIN * 2);
        float scale = Math.min(MAX_SCALE, Math.min(
                availableWidth / (float) DESIGN_WIDTH,
                availableHeight / (float) DESIGN_HEIGHT));
        int panelWidth = Math.max(1, Math.round(DESIGN_WIDTH * scale));
        int panelHeight = Math.max(1, Math.round(DESIGN_HEIGHT * scale));
        GuiBounds panel = new GuiBounds((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2,
                panelWidth, panelHeight);

        int buttonHeight = Math.max(MIN_BUTTON_HEIGHT, scaled(BUTTON_HEIGHT, scale));
        int gap = Math.max(MIN_COLUMN_GAP,
                Math.min(scaled(COLUMN_GAP, scale), panelWidth / RESPONSIVE_GAP_DIVISOR));
        int padding = Math.max(MIN_CONTENT_PADDING,
                Math.min(scaled(CONTENT_PADDING, scale), panelWidth / RESPONSIVE_PADDING_DIVISOR));
        int contentWidth = panelWidth - padding * 2;
        int buttonWidth = Math.min(scaled(BUTTON_WIDTH, scale),
                Math.max(MIN_BUTTON_WIDTH, (contentWidth - gap * 3) / 4));
        Map<Integer, GuiBounds> bounds = new HashMap<>();

        if (elementIds.contains(4)) {
            int width = Math.max(buttonWidth, scaled(TOP_BUTTON_WIDTH, scale));
            bounds.put(4, new GuiBounds(panel.x() + (panel.width() - width) / 2,
                    panel.y() + scaled(TOP_BUTTON_Y, scale), width, buttonHeight));
        }

        List<Integer> main = MAIN_ROW.stream().filter(elementIds::contains).toList();
        placeCenteredRow(bounds, main, panel, buttonWidth, buttonHeight, gap,
                panel.y() + scaled(MAIN_ROW_Y, scale));

        List<Integer> secondary = SECONDARY_ROW.stream().filter(elementIds::contains).toList();
        placeCenteredRow(bounds, secondary, panel, buttonWidth, buttonHeight, gap,
                panel.y() + scaled(SECONDARY_ROW_Y, scale));

        int closeSize = Math.max(MIN_CLOSE_SIZE, scaled(CLOSE_SIZE, scale));
        GuiBounds close = new GuiBounds(panel.right() - closeSize - scaled(CLOSE_INSET, scale),
                panel.y() + scaled(CLOSE_INSET, scale), closeSize, closeSize);
        return new AdminDesktopLayout(panel, close, bounds, scale);
    }

    public GuiBounds panel() { return panel; }
    public GuiBounds close() { return close; }
    public GuiBounds element(int id) { return elementBounds.get(id); }
    public int titleX() { return panel.x() + scaled(TITLE_X, scale); }
    public int titleY() { return panel.y() + scaled(TITLE_Y, scale); }
    public int statusX() { return panel.x() + scaled(STATUS_X, scale); }
    public int statusY() {
        // The font itself is not scaled, so keep its line inside very small panels.
        return Math.min(panel.bottom() - STATUS_TEXT_HEIGHT - STATUS_BOTTOM_PADDING,
                panel.bottom() - scaled(STATUS_BOTTOM, scale));
    }

    public static Text displayLabel(GuiMenuElement element) {
        String key = switch (element.id()) {
            case 4 -> "gui.terranexus.admin.terra";
            case 19 -> "gui.terranexus.admin.citizens";
            case 21 -> "gui.terranexus.admin.banks";
            case 23 -> "gui.terranexus.admin.institutions";
            case 25 -> "gui.terranexus.admin.properties";
            case 31 -> "gui.terranexus.admin.audit";
            case 33 -> "gui.terranexus.admin.central_bank";
            case 35 -> "gui.terranexus.admin.area_finance";
            default -> null;
        };
        return key == null ? Text.literal(element.label()) : Text.translatable(key);
    }

    private static void placeCenteredRow(Map<Integer, GuiBounds> target, List<Integer> ids,
                                         GuiBounds panel, int width, int height, int gap, int y) {
        if (ids.isEmpty()) return;
        int rowWidth = ids.size() * width + (ids.size() - 1) * gap;
        int x = panel.x() + (panel.width() - rowWidth) / 2;
        for (int id : ids) {
            target.put(id, new GuiBounds(x, y, width, height));
            x += width + gap;
        }
    }

    private static int scaled(int value, float scale) {
        return Math.round(value * scale);
    }
}
