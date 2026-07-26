package net.evarius.terranexus.item;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
import net.evarius.terranexus.item.custom.ManagementTabletItem;
import net.evarius.terranexus.item.custom.BuildingAuthorityTabletItem;
import net.evarius.terranexus.item.custom.LandSurveyToolItem;
import net.evarius.terranexus.item.custom.LandRegistryExtractItem;
import net.evarius.terranexus.item.custom.MobilePhoneItem;
import net.evarius.terranexus.item.custom.EmployeeChipItem;
import net.evarius.terranexus.item.custom.PropertyKeyItem;
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
    public static final Item BUILDING_AUTHORITY_TABLET = registerItem("building_authority_tablet",
            new Item.Settings().maxCount(1), BuildingAuthorityTabletItem::new);
    public static final Item LAND_SURVEY_TOOL = registerItem("land_survey_tool",
            new Item.Settings().maxCount(1), LandSurveyToolItem::new);
    public static final Item LAND_REGISTRY_EXTRACT = registerItem("land_registry_extract",
            new Item.Settings().maxCount(1), LandRegistryExtractItem::new);
    public static final Item MOBILE_PHONE = registerItem("mobile_phone",
            new Item.Settings().maxCount(1), MobilePhoneItem::new);
    public static final Item EMPLOYEE_CHIP = registerItem("employee_chip",
            new Item.Settings().maxCount(1), EmployeeChipItem::new);
    public static final Item PROPERTY_KEY = registerItem("property_key",
            new Item.Settings().maxCount(1), PropertyKeyItem::new);

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
            entries.add(BUILDING_AUTHORITY_TABLET);
            entries.add(LAND_SURVEY_TOOL);
            entries.add(LAND_REGISTRY_EXTRACT);
            entries.add(MOBILE_PHONE);
            entries.add(EMPLOYEE_CHIP);
            entries.add(PROPERTY_KEY);
        });
    }
}
