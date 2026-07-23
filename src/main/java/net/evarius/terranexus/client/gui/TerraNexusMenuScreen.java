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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TerraNexusMenuScreen extends Screen {
    private static final int TOOLTIP_WIDTH = 230;
    private static final int TABLE_HEADER_HEIGHT = 16;

    private String sessionToken;
    private List<GuiMenuElement> elements;
    private final List<GuiActionButton> buttons = new ArrayList<>();
    private final List<GuiListRow> rows = new ArrayList<>();
    private final List<InfoPlacement> infoCards = new ArrayList<>();
    private final GuiScrollableList scrollableList = new GuiScrollableList();
    private final GuiScrollableGrid scrollableGrid = new GuiScrollableGrid();
    private boolean serverClosing;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int titleX;
    private int titleY;
    private int statusX;
    private int statusY;
    private int selectedElementId = Integer.MIN_VALUE;
    private GuiPageModel pageModel;
    private StructuredGuiLayout structuredLayout;
    private GuiBounds tableHeader;
    private GuiBounds detailPanel;
    private GuiActionButton detailOpenButton;
    private int toolbarPage;
    private String layoutIdentity = "";

    public TerraNexusMenuScreen(OpenGuiPayload payload) {
        super(Text.literal(payload.title()));
        sessionToken = payload.sessionToken();
        elements = payload.elements();
    }

    public boolean belongsTo(String token) { return sessionToken.equals(token); }

    public void update(OpenGuiPayload payload) {
        sessionToken = payload.sessionToken();
        elements = payload.elements();
        if (elements.stream().noneMatch(element -> element.id() == selectedElementId))
            selectedElementId = Integer.MIN_VALUE;
        if (client != null) clearAndInit();
    }

    public void closeFromServer() {
        serverClosing = true;
        if (client != null) client.setScreen(null);
    }

    @Override
    protected void init() {
        rebuildLayout();
    }

    private void rebuildLayout() {
        buttons.clear();
        rows.clear();
        infoCards.clear();
        tableHeader = null;
        detailPanel = null;
        detailOpenButton = null;
        scrollableList.hide();
        scrollableGrid.hide();
        pageModel = GuiPageModel.create(title.getString(), elements);
        String nextIdentity = title.getString() + '|' + pageModel.type();
        if (!layoutIdentity.equals(nextIdentity)) {
            toolbarPage = 0;
            scrollableList.reset();
            scrollableGrid.reset();
        }
        layoutIdentity = nextIdentity;

        if (pageModel.type() == GuiPageType.ADMIN_DESKTOP) {
            buildAdminDesktop();
            return;
        }

        structuredLayout = StructuredGuiLayout.calculate(width, height);
        setCommonBounds(structuredLayout.panel(), structuredLayout.titleX(), structuredLayout.titleY(),
                structuredLayout.statusX(), structuredLayout.statusY());
        switch (pageModel.type()) {
            case BANK_ACCOUNTS, AUDIT_LOG, LIST -> buildListPage();
            case CENTRAL_BANK -> buildCentralBankDashboard();
            case LAND_TOOL -> buildLandTool();
            case DETAIL -> buildDetailPage();
            case HUB -> buildHubPage();
            default -> { }
        }
        addCloseButton(structuredLayout.close());
    }

    private void buildAdminDesktop() {
        if (LayoutMode.detect(width, height) != LayoutMode.LARGE) {
            structuredLayout = StructuredGuiLayout.calculate(width, height);
            setCommonBounds(structuredLayout.panel(), structuredLayout.titleX(), structuredLayout.titleY(),
                    structuredLayout.statusX(), structuredLayout.statusY());
            addHeaderSummary(true);
            addActionGrid(pageModel.elements(GuiElementRole.MODULE), structuredLayout.content(0),
                    GuiVisualStyle.MODULE_TILE);
            addCloseButton(structuredLayout.close());
            return;
        }
        AdminDesktopLayout layout = AdminDesktopLayout.calculate(width, height, elements);
        setCommonBounds(layout.panel(), layout.titleX(), layout.titleY(), layout.statusX(), layout.statusY());
        for (GuiMenuElement element : elements) {
            GuiBounds bounds = layout.element(element.id());
            if (bounds != null) addActionButton(element, bounds, AdminDesktopLayout.displayLabel(element),
                    GuiVisualStyle.MODULE_TILE);
        }
        addCloseButton(layout.close());
    }

    private void buildListPage() {
        addHeaderSummary(false);
        List<GuiMenuElement> toolbar = toolbarElements();
        int toolbarCount = addToolbar(toolbar);
        StructuredGuiLayout.ContentSplit split = structuredLayout.splitContent(toolbarCount, true);
        int headerHeight = structuredLayout.mode() == LayoutMode.COMPACT ? 0
                : Math.max(13, structuredLayout.scaled(TABLE_HEADER_HEIGHT));
        tableHeader = new GuiBounds(split.primary().x(), split.primary().y(), split.primary().width(), headerHeight);
        GuiBounds viewport = new GuiBounds(split.primary().x(), tableHeader.bottom(), split.primary().width(),
                Math.max(1, split.primary().bottom() - tableHeader.bottom()));
        List<GuiMenuElement> data = pageModel.elements(GuiElementRole.LIST_ROW);
        ensureSelection(data);
        scrollableList.configure(data, viewport, structuredLayout.listRowHeight());
        for (GuiScrollableList.VisibleRow visible : scrollableList.visibleRows()) {
            GuiListRow row = new GuiListRow(visible.element(), pageModel.type(), visible.index(),
                    visible.element().id() == selectedElementId, visible.bounds(), this::selectElement,
                    id -> performAction(GuiAction.ACTIVATE_ELEMENT, id));
            rows.add(addDrawableChild(row));
        }
        configureDetail(split.detail());
    }

    private void buildCentralBankDashboard() {
        addHeaderSummary(true);
        List<GuiMenuElement> navigation = pageModel.elements(GuiElementRole.NAVIGATION);
        int toolbarCount = addToolbar(navigation);
        GuiBounds content = structuredLayout.content(toolbarCount);
        List<GuiMenuElement> metrics = pageModel.elements(GuiElementRole.METRIC).stream()
                .sorted(Comparator.comparingInt(element -> centralMetricOrder(element.id()))).toList();
        int columns = content.width() >= 360 ? 4 : 2;
        int gap = structuredLayout.gap();
        int metricRows = Math.max(1, (metrics.size() + columns - 1) / columns);
        int actionHeight = Math.max(24, structuredLayout.scaled(44));
        int availableMetrics = Math.max(metricRows * 18,
                content.height() - actionHeight - gap);
        int metricHeight = Math.max(18, (availableMetrics - gap * (metricRows - 1)) / metricRows);
        int metricWidth = Math.max(24, (content.width() - gap * (columns - 1)) / columns);
        for (int index = 0; index < metrics.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            infoCards.add(new InfoPlacement(metrics.get(index), new GuiBounds(
                    content.x() + column * (metricWidth + gap), content.y() + row * (metricHeight + gap),
                    metricWidth, metricHeight), false));
        }

        List<GuiMenuElement> actions = pageModel.elements(GuiElementRole.ACTION);
        int actionY = content.bottom() - actionHeight;
        int actionWidth = Math.max(28, (content.width() - gap * Math.max(0, actions.size() - 1))
                / Math.max(1, actions.size()));
        for (int index = 0; index < actions.size(); index++)
            addActionButton(actions.get(index), new GuiBounds(content.x() + index * (actionWidth + gap),
                    actionY, actionWidth, actionHeight), Text.literal(actions.get(index).label()),
                    GuiVisualStyle.ACTION_TILE);
    }

    private void buildLandTool() {
        addHeaderSummary(false);
        List<GuiMenuElement> toolbar = toolbarElements();
        int toolbarCount = addToolbar(toolbar);
        StructuredGuiLayout.ContentSplit split = structuredLayout.splitContent(toolbarCount, true);
        GuiBounds primary = split.primary();
        List<GuiMenuElement> chunks = pageModel.elements(GuiElementRole.GRID_CELL);
        List<GuiMenuElement> properties = pageModel.elements(GuiElementRole.LIST_ROW);
        List<GuiMenuElement> selectable = new ArrayList<>(chunks);
        selectable.addAll(properties);
        ensureSelection(selectable);
        int gap = structuredLayout.gap();
        if (structuredLayout.mode() == LayoutMode.COMPACT) {
            scrollableGrid.configure(selectable, primary, 64, structuredLayout.minimumCardHeight(), gap);
            for (GuiScrollableGrid.VisibleCell visible : scrollableGrid.visibleCells()) {
                if (visible.element().id() >= 45)
                    addActionButton(visible.element(), visible.bounds(), Text.literal(visible.element().label()),
                            GuiVisualStyle.GRID_CELL);
                else
                    addSelectableButton(visible.element(), visible.bounds(), GuiVisualStyle.GRID_CELL);
            }
            configureDetail(split.detail());
            return;
        }
        int gridHeight = properties.isEmpty() ? primary.height() : Math.max(1, Math.round(primary.height() * 0.58F));
        GuiBounds chunkViewport = new GuiBounds(primary.x(), primary.y(), primary.width(), gridHeight);
        scrollableGrid.configure(chunks, chunkViewport, Math.max(64, structuredLayout.minimumCardWidth() - 20),
                structuredLayout.minimumCardHeight(), gap);
        for (GuiScrollableGrid.VisibleCell visible : scrollableGrid.visibleCells())
            addSelectableButton(visible.element(), visible.bounds(), GuiVisualStyle.GRID_CELL);

        int listTop = primary.y() + gridHeight + gap;
        if (listTop < primary.bottom() && !properties.isEmpty()) {
            tableHeader = new GuiBounds(primary.x(), listTop, primary.width(),
                    Math.max(13, structuredLayout.scaled(TABLE_HEADER_HEIGHT)));
            GuiBounds viewport = new GuiBounds(primary.x(), tableHeader.bottom(), primary.width(),
                    Math.max(1, primary.bottom() - tableHeader.bottom()));
            scrollableList.configure(properties, viewport, structuredLayout.listRowHeight());
            for (GuiScrollableList.VisibleRow visible : scrollableList.visibleRows()) {
                GuiListRow row = new GuiListRow(visible.element(), GuiPageType.LIST, visible.index(),
                        visible.element().id() == selectedElementId, visible.bounds(), this::selectElement,
                        id -> performAction(GuiAction.ACTIVATE_ELEMENT, id));
                rows.add(addDrawableChild(row));
            }
        }
        configureDetail(split.detail());
    }

    private void buildDetailPage() {
        addHeaderSummary(true);
        List<GuiMenuElement> navigation = pageModel.elements(GuiElementRole.NAVIGATION);
        int toolbarCount = addToolbar(navigation);
        GuiBounds content = structuredLayout.content(toolbarCount);
        List<GuiMenuElement> information = pageModel.elements(GuiElementRole.DETAIL_INFO);
        List<GuiMenuElement> actions = pageModel.elements(GuiElementRole.ACTION);
        int gap = structuredLayout.gap();
        int columns = content.width() >= 420 ? 2 : 1;
        int width = (content.width() - gap * (columns - 1)) / columns;
        int infoHeight = Math.max(24, structuredLayout.scaled(42));
        int index = 0;
        for (GuiMenuElement element : information) {
            infoCards.add(new InfoPlacement(element, new GuiBounds(content.x() + (index % columns) * (width + gap),
                    content.y() + (index / columns) * (infoHeight + gap), width, infoHeight), false));
            index++;
        }
        int actionY = content.y() + ((information.size() + columns - 1) / columns) * (infoHeight + gap);
        addActionGrid(actions, new GuiBounds(content.x(), actionY, content.width(),
                Math.max(1, content.bottom() - actionY)), GuiVisualStyle.ACTION_TILE);
    }

    private void buildHubPage() {
        addHeaderSummary(true);
        List<GuiMenuElement> navigation = pageModel.elements(GuiElementRole.NAVIGATION);
        int toolbarCount = addToolbar(navigation);
        List<GuiMenuElement> modules = pageModel.elements(GuiElementRole.MODULE);
        GuiBounds content = structuredLayout.content(toolbarCount);
        addActionGrid(modules, content, GuiVisualStyle.MODULE_TILE);
        for (GuiMenuElement element : pageModel.elements(GuiElementRole.DETAIL_INFO)) {
            if (element.id() == 4) continue;
            infoCards.add(new InfoPlacement(element, structuredLayout.summary(), false));
        }
    }

    private void addActionGrid(List<GuiMenuElement> source, GuiBounds area, GuiVisualStyle style) {
        if (source.isEmpty()) return;
        int gap = structuredLayout.gap();
        scrollableGrid.configure(source, area, structuredLayout.minimumCardWidth(),
                structuredLayout.minimumCardHeight(), gap);
        for (GuiScrollableGrid.VisibleCell visible : scrollableGrid.visibleCells())
            addActionButton(visible.element(), visible.bounds(), Text.literal(visible.element().label()), style);
    }

    private void addHeaderSummary(boolean emphasized) {
        GuiMenuElement header = pageModel.header();
        if (header != null) infoCards.add(new InfoPlacement(header, structuredLayout.summary(), emphasized));
    }

    private List<GuiMenuElement> toolbarElements() {
        List<GuiMenuElement> toolbar = new ArrayList<>(pageModel.elements(GuiElementRole.TOOLBAR));
        toolbar.addAll(pageModel.elements(GuiElementRole.NAVIGATION));
        toolbar.sort(Comparator.comparingInt(this::toolbarWeight));
        return toolbar;
    }

    private int toolbarWeight(GuiMenuElement element) {
        if (pageModel.type() == GuiPageType.LAND_TOOL) {
            return switch (element.id()) {
                case 10 -> 1;
                case 12 -> 2;
                case 13 -> 3;
                case 14 -> 4;
                case 15 -> 5;
                case 16 -> 6;
                case 6 -> 7;
                case 7 -> 8;
                case 17 -> 9;
                case 8 -> 30;
                default -> 20;
            };
        }
        return navigationWeight(element.label());
    }

    private static int centralMetricOrder(int id) {
        return switch (id) {
            case 11 -> 1;
            case 13 -> 2;
            case 15 -> 3;
            case 26 -> 4;
            case 20 -> 5;
            case 22 -> 6;
            case 24 -> 7;
            default -> 20;
        };
    }

    private int navigationWeight(String label) {
        String value = label.toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("zurück")) return 30;
        if (value.contains("seite")) return 20;
        return 10;
    }

    private int addToolbar(List<GuiMenuElement> toolbar) {
        if (toolbar.isEmpty()) return 0;
        int maximumVisible = structuredLayout.mode() == LayoutMode.COMPACT
                ? (structuredLayout.panel().width() >= 250 ? 3 : 2)
                : structuredLayout.mode() == LayoutMode.MEDIUM ? 6 : toolbar.size();
        boolean paged = toolbar.size() > maximumVisible;
        int pageSize = paged ? Math.max(1, maximumVisible - 1) : toolbar.size();
        int pageCount = Math.max(1, (toolbar.size() + pageSize - 1) / pageSize);
        toolbarPage = Math.floorMod(toolbarPage, pageCount);
        int start = toolbarPage * pageSize;
        List<GuiMenuElement> visible = toolbar.subList(start, Math.min(toolbar.size(), start + pageSize));
        int renderedCount = visible.size() + (paged ? 1 : 0);
        List<GuiBounds> bounds = structuredLayout.toolbar(renderedCount);
        for (int index = 0; index < visible.size(); index++) {
            GuiMenuElement element = visible.get(index);
            String normalized = element.label().toLowerCase(java.util.Locale.ROOT);
            GuiVisualStyle style = normalized.startsWith("zur") ? GuiVisualStyle.NAVIGATION
                    : normalized.contains("suche") ? GuiVisualStyle.SEARCH_FIELD : GuiVisualStyle.TOOLBAR;
            addActionButton(element, bounds.get(index), Text.literal(element.label()), style);
        }
        if (paged) {
            GuiBounds moreBounds = bounds.getLast();
            Text label = Text.translatable("gui.terranexus.toolbar.more", toolbarPage + 1, pageCount);
            buttons.add(addDrawableChild(new GuiActionButton(-3, GuiAction.ACTIVATE_ELEMENT, GuiIcon.SETTINGS,
                    label, Text.translatable("gui.terranexus.toolbar.more.tooltip").getString(),
                    moreBounds.x(), moreBounds.y(), moreBounds.width(), moreBounds.height(), true, false,
                    GuiVisualStyle.NAVIGATION, (ignoredAction, ignoredId) -> {
                        toolbarPage = (toolbarPage + 1) % pageCount;
                        clearAndInit();
                    })));
        }
        return renderedCount;
    }

    private void configureDetail(GuiBounds bounds) {
        detailPanel = bounds;
        if (bounds == null) return;
        int buttonHeight = Math.max(17, structuredLayout.scaled(22));
        int buttonWidth = Math.min(bounds.width() - 12, Math.max(58, structuredLayout.scaled(90)));
        GuiBounds action = new GuiBounds(bounds.right() - buttonWidth - 6,
                bounds.bottom() - buttonHeight - 6, buttonWidth, buttonHeight);
        detailOpenButton = new GuiActionButton(-2, GuiAction.ACTIVATE_ELEMENT, GuiIcon.CONFIRM,
                Text.translatable("gui.terranexus.detail.open"), "", action.x(), action.y(), action.width(), action.height(),
                selectedElement() != null && selectedElement().enabled(), false, GuiVisualStyle.DETAIL_ACTION,
                (ignoredAction, ignoredId) -> activateSelected());
        buttons.add(addDrawableChild(detailOpenButton));
    }

    private void ensureSelection(List<GuiMenuElement> candidates) {
        if (candidates.stream().noneMatch(element -> element.id() == selectedElementId))
            selectedElementId = candidates.isEmpty() ? Integer.MIN_VALUE : candidates.getFirst().id();
    }

    private void selectElement(int id) {
        selectedElementId = id;
        rows.forEach(row -> row.setSelected(row.element().id() == id));
        if (detailOpenButton != null) {
            GuiMenuElement selected = selectedElement();
            detailOpenButton.active = selected != null && selected.enabled();
        }
    }

    private void activateSelected() {
        GuiMenuElement selected = selectedElement();
        if (selected != null && selected.enabled()) performAction(GuiAction.ACTIVATE_ELEMENT, selected.id());
    }

    private GuiMenuElement selectedElement() {
        return elements.stream().filter(element -> element.id() == selectedElementId).findFirst().orElse(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Minecraft 1.21.8 already applies blur before this method; rendering it again crashes.
        context.fill(0, 0, width, height, 0x78030A12);
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.BACKGROUND, panelX, panelY, panelWidth, panelHeight);
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFE8F7FA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.terranexus.secure_session"),
                statusX, statusY, 0xFF6EA1AA);
        for (InfoPlacement card : infoCards)
            GuiInfoCard.render(context, textRenderer, card.bounds(), card.element(), card.emphasized());
        if (tableHeader != null) renderTableHeader(context);
        if (detailPanel != null) GuiDetailPanel.render(context, textRenderer, detailPanel,
                selectedElement(), pageModel.type());
        super.render(context, mouseX, mouseY, deltaTicks);
        scrollableList.renderScrollbar(context);
        scrollableGrid.renderScrollbar(context);
        if (pageModel != null && (pageModel.type() == GuiPageType.BANK_ACCOUNTS
                || pageModel.type() == GuiPageType.AUDIT_LOG || pageModel.type() == GuiPageType.LIST)
                && pageModel.elements(GuiElementRole.LIST_ROW).isEmpty() && tableHeader != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.terranexus.search.empty"),
                    tableHeader.x() + tableHeader.width() / 2, tableHeader.bottom() + 12, 0xFF7898A2);
        }
        renderHoveredTooltip(context, mouseX, mouseY);
    }

    private void renderTableHeader(DrawContext context) {
        context.fill(tableHeader.x(), tableHeader.y(), tableHeader.right(), tableHeader.bottom(), 0xD0132B38);
        if (pageModel.type() == GuiPageType.BANK_ACCOUNTS) {
            drawHeader(context, "gui.terranexus.table.name", 0.04F);
            drawHeader(context, "gui.terranexus.table.account", 0.44F);
            drawHeader(context, "gui.terranexus.table.balance", 0.72F);
            drawHeader(context, "gui.terranexus.table.status", 0.89F);
        } else if (pageModel.type() == GuiPageType.AUDIT_LOG) {
            drawHeader(context, "gui.terranexus.table.time", 0.04F);
            drawHeader(context, "gui.terranexus.table.action", 0.23F);
            drawHeader(context, "gui.terranexus.table.source", 0.42F);
            drawHeader(context, "gui.terranexus.table.category", 0.67F);
            drawHeader(context, "gui.terranexus.table.description", 0.82F);
        } else {
            drawHeader(context, "gui.terranexus.table.entry", 0.04F);
            drawHeader(context, "gui.terranexus.table.details", 0.44F);
        }
    }

    private void drawHeader(DrawContext context, String key, float relativeX) {
        context.drawText(textRenderer, Text.translatable(key),
                tableHeader.x() + Math.round(tableHeader.width() * relativeX), tableHeader.y() + 4,
                0xFF76AEBB, false);
    }

    private void renderHoveredTooltip(DrawContext context, int mouseX, int mouseY) {
        String tooltip = buttons.stream().filter(button -> button.isHovered() && !button.tooltipText().isBlank())
                .map(GuiActionButton::tooltipText).findFirst().orElseGet(() -> rows.stream()
                        .filter(row -> row.isHovered() && !row.tooltipText().isBlank())
                        .map(GuiListRow::tooltipText).findFirst().orElse(""));
        if (!tooltip.isBlank()) renderCustomTooltip(context, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scrollableList.scroll(mouseX, mouseY, verticalAmount)) {
            clearAndInit();
            return true;
        }
        if (scrollableGrid.scroll(mouseX, mouseY, verticalAmount)) {
            clearAndInit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        performAction(GuiAction.CLOSE, -1);
    }

    @Override
    public void removed() {
        if (!serverClosing && ClientPlayNetworking.canSend(GuiActionPayload.ID))
            ClientPlayNetworking.send(new GuiActionPayload(sessionToken, GuiAction.CLOSE.name(), -1));
        super.removed();
    }

    private void performAction(GuiAction action, int elementId) {
        if (!ClientPlayNetworking.canSend(GuiActionPayload.ID)) return;
        ClientPlayNetworking.send(new GuiActionPayload(sessionToken, action.name(), elementId));
        if (action == GuiAction.CLOSE) {
            serverClosing = true;
            if (client != null) client.setScreen(null);
        } else {
            buttons.forEach(button -> button.active = false);
            rows.forEach(row -> row.active = false);
        }
    }

    private void renderCustomTooltip(DrawContext context, String tooltip, int mouseX, int mouseY) {
        int tooltipWidth = Math.min(TOOLTIP_WIDTH, Math.max(80, width - 8));
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(tooltip), tooltipWidth - 16);
        int tooltipHeight = 12 + lines.size() * 10;
        int x = Math.max(4, Math.min(mouseX + 12, width - tooltipWidth - 4));
        int y = Math.max(4, Math.min(mouseY + 10, height - tooltipHeight - 4));
        context.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xF20B1622);
        context.fill(x, y, x + tooltipWidth, y + 1, 0xFF39C5D8);
        context.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight, 0xFF1B6672);
        int lineY = y + 7;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, x + 8, lineY, 0xFFE7F1F4);
            lineY += 10;
        }
    }

    private void addActionButton(GuiMenuElement element, GuiBounds bounds, Text label, GuiVisualStyle style) {
        buttons.add(addDrawableChild(new GuiActionButton(element.id(), GuiAction.ACTIVATE_ELEMENT,
                element.resolvedIcon(), label, element.tooltip(), bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                element.enabled(), element.selected(), style, this::performAction)));
    }

    private void addSelectableButton(GuiMenuElement element, GuiBounds bounds, GuiVisualStyle style) {
        buttons.add(addDrawableChild(new GuiActionButton(element.id(), GuiAction.ACTIVATE_ELEMENT,
                element.resolvedIcon(), Text.literal(element.label()), element.tooltip(), bounds.x(), bounds.y(),
                bounds.width(), bounds.height(), true, element.id() == selectedElementId, style,
                (ignoredAction, id) -> {
                    selectedElementId = id;
                    clearAndInit();
                })));
    }

    private void addCloseButton(GuiBounds bounds) {
        buttons.add(addDrawableChild(new GuiActionButton(-1, GuiAction.CLOSE, GuiIcon.CLOSE,
                Text.empty(), Text.translatable("gui.terranexus.close").getString(), bounds.x(), bounds.y(),
                bounds.width(), bounds.height(), true, false, GuiVisualStyle.NAVIGATION, this::performAction)));
    }

    private void setCommonBounds(GuiBounds panel, int titleX, int titleY, int statusX, int statusY) {
        panelX = panel.x();
        panelY = panel.y();
        panelWidth = panel.width();
        panelHeight = panel.height();
        this.titleX = titleX;
        this.titleY = titleY;
        this.statusX = statusX;
        this.statusY = statusY;
    }

    private record InfoPlacement(GuiMenuElement element, GuiBounds bounds, boolean emphasized) { }
}
