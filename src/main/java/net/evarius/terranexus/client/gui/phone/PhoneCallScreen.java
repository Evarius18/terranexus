package net.evarius.terranexus.client.gui.phone;

import net.evarius.terranexus.client.gui.GuiBounds;
import net.evarius.terranexus.network.phone.PhoneActionPayload;
import net.evarius.terranexus.phone.model.EmergencyNumber;
import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneClientState;
import net.evarius.terranexus.phone.model.PhoneContact;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** TerraNexus-styled telephone UI. All mutations are requests to the authoritative server. */
public final class PhoneCallScreen extends Screen {
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());
    private PhoneClientState state;
    private Page page = Page.HOME;
    private GuiBounds phone;
    private GuiBounds closeButton;
    private GuiBounds content;
    private TextFieldWidget dialField;
    private String dialValue = "";
    private int scroll;
    private int maximumScroll;
    private boolean handoff;

    public PhoneCallScreen(PhoneClientState state) {
        super(Text.translatable("gui.terranexus.phone.title"));
        this.state = state;
    }

    public void update(PhoneClientState updated) {
        state = updated;
        if (!updated.available() && client != null) {
            handoff = true;
            send(PhoneAction.RETURN_TO_APPS, "");
        }
    }

    @Override
    protected void init() {
        phone = PhonePlacement.calculate(width, height);
        int inset = Math.max(8, phone.width() / 24);
        closeButton = new GuiBounds(phone.right() - inset - 16, phone.y() + 13, 16, 16);
        content = new GuiBounds(phone.x() + inset, phone.y() + 77,
                phone.width() - inset * 2, phone.height() - 128);
        if (page == Page.DIAL) {
            dialField = new TextFieldWidget(textRenderer, content.x() + 7, dialFieldY() + 4,
                    content.width() - 14, 18, Text.translatable("gui.terranexus.phone.destination"));
            dialField.setMaxLength(128);
            dialField.setText(dialValue);
            dialField.setPlaceholder(Text.translatable("gui.terranexus.phone.destination"));
            dialField.setDrawsBackground(false);
            dialField.setEditableColor(0xFFE9F5F7);
            dialField.setChangedListener(value -> dialValue = value);
            addDrawableChild(dialField);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        PhoneShellRenderer.frame(context, phone);
        renderStatusBar(context);
        renderHeader(context);
        switch (page) {
            case HOME -> renderHome(context, mouseX, mouseY);
            case DIAL -> renderDial(context, mouseX, mouseY);
            case CONTACTS -> renderContacts(context, mouseX, mouseY);
            case EMERGENCY -> renderEmergency(context, mouseX, mouseY);
            case HISTORY -> renderHistory(context, mouseX, mouseY);
        }
        renderClose(context, mouseX, mouseY);
        PhoneShellRenderer.homeIndicator(context, phone);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderStatusBar(DrawContext context) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        context.drawText(textRenderer, time, phone.x() + 14, phone.y() + 16, 0xFFDCECF1, false);
        String signal = state.coverage() ? "TN  ▮▮▮" : "TN  ×";
        context.drawText(textRenderer, signal, phone.right() - textRenderer.getWidth(signal) - 14,
                phone.y() + 16, state.coverage() ? 0xFF9FC2CA : 0xFFFF8D86, false);
    }

    private void renderHeader(DrawContext context) {
        context.drawText(textRenderer, Text.translatable("gui.terranexus.phone.title"),
                phone.x() + 15, phone.y() + 42, 0xFFF2FAFC, false);
        String subtitle = compact() && state.state() != PhoneCallState.IDLE
                ? callState() + (state.peer().isBlank() ? "" : " · " + state.peer()) : pageTitle();
        context.drawText(textRenderer, fit(subtitle, phone.width() - 44),
                phone.x() + 15, phone.y() + 55, 0xFF72A9B6, false);
    }

    private void renderHome(DrawContext context, int mouseX, int mouseY) {
        int top = content.y();
        if (state.state() != PhoneCallState.IDLE && !compact()) {
            renderCallPanel(context, new GuiBounds(content.x(), top, content.width(), 43));
            top += 49;
        }
        int gap = compact() ? 4 : 7;
        int tileWidth = (content.width() - gap) / 2;
        int tileHeight = homeTileHeight(top, gap);
        List<Control> controls = List.of(
                new Control(new GuiBounds(content.x(), top, tileWidth, tileHeight), "☎", "Wählen", Command.PAGE_DIAL),
                new Control(new GuiBounds(content.x() + tileWidth + gap, top, tileWidth, tileHeight), "♟", "Kontakte", Command.PAGE_CONTACTS),
                new Control(new GuiBounds(content.x(), top + tileHeight + gap, tileWidth, tileHeight), "!", "Notruf", Command.PAGE_EMERGENCY),
                new Control(new GuiBounds(content.x() + tileWidth + gap, top + tileHeight + gap, tileWidth, tileHeight), "↺", "Verlauf", Command.PAGE_HISTORY)
        );
        controls.forEach(control -> renderTile(context, control, mouseX, mouseY, false));
        renderButton(context, backControl("Apps"), mouseX, mouseY, false);
    }

    private void renderDial(DrawContext context, int mouseX, int mouseY) {
        if (!compact()) renderCallPanel(context, new GuiBounds(content.x(), content.y(), content.width(), 36));
        GuiBounds field = new GuiBounds(content.x(), dialFieldY(), content.width(), 24);
        context.fill(field.x(), field.y(), field.right(), field.bottom(), 0xE0122F3C);
        context.fill(field.x(), field.bottom() - 1, field.right(), field.bottom(), 0xFF3DBFD0);
        int y = field.bottom() + (compact() ? 4 : 8);
        int gap = 6;
        int half = (content.width() - gap) / 2;
        List<Control> controls = new ArrayList<>();
        if (state.state() == PhoneCallState.IDLE)
            controls.add(new Control(new GuiBounds(content.x(), y, content.width(), 28), "☎", "Anrufen", Command.CALL));
        if (state.state() == PhoneCallState.INCOMING) {
            controls.add(new Control(new GuiBounds(content.x(), y, half, 28), "✓", "Annehmen", Command.ANSWER));
            controls.add(new Control(new GuiBounds(content.x() + half + gap, y, half, 28), "×", "Ablehnen", Command.DECLINE));
        }
        if (state.state() == PhoneCallState.ACTIVE || state.state() == PhoneCallState.RINGING) {
            controls.add(new Control(new GuiBounds(content.x(), y, half, 28), "×", "Auflegen", Command.HANGUP));
            controls.add(new Control(new GuiBounds(content.x() + half + gap, y, half, 28), "◖",
                    state.speaker() ? "Lautsprecher an" : "Lautsprecher", Command.SPEAKER));
        }
        controls.forEach(control -> renderButton(context, control, mouseX, mouseY,
                control.command == Command.DECLINE || control.command == Command.HANGUP));
        renderButton(context, backControl("Zurück"), mouseX, mouseY, false);
    }

    private void renderContacts(DrawContext context, int mouseX, int mouseY) {
        if (state.contacts().isEmpty()) {
            renderEmpty(context, "Keine Kontakte vorhanden");
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.contacts().size(), index -> {
            PhoneContact contact = state.contacts().get(index);
            return new Row(contact.name(), contact.number(), contact.number(), false);
        });
        renderButton(context, backControl("Zurück"), mouseX, mouseY, false);
    }

    private void renderEmergency(DrawContext context, int mouseX, int mouseY) {
        if (state.emergencyNumbers().isEmpty()) {
            renderEmpty(context, "Keine Notrufnummern konfiguriert");
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.emergencyNumbers().size(), index -> {
            EmergencyNumber number = state.emergencyNumbers().get(index);
            return new Row(number.label(), number.number(), number.number(), true);
        });
        renderButton(context, backControl("Zurück"), mouseX, mouseY, false);
    }

    private void renderHistory(DrawContext context, int mouseX, int mouseY) {
        if (state.history().isEmpty()) {
            renderEmpty(context, "Noch keine Anrufe");
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.history().size(), index -> {
            PhoneHistoryEntry entry = state.history().get(index);
            String peer = entry.peer().isBlank() ? entry.number() : entry.peer();
            String details = historyDirection(entry) + " · " + historyOutcome(entry) + " · "
                    + HISTORY_TIME.format(Instant.ofEpochMilli(entry.timestamp()));
            return new Row(peer, details, entry.number(), false);
        });
        renderButton(context, backControl("Zurück"), mouseX, mouseY, false);
    }

    private void renderRows(DrawContext context, int mouseX, int mouseY, int size, RowFactory factory) {
        int rowHeight = 38;
        int listBottom = content.bottom() - 38;
        int visibleHeight = listBottom - content.y();
        maximumScroll = Math.max(0, size * rowHeight - visibleHeight);
        for (int index = 0; index < size; index++) {
            int y = content.y() + index * rowHeight - scroll;
            GuiBounds bounds = new GuiBounds(content.x(), y, content.width(), 34);
            if (bounds.bottom() <= content.y() || bounds.y() >= listBottom) continue;
            Row row = factory.create(index);
            boolean hovered = bounds.contains(mouseX, mouseY);
            int fill = row.emergency ? hovered ? 0xEB6A3138 : 0xDE4D272D
                    : hovered ? 0xED1C5261 : 0xE0143947;
            context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), fill);
            context.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(),
                    row.emergency ? 0xFFFF8D86 : 0xFF3DBFD0);
            context.drawText(textRenderer, fit(row.title, bounds.width() - 37),
                    bounds.x() + 8, bounds.y() + 6, 0xFFF2FAFC, false);
            context.drawText(textRenderer, fit(row.subtitle, bounds.width() - 37),
                    bounds.x() + 8, bounds.y() + 19, 0xFF87ACB4, false);
            context.drawText(textRenderer, "☎", bounds.right() - 20, bounds.y() + 12,
                    row.emergency ? 0xFFFFC2C2 : 0xFF72D7E4, false);
        }
    }

    private void renderCallPanel(DrawContext context, GuiBounds bounds) {
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xD811303C);
        context.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(),
                state.coverage() ? 0xFF3DBFD0 : 0xFFFF6E76);
        String peer = state.peer().isBlank() ? "Keine Gegenstelle" : state.peer();
        context.drawText(textRenderer, fit(peer, bounds.width() - 13), bounds.x() + 8,
                bounds.y() + 7, 0xFFF2FAFC, false);
        String status = callState();
        if (!state.notice().isBlank()) status += " · " + state.notice();
        context.drawText(textRenderer, fit(status, bounds.width() - 13), bounds.x() + 8,
                bounds.y() + 20, state.coverage() ? 0xFF87ACB4 : 0xFFFF9A9F, false);
    }

    private void renderTile(DrawContext context, Control control, int mouseX, int mouseY, boolean danger) {
        GuiBounds bounds = control.bounds;
        boolean hovered = bounds.contains(mouseX, mouseY);
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                danger ? 0xDE4D272D : hovered ? 0xED1C5261 : 0xE0143947);
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1,
                danger ? 0xFFFF8D86 : hovered ? 0xFF54D9E7 : 0xFF2F7888);
        if (bounds.height() < 40) {
            context.drawText(textRenderer, control.icon, bounds.x() + 7, bounds.y() + 8, 0xFF72D7E4, false);
            context.drawText(textRenderer, fit(control.label, bounds.width() - 27),
                    bounds.x() + 22, bounds.y() + 8, 0xFFF2FAFC, false);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, control.icon,
                    bounds.x() + bounds.width() / 2, bounds.y() + 12, 0xFF72D7E4);
            context.drawCenteredTextWithShadow(textRenderer, fit(control.label, bounds.width() - 6),
                    bounds.x() + bounds.width() / 2, bounds.bottom() - 16, 0xFFF2FAFC);
        }
    }

    private void renderButton(DrawContext context, Control control, int mouseX, int mouseY, boolean danger) {
        GuiBounds bounds = control.bounds;
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = danger ? hovered ? 0xF078343C : 0xE0522A31 : hovered ? 0xED1C5261 : 0xE0143947;
        int border = danger ? 0xFFFF8D86 : hovered ? 0xFF54D9E7 : 0xFF2F7888;
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), fill);
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, border);
        context.drawText(textRenderer, control.icon, bounds.x() + 9, bounds.y() + 10,
                danger ? 0xFFFFB6BA : 0xFF72D7E4, false);
        context.drawText(textRenderer, fit(control.label, bounds.width() - 35),
                bounds.x() + 27, bounds.y() + 10, 0xFFF2FAFC, false);
    }

    private void renderEmpty(DrawContext context, String label) {
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                content.x() + content.width() / 2, content.y() + 38, 0xFF87ACB4);
    }

    private void renderClose(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = closeButton.contains(mouseX, mouseY);
        context.fill(closeButton.x(), closeButton.y(), closeButton.right(), closeButton.bottom(),
                hovered ? 0xFFE0545D : 0xCC8A3038);
        context.drawCenteredTextWithShadow(textRenderer, "×",
                closeButton.x() + closeButton.width() / 2, closeButton.y() + 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (closeButton.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (backControl("Zurück").bounds.contains(mouseX, mouseY)) {
            if (page == Page.HOME) returnToApps();
            else openPage(Page.HOME);
            return true;
        }
        if (page == Page.HOME && handleHomeClick(mouseX, mouseY)) return true;
        if (page == Page.DIAL && handleDialClick(mouseX, mouseY)) return true;
        if ((page == Page.CONTACTS || page == Page.EMERGENCY || page == Page.HISTORY)
                && handleRowClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleHomeClick(double mouseX, double mouseY) {
        int top = content.y() + (state.state() == PhoneCallState.IDLE || compact() ? 0 : 49);
        int gap = compact() ? 4 : 7;
        int tileWidth = (content.width() - gap) / 2;
        int tileHeight = homeTileHeight(top, gap);
        Page[] pages = {Page.DIAL, Page.CONTACTS, Page.EMERGENCY, Page.HISTORY};
        for (int index = 0; index < pages.length; index++) {
            GuiBounds bounds = new GuiBounds(content.x() + (index % 2) * (tileWidth + gap),
                    top + (index / 2) * (tileHeight + gap), tileWidth, tileHeight);
            if (bounds.contains(mouseX, mouseY)) {
                openPage(pages[index]);
                return true;
            }
        }
        return false;
    }

    private boolean handleDialClick(double mouseX, double mouseY) {
        int y = dialFieldY() + 24 + (compact() ? 4 : 8);
        int gap = 6;
        int half = (content.width() - gap) / 2;
        if (state.state() == PhoneCallState.IDLE
                && new GuiBounds(content.x(), y, content.width(), 28).contains(mouseX, mouseY)) {
            send(PhoneAction.CALL, dialValue);
            return true;
        }
        if (state.state() == PhoneCallState.INCOMING) {
            if (new GuiBounds(content.x(), y, half, 28).contains(mouseX, mouseY)) send(PhoneAction.ANSWER, "");
            else if (new GuiBounds(content.x() + half + gap, y, half, 28).contains(mouseX, mouseY))
                send(PhoneAction.DECLINE, "");
            else return false;
            return true;
        }
        if (state.state() == PhoneCallState.ACTIVE || state.state() == PhoneCallState.RINGING) {
            if (new GuiBounds(content.x(), y, half, 28).contains(mouseX, mouseY)) send(PhoneAction.HANGUP, "");
            else if (new GuiBounds(content.x() + half + gap, y, half, 28).contains(mouseX, mouseY))
                send(PhoneAction.TOGGLE_SPEAKER, "");
            else return false;
            return true;
        }
        return false;
    }

    private boolean handleRowClick(double mouseX, double mouseY) {
        int listBottom = content.bottom() - 38;
        if (mouseY < content.y() || mouseY >= listBottom) return false;
        int index = ((int) mouseY - content.y() + scroll) / 38;
        String number = switch (page) {
            case CONTACTS -> index < state.contacts().size() ? state.contacts().get(index).number() : "";
            case EMERGENCY -> index < state.emergencyNumbers().size() ? state.emergencyNumbers().get(index).number() : "";
            case HISTORY -> index < state.history().size() ? state.history().get(index).number() : "";
            default -> "";
        };
        if (number.isBlank()) return false;
        send(PhoneAction.CALL, number);
        page = Page.DIAL;
        dialValue = number;
        clearAndInit();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maximumScroll <= 0 || !content.contains(mouseX, mouseY)) return false;
        scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) Math.signum(verticalAmount) * 30));
        return true;
    }

    @Override
    public void close() {
        send(PhoneAction.CLOSE, "");
        handoff = true;
        if (client != null) client.setScreen(null);
    }

    @Override
    public void removed() {
        if (!handoff) send(PhoneAction.CLOSE, "");
        super.removed();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {}
    @Override protected void applyBlur(DrawContext context) {}

    private void returnToApps() {
        handoff = true;
        send(PhoneAction.RETURN_TO_APPS, "");
    }

    private void openPage(Page target) {
        page = target;
        scroll = 0;
        clearAndInit();
    }

    private void send(PhoneAction action, String value) {
        if (ClientPlayNetworking.canSend(PhoneActionPayload.ID))
            ClientPlayNetworking.send(new PhoneActionPayload(action.name(), value == null ? "" : value));
    }

    private Control backControl(String label) {
        return new Control(new GuiBounds(content.x(), content.bottom() - 30, content.width(), 27),
                "←", label, Command.BACK);
    }

    private boolean compact() {
        return content.height() < 130;
    }

    private int dialFieldY() {
        return content.y() + (compact() ? 0 : 39);
    }

    private int homeTileHeight(int top, int gap) {
        int available = Math.max(48, content.bottom() - 36 - top);
        return Math.max(24, Math.min(56, (available - gap) / 2));
    }

    private String pageTitle() {
        return switch (page) {
            case HOME -> state.number().isBlank() ? "Telefon" : state.number();
            case DIAL -> "Wählen & Gespräch";
            case CONTACTS -> "Kontakte";
            case EMERGENCY -> "Notruf-Schnellwahl";
            case HISTORY -> "Anrufhistorie";
        };
    }

    private String callState() {
        if (!state.coverage()) return "Kein Netz";
        return switch (state.state()) {
            case IDLE -> "Bereit";
            case INCOMING -> "Eingehender Anruf";
            case RINGING -> "Wird angerufen";
            case ACTIVE -> state.speaker() ? "Im Gespräch · Lautsprecher" : "Im Gespräch";
        };
    }

    private static String historyDirection(PhoneHistoryEntry entry) {
        return entry.direction().equals("INCOMING") ? "Eingehend" : "Ausgehend";
    }

    private static String historyOutcome(PhoneHistoryEntry entry) {
        return switch (entry.outcome()) {
            case "ANSWERED" -> entry.durationSeconds() > 0 ? "Angenommen " + entry.durationSeconds() + "s" : "Angenommen";
            case "REJECTED" -> "Abgelehnt";
            case "MISSED" -> "Verpasst";
            default -> "Fehlgeschlagen";
        };
    }

    private Text fit(String value, int width) {
        if (textRenderer.getWidth(value) <= width) return Text.literal(value);
        String ellipsis = "…";
        return Text.literal(textRenderer.trimToWidth(value,
                Math.max(1, width - textRenderer.getWidth(ellipsis))) + ellipsis);
    }

    private enum Page { HOME, DIAL, CONTACTS, EMERGENCY, HISTORY }
    private enum Command { PAGE_DIAL, PAGE_CONTACTS, PAGE_EMERGENCY, PAGE_HISTORY, CALL, ANSWER, DECLINE, HANGUP, SPEAKER, BACK }
    private record Control(GuiBounds bounds, String icon, String label, Command command) {}
    private record Row(String title, String subtitle, String number, boolean emergency) {}
    @FunctionalInterface private interface RowFactory { Row create(int index); }
}
