package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;

import java.util.ArrayList;
import java.util.List;

final class StructuredGuiLayoutGeometryTest {
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport(640, 360, 2),
            new Viewport(640, 360, 3),
            new Viewport(640, 360, 4),
            new Viewport(854, 480, 2),
            new Viewport(854, 480, 3),
            new Viewport(854, 480, 4),
            new Viewport(1280, 720, 2),
            new Viewport(1280, 720, 3),
            new Viewport(1280, 720, 4),
            new Viewport(1920, 1080, 2),
            new Viewport(1920, 1080, 3),
            new Viewport(1920, 1080, 4),
            new Viewport(2560, 1440, 4));

    private StructuredGuiLayoutGeometryTest() { }

    static void run() {
        for (Viewport viewport : VIEWPORTS) validateGeometry(viewport);
        validateProfiles();
        validateScrolling();
    }

    private static void validateGeometry(Viewport viewport) {
        int width = viewport.width / viewport.scale;
        int height = viewport.height / viewport.scale;
        StructuredGuiLayout layout = StructuredGuiLayout.calculate(width, height);
        require(inside(new GuiBounds(0, 0, width, height), layout.panel()), "panel outside viewport");
        require(inside(layout.panel(), layout.summary()), "summary outside panel");
        require(inside(layout.panel(), layout.close()), "close outside panel");
        int toolbarItems = layout.mode() == LayoutMode.COMPACT ? 2
                : layout.mode() == LayoutMode.MEDIUM ? 6 : 10;
        List<GuiBounds> toolbar = layout.toolbar(toolbarItems);
        for (int index = 0; index < toolbar.size(); index++) {
            require(inside(layout.panel(), toolbar.get(index)), "toolbar outside panel");
            for (int other = index + 1; other < toolbar.size(); other++)
                require(!overlap(toolbar.get(index), toolbar.get(other)), "toolbar overlap");
        }
        StructuredGuiLayout.ContentSplit split = layout.splitContent(toolbarItems, true);
        require(inside(layout.panel(), split.primary()), "primary content outside panel");
        if (split.detail() != null) {
            require(inside(layout.panel(), split.detail()), "detail outside panel");
            require(!overlap(split.primary(), split.detail()), "content and detail overlap");
        }
    }

    private static void validateProfiles() {
        List<GuiMenuElement> bank = List.of(element(4, false), element(1, true),
                new GuiMenuElement(8, "BACK", "Zurück", "", true, false), element(9, true));
        GuiPageModel bankModel = GuiPageModel.create("Bank · Kontenübersicht", bank);
        require(bankModel.type() == GuiPageType.BANK_ACCOUNTS, "bank page profile not detected");
        require(bankModel.elements(GuiElementRole.LIST_ROW).size() == 1, "bank row not classified");
        require(bankModel.elements(GuiElementRole.NAVIGATION).size() == 1, "bank navigation not classified");

        List<GuiMenuElement> land = List.of(element(4, false), element(10, true), element(20, false), element(45, true));
        GuiPageModel landModel = GuiPageModel.create("TerraNexus Grundstücke", land);
        require(landModel.type() == GuiPageType.LAND_TOOL, "land page profile not detected");
        require(landModel.elements(GuiElementRole.GRID_CELL).size() == 1, "chunk not classified");
        require(landModel.elements(GuiElementRole.LIST_ROW).size() == 1, "property not classified");

        GuiPageModel audit = GuiPageModel.create("Audit-Log", List.of(element(4, false), element(1, true), element(9, false)));
        require(audit.type() == GuiPageType.AUDIT_LOG, "audit page profile not detected");
        require(audit.elements(GuiElementRole.TOOLBAR).size() == 1, "audit filter not classified");
    }

    private static void validateScrolling() {
        List<GuiMenuElement> elements = new ArrayList<>();
        for (int id = 0; id < 36; id++) elements.add(element(id, true));
        GuiScrollableList list = new GuiScrollableList();
        list.configure(elements, new GuiBounds(10, 10, 200, 100), 20);
        require(list.visibleRows().size() == 5, "visible row count incorrect");
        require(list.scroll(20, 20, -1), "scroll down ignored");
        require(list.offset() == 1, "scroll offset incorrect");
        require(!list.scroll(0, 0, -1), "scroll accepted outside hitbox");

        GuiScrollableList shortList = new GuiScrollableList();
        shortList.configure(elements.subList(0, 2), new GuiBounds(10, 10, 200, 100), 20);
        require(shortList.visibleRows().size() == 2, "short list lost entries");
        require(!shortList.scroll(20, 20, -1), "short list scrolled without overflow");
        require(new GuiBounds(10, 10, 20, 20).contains(10, 10), "hitbox excludes top-left edge");
        require(!new GuiBounds(10, 10, 20, 20).contains(30, 30), "hitbox includes bottom-right edge");

        GuiScrollableGrid grid = new GuiScrollableGrid();
        grid.configure(elements, new GuiBounds(10, 10, 210, 70), 64, 24, 4);
        require(grid.columns() == 3, "responsive grid column count incorrect");
        require(grid.visibleCells().size() == 6, "responsive grid visible cell count incorrect");
        require(grid.scroll(20, 20, -1), "grid scroll down ignored");
        require(grid.offsetRows() == 1, "grid scroll offset incorrect");
    }

    private static GuiMenuElement element(int id, boolean enabled) {
        return new GuiMenuElement(id, "DOCUMENT", "Element " + id, "Detail " + id, enabled, false);
    }

    private static boolean inside(GuiBounds outer, GuiBounds inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static boolean overlap(GuiBounds left, GuiBounds right) {
        return left.x() < right.right() && left.right() > right.x()
                && left.y() < right.bottom() && left.bottom() > right.y();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Viewport(int width, int height, int scale) { }
}
