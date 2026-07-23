package net.evarius.terranexus.client.gui;

import java.util.ArrayList;
import java.util.List;

public final class StructuredGuiLayout {
    public static final int HEADER_HEIGHT = 34;
    public static final int TOOLBAR_HEIGHT = 26;
    public static final int PANEL_PADDING = 24;
    public static final int SECTION_GAP = 8;
    public static final int TILE_WIDTH = 132;
    public static final int TILE_HEIGHT = 58;
    public static final int LIST_ROW_HEIGHT = 28;
    public static final int DETAIL_PANEL_WIDTH = 205;
    public static final int MIN_BUTTON_HEIGHT = 20;
    public static final int MIN_BUTTON_WIDTH = 64;
    public static final int MIN_ICON_SIZE = 10;
    public static final int MIN_LIST_ROW_HEIGHT = 20;
    public static final float MIN_TEXT_SCALE = 0.85F;
    public static final int MIN_CONTENT_PADDING = 8;
    public static final int MIN_HITBOX_SIZE = 20;

    private static final int TOOLBAR_BUTTON_WIDTH = 108;
    private static final int FOOTER_HEIGHT = 30;
    private static final int TITLE_Y = 20;
    private static final int CLOSE_SIZE = 24;
    private static final int MIN_TOOLBAR_WIDTH = 64;

    private final GuiBounds panel;
    private final GuiBounds close;
    private final float scale;
    private final int padding;
    private final int gap;
    private final LayoutMode mode;

    private StructuredGuiLayout(GuiBounds panel, GuiBounds close, float scale, int padding, int gap) {
        this.panel = panel;
        this.close = close;
        this.scale = scale;
        this.padding = padding;
        this.gap = gap;
        this.mode = LayoutMode.detect(panel.width(), panel.height());
    }

    public static StructuredGuiLayout calculate(int screenWidth, int screenHeight) {
        LayoutMode mode = LayoutMode.detect(screenWidth, screenHeight);
        int margin = mode == LayoutMode.LARGE ? AdminDesktopLayout.SCREEN_MARGIN
                : mode == LayoutMode.MEDIUM ? 8 : 4;
        int availableWidth = Math.max(1, screenWidth - margin * 2);
        int availableHeight = Math.max(1, screenHeight - margin * 2);
        float calculatedScale = Math.min(AdminDesktopLayout.MAX_SCALE, Math.min(
                availableWidth / (float) AdminDesktopLayout.DESIGN_WIDTH,
                availableHeight / (float) AdminDesktopLayout.DESIGN_HEIGHT));
        float scale = mode == LayoutMode.LARGE ? calculatedScale : 1.0F;
        int width = mode == LayoutMode.LARGE ? Math.max(1, Math.round(AdminDesktopLayout.DESIGN_WIDTH * scale))
                : availableWidth;
        int height = mode == LayoutMode.LARGE ? Math.max(1, Math.round(AdminDesktopLayout.DESIGN_HEIGHT * scale))
                : availableHeight;
        GuiBounds panel = new GuiBounds((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        int padding = mode == LayoutMode.LARGE ? Math.max(MIN_CONTENT_PADDING, Math.round(PANEL_PADDING * scale))
                : mode == LayoutMode.MEDIUM ? 14 : MIN_CONTENT_PADDING;
        int gap = mode == LayoutMode.LARGE ? Math.max(4, Math.round(SECTION_GAP * scale)) : 4;
        int closeSize = Math.max(MIN_HITBOX_SIZE, Math.round(CLOSE_SIZE * scale));
        int closeInset = mode == LayoutMode.COMPACT ? 4 : Math.max(5, Math.round(12 * scale));
        GuiBounds close = new GuiBounds(panel.right() - closeSize - closeInset,
                panel.y() + closeInset, closeSize, closeSize);
        return new StructuredGuiLayout(panel, close, scale, padding, gap);
    }

    public GuiBounds panel() { return panel; }
    public GuiBounds close() { return close; }
    public float scale() { return scale; }
    public LayoutMode mode() { return mode; }
    public int gap() { return gap; }
    public int titleX() { return panel.x() + padding; }
    public int titleY() { return panel.y() + (mode == LayoutMode.LARGE ? scaled(TITLE_Y)
            : mode == LayoutMode.MEDIUM ? 12 : 8); }
    public int statusX() { return panel.x() + padding; }
    public int statusY() { return Math.min(panel.bottom() - 11, panel.bottom() - scaled(18)); }

    public GuiBounds summary() {
        int y = panel.y() + (mode == LayoutMode.LARGE ? scaled(42) : mode == LayoutMode.MEDIUM ? 30 : 18);
        int height = mode == LayoutMode.LARGE ? Math.max(24, scaled(HEADER_HEIGHT))
                : mode == LayoutMode.MEDIUM ? 24 : 0;
        return new GuiBounds(panel.x() + padding, y, panel.width() - padding * 2,
                height);
    }

    public List<GuiBounds> toolbar(int count) {
        if (count <= 0) return List.of();
        GuiBounds summary = summary();
        int preferredWidth = mode == LayoutMode.LARGE ? scaled(TOOLBAR_BUTTON_WIDTH)
                : mode == LayoutMode.MEDIUM ? 92 : 72;
        int width = Math.max(MIN_TOOLBAR_WIDTH, preferredWidth);
        int height = mode == LayoutMode.LARGE ? Math.max(MIN_BUTTON_HEIGHT, scaled(TOOLBAR_HEIGHT))
                : mode == LayoutMode.MEDIUM ? 22 : MIN_BUTTON_HEIGHT;
        int available = panel.width() - padding * 2;
        int columns = Math.max(1, Math.min(count, (available + gap) / (width + gap)));
        width = Math.min(width, Math.max(MIN_TOOLBAR_WIDTH, (available - gap * (columns - 1)) / columns));
        int y = summary.bottom() + gap;
        List<GuiBounds> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int inRow = index % columns;
            int itemsInRow = Math.min(columns, count - row * columns);
            int rowWidth = itemsInRow * width + (itemsInRow - 1) * gap;
            int x = panel.x() + (panel.width() - rowWidth) / 2 + inRow * (width + gap);
            result.add(new GuiBounds(x, y + row * (height + gap), width, height));
        }
        return result;
    }

    public int contentTop(int toolbarCount) {
        List<GuiBounds> buttons = toolbar(toolbarCount);
        return buttons.isEmpty() ? summary().bottom() + gap : buttons.getLast().bottom() + gap;
    }

    public GuiBounds content(int toolbarCount) {
        int top = contentTop(toolbarCount);
        int footer = mode == LayoutMode.LARGE ? Math.max(18, scaled(FOOTER_HEIGHT))
                : mode == LayoutMode.MEDIUM ? 22 : 14;
        int bottom = panel.bottom() - footer;
        return new GuiBounds(panel.x() + padding, top, panel.width() - padding * 2,
                Math.max(1, bottom - top));
    }

    public ContentSplit splitContent(int toolbarCount, boolean detailRequested) {
        GuiBounds content = content(toolbarCount);
        if (!detailRequested) return new ContentSplit(content, null, false);
        if (content.height() < 64) return new ContentSplit(content, null, false);
        if (mode == LayoutMode.LARGE && content.width() >= scaled(520) && content.height() >= scaled(190)) {
            int detailWidth = Math.min(scaled(DETAIL_PANEL_WIDTH), content.width() / 2);
            GuiBounds list = new GuiBounds(content.x(), content.y(),
                    content.width() - detailWidth - gap, content.height());
            GuiBounds detail = new GuiBounds(list.right() + gap, content.y(), detailWidth, content.height());
            return new ContentSplit(list, detail, false);
        }
        int detailHeight = Math.max(30, Math.min(content.height() / 2,
                mode == LayoutMode.COMPACT ? 48 : scaled(82)));
        GuiBounds list = new GuiBounds(content.x(), content.y(), content.width(),
                Math.max(1, content.height() - detailHeight - gap));
        GuiBounds detail = new GuiBounds(content.x(), list.bottom() + gap, content.width(), detailHeight);
        return new ContentSplit(list, detail, true);
    }

    public int listRowHeight() { return mode == LayoutMode.LARGE
            ? Math.max(MIN_LIST_ROW_HEIGHT, scaled(LIST_ROW_HEIGHT))
            : mode == LayoutMode.MEDIUM ? 24 : MIN_LIST_ROW_HEIGHT; }
    public int minimumCardWidth() { return mode == LayoutMode.LARGE ? Math.max(96, scaled(112))
            : mode == LayoutMode.MEDIUM ? 96 : 84; }
    public int minimumCardHeight() { return mode == LayoutMode.LARGE ? Math.max(36, scaled(48))
            : mode == LayoutMode.MEDIUM ? 40 : 36; }
    public int scaled(int value) { return Math.round(value * scale); }

    public record ContentSplit(GuiBounds primary, GuiBounds detail, boolean stacked) { }
}
