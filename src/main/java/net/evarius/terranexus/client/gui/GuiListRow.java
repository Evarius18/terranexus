package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.function.IntConsumer;

public final class GuiListRow extends ClickableWidget {
    private static final long DOUBLE_CLICK_MILLIS = 300L;
    private static final String SEPARATOR = " · ";

    private final GuiMenuElement element;
    private final GuiPageType pageType;
    private final int rowIndex;
    private boolean selected;
    private final IntConsumer selectHandler;
    private final IntConsumer activateHandler;
    private long lastClick;

    public GuiListRow(GuiMenuElement element, GuiPageType pageType, int rowIndex, boolean selected,
                      GuiBounds bounds, IntConsumer selectHandler, IntConsumer activateHandler) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Text.literal(element.label()));
        this.element = element;
        this.pageType = pageType;
        this.rowIndex = rowIndex;
        this.selected = selected;
        this.selectHandler = selectHandler;
        this.activateHandler = activateHandler;
    }

    public String tooltipText() { return element.tooltip(); }
    public GuiMenuElement element() { return element; }
    public void setSelected(boolean selected) { this.selected = selected; }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int background = selected ? 0xDF173A49 : hovered ? 0xD8153040
                : (rowIndex & 1) == 0 ? 0xB80B1B28 : 0xB80E202E;
        context.fill(getX(), getY(), getX() + width, getY() + height, background);
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0x80325462);
        context.fill(getX(), getY(), getX() + (selected ? 3 : 1), getY() + height,
                selected ? 0xFF3DD4E8 : 0xFF285162);

        var renderer = MinecraftClient.getInstance().textRenderer;
        int iconSize = Math.max(9, Math.min(16, height - 8));
        int iconX = getX() + 6;
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(element.resolvedIcon()),
                iconX, getY() + (height - iconSize) / 2, iconSize, iconSize);
        if (pageType == GuiPageType.AUDIT_LOG) renderAuditColumns(context, renderer, iconX + iconSize + 6);
        else if (pageType == GuiPageType.BANK_ACCOUNTS) renderBankColumns(context, renderer, iconX + iconSize + 6);
        else renderGenericColumns(context, renderer, iconX + iconSize + 6);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = Util.getMeasuringTimeMs();
        boolean activate = selected && element.enabled() && now - lastClick <= DOUBLE_CLICK_MILLIS;
        selectHandler.accept(element.id());
        if (activate) activateHandler.accept(element.id());
        lastClick = now;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    private void renderBankColumns(DrawContext context, net.minecraft.client.font.TextRenderer renderer, int startX) {
        String[] details = element.tooltip().split(SEPARATOR, 4);
        int right = getX() + width - 7;
        int nameWidth = Math.max(20, Math.round(width * 0.36F));
        int numberX = startX + nameWidth;
        int balanceX = getX() + Math.round(width * 0.72F);
        boolean accountNumberVisible = details.length >= 4;
        String number = accountNumberVisible ? details[0] : "—";
        String balance = accountNumberVisible ? details[1] : details.length > 0 ? details[0] : "—";
        String type = accountNumberVisible ? details[2] : details.length > 1 ? details[1] : "KONTO";
        String statusValue = accountNumberVisible ? details[3] : details.length > 2 ? details[2] : "AKTIV";
        draw(context, renderer, element.label(), startX, nameWidth - 6, 0xFFE8F2F5);
        draw(context, renderer, number, numberX,
                Math.max(12, balanceX - numberX - 6), 0xFF91B5BF);
        Text status = Text.literal(statusValue);
        int badgeWidth = GuiStatusBadge.width(renderer, status);
        int typeWidth = Math.min(58, Math.max(24, renderer.getWidth(type) + 5));
        draw(context, renderer, balance, balanceX,
                Math.max(12, right - balanceX - badgeWidth - typeWidth - 5), 0xFFDCE7BE);
        draw(context, renderer, type, right - badgeWidth - typeWidth - 3, typeWidth, 0xFF83A7B1);
        if (right - badgeWidth > balanceX) {
            GuiStatusBadge.render(context, renderer, right - badgeWidth, getY() + (height - 11) / 2,
                    status, GuiStatusBadge.infer(statusValue, element.enabled(), element.selected()));
        }
    }

    private void renderAuditColumns(DrawContext context, net.minecraft.client.font.TextRenderer renderer, int startX) {
        String[] label = element.label().split(SEPARATOR, 2);
        String[] details = element.tooltip().split(SEPARATOR, 4);
        int timeWidth = Math.max(46, Math.round(width * 0.17F));
        int actionWidth = Math.max(38, Math.round(width * 0.15F));
        int sourceWidth = Math.max(48, Math.round(width * 0.25F));
        int categoryWidth = Math.max(40, Math.round(width * 0.15F));
        int actionX = startX + timeWidth;
        int sourceX = actionX + actionWidth;
        int categoryX = sourceX + sourceWidth;
        int detailX = categoryX + categoryWidth;
        draw(context, renderer, details.length > 0 ? details[0] : "—", startX, timeWidth - 5, 0xFF84AAB5);
        draw(context, renderer, label.length > 0 ? label[0] : "—", actionX, actionWidth - 5, 0xFFE7F2F5);
        String source = (label.length > 1 ? label[1] : "—")
                + (details.length > 1 ? " / " + details[1] : "");
        draw(context, renderer, source, sourceX, sourceWidth - 5, 0xFF9FD5C7);
        draw(context, renderer, details.length > 2 ? details[2] : "—", categoryX,
                categoryWidth - 5, 0xFFD4B97A);
        draw(context, renderer, details.length > 3 ? details[3] : "—", detailX,
                Math.max(12, getX() + width - detailX - 7), 0xFF91AAB2);
    }

    private void renderGenericColumns(DrawContext context, net.minecraft.client.font.TextRenderer renderer, int startX) {
        int labelWidth = Math.max(28, Math.round(width * 0.40F));
        draw(context, renderer, element.label(), startX, labelWidth - 6, 0xFFE8F2F5);
        draw(context, renderer, GuiInfoCard.firstLine(element.tooltip()), startX + labelWidth,
                Math.max(12, getX() + width - startX - labelWidth - 7), 0xFF91AAB2);
    }

    private void draw(DrawContext context, net.minecraft.client.font.TextRenderer renderer,
                      String value, int x, int maximumWidth, int color) {
        context.drawText(renderer, GuiInfoCard.fit(renderer, Text.literal(value), maximumWidth),
                x, getY() + (height - renderer.fontHeight) / 2, color, false);
    }
}
