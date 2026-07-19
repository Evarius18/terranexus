package net.evarius.terranexus.item;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final ItemGroup ROLEPLAY_BUILDING_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(TerraNexus.MOD_ID, "roleplay_building"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.ASPHALT))
                    .displayName(Text.translatable("itemgroup.terranexus.roleplay_building"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.ASPHALT);
                        entries.add(ModBlocks.ASPHALT_SLAB);
                        entries.add(ModBlocks.ASPHALT_STAIRS);
                        entries.add(ModBlocks.WORN_ASPHALT);
                        entries.add(ModBlocks.WORN_ASPHALT_SLAB);
                        entries.add(ModBlocks.WORN_ASPHALT_STAIRS);
                        entries.add(ModBlocks.WHITE_LINE_ASPHALT);
                        entries.add(ModBlocks.YELLOW_LINE_ASPHALT);
                        entries.add(ModBlocks.CONSTRUCTION_SAND);
                        entries.add(ModBlocks.CRUSHED_STONE);
                        entries.add(ModBlocks.MILLED_ASPHALT);
                        entries.add(ModBlocks.ROLLED_GRIT);
                        entries.add(ModBlocks.TAR);
                        entries.add(ModBlocks.CONSTRUCTION_BARRIER);
                        entries.add(ModBlocks.DELINEATOR);
                        entries.add(ModBlocks.DELINEATOR_LEFT);
                        entries.add(ModBlocks.GUARDRAIL);
                        entries.add(ModBlocks.GUARDRAIL_END);
                        entries.add(ModBlocks.BRIDGE_GUARDRAIL);
                        entries.add(ModBlocks.BRIDGE_CONCRETE);
                        entries.add(ModBlocks.BRIDGE_CONCRETE_SLAB);
                        entries.add(ModBlocks.BRIDGE_STEEL);
                        entries.add(ModBlocks.BRIDGE_EXPANSION_JOINT);
                        entries.add(ModBlocks.ROAD_MANHOLE_D400);
                        entries.add(ModBlocks.PATH_MANHOLE_B125);
                        entries.add(ModBlocks.STREET_DRAIN_C250);
                        entries.add(ModBlocks.CURB_DRAIN_C250);
                        entries.add(ModBlocks.DRAINAGE_CHANNEL_B125);
                    }).build());

    public static final ItemGroup PINK_GARNET_ITEMS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(TerraNexus.MOD_ID, "pink_garnet_items"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.PINK_GARNET))
                    .displayName(Text.translatable("itemgroup.terranexus.pink_garnet_items"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.PINK_GARNET);
                        entries.add(ModItems.RAW_PINK_GARNET);
                    }).build());

    public static final ItemGroup PINK_GARNET_BLOCKS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(TerraNexus.MOD_ID, "pink_garnet_blocks"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.PINK_GARNET_BLOCK))
                    .displayName(Text.translatable("itemgroup.terranexus.pink_garnet_blocks"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.PINK_GARNET_BLOCK);
                        entries.add(ModBlocks.RAW_PINK_GARNET_BLOCK);
                    }).build());

    public static void registerItemGroups() {
        TerraNexus.LOGGER.info("Registering Item Groups for " + TerraNexus.MOD_ID);
    }
}
