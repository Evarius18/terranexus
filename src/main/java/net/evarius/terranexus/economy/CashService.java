package net.evarius.terranexus.economy;

import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.CashItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CashService {
    private CashService() {}

    public static long carriedValue(ServerPlayerEntity player) {
        long total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof CashItem cash)) continue;
            try { total = Math.addExact(total, Math.multiplyExact(cash.denomination().cents(), stack.getCount())); }
            catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
        }
        return total;
    }

    public static boolean depositAll(ServerPlayerEntity player) {
        long amount = carriedValue(player);
        if (amount <= 0 || amount == Long.MAX_VALUE) return false;
        String account = EconomyState.playerAccount(player.getUuid());
        EconomyState economy = EconomyState.get(player.getServer());
        if (economy.isFrozen(account) || !economy.adjust(account, amount, "Bargeldeinzahlung am Bankautomaten",
                player.getUuidAsString(), "", "ATM_CASH_DEPOSIT")) return false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof CashItem) player.getInventory().setStack(slot, ItemStack.EMPTY);
        }
        return true;
    }

    public static boolean withdrawBanknotes(ServerPlayerEntity player, long amount) {
        if (!isBanknoteAmount(amount)) return false;
        Map<Item, Integer> stacks = breakdownBanknotes(amount);
        if (stacks.isEmpty() || !hasCapacity(player, stacks)) return false;
        String account = EconomyState.playerAccount(player.getUuid());
        EconomyState economy = EconomyState.get(player.getServer());
        if (!economy.adjust(account, -amount, "Bargeldauszahlung am Bankautomaten",
                player.getUuidAsString(), "", "ATM_CASH_WITHDRAWAL")) return false;
        stacks.forEach((item, count) -> {
            int remaining = count;
            while (remaining > 0) {
                int batch = Math.min(item.getMaxCount(), remaining);
                ItemStack stack = new ItemStack(item, batch);
                if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
                remaining -= batch;
            }
        });
        return true;
    }

    public static boolean isBanknoteAmount(long amount) {
        return amount >= CashDenomination.NEXUS_5.cents() && amount % CashDenomination.NEXUS_5.cents() == 0;
    }

    static Map<Item, Integer> breakdownBanknotes(long amount) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        long remaining = amount;
        for (CashDenomination denomination : CashDenomination.descending()) {
            if (denomination.coin()) continue;
            long count = remaining / denomination.cents();
            if (count > Integer.MAX_VALUE) return Map.of();
            if (count > 0) result.put(ModItems.CASH.get(denomination), (int) count);
            remaining %= denomination.cents();
        }
        return remaining == 0 ? result : Map.of();
    }

    private static boolean hasCapacity(ServerPlayerEntity player, Map<Item, Integer> requested) {
        int freeSlots = 0;
        Map<Item, Integer> partialCapacity = new java.util.HashMap<>();
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) freeSlots++;
            else if (requested.containsKey(stack.getItem()))
                partialCapacity.merge(stack.getItem(), stack.getMaxCount() - stack.getCount(), Integer::sum);
        }
        int neededSlots = 0;
        for (Map.Entry<Item, Integer> entry : requested.entrySet()) {
            int remaining = Math.max(0, entry.getValue() - partialCapacity.getOrDefault(entry.getKey(), 0));
            neededSlots += (remaining + entry.getKey().getMaxCount() - 1) / entry.getKey().getMaxCount();
        }
        return neededSlots <= freeSlots;
    }
}
