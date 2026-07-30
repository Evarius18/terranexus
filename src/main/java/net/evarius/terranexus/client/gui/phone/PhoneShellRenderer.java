package net.evarius.terranexus.client.gui.phone;

import net.evarius.terranexus.client.gui.GuiBounds;
import net.evarius.terranexus.client.gui.GuiRenderUtil;
import net.evarius.terranexus.client.gui.ManagementGuiAtlas;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.network.gui.GuiIcon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Shared TerraNexus phone chrome; intentionally contains no full-screen rendering. */
public final class PhoneShellRenderer {
    private PhoneShellRenderer() {}

    public static void frame(DrawContext context, GuiBounds phone) {
        context.fill(phone.x(), phone.y() + 8, phone.right(), phone.bottom() - 8, 0xFF05080C);
        context.fill(phone.x() + 5, phone.y(), phone.right() - 5, phone.bottom(), 0xFF05080C);
        context.fill(phone.x() + 8, phone.y() + 8, phone.right() - 8, phone.bottom() - 8, 0xFF0D1720);
        context.fill(phone.x() + 10, phone.y() + 32, phone.right() - 10, phone.bottom() - 18, 0xFF102633);
        context.fill(phone.x() + phone.width() / 2 - 22, phone.y() + 7,
                phone.x() + phone.width() / 2 + 22, phone.y() + 11, 0xFF25323A);
    }

    public static void homeIndicator(DrawContext context, GuiBounds phone) {
        context.fill(phone.x() + phone.width() / 2 - 26, phone.bottom() - 11,
                phone.x() + phone.width() / 2 + 26, phone.bottom() - 8, 0xFF8BA6AD);
    }

    public static void icon(DrawContext context, GuiIcon icon, int x, int y, int size) {
        if (icon != GuiIcon.PHONE) {
            GuiRenderUtil.sprite(context, ManagementGuiAtlas.icon(icon), x, y, size, size);
            return;
        }
        float scale = size / 16.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawItem(new ItemStack(ModItems.MOBILE_PHONE), 0, 0);
        context.getMatrices().popMatrix();
    }
}
