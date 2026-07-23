package net.evarius.terranexus.client.gui;

import java.util.List;
import java.util.Set;

public final class AdminDesktopLayoutGeometryTest {
    private static final Set<Integer> STANDARD_ELEMENTS = Set.of(4, 19, 21, 23, 25, 31, 33);
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport("854x480 scale 2", 854, 480, 2),
            new Viewport("1280x720 scale 2", 1280, 720, 2),
            new Viewport("1280x720 scale 3", 1280, 720, 3),
            new Viewport("1280x720 scale 4", 1280, 720, 4),
            new Viewport("1920x1080 scale 2", 1920, 1080, 2),
            new Viewport("1920x1080 scale 3", 1920, 1080, 3),
            new Viewport("1920x1080 scale 4", 1920, 1080, 4),
            new Viewport("854x480 automatic", 854, 480, 2),
            new Viewport("1280x720 automatic", 1280, 720, 3),
            new Viewport("1920x1080 automatic", 1920, 1080, 4),
            new Viewport("2560x1440 maximized scale 4", 2560, 1440, 4)
    );

    private AdminDesktopLayoutGeometryTest() { }

    public static void main(String[] args) {
        for (Viewport viewport : VIEWPORTS) validate(viewport);
        validateOptionalFinanceButton();
        StructuredGuiLayoutGeometryTest.run();
    }

    private static void validate(Viewport viewport) {
        int guiWidth = viewport.physicalWidth() / viewport.guiScale();
        int guiHeight = viewport.physicalHeight() / viewport.guiScale();
        AdminDesktopLayout layout = AdminDesktopLayout.calculateForIds(guiWidth, guiHeight, STANDARD_ELEMENTS);
        GuiBounds panel = layout.panel();

        require(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= guiWidth && panel.bottom() <= guiHeight,
                viewport.name() + ": panel leaves the screen");
        float aspect = panel.width() / (float) panel.height();
        float designAspect = AdminDesktopLayout.DESIGN_WIDTH / (float) AdminDesktopLayout.DESIGN_HEIGHT;
        require(Math.abs(aspect - designAspect) < 0.015F,
                viewport.name() + ": panel aspect ratio changed");
        require(inside(panel, layout.close()), viewport.name() + ": close button leaves the panel");
        require(layout.statusY() + 9 <= panel.bottom(), viewport.name() + ": status line leaves the panel");

        validateRow(viewport.name(), panel, layout, List.of(19, 21, 23, 25));
        validateRow(viewport.name(), panel, layout, List.of(31, 33));
        require(centered(panel, layout.element(4)), viewport.name() + ": Terra button is not centered");
        require(centered(panel, rowBounds(layout, List.of(31, 33))),
                viewport.name() + ": secondary row is not centered");
    }

    private static void validateOptionalFinanceButton() {
        AdminDesktopLayout layout = AdminDesktopLayout.calculateForIds(
                640, 360, Set.of(4, 19, 21, 23, 25, 31, 33, 35));
        validateRow("optional public finance", layout.panel(), layout, List.of(31, 33, 35));
        require(centered(layout.panel(), rowBounds(layout, List.of(31, 33, 35))),
                "optional public finance: secondary row is not centered");
    }

    private static void validateRow(String name, GuiBounds panel, AdminDesktopLayout layout, List<Integer> ids) {
        GuiBounds previous = null;
        for (int id : ids) {
            GuiBounds current = layout.element(id);
            require(current != null, name + ": missing element " + id);
            require(inside(panel, current), name + ": element " + id + " leaves the panel");
            if (previous != null) {
                require(current.width() == previous.width() && current.height() == previous.height(),
                        name + ": row buttons have different sizes");
                require(current.y() == previous.y(), name + ": row buttons are not aligned");
                require(current.x() >= previous.right(), name + ": row buttons overlap");
            }
            previous = current;
        }
    }

    private static GuiBounds rowBounds(AdminDesktopLayout layout, List<Integer> ids) {
        GuiBounds first = layout.element(ids.getFirst());
        GuiBounds last = layout.element(ids.getLast());
        return new GuiBounds(first.x(), first.y(), last.right() - first.x(), first.height());
    }

    private static boolean centered(GuiBounds outer, GuiBounds inner) {
        return Math.abs((inner.x() - outer.x()) - (outer.right() - inner.right())) <= 1;
    }

    private static boolean inside(GuiBounds outer, GuiBounds inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Viewport(String name, int physicalWidth, int physicalHeight, int guiScale) { }
}
