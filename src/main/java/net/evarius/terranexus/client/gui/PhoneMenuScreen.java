package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.evarius.terranexus.client.gui.phone.PhonePlacement;
import net.evarius.terranexus.client.gui.phone.PhoneShellRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Dedicated portrait presentation for server-driven phone apps. */
public final class PhoneMenuScreen extends Screen {
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
    private long closeStartedAt;
    private int scroll;
    private int maximumScroll;

    public PhoneMenuScreen(OpenGuiPayload payload) {
        super(payload.title());
        sessionToken = payload.sessionToken();
        elements = payload.elements();
    }

    public static boolean applies(Text title) {
        if (title == null) return false;
        if (title.getContent() instanceof TranslatableTextContent translated
                && translated.getKey().startsWith("gui.terranexus.phone.")) return true;
        String rendered = title.getString().toLowerCase(Locale.ROOT);
        return rendered.startsWith("tn-handy") || rendered.startsWith("tn phone");
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
        return PhonePlacement.calculate(screenWidth, screenHeight);
    }

    private void buildButtons() {
        boolean launcher = isLauncher();
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
        renderPhone(context, mouseX, mouseY);
    }

    private void renderPhone(DrawContext context, int mouseX, int mouseY) {
        PhoneShellRenderer.frame(context, phone);

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        context.drawText(textRenderer, Text.literal(time), phone.x() + 14, phone.y() + 16, 0xFFDCECF1, false);
        context.drawText(textRenderer, Text.literal("TN  ▮▮▮"), phone.right() - 52,
                phone.y() + 16, 0xFF9FC2CA, false);
        renderClose(context, mouseX, mouseY);

        GuiMenuElement header = elements.stream().filter(element -> element.id() == 4 && !element.enabled())
                .findFirst().orElse(null);
        String heading = header == null ? title.getString().replace("TN-Handy · ", "") : header.label().getString();
        String subtitle = header == null ? Text.translatable("gui.terranexus.phone.launcher.subtitle").getString()
                : firstLine(header.tooltip().getString());
        context.drawText(textRenderer, fit(heading, phone.width() - 48),
                phone.x() + 15, phone.y() + 42, 0xFFF2FAFC, false);
        context.drawText(textRenderer, fit(subtitle, phone.width() - 30),
                phone.x() + 15, phone.y() + 55, 0xFF72A9B6, false);

        boolean launcher = isLauncher();
        if (!launcher) renderInformation(context, mouseX, mouseY);
        for (PhoneButton button : buttons) renderButton(context, button, mouseX, mouseY);
        PhoneShellRenderer.homeIndicator(context, phone);
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
            PhoneShellRenderer.icon(context, element.resolvedIcon(), content.x() + 7, y + 7, 16);
            context.drawText(textRenderer, fit(element.label().getString(), content.width() - 38),
                    content.x() + 29, y + 5, 0xFFEAF7F9, false);
            context.drawText(textRenderer, fit(firstLine(element.tooltip().getString()), content.width() - 38),
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
            PhoneShellRenderer.icon(context, button.element().resolvedIcon(),
                    bounds.x() + (bounds.width() - iconSize) / 2, bounds.y() + 9, iconSize);
            context.drawCenteredTextWithShadow(textRenderer, fit(button.element().label().getString(), bounds.width() - 6),
                    bounds.x() + bounds.width() / 2, bounds.bottom() - 16, 0xFFF2FAFC);
        } else {
            PhoneShellRenderer.icon(context, button.element().resolvedIcon(), bounds.x() + 9, bounds.y() + 8, 16);
            context.drawTextWithShadow(textRenderer, fit(button.element().label().getString(), bounds.width() - 38),
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
        if (button == 1 && !closing) {
            close();
            return true;
        }
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Handheld screens intentionally leave the game world untouched.
    }

    @Override
    protected void applyBlur(DrawContext context) {
        // Screen#renderBackground normally applies the in-game blur; the phone explicitly opts out.
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
        String label = element.label().getString().toLowerCase(Locale.ROOT);
        return element.id() == 49 || element.id() == 53
                || label.startsWith("zurück") || label.startsWith("back");
    }

    private boolean isLauncher() {
        return title.getContent() instanceof TranslatableTextContent translated
                ? translated.getKey().equals("gui.terranexus.phone.launcher.title")
                : title.getString().equalsIgnoreCase("TN-Handy")
                || title.getString().equalsIgnoreCase("TN Phone");
    }

    private static int rows(int entries, int columns) {
        return entries == 0 ? 0 : (entries + columns - 1) / columns;
    }

    private record PhoneButton(GuiMenuElement element, GuiBounds bounds, boolean launcher) {}
}
