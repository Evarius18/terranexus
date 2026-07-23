package net.evarius.terranexus.block;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.block.custom.ManagementComputerBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.UniformIntProvider;

import java.util.function.Function;

public class ModBlocks {
    public static final Block MANAGEMENT_COMPUTER = registerBlock("management_computer",
            AbstractBlock.Settings.create().strength(2.5f, 5f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(),
            ManagementComputerBlock::new);

    // Pink Garnet Blocks
    public static Block PINK_GARNET_BLOCK = registerBlock("pink_garnet_block",
            AbstractBlock.Settings.create().strength(4f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK));
    public static Block RAW_PINK_GARNET_BLOCK = registerBlock("raw_pink_garnet_block",
            AbstractBlock.Settings.create().strength(3f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK));

    public static final Block PINK_GARNET_ORE = registerBlock("pink_garnet_ore",
            AbstractBlock.Settings.create().strength(3f).requiresTool(),
            (settings) -> new ExperienceDroppingBlock(UniformIntProvider.create(2, 5), settings));
    public static final Block PINK_GARNET_DEEPSLATE_ORE = registerBlock("pink_garnet_deepslate_ore",
            AbstractBlock.Settings.create().strength(4f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE),
            (settings) -> new ExperienceDroppingBlock(UniformIntProvider.create(3, 6), settings));

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
            entries.add(ModBlocks.PINK_GARNET_BLOCK);
            entries.add(ModBlocks.RAW_PINK_GARNET_BLOCK);
            entries.add(ModBlocks.PINK_GARNET_ORE);
            entries.add(ModBlocks.PINK_GARNET_DEEPSLATE_ORE);
            entries.add(MANAGEMENT_COMPUTER);
        });
    }
}
