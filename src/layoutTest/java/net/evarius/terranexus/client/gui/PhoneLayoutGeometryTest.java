package net.evarius.terranexus.client.gui;

import java.util.List;

public final class PhoneLayoutGeometryTest {
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport("854x480 scale 2", 427, 240),
            new Viewport("1280x720 scale 2", 640, 360),
            new Viewport("1280x720 scale 3", 426, 240),
            new Viewport("1920x1080 scale 3", 640, 360),
            new Viewport("1920x1080 scale 4", 480, 270),
            new Viewport("2560x1440 scale 4", 640, 360)
    );

    private PhoneLayoutGeometryTest() {}

    public static void run() {
        for (Viewport viewport : VIEWPORTS) {
            GuiBounds phone = PhoneMenuScreen.calculatePhoneBounds(viewport.width(), viewport.height());
            require(phone.x() >= 0 && phone.y() >= 0
                            && phone.right() <= viewport.width() && phone.bottom() <= viewport.height(),
                    viewport.name() + ": Handy verlässt den Bildschirm");
            require(Math.abs((phone.x() - (viewport.width() - phone.right()))) <= 1,
                    viewport.name() + ": Handy ist horizontal nicht zentriert");
            require(Math.abs((phone.y() - (viewport.height() - phone.bottom()))) <= 1,
                    viewport.name() + ": Handy ist vertikal nicht zentriert");
            require(phone.height() > phone.width(),
                    viewport.name() + ": Hochformat ging verloren");
            require(phone.width() >= 176 || viewport.width() < 196,
                    viewport.name() + ": Handy wurde unnötig unlesbar klein");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Viewport(String name, int width, int height) {}
}
