package net.evarius.terranexus.client.gui.phone;

import net.evarius.terranexus.client.gui.GuiBounds;
import net.evarius.terranexus.network.gui.GuiIcon;
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
    private TextFieldWidget contactNameField;
    private TextFieldWidget contactNumberField;
    private TextFieldWidget historyTargetField;
    private String dialValue = "";
    private String contactNameValue = "";
    private String contactNumberValue = "";
    private String editingContactName = "";
    private int scroll;
    private int maximumScroll;
    private boolean handoff;
    private boolean callRequested;
    private boolean confirmClearAll;

    public PhoneCallScreen(PhoneClientState state) {
        super(Text.translatable("gui.terranexus.phone.title"));
        this.state = state;
    }

    public void update(PhoneClientState updated) {
        boolean hadDialInput = showDialInput();
        PhoneCallState previousCallState = state.state();
        state = updated;
        if (callRequested && updated.state() == PhoneCallState.IDLE
                && (previousCallState != PhoneCallState.IDLE || !updated.notice().isBlank()))
            callRequested = false;
        if (client != null && page == Page.DIAL && hadDialInput != showDialInput()) clearAndInit();
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
        if (page == Page.DIAL && showDialInput()) {
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
        if (page == Page.CONTACT_EDITOR) {
            contactNameField = phoneField(contactNameFieldY(),
                    Text.translatable("gui.terranexus.phone.contact.name"), 80, contactNameValue);
            contactNameField.setEditable(editingContactName.isBlank());
            contactNumberField = phoneField(contactNumberFieldY(),
                    Text.translatable("gui.terranexus.phone.contact.number"), 64, contactNumberValue);
        }
        if (page == Page.HISTORY_ADMIN) {
            historyTargetField = phoneField(content.y() + (compact() ? 12 : 29),
                    Text.translatable("gui.terranexus.phone.history.player_uuid"), 36, "");
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
            case CONTACT_EDITOR -> renderContactEditor(context, mouseX, mouseY);
            case EMERGENCY -> renderEmergency(context, mouseX, mouseY);
            case HISTORY -> renderHistory(context, mouseX, mouseY);
            case HISTORY_ADMIN -> renderHistoryAdmin(context, mouseX, mouseY);
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
                new Control(new GuiBounds(content.x(), top, tileWidth, tileHeight), GuiIcon.PHONE, tr("gui.terranexus.phone.dial"), Command.PAGE_DIAL),
                new Control(new GuiBounds(content.x() + tileWidth + gap, top, tileWidth, tileHeight), GuiIcon.PERSON, tr("gui.terranexus.phone.contacts"), Command.PAGE_CONTACTS),
                new Control(new GuiBounds(content.x(), top + tileHeight + gap, tileWidth, tileHeight), GuiIcon.WARNING, tr("gui.terranexus.phone.emergency"), Command.PAGE_EMERGENCY),
                new Control(new GuiBounds(content.x() + tileWidth + gap, top + tileHeight + gap, tileWidth, tileHeight), GuiIcon.CLOCK, tr("gui.terranexus.phone.history"), Command.PAGE_HISTORY)
        );
        controls.forEach(control -> renderTile(context, control, mouseX, mouseY, false));
        renderButton(context, backControl(tr("gui.terranexus.phone.apps")), mouseX, mouseY, false);
    }

    private void renderDial(DrawContext context, int mouseX, int mouseY) {
        if (!compact()) renderCallPanel(context, new GuiBounds(content.x(), content.y(), content.width(), 36));
        GuiBounds field = new GuiBounds(content.x(), dialFieldY(), content.width(), 24);
        if (showDialInput()) {
            context.fill(field.x(), field.y(), field.right(), field.bottom(), 0xE0122F3C);
            context.fill(field.x(), field.bottom() - 1, field.right(), field.bottom(), 0xFF3DBFD0);
        } else if (callRequested && state.state() == PhoneCallState.IDLE) {
            context.fill(field.x(), field.y(), field.right(), field.bottom(), 0xD811303C);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.terranexus.phone.call_connecting"),
                    field.x() + field.width() / 2, field.y() + 8, 0xFF72D7E4);
        }
        int y = field.bottom() + (compact() ? 4 : 8);
        int gap = 6;
        int half = (content.width() - gap) / 2;
        List<Control> controls = new ArrayList<>();
        if (state.state() == PhoneCallState.IDLE && state.coverage() && !callRequested)
            controls.add(new Control(new GuiBounds(content.x(), y, content.width(), 28),
                    GuiIcon.PHONE, tr("gui.terranexus.phone.call"), Command.CALL));
        if (state.state() == PhoneCallState.INCOMING) {
            controls.add(new Control(new GuiBounds(content.x(), y, half, 28),
                    GuiIcon.CONFIRM, tr("gui.terranexus.phone.answer"), Command.ANSWER));
            controls.add(new Control(new GuiBounds(content.x() + half + gap, y, half, 28),
                    GuiIcon.CLOSE, tr("gui.terranexus.phone.decline"), Command.DECLINE));
        }
        if (state.state() == PhoneCallState.ACTIVE || state.state() == PhoneCallState.RINGING) {
            controls.add(new Control(new GuiBounds(content.x(), y, half, 28),
                    GuiIcon.CLOSE, tr("gui.terranexus.phone.hangup"), Command.HANGUP));
            controls.add(new Control(new GuiBounds(content.x() + half + gap, y, half, 28), GuiIcon.SETTINGS,
                    tr(state.speaker() ? "gui.terranexus.phone.speaker_on" : "gui.terranexus.phone.speaker"), Command.SPEAKER));
        }
        controls.forEach(control -> renderButton(context, control, mouseX, mouseY,
                control.command == Command.DECLINE || control.command == Command.HANGUP));
        renderButton(context, backControl(tr("gui.terranexus.back")), mouseX, mouseY, false);
    }

    private void renderContacts(DrawContext context, int mouseX, int mouseY) {
        if (state.contacts().isEmpty()) {
            renderEmpty(context, tr("gui.terranexus.phone.contacts.empty"));
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.contacts().size(), index -> {
            PhoneContact contact = state.contacts().get(index);
            return new Row(contact.name(), contact.number(), contact.number(), false, RowActions.CONTACT);
        });
        int gap = 5;
        int half = (content.width() - gap) / 2;
        renderButton(context, new Control(new GuiBounds(content.x(), content.bottom() - 30, half, 27),
                GuiIcon.ADD, tr("gui.terranexus.phone.contact.new"), Command.NEW_CONTACT), mouseX, mouseY, false);
        renderButton(context, new Control(new GuiBounds(content.x() + half + gap, content.bottom() - 30, half, 27),
                GuiIcon.BACK, tr("gui.terranexus.back"), Command.BACK), mouseX, mouseY, false);
    }

    private void renderContactEditor(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, tr(editingContactName.isBlank()
                        ? "gui.terranexus.phone.contact.name" : "gui.terranexus.phone.contact.name_fixed"),
                content.x() + 2, content.y() + (compact() ? 1 : 10), 0xFF87ACB4, false);
        context.drawText(textRenderer, tr("gui.terranexus.phone.contact.number"),
                content.x() + 2, content.y() + (compact() ? 34 : 52), 0xFF87ACB4, false);
        int gap = 5;
        int half = (content.width() - gap) / 2;
        int y = contactButtonsY();
        renderButton(context, new Control(new GuiBounds(content.x(), y, half, 27),
                GuiIcon.CONFIRM, tr("gui.terranexus.save"), Command.SAVE_CONTACT), mouseX, mouseY, false);
        renderButton(context, new Control(new GuiBounds(content.x() + half + gap, y, half, 27),
                GuiIcon.BACK, tr("gui.terranexus.cancel"), Command.BACK), mouseX, mouseY, false);
    }

    private void renderEmergency(DrawContext context, int mouseX, int mouseY) {
        if (state.emergencyNumbers().isEmpty()) {
            renderEmpty(context, tr("gui.terranexus.phone.emergency.empty"));
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.emergencyNumbers().size(), index -> {
            EmergencyNumber number = state.emergencyNumbers().get(index);
            return new Row(number.label(), number.number(), number.number(), true, RowActions.CALL_ONLY);
        });
        renderButton(context, backControl(tr("gui.terranexus.back")), mouseX, mouseY, false);
    }

    private void renderHistory(DrawContext context, int mouseX, int mouseY) {
        if (state.history().isEmpty()) {
            renderEmpty(context, tr("gui.terranexus.phone.history.empty"));
            maximumScroll = 0;
        } else renderRows(context, mouseX, mouseY, state.history().size(), index -> {
            PhoneHistoryEntry entry = state.history().get(index);
            String peer = entry.peer().isBlank() ? entry.number() : entry.peer();
            String details = historyDirection(entry) + " · " + historyOutcome(entry) + " · "
                    + HISTORY_TIME.format(Instant.ofEpochMilli(entry.timestamp()));
            return new Row(peer, details, entry.number(), false, RowActions.HISTORY);
        });
        int buttons = showHistoryAdminButton() ? 3 : 2;
        int gap = 4;
        int width = (content.width() - gap * (buttons - 1)) / buttons;
        int x = content.x();
        renderButton(context, new Control(new GuiBounds(x, content.bottom() - 30, width, 27),
                GuiIcon.CLOSE, tr("gui.terranexus.phone.history.clear"), Command.CLEAR_OWN_HISTORY), mouseX, mouseY, true);
        x += width + gap;
        if (showHistoryAdminButton()) {
            renderButton(context, new Control(new GuiBounds(x, content.bottom() - 30, width, 27),
                    GuiIcon.ADMIN, tr("gui.terranexus.phone.history.admin"), Command.HISTORY_ADMIN), mouseX, mouseY, false);
            x += width + gap;
        }
        renderButton(context, new Control(new GuiBounds(x, content.bottom() - 30, width, 27),
                GuiIcon.BACK, tr("gui.terranexus.back"), Command.BACK), mouseX, mouseY, false);
    }

    private void renderHistoryAdmin(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, tr("gui.terranexus.phone.history.delete_player"),
                content.x() + 2, content.y() + (compact() ? 1 : 12), 0xFF87ACB4, false);
        int y = content.y() + (compact() ? 36 : 60);
        int buttonHeight = compact() ? 24 : 27;
        renderButton(context, new Control(new GuiBounds(content.x(), y, content.width(), buttonHeight),
                GuiIcon.CLOSE, tr("gui.terranexus.phone.history.delete_target"), Command.CLEAR_TARGET_HISTORY), mouseX, mouseY, true);
        renderButton(context, new Control(new GuiBounds(content.x(), y + (compact() ? 27 : 34),
                content.width(), buttonHeight),
                GuiIcon.WARNING, tr(confirmClearAll ? "gui.terranexus.phone.history.confirm_clear_all"
                        : "gui.terranexus.phone.history.clear_all"),
                Command.CLEAR_ALL_HISTORIES), mouseX, mouseY, true);
        if (!compact()) renderButton(context, backControl(tr("gui.terranexus.back")), mouseX, mouseY, false);
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
            int actionWidth = row.actions == RowActions.CONTACT ? 46 : 27;
            context.drawText(textRenderer, fit(row.title, bounds.width() - actionWidth - 10),
                    bounds.x() + 8, bounds.y() + 6, 0xFFF2FAFC, false);
            context.drawText(textRenderer, fit(row.subtitle, bounds.width() - actionWidth - 10),
                    bounds.x() + 8, bounds.y() + 19, 0xFF87ACB4, false);
            if (row.actions == RowActions.CONTACT) {
                PhoneShellRenderer.icon(context, GuiIcon.SETTINGS, bounds.right() - 43, bounds.y() + 9, 16);
                PhoneShellRenderer.icon(context, GuiIcon.CLOSE, bounds.right() - 22, bounds.y() + 9, 16);
            } else if (row.actions == RowActions.HISTORY) {
                PhoneShellRenderer.icon(context, GuiIcon.CLOSE, bounds.right() - 22, bounds.y() + 9, 16);
            } else {
                PhoneShellRenderer.icon(context, GuiIcon.PHONE, bounds.right() - 24, bounds.y() + 9, 16);
            }
        }
    }

    private void renderCallPanel(DrawContext context, GuiBounds bounds) {
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xD811303C);
        context.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(),
                state.coverage() ? 0xFF3DBFD0 : 0xFFFF6E76);
        String peer = state.peer().isBlank() ? tr("gui.terranexus.phone.no_peer") : state.peer();
        if (state.savedContact() && !state.peerNumber().isBlank()) {
            peer += " · " + state.peerNumber();
        }
        context.drawText(textRenderer, fit(peer, bounds.width() - 13), bounds.x() + 8,
                bounds.y() + 7, 0xFFF2FAFC, false);
        String status = callState();
        if (!state.notice().isBlank()) {
            String notice = state.notice().startsWith("notice.rp-vca.")
                    ? Text.translatable(state.notice()).getString() : state.notice();
            status += " · " + notice;
        }
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
            PhoneShellRenderer.icon(context, control.icon, bounds.x() + 6, bounds.y() + 5, 16);
            context.drawText(textRenderer, fit(control.label, bounds.width() - 27),
                    bounds.x() + 22, bounds.y() + 8, 0xFFF2FAFC, false);
        } else {
            int iconSize = Math.min(26, bounds.height() - 27);
            PhoneShellRenderer.icon(context, control.icon,
                    bounds.x() + (bounds.width() - iconSize) / 2, bounds.y() + 8, iconSize);
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
        PhoneShellRenderer.icon(context, control.icon, bounds.x() + 7, bounds.y() + 6, 16);
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
        if (button == 1) {
            close();
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (closeButton.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (handlePageActions(mouseX, mouseY)) return true;
        if ((page == Page.HOME || page == Page.DIAL || page == Page.EMERGENCY
                || page == Page.HISTORY_ADMIN && !compact())
                && backControl(tr("gui.terranexus.back")).bounds.contains(mouseX, mouseY)) {
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

    private boolean handlePageActions(double mouseX, double mouseY) {
        int gap = 5;
        int half = (content.width() - gap) / 2;
        int footerY = content.bottom() - 30;
        if (page == Page.CONTACTS) {
            if (new GuiBounds(content.x(), footerY, half, 27).contains(mouseX, mouseY)) {
                openContactEditor(null);
                return true;
            }
            if (new GuiBounds(content.x() + half + gap, footerY, half, 27).contains(mouseX, mouseY)) {
                openPage(Page.HOME);
                return true;
            }
        }
        if (page == Page.CONTACT_EDITOR) {
            int y = contactButtonsY();
            if (new GuiBounds(content.x(), y, half, 27).contains(mouseX, mouseY)) {
                String name = contactNameField == null ? contactNameValue : contactNameField.getText();
                String number = contactNumberField == null ? contactNumberValue : contactNumberField.getText();
                if (!name.isBlank() && !number.isBlank()) {
                    send(PhoneAction.UPSERT_CONTACT, name, number);
                    openPage(Page.CONTACTS);
                }
                return true;
            }
            if (new GuiBounds(content.x() + half + gap, y, half, 27).contains(mouseX, mouseY)) {
                openPage(Page.CONTACTS);
                return true;
            }
        }
        if (page == Page.HISTORY) {
            int buttons = showHistoryAdminButton() ? 3 : 2;
            int historyGap = 4;
            int buttonWidth = (content.width() - historyGap * (buttons - 1)) / buttons;
            int index = (int) ((mouseX - content.x()) / (buttonWidth + historyGap));
            if (mouseY >= footerY && mouseY < footerY + 27 && mouseX >= content.x()
                    && mouseX < content.right()) {
                if (index == 0) send(PhoneAction.CLEAR_OWN_HISTORY, "");
                else if (showHistoryAdminButton() && index == 1) openPage(Page.HISTORY_ADMIN);
                else openPage(Page.HOME);
                return true;
            }
        }
        if (page == Page.HISTORY_ADMIN) {
            int y = content.y() + (compact() ? 36 : 60);
            int buttonHeight = compact() ? 24 : 27;
            if (new GuiBounds(content.x(), y, content.width(), buttonHeight).contains(mouseX, mouseY)) {
                send(PhoneAction.CLEAR_HISTORY, historyTargetField == null ? "" : historyTargetField.getText());
                return true;
            }
            if (new GuiBounds(content.x(), y + (compact() ? 27 : 34),
                    content.width(), buttonHeight).contains(mouseX, mouseY)) {
                if (confirmClearAll) {
                    send(PhoneAction.CLEAR_ALL_HISTORIES, "", "CONFIRM");
                    confirmClearAll = false;
                } else confirmClearAll = true;
                return true;
            }
        }
        return false;
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
        if (state.state() == PhoneCallState.IDLE && state.coverage() && !callRequested
                && new GuiBounds(content.x(), y, content.width(), 28).contains(mouseX, mouseY)) {
            requestCall(dialValue);
            return true;
        }
        if (state.state() == PhoneCallState.INCOMING) {
            if (new GuiBounds(content.x(), y, half, 28).contains(mouseX, mouseY))
                send(PhoneAction.ANSWER, state.callId());
            else if (new GuiBounds(content.x() + half + gap, y, half, 28).contains(mouseX, mouseY))
                send(PhoneAction.DECLINE, state.callId());
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
        String number = "";
        if (page == Page.CONTACTS && index < state.contacts().size()) {
            PhoneContact contact = state.contacts().get(index);
            if (mouseX >= content.right() - 23) {
                send(PhoneAction.REMOVE_CONTACT, contact.name());
                return true;
            }
            if (mouseX >= content.right() - 46) {
                openContactEditor(contact);
                return true;
            }
            number = contact.number();
        } else if (page == Page.EMERGENCY && index < state.emergencyNumbers().size()) {
            number = state.emergencyNumbers().get(index).number();
        } else if (page == Page.HISTORY && index < state.history().size()) {
            PhoneHistoryEntry entry = state.history().get(index);
            if (mouseX >= content.right() - 23) {
                send(PhoneAction.REMOVE_HISTORY_ENTRY, entry.id());
                return true;
            }
            number = entry.number();
        }
        if (number.isBlank()) return false;
        page = Page.DIAL;
        dialValue = number;
        if (state.coverage()) requestCall(number);
        else clearAndInit();
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
        confirmClearAll = false;
        clearAndInit();
    }

    private void openContactEditor(PhoneContact contact) {
        editingContactName = contact == null ? "" : contact.name();
        contactNameValue = contact == null ? "" : contact.name();
        contactNumberValue = contact == null ? "" : contact.number();
        openPage(Page.CONTACT_EDITOR);
    }

    private void send(PhoneAction action, String value) {
        send(action, value, "");
    }

    private void send(PhoneAction action, String value, String secondaryValue) {
        if (ClientPlayNetworking.canSend(PhoneActionPayload.ID))
            ClientPlayNetworking.send(new PhoneActionPayload(action.name(),
                    value == null ? "" : value, secondaryValue == null ? "" : secondaryValue));
    }

    private void requestCall(String destination) {
        String normalized = destination == null ? "" : destination.trim();
        if (normalized.isBlank() || callRequested) return;
        dialValue = normalized;
        callRequested = true;
        send(PhoneAction.CALL, normalized);
        clearAndInit();
    }

    private Control backControl(String label) {
        return new Control(new GuiBounds(content.x(), content.bottom() - 30, content.width(), 27),
                GuiIcon.BACK, label, Command.BACK);
    }

    private TextFieldWidget phoneField(int y, Text placeholder, int maximumLength, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, content.x() + 6, y,
                content.width() - 12, 20, placeholder);
        field.setMaxLength(maximumLength);
        field.setText(value);
        field.setPlaceholder(placeholder);
        field.setDrawsBackground(true);
        field.setEditableColor(0xFFE9F5F7);
        addDrawableChild(field);
        return field;
    }

    private int contactNameFieldY() {
        return content.y() + (compact() ? 12 : 24);
    }

    private int contactNumberFieldY() {
        return content.y() + (compact() ? 44 : 66);
    }

    private int contactButtonsY() {
        return compact() ? content.bottom() - 27 : Math.min(content.y() + 100, content.bottom() - 30);
    }

    private boolean showHistoryAdminButton() {
        return state.historyAdministrator() && !compact();
    }

    private boolean compact() {
        return content.height() < 130;
    }

    private int dialFieldY() {
        return content.y() + (compact() ? 0 : 39);
    }

    private boolean showDialInput() {
        return page == Page.DIAL && state.state() == PhoneCallState.IDLE && !callRequested;
    }

    private int homeTileHeight(int top, int gap) {
        int available = Math.max(48, content.bottom() - 36 - top);
        return Math.max(24, Math.min(56, (available - gap) / 2));
    }

    private String pageTitle() {
        return switch (page) {
            case HOME -> state.number().isBlank() ? tr("gui.terranexus.phone.app") : state.number();
            case DIAL -> tr("gui.terranexus.phone.dial_and_call");
            case CONTACTS -> tr("gui.terranexus.phone.contacts");
            case CONTACT_EDITOR -> tr(editingContactName.isBlank()
                    ? "gui.terranexus.phone.contact.new" : "gui.terranexus.phone.contact.edit");
            case EMERGENCY -> tr("gui.terranexus.phone.emergency_speed_dial");
            case HISTORY -> tr("gui.terranexus.phone.call_history");
            case HISTORY_ADMIN -> tr("gui.terranexus.phone.history.administration");
        };
    }

    private String callState() {
        if (!state.coverage()) return tr("gui.terranexus.phone.no_coverage");
        return switch (state.state()) {
            case IDLE -> tr("gui.terranexus.phone.state.ready");
            case INCOMING -> tr("gui.terranexus.phone.state.incoming");
            case RINGING -> tr("gui.terranexus.phone.state.ringing");
            case ACTIVE -> tr(state.speaker()
                    ? "gui.terranexus.phone.state.active_speaker" : "gui.terranexus.phone.state.active");
        };
    }

    private static String historyDirection(PhoneHistoryEntry entry) {
        return tr(entry.direction().equals("INCOMING")
                ? "gui.terranexus.phone.history.incoming" : "gui.terranexus.phone.history.outgoing");
    }

    private static String historyOutcome(PhoneHistoryEntry entry) {
        return switch (entry.outcome()) {
            case "COMPLETED" -> entry.durationSeconds() > 0
                    ? tr("gui.terranexus.phone.history.completed_duration", entry.durationSeconds())
                    : tr("gui.terranexus.phone.history.completed");
            case "DECLINED" -> tr("gui.terranexus.phone.history.declined");
            case "MISSED" -> tr("gui.terranexus.phone.history.missed");
            case "CANCELLED" -> tr("gui.terranexus.phone.history.cancelled");
            case "BUSY" -> tr("gui.terranexus.phone.history.busy");
            case "UNREACHABLE" -> tr("gui.terranexus.phone.history.unreachable");
            default -> tr("gui.terranexus.phone.history.failed");
        };
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private Text fit(String value, int width) {
        if (textRenderer.getWidth(value) <= width) return Text.literal(value);
        String ellipsis = "…";
        return Text.literal(textRenderer.trimToWidth(value,
                Math.max(1, width - textRenderer.getWidth(ellipsis))) + ellipsis);
    }

    private enum Page { HOME, DIAL, CONTACTS, CONTACT_EDITOR, EMERGENCY, HISTORY, HISTORY_ADMIN }
    private enum Command {
        PAGE_DIAL, PAGE_CONTACTS, PAGE_EMERGENCY, PAGE_HISTORY, CALL, ANSWER, DECLINE, HANGUP,
        SPEAKER, NEW_CONTACT, SAVE_CONTACT, CLEAR_OWN_HISTORY, HISTORY_ADMIN,
        CLEAR_TARGET_HISTORY, CLEAR_ALL_HISTORIES, BACK
    }
    private enum RowActions { CALL_ONLY, CONTACT, HISTORY }
    private record Control(GuiBounds bounds, GuiIcon icon, String label, Command command) {}
    private record Row(String title, String subtitle, String number, boolean emergency, RowActions actions) {}
    @FunctionalInterface private interface RowFactory { Row create(int index); }
}
