package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class GuiInfoCard {
    private GuiInfoCard() { }

    public static void render(DrawContext context, TextRenderer renderer, GuiBounds bounds,
                              GuiMenuElement element, boolean emphasized) {
        GuiRenderUtil.panel(context, bounds, emphasized);
        int iconSize = Math.max(10, Math.min(22, bounds.height() - 10));
        int iconX = bounds.x() + 7;
        int iconY = bounds.y() + (bounds.height() - iconSize) / 2;
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(element.resolvedIcon()),
                iconX, iconY, iconSize, iconSize);
        int textX = iconX + iconSize + 7;
        int textWidth = Math.max(8, bounds.right() - textX - 6);
        Text label = fit(renderer, Text.literal(element.label()), textWidth);
        context.drawTextWithShadow(renderer, label, textX, bounds.y() + 6, 0xFFE9F4F7);
        if (bounds.height() >= 28 && !element.tooltip().isBlank()) {
            Text detail = fit(renderer, Text.literal(firstLine(element.tooltip())), textWidth);
            context.drawText(renderer, detail, textX, bounds.y() + 17, 0xFF78A7B2, false);
        }
    }

    static Text fit(TextRenderer renderer, Text text, int width) {
        if (renderer.getWidth(text) <= width) return text;
        String ellipsis = "…";
        String fitted = renderer.trimToWidth(text.getString(), Math.max(1, width - renderer.getWidth(ellipsis)));
        return Text.literal(fitted + ellipsis);
    }

    static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }
}
