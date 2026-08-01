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

public final class ModItemGroups {
    public static final ItemGroup TERRANEXUS_ITEMS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(TerraNexus.MOD_ID, "items"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.MANAGEMENT_TABLET))
                    .displayName(Text.translatable("itemgroup.terranexus.items"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.CITIZEN_ID_CARD);
                        entries.add(ModItems.MANAGEMENT_TABLET);
                        entries.add(ModItems.BUILDING_AUTHORITY_TABLET);
                        entries.add(ModItems.LAND_SURVEY_TOOL);
                        entries.add(ModItems.LAND_REGISTRY_EXTRACT);
                        entries.add(ModItems.MOBILE_PHONE);
                        entries.add(ModItems.MOBILE_PHONE_GREEN);
                        entries.add(ModItems.MOBILE_PHONE_RED);
                        entries.add(ModItems.EMPLOYEE_CHIP);
                        entries.add(ModItems.PROPERTY_KEY);
                        ModItems.CASH.values().forEach(entries::add);
                    }).build());

    public static final ItemGroup TERRANEXUS_BLOCKS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(TerraNexus.MOD_ID, "blocks"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.MANAGEMENT_COMPUTER))
                    .displayName(Text.translatable("itemgroup.terranexus.blocks"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.MANAGEMENT_COMPUTER);
                        entries.add(ModBlocks.TIME_CLOCK_TERMINAL);
                        entries.add(ModBlocks.BANK_ATM);
                    }).build());

    private ModItemGroups() {}

    public static void registerItemGroups() {
        TerraNexus.LOGGER.info("Registering Item Groups for " + TerraNexus.MOD_ID);
    }
}
