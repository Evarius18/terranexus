package net.evarius.terranexus.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

public final class GuiStatusBadge {
    private GuiStatusBadge() { }

    public static int width(TextRenderer renderer, Text text) {
        return renderer.getWidth(text) + 8;
    }

    public static void render(DrawContext context, TextRenderer renderer, int x, int y, Text text, Status status) {
        int width = width(renderer, text);
        context.fill(x, y, x + width, y + 11, status.background);
        context.fill(x, y, x + 2, y + 11, status.accent);
        context.drawText(renderer, text, x + 5, y + 2, status.text, false);
    }

    public static Status infer(String value, boolean enabled, boolean selected) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("gesperrt") || normalized.contains("belegt") || normalized.contains("abgelehnt"))
            return Status.WARNING;
        if (selected || normalized.contains("frei") || normalized.contains("aktiv") || normalized.contains("erfolgreich"))
            return Status.OK;
        return enabled ? Status.NEUTRAL : Status.INFO;
    }

    public enum Status {
        OK(0xA514342E, 0xFF40D7A0, 0xFFD8FFF0),
        WARNING(0xA53B2519, 0xFFE0A13A, 0xFFFFE4B0),
        INFO(0xA5142738, 0xFF47A9D1, 0xFFD8F4FF),
        NEUTRAL(0xA5232931, 0xFF758795, 0xFFE2E7EB);

        private final int background;
        private final int accent;
        private final int text;

        Status(int background, int accent, int text) {
            this.background = background;
            this.accent = accent;
            this.text = text;
        }
    }
}
