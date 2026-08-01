package net.evarius.terranexus.client.gui.phone;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** Small multiline editor tailored to the phone; input remains local until explicitly submitted. */
public final class PhoneMessageEditor extends ClickableWidget {
    private static final int PADDING = 6;
    private final int maximumLength;
    private final Text placeholder;
    private final Consumer<String> changed;
    private String value = "";

    public PhoneMessageEditor(int x, int y, int width, int height, int maximumLength,
                              Text placeholder, Consumer<String> changed) {
        super(x, y, width, height, Text.translatable("gui.terranexus.phone.messenger.message"));
        this.maximumLength = Math.max(1, maximumLength);
        this.placeholder = placeholder;
        this.changed = changed;
    }

    public String value() { return value; }
    public int remaining() { return maximumLength - value.length(); }
    public boolean valid() { return !value.trim().isEmpty() && value.length() <= maximumLength; }

    public void insert(String text) {
        if (text == null || text.isEmpty()) return;
        String clean = text.replace('\r', '\n').replace("\u0000", "");
        int available = maximumLength - value.length();
        if (available <= 0) return;
        value += clean.substring(0, Math.min(available, clean.length()));
        changed.accept(value);
    }

    @Override
    public void onClick(double mouseX, double mouseY) { setFocused(true); }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || Character.isISOControl(chr)) return false;
        insert(String.valueOf(chr));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!value.isEmpty()) {
                int end = value.offsetByCodePoints(value.length(), -1);
                value = value.substring(0, end);
                changed.accept(value);
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            insert(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) return true;
        return false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        Text shown = value.isEmpty() ? placeholder : Text.literal(value);
        List<OrderedText> lines = renderer.wrapLines(shown, Math.max(10, width - PADDING * 2));
        int actualHeight = Math.min(height, Math.max(27, 12 + Math.max(1, lines.size()) * 10));
        int top = getY() + height - actualHeight;
        int fill = isFocused() ? 0xF0153744 : 0xE0122F3C;
        context.fill(getX(), top, getX() + width, getY() + height, fill);
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height,
                isFocused() ? 0xFF55D9E7 : 0xFF3DBFD0);
        int color = value.isEmpty() ? 0xFF71949D : 0xFFEAF7F9;
        int visibleLines = Math.max(1, (actualHeight - 12) / 10);
        int start = Math.max(0, lines.size() - visibleLines);
        for (int line = start; line < lines.size(); line++)
            context.drawText(renderer, lines.get(line), getX() + PADDING,
                    top + PADDING + (line - start) * 10, color, false);
        if (isFocused() && (System.currentTimeMillis() / 500L) % 2 == 0) {
            OrderedText last = lines.isEmpty() ? Text.empty().asOrderedText() : lines.getLast();
            int cursorX = Math.min(getX() + width - PADDING, getX() + PADDING + renderer.getWidth(last));
            int cursorY = top + PADDING + (Math.min(visibleLines, Math.max(1, lines.size())) - 1) * 10;
            context.fill(cursorX, cursorY, cursorX + 1, cursorY + 9, 0xFFF2FAFC);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
