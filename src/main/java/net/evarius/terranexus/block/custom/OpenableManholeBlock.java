package net.evarius.terranexus.block.custom;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.TrapdoorBlock;

public class OpenableManholeBlock extends TrapdoorBlock {
    public OpenableManholeBlock(AbstractBlock.Settings settings) {
        super(BlockSetType.COPPER, settings);
    }
}
