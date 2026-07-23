package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.function.BiConsumer;

public final class GuiActionButton extends ClickableWidget {
    private static final int MAX_ICON_SIZE = 24;
    private static final long PRESSED_MILLIS = 140L;

    private final int elementId;
    private final GuiAction action;
    private final GuiIcon icon;
    private final String tooltip;
    private final boolean selected;
    private final GuiVisualStyle style;
    private final BiConsumer<GuiAction, Integer> clickHandler;
    private long pressedUntil;
    private boolean renderedTruncated;

    public GuiActionButton(int elementId, GuiAction action, GuiIcon icon, Text label, String tooltip,
                           int x, int y, int width, int height, boolean enabled, boolean selected,
                           BiConsumer<GuiAction, Integer> clickHandler) {
        this(elementId, action, icon, label, tooltip, x, y, width, height, enabled, selected,
                GuiVisualStyle.MODULE_TILE, clickHandler);
    }

    public GuiActionButton(int elementId, GuiAction action, GuiIcon icon, Text label, String tooltip,
                           int x, int y, int width, int height, boolean enabled, boolean selected,
                           GuiVisualStyle style, BiConsumer<GuiAction, Integer> clickHandler) {
        super(x, y, width, height, label);
        this.elementId = elementId;
        this.action = action;
        this.icon = icon;
        this.tooltip = tooltip;
        this.selected = selected;
        this.style = style;
        this.clickHandler = clickHandler;
        this.active = enabled;
    }

    public String tooltipText() {
        return tooltip.isBlank() && renderedTruncated ? getMessage().getString() : tooltip;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        boolean pressed = selected || Util.getMeasuringTimeMs() < pressedUntil;
        if (style == GuiVisualStyle.MODULE_TILE || style == GuiVisualStyle.ACTION_TILE
                || style == GuiVisualStyle.GRID_CELL) {
            ManagementGuiAtlas.Sprite background = !active ? ManagementGuiAtlas.BUTTON_DISABLED
                    : pressed ? ManagementGuiAtlas.BUTTON_SELECTED
                    : hovered ? ManagementGuiAtlas.BUTTON_HOVER : ManagementGuiAtlas.BUTTON_NORMAL;
            GuiRenderUtil.sprite(context, background, getX(), getY(), width, height);
        } else {
            renderCompactBackground(context, pressed);
        }

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int padding = Math.max(2, Math.min(8, width / 16));
        int gap = getMessage().getString().isEmpty() ? 0 : Math.max(2, Math.min(6, width / 24));
        Text renderedLabel = getMessage();
        int textWidth = textRenderer.getWidth(renderedLabel);
        int innerWidth = Math.max(1, width - padding * 2);
        int maximumIcon = style == GuiVisualStyle.MODULE_TILE ? MAX_ICON_SIZE : 16;
        int iconSize = Math.max(StructuredGuiLayout.MIN_ICON_SIZE, Math.min(maximumIcon, height - 8));
        int availableTextWidth = Math.max(1, innerWidth - iconSize - gap);
        float textScale = textWidth <= availableTextWidth ? 1.0F : StructuredGuiLayout.MIN_TEXT_SCALE;
        renderedTruncated = textWidth * textScale > availableTextWidth;
        if (renderedTruncated) {
            int unscaledLimit = Math.max(1, (int) Math.floor(availableTextWidth / textScale));
            String ellipsis = "…";
            int ellipsisWidth = textRenderer.getWidth(ellipsis);
            String shortened = textRenderer.trimToWidth(getMessage().getString(),
                    Math.max(1, unscaledLimit - ellipsisWidth));
            renderedLabel = Text.literal(shortened + ellipsis);
            textWidth = textRenderer.getWidth(renderedLabel);
        }
        int scaledTextWidth = Math.round(textWidth * textScale);
        int contentWidth = iconSize + gap + scaledTextWidth;
        int contentX = getX() + (width - contentWidth) / 2;
        int iconY = getY() + (height - iconSize) / 2;
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(icon), contentX, iconY, iconSize, iconSize);
        int color = active ? 0xFFF1F7FA : 0xFF87929A;
        if (textWidth > 0) {
            int textX = contentX + iconSize + gap;
            int textY = getY() + Math.round((height - textRenderer.fontHeight * textScale) / 2.0F);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(textX, textY);
            context.getMatrices().scale(textScale, textScale);
            context.drawTextWithShadow(textRenderer, renderedLabel, 0, 0, color);
            context.getMatrices().popMatrix();
        }
    }

    private void renderCompactBackground(DrawContext context, boolean pressed) {
        int fill = !active ? 0xA51C252C : pressed ? 0xE0224D58 : hovered ? 0xDD173D4B : 0xC50D2230;
        int accent = !active ? 0xFF59666D : hovered || pressed ? 0xFF40D7EA : 0xFF2B6675;
        context.fill(getX(), getY(), getX() + width, getY() + height, fill);
        context.fill(getX(), getY(), getX() + width, getY() + 1, accent);
        context.fill(getX(), getY(), getX() + 1, getY() + height, accent);
        if (style == GuiVisualStyle.SEARCH_FIELD)
            context.fill(getX() + 5, getY() + height - 3, getX() + width - 5, getY() + height - 2, 0xFF4B8795);
        if (style == GuiVisualStyle.NAVIGATION)
            context.fill(getX(), getY() + height - 2, getX() + width, getY() + height, 0xFF173B49);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!active) return;
        pressedUntil = Util.getMeasuringTimeMs() + PRESSED_MILLIS;
        clickHandler.accept(action, elementId);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

}
