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
            require(viewport.width() - phone.right() <= 10,
                    viewport.name() + ": Handy liegt nicht am rechten Rand");
            require(viewport.height() - phone.bottom() <= 10,
                    viewport.name() + ": Handy liegt nicht am unteren Rand");
            require(phone.height() > phone.width(),
                    viewport.name() + ": Hochformat ging verloren");
            require(phone.width() >= 176 || viewport.width() < 196,
                    viewport.name() + ": Handy wurde unnötig unlesbar klein");
            int contentHeight = phone.height() - 128;
            if (contentHeight < 130) {
                int usableHeight = contentHeight - 36;
                int tileHeight = Math.max(24, Math.min(56, (Math.max(48, usableHeight) - 4) / 2));
                require(tileHeight * 2 + 4 <= usableHeight,
                        viewport.name() + ": kompakte App-Kacheln kollidieren mit der Zurück-Navigation");
                require(24 + 4 + 28 <= usableHeight,
                        viewport.name() + ": kompaktes Wählfeld kollidiert mit der Zurück-Navigation");
                require(44 + 20 <= contentHeight - 27,
                        viewport.name() + ": Kontaktfelder kollidieren mit den Speichern-Schaltflächen");
                require(36 + 24 + 3 + 24 <= contentHeight,
                        viewport.name() + ": kompakte History-Administration verlässt den Inhaltsbereich");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Viewport(String name, int width, int height) {}
}
