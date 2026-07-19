package net.evarius.terranexus.management;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Map;
import java.util.function.Consumer;

public class ActionMenuScreenHandler extends ScreenHandler {
    private final Map<Integer, Consumer<PlayerEntity>> actions;

    public ActionMenuScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory menu,
                                   Map<Integer, Consumer<PlayerEntity>> actions) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.actions = actions;
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(menu, column + row * 9, 8 + column * 18, 18 + row * 18) {
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                    @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
                });
            }
        }
        addPlayerSlots(playerInventory, 8, 140);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < 54) {
            Consumer<PlayerEntity> action = actions.get(slotIndex);
            if (action != null) action.accept(player);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
