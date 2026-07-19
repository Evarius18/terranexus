package net.evarius.terranexus.block;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.block.custom.RoadFurnitureBlock;
import net.evarius.terranexus.block.custom.DelineatorBlock;
import net.evarius.terranexus.block.custom.GuardrailBlock;
import net.evarius.terranexus.block.custom.OpenableManholeBlock;
import net.minecraft.block.GlazedTerracottaBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
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

    // Road construction
    public static final Block ASPHALT = registerBlock("asphalt",
            AbstractBlock.Settings.create().strength(2.2f, 6f).requiresTool().sounds(BlockSoundGroup.STONE));
    public static final Block ASPHALT_SLAB = registerBlock("asphalt_slab",
            AbstractBlock.Settings.create().strength(2.2f, 6f).requiresTool().sounds(BlockSoundGroup.STONE), SlabBlock::new);
    public static final Block ASPHALT_STAIRS = registerBlock("asphalt_stairs",
            AbstractBlock.Settings.create().strength(2.2f, 6f).requiresTool().sounds(BlockSoundGroup.STONE),
            settings -> new StairsBlock(ASPHALT.getDefaultState(), settings));
    public static final Block WORN_ASPHALT = registerBlock("worn_asphalt",
            AbstractBlock.Settings.create().strength(2f, 5.5f).requiresTool().sounds(BlockSoundGroup.STONE));
    public static final Block WORN_ASPHALT_SLAB = registerBlock("worn_asphalt_slab",
            AbstractBlock.Settings.create().strength(2f, 5.5f).requiresTool().sounds(BlockSoundGroup.STONE), SlabBlock::new);
    public static final Block WORN_ASPHALT_STAIRS = registerBlock("worn_asphalt_stairs",
            AbstractBlock.Settings.create().strength(2f, 5.5f).requiresTool().sounds(BlockSoundGroup.STONE),
            settings -> new StairsBlock(WORN_ASPHALT.getDefaultState(), settings));
    public static final Block WHITE_LINE_ASPHALT = registerBlock("white_line_asphalt",
            AbstractBlock.Settings.create().strength(2.2f, 6f).requiresTool().sounds(BlockSoundGroup.STONE));
    public static final Block YELLOW_LINE_ASPHALT = registerBlock("yellow_line_asphalt",
            AbstractBlock.Settings.create().strength(2.2f, 6f).requiresTool().sounds(BlockSoundGroup.STONE));

    // Loose and processed construction materials
    public static final Block CONSTRUCTION_SAND = registerBlock("construction_sand",
            AbstractBlock.Settings.create().strength(0.6f).sounds(BlockSoundGroup.SAND));
    public static final Block CRUSHED_STONE = registerBlock("crushed_stone",
            AbstractBlock.Settings.create().strength(0.8f).sounds(BlockSoundGroup.GRAVEL));
    public static final Block MILLED_ASPHALT = registerBlock("milled_asphalt",
            AbstractBlock.Settings.create().strength(0.9f).sounds(BlockSoundGroup.GRAVEL));
    public static final Block ROLLED_GRIT = registerBlock("rolled_grit",
            AbstractBlock.Settings.create().strength(0.8f).sounds(BlockSoundGroup.GRAVEL));
    public static final Block TAR = registerBlock("tar",
            AbstractBlock.Settings.create().strength(1.4f).sounds(BlockSoundGroup.MUD));
    public static final Block CONSTRUCTION_BARRIER = registerBlock("construction_barrier",
            AbstractBlock.Settings.create().strength(1.2f).sounds(BlockSoundGroup.WOOD).nonOpaque(), RoadFurnitureBlock::new);
    public static final Block DELINEATOR = registerBlock("delineator",
            AbstractBlock.Settings.create().strength(1f).sounds(BlockSoundGroup.STONE).nonOpaque(), DelineatorBlock::new);
    public static final Block DELINEATOR_LEFT = registerBlock("delineator_left",
            AbstractBlock.Settings.create().strength(1f).sounds(BlockSoundGroup.STONE).nonOpaque(), DelineatorBlock::new);
    public static final Block GUARDRAIL = registerBlock("guardrail",
            AbstractBlock.Settings.create().strength(2f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(), GuardrailBlock::new);
    public static final Block GUARDRAIL_END = registerBlock("guardrail_end",
            AbstractBlock.Settings.create().strength(2f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(), GuardrailBlock::new);
    public static final Block BRIDGE_GUARDRAIL = registerBlock("bridge_guardrail",
            AbstractBlock.Settings.create().strength(3f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(), GuardrailBlock::new);

    // Bridge construction
    public static final Block BRIDGE_CONCRETE = registerBlock("bridge_concrete",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.STONE));
    public static final Block BRIDGE_CONCRETE_SLAB = registerBlock("bridge_concrete_slab",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.STONE), SlabBlock::new);
    public static final Block BRIDGE_STEEL = registerBlock("bridge_steel",
            AbstractBlock.Settings.create().strength(5f, 9f).requiresTool().sounds(BlockSoundGroup.METAL));
    public static final Block BRIDGE_EXPANSION_JOINT = registerBlock("bridge_expansion_joint",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.METAL));

    // Drainage according to the typical DIN EN 124 application classes
    public static final Block ROAD_MANHOLE_D400 = registerBlock("road_manhole_d400",
            AbstractBlock.Settings.create().strength(5f, 10f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(), OpenableManholeBlock::new);
    public static final Block PATH_MANHOLE_B125 = registerBlock("path_manhole_b125",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque(), OpenableManholeBlock::new);
    public static final Block STREET_DRAIN_C250 = registerBlock("street_drain_c250",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.METAL), GlazedTerracottaBlock::new);
    public static final Block CURB_DRAIN_C250 = registerBlock("curb_drain_c250",
            AbstractBlock.Settings.create().strength(4f, 8f).requiresTool().sounds(BlockSoundGroup.METAL), GlazedTerracottaBlock::new);
    public static final Block DRAINAGE_CHANNEL_B125 = registerBlock("drainage_channel_b125",
            AbstractBlock.Settings.create().strength(3f, 7f).requiresTool().sounds(BlockSoundGroup.METAL), GlazedTerracottaBlock::new);

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
            entries.add(ASPHALT);
            entries.add(ASPHALT_SLAB);
            entries.add(ASPHALT_STAIRS);
            entries.add(WORN_ASPHALT);
            entries.add(WORN_ASPHALT_SLAB);
            entries.add(WORN_ASPHALT_STAIRS);
            entries.add(WHITE_LINE_ASPHALT);
            entries.add(YELLOW_LINE_ASPHALT);
            entries.add(CONSTRUCTION_SAND);
            entries.add(CRUSHED_STONE);
            entries.add(MILLED_ASPHALT);
            entries.add(ROLLED_GRIT);
            entries.add(TAR);
            entries.add(CONSTRUCTION_BARRIER);
            entries.add(DELINEATOR);
            entries.add(DELINEATOR_LEFT);
            entries.add(GUARDRAIL);
            entries.add(GUARDRAIL_END);
            entries.add(BRIDGE_GUARDRAIL);
            entries.add(BRIDGE_CONCRETE);
            entries.add(BRIDGE_CONCRETE_SLAB);
            entries.add(BRIDGE_STEEL);
            entries.add(BRIDGE_EXPANSION_JOINT);
            entries.add(ROAD_MANHOLE_D400);
            entries.add(PATH_MANHOLE_B125);
            entries.add(STREET_DRAIN_C250);
            entries.add(CURB_DRAIN_C250);
            entries.add(DRAINAGE_CHANNEL_B125);
        });
    }
}
