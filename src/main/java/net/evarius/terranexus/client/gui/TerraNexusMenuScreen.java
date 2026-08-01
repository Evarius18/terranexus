package net.evarius.terranexus.client.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evarius.terranexus.identity.CitizenPortraitSnapshot;
import net.evarius.terranexus.network.gui.GuiAction;
import net.evarius.terranexus.network.gui.GuiActionPayload;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.evarius.terranexus.network.gui.OpenGuiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.SkinTextures;
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
    private GuiBounds documentPaper;
    private GuiBounds identityCardBounds;
    private SkinTextures identityPortrait;
    private String identityPortraitSource = "";
    private int toolbarPage;
    private String layoutIdentity = "";

    public TerraNexusMenuScreen(OpenGuiPayload payload) {
        super(payload.title());
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
        documentPaper = null;
        identityCardBounds = null;
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
        if (pageModel.type() == GuiPageType.IDENTITY_CARD) {
            buildIdentityCard();
            return;
        }

        structuredLayout = StructuredGuiLayout.calculate(width, height);
        setCommonBounds(structuredLayout.panel(), structuredLayout.titleX(), structuredLayout.titleY(),
                structuredLayout.statusX(), structuredLayout.statusY());
        switch (pageModel.type()) {
            case BANK_ACCOUNTS, AUDIT_LOG, LIST -> buildListPage();
            case CENTRAL_BANK -> buildCentralBankDashboard();
            case LAND_TOOL -> buildLandTool();
            case DOCUMENT_LIST -> buildDocumentList();
            case DOCUMENT_DETAIL -> buildDocumentDetail();
            case SHOP_DIALOG -> buildShopDialog();
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

    private void buildDocumentList() {
        documentPaper = documentBounds();
        addHeaderSummary(false);
        int toolbarCount = addToolbar(toolbarElements());
        GuiBounds content = structuredLayout.content(toolbarCount);
        int headerHeight = structuredLayout.mode() == LayoutMode.COMPACT ? 0
                : Math.max(13, structuredLayout.scaled(TABLE_HEADER_HEIGHT));
        tableHeader = new GuiBounds(content.x(), content.y(), content.width(), headerHeight);
        GuiBounds viewport = new GuiBounds(content.x(), tableHeader.bottom(), content.width(),
                Math.max(1, content.bottom() - tableHeader.bottom()));
        List<GuiMenuElement> data = pageModel.elements(GuiElementRole.LIST_ROW);
        ensureSelection(data);
        scrollableList.configure(data, viewport, structuredLayout.listRowHeight());
        for (GuiScrollableList.VisibleRow visible : scrollableList.visibleRows()) {
            GuiListRow row = new GuiListRow(visible.element(), GuiPageType.DOCUMENT_LIST, visible.index(),
                    visible.element().id() == selectedElementId, visible.bounds(), this::selectElement,
                    id -> performAction(GuiAction.ACTIVATE_ELEMENT, id));
            rows.add(addDrawableChild(row));
        }
    }

    private void buildDocumentDetail() {
        documentPaper = documentBounds();
        buildDetailPage();
    }

    private void buildIdentityCard() {
        final int designWidth = 540;
        final int designHeight = 340;
        int margin = 12;
        float scale = Math.min(1.15F, Math.min((width - margin * 2) / (float) designWidth,
                (height - margin * 2) / (float) designHeight));
        int cardWidth = Math.max(1, Math.round(designWidth * scale));
        int cardHeight = Math.max(1, Math.round(designHeight * scale));
        identityCardBounds = new GuiBounds((width - cardWidth) / 2, (height - cardHeight) / 2,
                cardWidth, cardHeight);
        prepareIdentityPortrait();
        int closeSize = Math.max(12, Math.round(24 * scale));
        addCloseButton(new GuiBounds(identityCardBounds.right() - closeSize - 6,
                identityCardBounds.y() + 6, closeSize, closeSize));
    }

    private void buildShopDialog() {
        int margin = 8;
        int dialogWidth = Math.min(Math.max(300, Math.round(width * 0.72F)), 460);
        int dialogHeight = Math.min(Math.max(190, Math.round(height * 0.64F)), 280);
        dialogWidth = Math.min(dialogWidth, width - margin * 2);
        dialogHeight = Math.min(dialogHeight, height - margin * 2);
        GuiBounds panel = new GuiBounds((width - dialogWidth) / 2, (height - dialogHeight) / 2,
                dialogWidth, dialogHeight);
        int padding = 18;
        setCommonBounds(panel, panel.x() + padding, panel.y() + 14,
                panel.x() + padding, panel.bottom() - 15);
        List<GuiMenuElement> information = pageModel.elements(GuiElementRole.DETAIL_INFO);
        if (!information.isEmpty())
            infoCards.add(new InfoPlacement(information.getFirst(),
                    new GuiBounds(panel.x() + padding, panel.y() + 38,
                            panel.width() - padding * 2, 48), true));
        List<GuiMenuElement> actions = pageModel.elements(GuiElementRole.ACTION);
        int columns = actions.size() >= 4 ? 2 : Math.max(1, actions.size());
        int gap = 7;
        int buttonWidth = (panel.width() - padding * 2 - gap * (columns - 1)) / columns;
        int buttonHeight = 36;
        int startY = panel.y() + 96;
        for (int index = 0; index < actions.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            addActionButton(actions.get(index), new GuiBounds(panel.x() + padding + column * (buttonWidth + gap),
                    startY + row * (buttonHeight + gap), buttonWidth, buttonHeight),
                    Text.literal(actions.get(index).label().getString()), GuiVisualStyle.ACTION_TILE);
        }
        addCloseButton(new GuiBounds(panel.right() - 28, panel.y() + 8, 20, 20));
    }

    private GuiBounds documentBounds() {
        GuiBounds panel = structuredLayout.panel();
        int inset = Math.max(7, structuredLayout.scaled(14));
        return new GuiBounds(panel.x() + inset, panel.y() + inset,
                Math.max(1, panel.width() - inset * 2), Math.max(1, panel.height() - inset * 2));
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
                    actionY, actionWidth, actionHeight), Text.literal(actions.get(index).label().getString()),
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
                    addActionButton(visible.element(), visible.bounds(), Text.literal(visible.element().label().getString()),
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
            addActionButton(visible.element(), visible.bounds(), Text.literal(visible.element().label().getString()), style);
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
        return navigationWeight(element.label().getString());
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
            String normalized = element.label().getString().toLowerCase(java.util.Locale.ROOT);
            GuiVisualStyle style = normalized.startsWith("zur") ? GuiVisualStyle.NAVIGATION
                    : normalized.contains("suche") ? GuiVisualStyle.SEARCH_FIELD : GuiVisualStyle.TOOLBAR;
            addActionButton(element, bounds.get(index), Text.literal(element.label().getString()), style);
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
        if (pageModel != null && pageModel.type() == GuiPageType.IDENTITY_CARD) {
            context.fill(0, 0, width, height, 0xA8060B10);
            renderIdentityCard(context);
            super.render(context, mouseX, mouseY, deltaTicks);
            renderHoveredTooltip(context, mouseX, mouseY);
            return;
        }
        // Minecraft 1.21.8 already applies blur before this method; rendering it again crashes.
        context.fill(0, 0, width, height, 0x78030A12);
        GuiRenderUtil.sprite(context, ManagementGuiAtlas.BACKGROUND, panelX, panelY, panelWidth, panelHeight);
        boolean document = pageModel != null && (pageModel.type() == GuiPageType.DOCUMENT_LIST
                || pageModel.type() == GuiPageType.DOCUMENT_DETAIL);
        if (documentPaper != null) renderDocumentPaper(context, documentPaper);
        if (document) context.drawText(textRenderer, title, titleX, titleY, 0xFF2B241A, false);
        else context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFE8F7FA);
        Text status = document ? Text.literal("Amtlicher TerraNexus-Grundbuchauszug")
                : Text.translatable("gui.terranexus.secure_session");
        if (document) context.drawText(textRenderer, status, statusX, statusY, 0xFF6F6046, false);
        else context.drawTextWithShadow(textRenderer, status, statusX, statusY, 0xFF6EA1AA);
        for (InfoPlacement card : infoCards)
            if (document) GuiInfoCard.renderDocument(context, textRenderer, card.bounds(), card.element(), card.emphasized());
            else GuiInfoCard.render(context, textRenderer, card.bounds(), card.element(), card.emphasized());
        if (tableHeader != null) renderTableHeader(context);
        if (detailPanel != null) GuiDetailPanel.render(context, textRenderer, detailPanel,
                selectedElement(), pageModel.type());
        super.render(context, mouseX, mouseY, deltaTicks);
        scrollableList.renderScrollbar(context);
        scrollableGrid.renderScrollbar(context);
        if (pageModel != null && (pageModel.type() == GuiPageType.BANK_ACCOUNTS
                || pageModel.type() == GuiPageType.AUDIT_LOG || pageModel.type() == GuiPageType.LIST
                || pageModel.type() == GuiPageType.DOCUMENT_LIST)
                && pageModel.elements(GuiElementRole.LIST_ROW).isEmpty() && tableHeader != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.terranexus.search.empty"),
                    tableHeader.x() + tableHeader.width() / 2, tableHeader.bottom() + 12, 0xFF7898A2);
        }
        renderHoveredTooltip(context, mouseX, mouseY);
    }

    private void renderTableHeader(DrawContext context) {
        boolean document = pageModel.type() == GuiPageType.DOCUMENT_LIST;
        context.fill(tableHeader.x(), tableHeader.y(), tableHeader.right(), tableHeader.bottom(),
                document ? 0xFFE0D1AD : 0xD0132B38);
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
            drawHeader(context, "gui.terranexus.table.entry", 0.04F, document);
            drawHeader(context, "gui.terranexus.table.details", 0.44F, document);
        }
    }

    private void drawHeader(DrawContext context, String key, float relativeX) {
        drawHeader(context, key, relativeX, false);
    }

    private void drawHeader(DrawContext context, String key, float relativeX, boolean document) {
        context.drawText(textRenderer, Text.translatable(key),
                tableHeader.x() + Math.round(tableHeader.width() * relativeX), tableHeader.y() + 4,
                document ? 0xFF625338 : 0xFF76AEBB, false);
    }

    private void renderDocumentPaper(DrawContext context, GuiBounds paper) {
        context.fill(paper.x(), paper.y(), paper.right(), paper.bottom(), 0xFFF3E8CC);
        context.fill(paper.x(), paper.y(), paper.right(), paper.y() + 2, 0xFF9B865F);
        context.fill(paper.x(), paper.bottom() - 2, paper.right(), paper.bottom(), 0xFF9B865F);
        context.fill(paper.x(), paper.y(), paper.x() + 2, paper.bottom(), 0xFF9B865F);
        context.fill(paper.right() - 2, paper.y(), paper.right(), paper.bottom(), 0xFF9B865F);
        int marginX = paper.x() + Math.max(10, paper.width() / 24);
        context.fill(marginX, paper.y() + 8, marginX + 1, paper.bottom() - 8, 0x558A2D2D);
        int lineStart = paper.y() + 34;
        for (int y = lineStart; y < paper.bottom() - 12; y += 18)
            context.fill(paper.x() + 8, y, paper.right() - 8, y + 1, 0x22806E50);
    }

    private void renderIdentityCard(DrawContext context) {
        if (identityCardBounds == null) return;
        final float scale = identityCardBounds.width() / 540.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(identityCardBounds.x(), identityCardBounds.y());
        context.getMatrices().scale(scale, scale);

        // Fictional document inspired by current eID conventions, without reproducing an official document.
        context.fill(6, 9, 534, 334, 0x55101B22);
        context.fill(0, 5, 540, 335, 0xFFF2F1E8);
        context.fill(5, 0, 535, 340, 0xFFF2F1E8);
        context.fill(8, 8, 532, 332, 0xFFDAE8E2);
        context.fill(12, 12, 528, 328, 0xFFF4F1E7);
        context.fill(12, 12, 30, 328, 0xFF173F5B);
        context.fill(30, 12, 528, 54, 0xFFDCEBE5);
        renderIdentitySecurityPattern(context);

        context.fill(22, 22, 30, 27, 0xFF171717);
        context.fill(22, 27, 30, 32, 0xFFB22B32);
        context.fill(22, 32, 30, 37, 0xFFE0B73D);
        context.drawText(textRenderer, Text.translatable("gui.terranexus.id.country"), 40, 20, 0xFF173A50, false);
        context.drawText(textRenderer, Text.translatable("gui.terranexus.id.document"), 40, 35, 0xFF526C73, false);

        renderIdentityPortrait(context);
        renderIdentityChip(context);

        drawIdentityField(context, "gui.terranexus.id.surname", cardValue(12), 178, 72, 328);
        drawIdentityField(context, "gui.terranexus.id.given_names", cardValue(10), 178, 110, 328);
        drawIdentityField(context, "gui.terranexus.id.birth_date", cardValue(14), 178, 148, 116);
        drawIdentityField(context, "gui.terranexus.id.nationality", cardValue(20), 310, 148, 100);
        drawIdentityField(context, "gui.terranexus.id.gender", cardValue(22), 426, 148, 80);
        drawIdentityField(context, "gui.terranexus.id.birth_place", cardValue(16), 178, 186, 328);
        drawIdentityField(context, "gui.terranexus.id.address", cardValue(24), 29, 237, 477);

        String number = cardValue(4);
        context.drawText(textRenderer, GuiInfoCard.fit(textRenderer, Text.literal(number), 180),
                340, 24, 0xFF183D51, false);
        context.drawText(textRenderer, Text.literal("IDTNX"), 294, 24, 0xFF668188, false);
        String status = GuiInfoCard.firstLine(cardValue(0));
        context.fill(430, 52, 506, 66, status.toLowerCase(java.util.Locale.ROOT).contains("frei")
                ? 0xFF3F775A : 0xFF9B6E2F);
        context.drawCenteredTextWithShadow(textRenderer,
                GuiInfoCard.fit(textRenderer, Text.literal(status), 70), 468, 55, 0xFFF7F4E9);

        context.fill(22, 278, 518, 326, 0xFFE3E5DD);
        context.fill(22, 278, 518, 280, 0xFF6C8B8B);
        String machineLine = machineReadable(cardValue(12), cardValue(10), number);
        context.drawText(textRenderer, Text.literal("IDTNX" + machineLine), 29, 288, 0xFF28343A, false);
        context.drawText(textRenderer, Text.literal(machineLine + "<<<<<<<<<<"), 29, 304, 0xFF28343A, false);
        context.getMatrices().popMatrix();
    }

    private void renderIdentitySecurityPattern(DrawContext context) {
        for (int x = 36; x < 522; x += 16) {
            int offset = (x / 16) % 2 == 0 ? 0 : 4;
            context.fill(x, 60 + offset, x + 1, 271 - offset, 0x124B8B91);
        }
        for (int y = 64; y < 270; y += 13) {
            context.fill(35, y, 522, y + 1, 0x0D315D69);
            context.fill(42 + (y % 23), y + 4, 510 - (y % 19), y + 5, 0x0C73958D);
        }
        context.fill(463, 197, 507, 241, 0x17368B92);
        context.fill(470, 204, 500, 234, 0x175B4B91);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("TN"), 485, 216, 0x5573B8B3);
        context.drawText(textRenderer, Text.literal("TERRANEXUS • TERRANEXUS • TERRANEXUS"),
                179, 263, 0x35617D7E, false);
    }

    private void renderIdentityPortrait(DrawContext context) {
        context.fill(37, 68, 158, 221, 0xFF8BA1A1);
        context.fill(41, 72, 154, 217, 0xFFCAD4CF);
        if (identityPortrait == null)
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(GuiIcon.PERSON), 52, 91, 91, 108);
        else PlayerSkinDrawer.draw(context, identityPortrait, 52, 82, 91);
        context.fill(41, 197, 154, 217, 0x55445758);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.terranexus.id.photo"),
                97, 204, 0xFFF1F4EF);

        // Secondary portrait acts as a fictional holographic security element.
        if (identityPortrait == null)
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(GuiIcon.PERSON), 452, 82, 43, 52);
        else PlayerSkinDrawer.draw(context, identityPortrait, 452, 84, 43);
        context.fill(448, 78, 500, 137, 0x3378C8C1);
    }

    private void prepareIdentityPortrait() {
        String source = cardValue(25);
        if (source.equals(identityPortraitSource)) return;
        identityPortraitSource = source;
        identityPortrait = null;
        CitizenPortraitSnapshot snapshot = CitizenPortraitSnapshot.fromWireValue(source);
        if (snapshot == null || client == null) return;
        GameProfile profile = new GameProfile(snapshot.profileId(), "TerraNexus-ID");
        if (!snapshot.textureValue().isBlank()) {
            Property property = snapshot.textureSignature().isBlank()
                    ? new Property("textures", snapshot.textureValue())
                    : new Property("textures", snapshot.textureValue(), snapshot.textureSignature());
            profile.getProperties().put("textures", property);
        }
        String requestedSource = source;
        client.getSkinProvider().fetchSkinTextures(profile).thenAccept(result -> client.execute(() -> {
            if (!identityPortraitSource.equals(requestedSource)) return;
            identityPortrait = result.orElseGet(() -> client.getSkinProvider().getSkinTextures(profile));
        }));
    }

    private void renderIdentityChip(DrawContext context) {
        context.fill(55, 229, 137, 269, 0xFFC9A84D);
        context.fill(59, 233, 133, 265, 0xFFE3C76C);
        context.fill(80, 233, 82, 265, 0xFF9A7D36);
        context.fill(108, 233, 110, 265, 0xFF9A7D36);
        context.fill(59, 248, 133, 250, 0xFF9A7D36);
        context.fill(91, 233, 93, 248, 0xFF9A7D36);
        context.fill(91, 250, 93, 265, 0xFF9A7D36);
    }

    private void drawIdentityField(DrawContext context, String labelKey, String value,
                                   int x, int y, int maximumWidth) {
        context.drawText(textRenderer, Text.translatable(labelKey), x, y, 0xFF68787A, false);
        context.drawText(textRenderer, GuiInfoCard.fit(textRenderer, Text.literal(value), maximumWidth),
                x, y + 12, 0xFF1D2C31, false);
        context.fill(x, y + 25, x + maximumWidth, y + 26, 0x454D777C);
    }

    private String cardValue(int id) {
        return elements.stream().filter(element -> element.id() == id).findFirst()
                .map(element -> element.tooltip().getString().isBlank() ? element.label().getString() : element.tooltip().getString())
                .orElse("—");
    }

    private static String machineReadable(String surname, String givenNames, String number) {
        String normalized = (surname + "<<" + givenNames + "<" + number).toUpperCase(java.util.Locale.ROOT)
                .replace('Ä', 'A').replace('Ö', 'O').replace('Ü', 'U').replace('ß', 'S')
                .replaceAll("[^A-Z0-9<]", "<");
        return (normalized + "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
                .substring(0, 44);
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
                element.resolvedIcon(), label, element.tooltip().getString(), bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                element.enabled(), element.selected(), style, this::performAction)));
    }

    private void addSelectableButton(GuiMenuElement element, GuiBounds bounds, GuiVisualStyle style) {
        buttons.add(addDrawableChild(new GuiActionButton(element.id(), GuiAction.ACTIVATE_ELEMENT,
                element.resolvedIcon(), Text.literal(element.label().getString()), element.tooltip().getString(), bounds.x(), bounds.y(),
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
