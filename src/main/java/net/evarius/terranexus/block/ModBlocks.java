package net.evarius.terranexus.block;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.block.custom.ManagementComputerBlock;
import net.evarius.terranexus.block.custom.TimeClockTerminalBlock;
import net.evarius.terranexus.block.custom.BankAtmBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModBlocks {
    public static final Block MANAGEMENT_COMPUTER = registerBlock("management_computer",
            AbstractBlock.Settings.create().strength(2.5f, 5f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(),
            ManagementComputerBlock::new);
    public static final Block TIME_CLOCK_TERMINAL = registerBlock("time_clock_terminal",
            AbstractBlock.Settings.create().strength(2.5f, 5f).requiresTool()
                    .sounds(BlockSoundGroup.METAL).nonOpaque(),
            TimeClockTerminalBlock::new);
    public static final Block BANK_ATM = registerBlock("bank_atm",
            AbstractBlock.Settings.create().strength(3.5f, 8f).requiresTool()
                    .sounds(BlockSoundGroup.METAL).nonOpaque(), BankAtmBlock::new);

    private static Block registerBlock(String name, AbstractBlock.Settings settings) {
        return registerBlock(name, settings, Block::new);
    }

    private static Block registerBlock(String name, AbstractBlock.Settings settings, Function<AbstractBlock.Settings, Block> blockFactory) {
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TerraNexus.MOD_ID, name));
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TerraNexus.MOD_ID, name));

        Block block = blockFactory.apply(settings.registryKey(blockKey));

        Registry.register(Registries.ITEM, itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey)));

        return Registry.register(Registries.BLOCK, blockKey, block);
    }

    public static void registerModBlocks() {
        TerraNexus.LOGGER.info("Registering Mod Blocks for " + TerraNexus.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(MANAGEMENT_COMPUTER);
            entries.add(TIME_CLOCK_TERMINAL);
            entries.add(BANK_ATM);
        });
    }
}
