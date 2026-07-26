package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Dedicated portrait presentation for server-driven phone apps. */
public final class PhoneMenuScreen extends Screen {
    private static final int DESIGN_WIDTH = 236;
    private static final int DESIGN_HEIGHT = 420;
    private static final int OPEN_MILLIS = 180;
    private static final int CLOSE_MILLIS = 140;

    private String sessionToken;
    private List<GuiMenuElement> elements;
    private final List<PhoneButton> buttons = new ArrayList<>();
    private GuiBounds phone;
    private GuiBounds closeButton;
    private GuiBounds content;
    private boolean serverClosing;
    private boolean awaitingServer;
    private boolean closing;
    private boolean closeSent;
    private long openedAt = Util.getMeasuringTimeMs();
    private long closeStartedAt;
    private int scroll;
    private int maximumScroll;

    public PhoneMenuScreen(OpenGuiPayload payload) {
        super(Text.literal(payload.title()));
        sessionToken = payload.sessionToken();
        elements = payload.elements();
    }

    public static boolean applies(String title) {
        return title != null && title.toLowerCase(Locale.ROOT).startsWith("tn-handy");
    }

    public boolean belongsTo(String token) {
        return sessionToken.equals(token);
    }

    public void update(OpenGuiPayload payload) {
        sessionToken = payload.sessionToken();
        elements = payload.elements();
        awaitingServer = false;
        scroll = 0;
        if (client != null) clearAndInit();
    }

    public void closeFromServer() {
        serverClosing = true;
        if (client != null) client.setScreen(null);
    }

    @Override
    protected void init() {
        buttons.clear();
        phone = calculatePhoneBounds(width, height);
        int inset = Math.max(8, phone.width() / 24);
        closeButton = new GuiBounds(phone.right() - inset - 16, phone.y() + 13, 16, 16);
        content = new GuiBounds(phone.x() + inset, phone.y() + 74,
                phone.width() - inset * 2, phone.height() - 124);
        buildButtons();
    }

    static GuiBounds calculatePhoneBounds(int screenWidth, int screenHeight) {
        int margin = 10;
        float scale = Math.min(1.08F, Math.min((screenWidth - margin * 2) / (float) DESIGN_WIDTH,
                (screenHeight - margin * 2) / (float) DESIGN_HEIGHT));
        int phoneWidth = Math.max(176, Math.round(DESIGN_WIDTH * scale));
        int phoneHeight = Math.max(312, Math.round(DESIGN_HEIGHT * scale));
        phoneWidth = Math.min(phoneWidth, Math.max(1, screenWidth - margin * 2));
        phoneHeight = Math.min(phoneHeight, Math.max(1, screenHeight - margin * 2));
        return new GuiBounds((screenWidth - phoneWidth) / 2, (screenHeight - phoneHeight) / 2,
                phoneWidth, phoneHeight);
    }

    private void buildButtons() {
        boolean launcher = title.getString().equalsIgnoreCase("TN-Handy");
        List<GuiMenuElement> actions = elements.stream()
                .filter(element -> element.id() != 4)
                .filter(element -> !isNavigation(element))
                .filter(element -> launcher || element.enabled()).toList();
        List<GuiMenuElement> navigation = elements.stream().filter(GuiMenuElement::enabled)
                .filter(PhoneMenuScreen::isNavigation).toList();
        if (launcher) {
            int columns = content.width() >= 190 ? 3 : 2;
            int gap = 8;
            int tileWidth = (content.width() - gap * (columns - 1)) / columns;
            int tileHeight = Math.max(58, Math.min(72, tileWidth + 8));
            for (int index = 0; index < actions.size(); index++) {
                int row = index / columns;
                int column = index % columns;
                buttons.add(new PhoneButton(actions.get(index), new GuiBounds(
                        content.x() + column * (tileWidth + gap),
                        content.y() + row * (tileHeight + gap), tileWidth, tileHeight), true));
            }
            maximumScroll = Math.max(0, rows(actions.size(), columns) * (tileHeight + gap) - gap - content.height());
        } else {
            int y = content.bottom() - 38;
            for (int index = navigation.size() - 1; index >= 0; index--) {
                GuiMenuElement element = navigation.get(index);
                buttons.add(new PhoneButton(element,
                        new GuiBounds(content.x(), y, content.width(), 32), false));
                y -= 38;
            }
            for (int index = actions.size() - 1; index >= 0; index--) {
                GuiMenuElement element = actions.get(index);
                buttons.add(new PhoneButton(element,
                        new GuiBounds(content.x(), y, content.width(), 32), false));
                y -= 38;
            }
            maximumScroll = 0;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        long now = Util.getMeasuringTimeMs();
        if (closing && now - closeStartedAt >= CLOSE_MILLIS) {
            sendClose();
            return;
        }
        float progress = closing
                ? 1.0F - Math.min(1.0F, (now - closeStartedAt) / (float) CLOSE_MILLIS)
                : Math.min(1.0F, (now - openedAt) / (float) OPEN_MILLIS);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        int slide = Math.round((1.0F - progress) * 18.0F);

        context.fill(0, 0, width, height, 0x42000000);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, slide);
        renderPhone(context, mouseX, mouseY - slide);
        context.getMatrices().popMatrix();
    }

    private void renderPhone(DrawContext context, int mouseX, int mouseY) {
        context.fill(phone.x(), phone.y() + 8, phone.right(), phone.bottom() - 8, 0xFF05080C);
        context.fill(phone.x() + 5, phone.y(), phone.right() - 5, phone.bottom(), 0xFF05080C);
        context.fill(phone.x() + 8, phone.y() + 8, phone.right() - 8, phone.bottom() - 8, 0xFF0D1720);
        context.fill(phone.x() + 10, phone.y() + 32, phone.right() - 10, phone.bottom() - 18, 0xFF102633);
        context.fill(phone.x() + phone.width() / 2 - 22, phone.y() + 7,
                phone.x() + phone.width() / 2 + 22, phone.y() + 11, 0xFF25323A);

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        context.drawText(textRenderer, Text.literal(time), phone.x() + 14, phone.y() + 16, 0xFFDCECF1, false);
        context.drawText(textRenderer, Text.literal("TN  ▮▮▮"), phone.right() - 52,
                phone.y() + 16, 0xFF9FC2CA, false);
        renderClose(context, mouseX, mouseY);

        GuiMenuElement header = elements.stream().filter(element -> element.id() == 4 && !element.enabled())
                .findFirst().orElse(null);
        String heading = header == null ? title.getString().replace("TN-Handy · ", "") : header.label();
        String subtitle = header == null ? "TerraNexus Mobile" : firstLine(header.tooltip());
        context.drawText(textRenderer, fit(heading, phone.width() - 48),
                phone.x() + 15, phone.y() + 42, 0xFFF2FAFC, false);
        context.drawText(textRenderer, fit(subtitle, phone.width() - 30),
                phone.x() + 15, phone.y() + 55, 0xFF72A9B6, false);

        boolean launcher = title.getString().equalsIgnoreCase("TN-Handy");
        if (!launcher) renderInformation(context, mouseX, mouseY);
        for (PhoneButton button : buttons) renderButton(context, button, mouseX, mouseY);
        context.fill(phone.x() + phone.width() / 2 - 26, phone.bottom() - 11,
                phone.x() + phone.width() / 2 + 26, phone.bottom() - 8, 0xFF8BA6AD);
    }

    private void renderInformation(DrawContext context, int mouseX, int mouseY) {
        List<GuiMenuElement> information = elements.stream().filter(element -> !element.enabled())
                .filter(element -> element.id() != 4).toList();
        int actionTop = buttons.stream().map(button -> button.bounds().y()).min(Integer::compareTo)
                .orElse(content.bottom());
        int y = content.y();
        int bottom = Math.max(y, actionTop - 7);
        for (GuiMenuElement element : information) {
            if (y + 33 > bottom) break;
            boolean hovered = new GuiBounds(content.x(), y, content.width(), 30).contains(mouseX, mouseY);
            context.fill(content.x(), y, content.right(), y + 30, hovered ? 0xE01A3B49 : 0xCE122F3C);
            context.fill(content.x(), y, content.x() + 2, y + 30, 0xFF3DBFD0);
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(element.resolvedIcon()),
                    content.x() + 7, y + 7, 16, 16);
            context.drawText(textRenderer, fit(element.label(), content.width() - 38),
                    content.x() + 29, y + 5, 0xFFEAF7F9, false);
            context.drawText(textRenderer, fit(firstLine(element.tooltip()), content.width() - 38),
                    content.x() + 29, y + 17, 0xFF87ACB4, false);
            y += 34;
        }
    }

    private void renderButton(DrawContext context, PhoneButton button, int mouseX, int mouseY) {
        GuiBounds bounds = button.bounds().translated(0, button.launcher() ? -scroll : 0);
        if (button.launcher() && (bounds.bottom() < content.y() || bounds.y() > content.bottom())) return;
        boolean hovered = bounds.contains(mouseX, mouseY);
        boolean active = button.element().enabled();
        int fill = !active ? 0xD018242B : hovered ? 0xED1C5261 : 0xE0143947;
        int border = !active ? 0xFF43545B : hovered ? 0xFF54D9E7 : 0xFF2F7888;
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), fill);
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, border);
        if (button.launcher()) {
            int iconSize = Math.min(27, bounds.height() - 25);
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(button.element().resolvedIcon()),
                    bounds.x() + (bounds.width() - iconSize) / 2, bounds.y() + 9, iconSize, iconSize);
            context.drawCenteredTextWithShadow(textRenderer, fit(button.element().label(), bounds.width() - 6),
                    bounds.x() + bounds.width() / 2, bounds.bottom() - 16, 0xFFF2FAFC);
        } else {
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(button.element().resolvedIcon()),
                    bounds.x() + 9, bounds.y() + 8, 16, 16);
            context.drawTextWithShadow(textRenderer, fit(button.element().label(), bounds.width() - 38),
                    bounds.x() + 32, bounds.y() + 12, 0xFFF2FAFC);
        }
    }

    private void renderClose(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = closeButton.contains(mouseX, mouseY);
        context.fill(closeButton.x(), closeButton.y(), closeButton.right(), closeButton.bottom(),
                hovered ? 0xFFE0545D : 0xCC8A3038);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("×"),
                closeButton.x() + closeButton.width() / 2, closeButton.y() + 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || closing || awaitingServer) return false;
        if (closeButton.contains((int) mouseX, (int) mouseY)) {
            close();
            return true;
        }
        for (PhoneButton entry : buttons) {
            GuiBounds bounds = entry.bounds().translated(0, entry.launcher() ? -scroll : 0);
            if (entry.element().enabled() && bounds.contains((int) mouseX, (int) mouseY)) {
                awaitingServer = true;
                ClientPlayNetworking.send(new GuiActionPayload(sessionToken,
                        GuiAction.ACTIVATE_ELEMENT.name(), entry.element().id()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maximumScroll <= 0 || !content.contains((int) mouseX, (int) mouseY)) return false;
        scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) Math.signum(verticalAmount) * 24));
        return true;
    }

    @Override
    public void close() {
        if (closing || serverClosing) return;
        closing = true;
        closeStartedAt = Util.getMeasuringTimeMs();
    }

    @Override
    public void removed() {
        if (!serverClosing && !awaitingServer && !closeSent) sendClosePacket();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendClose() {
        sendClosePacket();
        serverClosing = true;
        if (client != null) client.setScreen(null);
    }

    private void sendClosePacket() {
        if (closeSent || !ClientPlayNetworking.canSend(GuiActionPayload.ID)) return;
        closeSent = true;
        ClientPlayNetworking.send(new GuiActionPayload(sessionToken, GuiAction.CLOSE.name(), -1));
    }

    private Text fit(String value, int width) {
        if (textRenderer.getWidth(value) <= width) return Text.literal(value);
        String ellipsis = "…";
        return Text.literal(textRenderer.trimToWidth(value,
                Math.max(1, width - textRenderer.getWidth(ellipsis))) + ellipsis);
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private static boolean isNavigation(GuiMenuElement element) {
        String label = element.label().toLowerCase(Locale.ROOT);
        return element.id() == 49 || element.id() == 53 || label.startsWith("zurück");
    }

    private static int rows(int entries, int columns) {
        return entries == 0 ? 0 : (entries + columns - 1) / columns;
    }

    private record PhoneButton(GuiMenuElement element, GuiBounds bounds, boolean launcher) {}
}
