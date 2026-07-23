package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.minecraft.util.Identifier;

public final class ManagementGuiAtlas {
    public static final Identifier TEXTURE = Identifier.of(TerraNexus.MOD_ID, "textures/gui/management_atlas.png");
    public static final int TEXTURE_WIDTH = 1024;
    public static final int TEXTURE_HEIGHT = 1536;

    public static final Sprite BACKGROUND = new Sprite(74, 56, 610, 398);
    public static final Sprite BUTTON_NORMAL = new Sprite(74, 482, 478, 78);
    public static final Sprite BUTTON_HOVER = new Sprite(74, 583, 478, 78);
    public static final Sprite BUTTON_SELECTED = new Sprite(74, 685, 478, 77);
    public static final Sprite BUTTON_DISABLED = new Sprite(74, 786, 478, 77);

    private static final int[] ICON_X = {139, 339, 539, 739};
    private static final int[] ICON_Y = {913, 1069, 1225, 1366};
    private static final int ICON_WIDTH = 142;
    private static final int ICON_HEIGHT = 130;

    private ManagementGuiAtlas() {}

    public static Sprite icon(GuiIcon icon) {
        int index = icon.ordinal();
        return new Sprite(ICON_X[index % 4], ICON_Y[index / 4], ICON_WIDTH, ICON_HEIGHT);
    }

    public record Sprite(int u, int v, int width, int height) {}
}
