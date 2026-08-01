package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.evarius.terranexus.network.gui.GuiTextActionPayload;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.evarius.terranexus.client.gui.phone.PhonePlacement;
import net.evarius.terranexus.client.gui.phone.PhoneShellRenderer;
import net.evarius.terranexus.client.gui.phone.PhoneMessageEditor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

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
    private PhoneMessageEditor inlineField;
    private GuiBounds inlineFieldBounds;
    private GuiBounds inlineSendBounds;
    private GuiBounds emojiToggleBounds;
    private final List<EmojiButton> emojiButtons = new ArrayList<>();
    private boolean emojiOpen;
    private GuiMenuElement inlineElement;

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
        inlineField = null;
        emojiButtons.clear();
        emojiOpen = false;
        inlineElement = elements.stream().filter(this::isInlineInput).findFirst().orElse(null);
        phone = calculatePhoneBounds(width, height);
        int inset = Math.max(8, phone.width() / 24);
        closeButton = new GuiBounds(phone.right() - inset - 16, phone.y() + 13, 16, 16);
        int subtitleLines = Math.max(1, subtitleLines().size());
        int contentTop = phone.y() + 68 + subtitleLines * 10;
        content = new GuiBounds(phone.x() + inset, contentTop,
                phone.width() - inset * 2, phone.bottom() - 50 - contentTop);
        buildButtons();
        if (inlineElement != null) {
            boolean groupName = isMessengerGroupCreate();
            int sendWidth = groupName ? 54 : 38;
            int fieldHeight = groupName ? 34 : 58;
            int controlsWidth = groupName ? sendWidth + 5 : sendWidth + 24 + 10;
            inlineFieldBounds = new GuiBounds(content.x(), content.bottom() - fieldHeight - 36,
                    content.width() - controlsWidth, fieldHeight);
            emojiToggleBounds = groupName ? null : new GuiBounds(inlineFieldBounds.right() + 5,
                    inlineFieldBounds.bottom() - 27, 24, 27);
            inlineSendBounds = new GuiBounds(groupName ? inlineFieldBounds.right() + 5 : emojiToggleBounds.right() + 5,
                    inlineFieldBounds.bottom() - 27, sendWidth, 27);
            inlineField = new PhoneMessageEditor(inlineFieldBounds.x(), inlineFieldBounds.y(),
                    inlineFieldBounds.width(), inlineFieldBounds.height(), inlineMaximumLength(),
                    Text.translatable(groupName ? "gui.terranexus.phone.messenger.group_name.placeholder"
                            : "gui.terranexus.phone.messenger.compose"), ignored -> {});
            addDrawableChild(inlineField);
            setInitialFocus(inlineField);
        }
        if (!isLauncher() && !isMessengerCollection()) recalculateInformationScroll();
    }

    static GuiBounds calculatePhoneBounds(int screenWidth, int screenHeight) {
        return PhonePlacement.calculate(screenWidth, screenHeight);
    }

    private void buildButtons() {
        boolean launcher = isLauncher();
        List<GuiMenuElement> actions = elements.stream()
                .filter(element -> element.id() != 4)
                .filter(element -> !isInlineInput(element))
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
                        content.y() + row * (tileHeight + gap), tileWidth, tileHeight), true, true));
            }
            maximumScroll = Math.max(0, rows(actions.size(), columns) * (tileHeight + gap) - gap - content.height());
        } else if (isMessengerCollection()) {
            int navigationY = content.bottom() - 32;
            for (int index = navigation.size() - 1; index >= 0; index--) {
                GuiMenuElement element = navigation.get(index);
                buttons.add(new PhoneButton(element,
                        new GuiBounds(content.x(), navigationY, content.width(), 32), false, false));
                navigationY -= 38;
            }
            int rowHeight = 32;
            int gap = 5;
            int visibleBottom = navigationY - 7;
            for (int index = 0; index < actions.size(); index++) {
                buttons.add(new PhoneButton(actions.get(index), new GuiBounds(content.x(),
                        content.y() + index * (rowHeight + gap), content.width(), rowHeight), false, true));
            }
            maximumScroll = Math.max(0, actions.size() * (rowHeight + gap) - gap
                    - Math.max(0, visibleBottom - content.y()));
        } else {
            int y = content.bottom() - 38;
            for (int index = navigation.size() - 1; index >= 0; index--) {
                GuiMenuElement element = navigation.get(index);
                buttons.add(new PhoneButton(element,
                        new GuiBounds(content.x(), y, content.width(), 32), false, false));
                y -= 38;
            }
            for (int index = actions.size() - 1; index >= 0; index--) {
                GuiMenuElement element = actions.get(index);
                buttons.add(new PhoneButton(element,
                        new GuiBounds(content.x(), y, content.width(), 32), false, false));
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
        List<OrderedText> subtitle = subtitleLines();
        context.drawText(textRenderer, fit(heading, phone.width() - 48),
                phone.x() + 15, phone.y() + 42, 0xFFF2FAFC, false);
        for (int line = 0; line < subtitle.size(); line++)
            context.drawText(textRenderer, subtitle.get(line), phone.x() + 15, phone.y() + 55 + line * 10,
                    0xFF72A9B6, false);

        boolean launcher = isLauncher();
        if (!launcher) renderInformation(context, mouseX, mouseY);
        context.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        for (PhoneButton button : buttons) renderButton(context, button, mouseX, mouseY);
        context.disableScissor();
        renderInlineInput(context, mouseX, mouseY);
        PhoneShellRenderer.homeIndicator(context, phone);
    }

    private void renderInformation(DrawContext context, int mouseX, int mouseY) {
        List<GuiMenuElement> information = elements.stream().filter(element -> !element.enabled())
                .filter(element -> element.id() != 4).toList();
        int actionTop = buttons.stream().map(button -> button.bounds().y()).min(Integer::compareTo)
                .orElse(content.bottom());
        int y = content.y() - scroll;
        int clipBottom = Math.min(actionTop - 7, inlineFieldBounds == null ? content.bottom() : inlineFieldBounds.y() - 7);
        context.enableScissor(content.x(), content.y(), content.right(), Math.max(content.y(), clipBottom));
        for (GuiMenuElement element : information) {
            int rowHeight=informationHeight(element);if(y>=clipBottom)break;
            boolean hovered = new GuiBounds(content.x(), y, content.width(), rowHeight).contains(mouseX, mouseY);
            context.fill(content.x(), y, content.right(), y + rowHeight, hovered ? 0xE01A3B49 : 0xCE122F3C);
            context.fill(content.x(), y, content.x() + 2, y + rowHeight, 0xFF3DBFD0);
            PhoneShellRenderer.icon(context, element.resolvedIcon(), content.x() + 7, y + 7, 16);
            context.drawText(textRenderer, fit(element.label().getString(), content.width() - 38),
                    content.x() + 29, y + 5, 0xFFEAF7F9, false);
            List<OrderedText> detail=isMessengerChat()?textRenderer.wrapLines(element.tooltip(),content.width()-38)
                    :List.of(fit(firstLine(element.tooltip().getString()),content.width()-38).asOrderedText());
            for(int line=0;line<detail.size();line++)context.drawText(textRenderer,detail.get(line),content.x()+29,y+17+line*10,0xFF87ACB4,false);
            y += rowHeight + 4;
        }
        context.disableScissor();
    }

    private void recalculateInformationScroll() {
        int actionTop = buttons.stream().map(button -> button.bounds().y()).min(Integer::compareTo).orElse(content.bottom());
        int bottom = Math.min(actionTop - 7, inlineFieldBounds == null ? content.bottom() : inlineFieldBounds.y() - 7);
        int total=elements.stream().filter(element -> !element.enabled()).filter(element -> element.id()!=4)
                .mapToInt(element->informationHeight(element)+4).sum();
        maximumScroll = Math.max(0, total - Math.max(0, bottom - content.y()));
    }

    private int informationHeight(GuiMenuElement element){if(!isMessengerChat())return 30;int lines=Math.max(1,textRenderer.wrapLines(element.tooltip(),Math.max(20,content.width()-38)).size());return Math.max(30,20+lines*10);}
    private boolean isMessengerChat(){return title.getContent() instanceof TranslatableTextContent translated&&translated.getKey().equals("gui.terranexus.phone.messenger.chat");}

    private void renderInlineInput(DrawContext context, int mouseX, int mouseY) {
        if (inlineField == null) return;
        inlineField.render(context, mouseX, mouseY, 0.0F);
        context.drawText(textRenderer, Text.literal(inlineField.remaining() + ""), inlineFieldBounds.right() - 24,
                inlineFieldBounds.bottom() - 10, inlineField.remaining() < 20 ? 0xFFFF9A91 : 0xFF759BA4, false);
        if (emojiToggleBounds != null) {
            boolean emojiHovered = emojiToggleBounds.contains(mouseX, mouseY);
            context.fill(emojiToggleBounds.x(), emojiToggleBounds.y(), emojiToggleBounds.right(), emojiToggleBounds.bottom(),
                    emojiHovered || emojiOpen ? 0xED1C5261 : 0xE0143947);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("☺"), emojiToggleBounds.x() + emojiToggleBounds.width() / 2,
                    emojiToggleBounds.y() + 9, 0xFFF2FAFC);
        }
        boolean hovered = inlineSendBounds.contains(mouseX, mouseY);
        int sendFill = !inlineField.valid() ? 0xB018242B : hovered ? 0xED1C5261 : 0xE0143947;
        context.fill(inlineSendBounds.x(), inlineSendBounds.y(), inlineSendBounds.right(), inlineSendBounds.bottom(), sendFill);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(isMessengerGroupCreate()
                        ? "gui.terranexus.next" : "gui.terranexus.phone.messenger.send_short"),
                inlineSendBounds.x() + inlineSendBounds.width() / 2, inlineSendBounds.y() + 9,
                inlineField.valid() ? 0xFFF2FAFC : 0xFF6D7E84);
        emojiButtons.clear();
        if (emojiOpen) {
            String[] emojis = {"🙂", "❤", "👍", "😂", "🎉"};
            int size = 25, gap = 3, total = emojis.length * size + (emojis.length - 1) * gap;
            int startX = content.x() + Math.max(0, (content.width() - total) / 2);
            int y = inlineFieldBounds.y() - size - 4;
            for (int index = 0; index < emojis.length; index++) {
                GuiBounds bounds = new GuiBounds(startX + index * (size + gap), y, size, size);
                emojiButtons.add(new EmojiButton(emojis[index], bounds));
                context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                        bounds.contains(mouseX, mouseY) ? 0xF01C5261 : 0xEE143947);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(emojis[index]),
                        bounds.x() + size / 2, bounds.y() + 8, 0xFFFFFFFF);
            }
        }
    }

    private void renderButton(DrawContext context, PhoneButton button, int mouseX, int mouseY) {
        GuiBounds bounds = button.bounds().translated(0, button.scrollable() ? -scroll : 0);
        if (button.scrollable() && (bounds.bottom() < content.y() || bounds.y() > content.bottom())) return;
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
        if (inlineFieldBounds != null && inlineFieldBounds.contains((int) mouseX, (int) mouseY)) {
            setFocused(inlineField);
            return inlineField.mouseClicked(mouseX, mouseY, button);
        }
        if (emojiToggleBounds != null && emojiToggleBounds.contains((int) mouseX, (int) mouseY)) {
            emojiOpen = !emojiOpen;
            return true;
        }
        for (EmojiButton emoji : emojiButtons) if (emoji.bounds().contains((int) mouseX, (int) mouseY)) {
            inlineField.insert(emoji.value());
            setFocused(inlineField);
            return true;
        }
        if (inlineSendBounds != null && inlineSendBounds.contains((int) mouseX, (int) mouseY)) {
            submitInline();
            return true;
        }
        for (PhoneButton entry : buttons) {
            GuiBounds bounds = entry.bounds().translated(0, entry.scrollable() ? -scroll : 0);
            boolean visible = !entry.scrollable() || bounds.bottom() >= content.y() && bounds.y() <= content.bottom();
            if (visible && entry.element().enabled() && bounds.contains((int) mouseX, (int) mouseY)) {
                awaitingServer = true;
                ClientPlayNetworking.send(new GuiActionPayload(sessionToken,
                        GuiAction.ACTIVATE_ELEMENT.name(), entry.element().id()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inlineField != null && inlineField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) inlineField.insert("\n");
            else submitInline();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitInline() {
        if (inlineField == null || inlineElement == null || awaitingServer) return;
        String value = inlineField.value().trim();
        if (!inlineField.valid()) return;
        awaitingServer = true;
        ClientPlayNetworking.send(new GuiTextActionPayload(sessionToken, inlineElement.id(), value));
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

    private List<OrderedText> subtitleLines() {
        GuiMenuElement header = elements.stream().filter(element -> element.id() == 4 && !element.enabled()).findFirst().orElse(null);
        Text value = header == null ? Text.translatable("gui.terranexus.phone.launcher.subtitle") : header.tooltip();
        return textRenderer.wrapLines(value, Math.max(40, phone.width() - 30));
    }

    private boolean isInlineInput(GuiMenuElement element) {
        return element.enabled() && element.id() == 0 && title.getContent() instanceof TranslatableTextContent translated
                && (translated.getKey().equals("gui.terranexus.phone.messenger.chat")
                || translated.getKey().equals("gui.terranexus.phone.messenger.group_create"));
    }

    private int inlineMaximumLength() {
        if (inlineElement == null) return 500;
        String marker = inlineElement.tooltip().getString();
        int index = marker.indexOf("#max=");
        if (index < 0) return 500;
        try { return Math.max(32, Math.min(2000, Integer.parseInt(marker.substring(index + 5).trim()))); }
        catch (NumberFormatException ignored) { return 500; }
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

    private boolean isMessengerGroupCreate() {
        return hasTitleKey("gui.terranexus.phone.messenger.group_create");
    }

    private boolean isMessengerCollection() {
        return hasTitleKey("gui.terranexus.phone.messenger.title")
                || hasTitleKey("gui.terranexus.phone.messenger.choose_contact")
                || hasTitleKey("gui.terranexus.phone.messenger.members");
    }

    private boolean hasTitleKey(String key) {
        return title.getContent() instanceof TranslatableTextContent translated
                && translated.getKey().equals(key);
    }

    private static int rows(int entries, int columns) {
        return entries == 0 ? 0 : (entries + columns - 1) / columns;
    }

    private record PhoneButton(GuiMenuElement element, GuiBounds bounds, boolean launcher, boolean scrollable) {}
    private record EmojiButton(String value, GuiBounds bounds) {}
}
