package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public final class GuiScrollableList {
    private List<GuiMenuElement> elements = List.of();
    private GuiBounds viewport = new GuiBounds(0, 0, 1, 1);
    private int rowHeight = 18;
    private int offset;

    public void configure(List<GuiMenuElement> elements, GuiBounds viewport, int rowHeight) {
        this.elements = List.copyOf(elements);
        this.viewport = viewport;
        this.rowHeight = Math.max(1, rowHeight);
        offset = Math.min(offset, maximumOffset());
    }

    public List<VisibleRow> visibleRows() {
        int count = Math.max(1, viewport.height() / rowHeight);
        int end = Math.min(elements.size(), offset + count);
        return java.util.stream.IntStream.range(offset, end)
                .mapToObj(index -> new VisibleRow(elements.get(index), index,
                        new GuiBounds(viewport.x(), viewport.y() + (index - offset) * rowHeight,
                                viewport.width(), rowHeight - 1)))
                .toList();
    }

    public boolean scroll(double mouseX, double mouseY, double verticalAmount) {
        if (!viewport.contains(mouseX, mouseY) || maximumOffset() == 0 || verticalAmount == 0) return false;
        int previous = offset;
        offset = Math.max(0, Math.min(maximumOffset(), offset + (verticalAmount < 0 ? 1 : -1)));
        return previous != offset;
    }

    public void renderScrollbar(DrawContext context) {
        if (maximumOffset() == 0) return;
        int trackX = viewport.right() - 3;
        context.fill(trackX, viewport.y(), trackX + 2, viewport.bottom(), 0x80233B49);
        int visible = Math.max(1, viewport.height() / rowHeight);
        int thumbHeight = Math.max(8, viewport.height() * visible / elements.size());
        int travel = viewport.height() - thumbHeight;
        int thumbY = viewport.y() + travel * offset / maximumOffset();
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF39C5D8);
    }

    public GuiBounds viewport() { return viewport; }
    public int offset() { return offset; }
    public void reset() { offset = 0; }
    public void hide() {
        elements = List.of();
        viewport = new GuiBounds(0, 0, 1, 1);
    }

    private int maximumOffset() {
        int visible = Math.max(1, viewport.height() / rowHeight);
        return Math.max(0, elements.size() - visible);
    }

    public record VisibleRow(GuiMenuElement element, int index, GuiBounds bounds) { }
}
