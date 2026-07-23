package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class GuiDetailPanel {
    private GuiDetailPanel() { }

    public static void render(DrawContext context, TextRenderer renderer, GuiBounds bounds,
                              GuiMenuElement selected, GuiPageType pageType) {
        GuiRenderUtil.panel(context, bounds, selected != null);
        context.drawTextWithShadow(renderer, Text.translatable("gui.terranexus.detail.title"),
                bounds.x() + 8, bounds.y() + 7, 0xFF9FDDEA);
        if (selected == null) {
            context.drawText(renderer, Text.translatable("gui.terranexus.detail.empty"),
                    bounds.x() + 8, bounds.y() + 23, 0xFF708A94, false);
            return;
        }

        int iconSize = Math.min(24, Math.max(12, bounds.height() / 5));
        int contentY = bounds.y() + 23;
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(selected.resolvedIcon()),
                bounds.x() + 8, contentY, iconSize, iconSize);
        int textX = bounds.x() + iconSize + 15;
        int textWidth = Math.max(12, bounds.right() - textX - 8);
        context.drawTextWithShadow(renderer, GuiInfoCard.fit(renderer, Text.literal(selected.label()), textWidth),
                textX, contentY + 2, 0xFFF0F6F8);

        Text badge = selected.enabled() ? Text.translatable("gui.terranexus.status.action")
                : Text.translatable("gui.terranexus.status.information");
        GuiStatusBadge.render(context, renderer, textX, contentY + 14, badge,
                GuiStatusBadge.infer(selected.tooltip(), selected.enabled(), selected.selected()));

        int detailsY = contentY + iconSize + 9;
        int availableHeight = Math.max(0, bounds.bottom() - detailsY - (selected.enabled() ? 27 : 7));
        List<OrderedText> lines = renderer.wrapLines(Text.literal(selected.tooltip()), Math.max(20, bounds.width() - 16));
        int maximumLines = availableHeight / 10;
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++)
            context.drawText(renderer, lines.get(index), bounds.x() + 8, detailsY + index * 10, 0xFF9BB4BC, false);
    }
}
