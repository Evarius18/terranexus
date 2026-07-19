package net.evarius.terranexus.management;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

public class TextInputScreenHandler extends AnvilScreenHandler {
    private final Consumer<String> onConfirm;
    private String value = "";

    public TextInputScreenHandler(int syncId, PlayerInventory inventory, Consumer<String> onConfirm) {
        super(syncId, inventory, ScreenHandlerContext.EMPTY);
        this.onConfirm = onConfirm;
        ItemStack inputItem = new ItemStack(Items.PAPER);
        inputItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-").formatted(Formatting.GRAY));
        input.setStack(0, inputItem);
        updateResult();
    }

    @Override
    public boolean setNewItemName(String name) {
        value = name == null || name.equals("-") ? "" : name.trim();
        updateResult();
        return true;
    }

    @Override
    public void updateResult() {
        ItemStack result = new ItemStack(value.isBlank() ? Items.GRAY_DYE : Items.LIME_DYE);
        result.set(DataComponentTypes.CUSTOM_NAME, value.isBlank()
                ? Text.literal("Bitte einen Wert eingeben").formatted(Formatting.RED)
                : Text.literal("Bestätigen: " + value).formatted(Formatting.GREEN));
        output.setStack(0, result);
        sendContentUpdates();
    }

    @Override protected boolean canUse(net.minecraft.block.BlockState state) { return true; }
    @Override protected boolean canTakeOutput(PlayerEntity player, boolean present) { return !value.isBlank(); }

    @Override
    protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
        String confirmed = value;
        setCursorStack(ItemStack.EMPTY);
        output.setStack(0, ItemStack.EMPTY);
        input.setStack(0, ItemStack.EMPTY);
        ((net.minecraft.server.network.ServerPlayerEntity) player).closeHandledScreen();
        player.getServer().execute(() -> onConfirm.accept(confirmed));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (slot == getResultSlotIndex()) return ItemStack.EMPTY;
        return super.quickMove(player, slot);
    }
}
