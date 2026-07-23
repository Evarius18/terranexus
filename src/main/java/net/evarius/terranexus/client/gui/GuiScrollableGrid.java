package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Computes visible card cells and scrolling from one shared set of GUI-coordinate bounds. */
public final class GuiScrollableGrid {
    private List<GuiMenuElement> elements = List.of();
    private GuiBounds viewport = new GuiBounds(0, 0, 1, 1);
    private int columns = 1;
    private int cellWidth = 1;
    private int cellHeight = 20;
    private int gap;
    private int offsetRows;

    public void configure(List<GuiMenuElement> elements, GuiBounds viewport,
                          int minimumCellWidth, int preferredCellHeight, int gap) {
        this.elements = List.copyOf(elements);
        this.viewport = viewport;
        this.gap = Math.max(0, gap);
        columns = Math.max(1, Math.min(Math.max(1, elements.size()),
                (viewport.width() + this.gap) / (Math.max(1, minimumCellWidth) + this.gap)));
        cellWidth = Math.max(1, (viewport.width() - this.gap * (columns - 1)) / columns);
        cellHeight = Math.max(1, Math.min(viewport.height(),
                Math.max(StructuredGuiLayout.MIN_BUTTON_HEIGHT, preferredCellHeight)));
        offsetRows = Math.min(offsetRows, maximumOffsetRows());
    }

    public List<VisibleCell> visibleCells() {
        int firstIndex = offsetRows * columns;
        int visibleRows = visibleRows();
        int end = Math.min(elements.size(), firstIndex + visibleRows * columns);
        List<VisibleCell> result = new ArrayList<>(Math.max(0, end - firstIndex));
        for (int index = firstIndex; index < end; index++) {
            int local = index - firstIndex;
            result.add(new VisibleCell(elements.get(index), index, new GuiBounds(
                    viewport.x() + (local % columns) * (cellWidth + gap),
                    viewport.y() + (local / columns) * (cellHeight + gap), cellWidth, cellHeight)));
        }
        return result;
    }

    public boolean scroll(double mouseX, double mouseY, double verticalAmount) {
        if (!viewport.contains(mouseX, mouseY) || maximumOffsetRows() == 0 || verticalAmount == 0) return false;
        int previous = offsetRows;
        offsetRows = Math.max(0, Math.min(maximumOffsetRows(), offsetRows + (verticalAmount < 0 ? 1 : -1)));
        return previous != offsetRows;
    }

    public void renderScrollbar(DrawContext context) {
        int maximum = maximumOffsetRows();
        if (maximum == 0) return;
        int totalRows = totalRows();
        int trackX = viewport.right() - 3;
        context.fill(trackX, viewport.y(), trackX + 2, viewport.bottom(), 0x80233B49);
        int thumbHeight = Math.max(8, viewport.height() * visibleRows() / Math.max(1, totalRows));
        int travel = Math.max(0, viewport.height() - thumbHeight);
        int thumbY = viewport.y() + travel * offsetRows / maximum;
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF39C5D8);
    }

    public void hide() {
        elements = List.of();
        viewport = new GuiBounds(0, 0, 1, 1);
    }

    public void reset() { offsetRows = 0; }

    public int offsetRows() { return offsetRows; }
    public int columns() { return columns; }

    private int visibleRows() {
        return Math.max(1, (viewport.height() + gap) / (cellHeight + gap));
    }

    private int totalRows() {
        return (elements.size() + columns - 1) / columns;
    }

    private int maximumOffsetRows() {
        return Math.max(0, totalRows() - visibleRows());
    }

    public record VisibleCell(GuiMenuElement element, int index, GuiBounds bounds) { }
}
