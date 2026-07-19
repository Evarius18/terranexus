package net.evarius.terranexus.item;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
import net.evarius.terranexus.item.custom.ManagementTabletItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import java.util.function.Function;

public class ModItems {
    public static final Item PINK_GARNET = registerItem("pink_garnet");
    public static final Item RAW_PINK_GARNET = registerItem("raw_pink_garnet");
    public static final Item CITIZEN_ID_CARD = registerItem("citizen_id_card",
            new Item.Settings().maxCount(1), CitizenIdCardItem::new);
    public static final Item MANAGEMENT_TABLET = registerItem("management_tablet",
            new Item.Settings().maxCount(1), ManagementTabletItem::new);

    private static Item registerItem(String name) {
        return registerItem(name, new Item.Settings(), Item::new);
    }

    private static Item registerItem(String name, Item.Settings settings, Function<Item.Settings, Item> factory) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TerraNexus.MOD_ID, name));
        return Registry.register(
                Registries.ITEM,
                key,
                factory.apply(settings.registryKey(key))
        );
    }

    public static void registerModItems() {
        TerraNexus.LOGGER.info("Registering Mod Items for " + TerraNexus.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(PINK_GARNET);
            entries.add(RAW_PINK_GARNET);
            entries.add(MANAGEMENT_TABLET);
        });
    }
}
