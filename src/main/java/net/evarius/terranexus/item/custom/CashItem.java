package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.economy.CashDenomination;
import net.minecraft.item.Item;

public final class CashItem extends Item {
    private final CashDenomination denomination;

    public CashItem(Settings settings, CashDenomination denomination) {
        super(settings);
        this.denomination = denomination;
    }

    public CashDenomination denomination() { return denomination; }
}
