package net.evarius.terranexus.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

public final class GuiRenderUtil {
    private GuiRenderUtil() { }

    public static void sprite(DrawContext context, ManagementGuiAtlas.Sprite sprite,
                              int x, int y, int width, int height) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ManagementGuiAtlas.TEXTURE,
                x, y, sprite.u(), sprite.v(), width, height, sprite.width(), sprite.height(),
                ManagementGuiAtlas.TEXTURE_WIDTH, ManagementGuiAtlas.TEXTURE_HEIGHT);
    }

    public static void panel(DrawContext context, GuiBounds bounds, boolean highlighted) {
        int border = highlighted ? 0xFF39C5D8 : 0xFF244755;
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xD00B1825);
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, border);
        context.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), 0xFF172F3B);
        context.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), border);
        context.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), 0xFF172F3B);
    }
}
