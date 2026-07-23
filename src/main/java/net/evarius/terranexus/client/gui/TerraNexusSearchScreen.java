package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.OpenSearchPayload;
import net.evarius.terranexus.network.gui.SearchActionPayload;
import net.evarius.terranexus.network.gui.SearchStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class TerraNexusSearchScreen extends Screen {
    private final OpenSearchPayload payload;
    private TextFieldWidget field;
    private String queryValue;
    private SearchState state = SearchState.INITIAL;
    private String statusMessage = "";
    private StructuredGuiLayout layout;
    private GuiBounds fieldBounds;
    private int stateTextY;
    private boolean serverFinished;
    private boolean cancelSent;

    public TerraNexusSearchScreen(OpenSearchPayload payload) {
        super(Text.literal(payload.title()));
        this.payload = payload;
        queryValue = payload.initialValue();
    }

    public boolean belongsTo(String token) { return payload.token().equals(token); }
    public void finishFromServer() { serverFinished = true; }

    public void updateStatus(SearchStatusPayload status) {
        if (!belongsTo(status.token())) return;
        state = status.state().equals("ERROR") ? SearchState.ERROR : SearchState.INITIAL;
        statusMessage = status.message();
        if (field != null) {
            field.setEditable(true);
            field.setFocused(true);
        }
    }

    @Override
    protected void init() {
        layout = StructuredGuiLayout.calculate(width, height);
        GuiBounds panel = layout.panel();
        int contentWidth = panel.width() - (layout.mode() == LayoutMode.COMPACT ? 16 : Math.max(24, layout.scaled(48)));
        int searchWidth = Math.max(80, Math.min(contentWidth, layout.scaled(430)));
        int searchHeight = Math.max(20, layout.scaled(28));
        int x = panel.x() + (panel.width() - searchWidth) / 2;
        int y = layout.mode() == LayoutMode.COMPACT ? panel.y() + 24
                : panel.y() + Math.max(34, layout.scaled(92));
        fieldBounds = new GuiBounds(x, y, searchWidth, searchHeight);

        field = new TextFieldWidget(textRenderer, x + 7, y + 4, searchWidth - 14, searchHeight - 8,
                Text.translatable("gui.terranexus.search.field"));
        field.setMaxLength(payload.maximumLength());
        field.setText(queryValue);
        field.setPlaceholder(Text.literal(payload.placeholder()));
        field.setDrawsBackground(false);
        field.setEditableColor(0xFFE9F5F7);
        field.setChangedListener(value -> queryValue = value);
        field.setFocused(true);
        addDrawableChild(field);
        setInitialFocus(field);

        int gap = Math.max(4, layout.gap());
        int buttonHeight = Math.max(20, layout.scaled(25));
        int buttonY = fieldBounds.bottom() + Math.max(8, layout.gap());
        if (layout.mode() == LayoutMode.COMPACT) {
            int clearWidth = 24;
            int buttonWidth = Math.max(28, (searchWidth - clearWidth - gap * 2) / 2);
            addDrawableChild(localButton(Text.translatable("gui.terranexus.search.submit"), net.evarius.terranexus.network.gui.GuiIcon.SEARCH,
                    new GuiBounds(fieldBounds.x(), buttonY, buttonWidth, buttonHeight), this::submit));
            addDrawableChild(localButton(Text.empty(), net.evarius.terranexus.network.gui.GuiIcon.WARNING,
                    new GuiBounds(fieldBounds.x() + buttonWidth + gap, buttonY, clearWidth, buttonHeight), this::clear));
            addDrawableChild(localButton(Text.translatable("gui.terranexus.search.back"), net.evarius.terranexus.network.gui.GuiIcon.BACK,
                    new GuiBounds(fieldBounds.right() - buttonWidth, buttonY, buttonWidth, buttonHeight), this::cancel));
            stateTextY = buttonY + buttonHeight + 3;
        } else {
            int buttonWidth = Math.max(StructuredGuiLayout.MIN_BUTTON_WIDTH,
                    Math.min(layout.scaled(110), (searchWidth - gap * 2) / 3));
            int groupWidth = buttonWidth * 3 + gap * 2;
            int buttonX = panel.x() + (panel.width() - groupWidth) / 2;
            addDrawableChild(localButton(Text.translatable("gui.terranexus.search.submit"), net.evarius.terranexus.network.gui.GuiIcon.SEARCH,
                    new GuiBounds(buttonX, buttonY, buttonWidth, buttonHeight), this::submit));
            addDrawableChild(localButton(Text.translatable("gui.terranexus.search.clear"), net.evarius.terranexus.network.gui.GuiIcon.WARNING,
                    new GuiBounds(buttonX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight), this::clear));
            addDrawableChild(localButton(Text.translatable("gui.terranexus.search.back"), net.evarius.terranexus.network.gui.GuiIcon.BACK,
                    new GuiBounds(buttonX + (buttonWidth + gap) * 2, buttonY, buttonWidth, buttonHeight), this::cancel));
            stateTextY = fieldBounds.bottom() + Math.max(40, layout.scaled(54));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x90030A12);
        GuiBounds panel = layout.panel();
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.BACKGROUND,
                panel.x(), panel.y(), panel.width(), panel.height());
        context.drawTextWithShadow(textRenderer, title, layout.titleX(), layout.titleY(), 0xFFE8F7FA);
        GuiRenderUtil.panel(context, fieldBounds, field.isFocused());
        super.render(context, mouseX, mouseY, deltaTicks);

        Text stateText = switch (state) {
            case INITIAL -> Text.translatable("gui.terranexus.search.initial");
            case LOADING -> Text.translatable("gui.terranexus.search.loading");
            case ERROR -> Text.literal(statusMessage);
        };
        int color = state == SearchState.ERROR ? 0xFFFF8D86 : state == SearchState.LOADING ? 0xFF73D8E8 : 0xFF7FA5AF;
        context.drawCenteredTextWithShadow(textRenderer, stateText, panel.x() + panel.width() / 2,
                Math.min(layout.statusY() - 12, stateTextY), color);
        if (layout.mode() != LayoutMode.COMPACT)
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.terranexus.secure_session"),
                    layout.statusX(), layout.statusY(), 0xFF6EA1AA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && state != SearchState.LOADING) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { cancel(); }

    @Override
    public void removed() {
        if (!serverFinished && !cancelSent) send("CANCEL", "");
        super.removed();
    }

    private GuiActionButton localButton(Text label, net.evarius.terranexus.network.gui.GuiIcon icon,
                                        GuiBounds bounds, Runnable action) {
        return new GuiActionButton(-1, net.evarius.terranexus.network.gui.GuiAction.ACTIVATE_ELEMENT,
                icon, label, "", bounds.x(), bounds.y(), bounds.width(), bounds.height(), true, false,
                GuiVisualStyle.TOOLBAR, (ignoredAction, ignoredId) -> action.run());
    }

    private void submit() {
        if (state == SearchState.LOADING) return;
        String query = field == null ? queryValue : field.getText().trim();
        if (query.length() < payload.minimumLength()) {
            state = SearchState.ERROR;
            statusMessage = Text.translatable("gui.terranexus.search.minimum_length",
                    payload.minimumLength()).getString();
            return;
        }
        state = SearchState.LOADING;
        statusMessage = "";
        field.setEditable(false);
        send("SUBMIT", query);
    }

    private void clear() {
        if (field == null || state == SearchState.LOADING) return;
        field.setText("");
        field.setFocused(true);
        state = SearchState.INITIAL;
        statusMessage = "";
    }

    private void cancel() {
        if (cancelSent || serverFinished) return;
        cancelSent = true;
        state = SearchState.LOADING;
        if (field != null) field.setEditable(false);
        send("CANCEL", "");
    }

    private void send(String action, String query) {
        if (ClientPlayNetworking.canSend(SearchActionPayload.ID))
            ClientPlayNetworking.send(new SearchActionPayload(payload.token(), action, query));
    }

    private enum SearchState { INITIAL, LOADING, ERROR }
}
