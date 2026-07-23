package net.evarius.terranexus.network.gui;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

public enum GuiIcon {
    HOME,
    PERSON,
    INSTITUTION,
    FINANCE,
    LAND,
    DOCUMENT,
    CLOCK,
    SETTINGS,
    SEARCH,
    BACK,
    CLOSE,
    CONFIRM,
    WARNING,
    ADD,
    SHOP,
    ADMIN;

    public static GuiIcon fromItem(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        if (path.equals("citizen_id_card") || path.equals("land_registry_extract")) return DOCUMENT;
        if (path.equals("land_survey_tool")) return LAND;
        if (path.equals("building_authority_tablet") || path.equals("management_tablet")) return ADMIN;
        if (item == Items.ARROW || item == Items.OAK_DOOR) return BACK;
        if (item == Items.PLAYER_HEAD || item == Items.NAME_TAG) return PERSON;
        if (item == Items.GOLD_INGOT || item == Items.GOLD_NUGGET || item == Items.GOLD_BLOCK
                || item == Items.EMERALD || item == Items.CHEST || item == Items.HOPPER) return FINANCE;
        if (item == Items.MAP || item == Items.FILLED_MAP || item == Items.GRASS_BLOCK
                || item == Items.COMPASS || item == Items.RECOVERY_COMPASS) return LAND;
        if (item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK || item == Items.PAPER
                || item == Items.BOOKSHELF) return DOCUMENT;
        if (item == Items.CLOCK || item == Items.REDSTONE_TORCH) return CLOCK;
        if (item == Items.COMPARATOR || item == Items.REDSTONE) return SETTINGS;
        if (item == Items.ENDER_EYE) return SEARCH;
        if (item == Items.BELL) return WARNING;
        if (item == Items.SHEARS || item == Items.WOODEN_AXE || item == Items.IRON_AXE
                || item == Items.GOLDEN_HOE) return LAND;
        if (item == Items.BARRIER || item == Items.RED_DYE || item == Items.RED_STAINED_GLASS_PANE
                || item == Items.LAVA_BUCKET) return WARNING;
        if (item == Items.LIME_DYE || item == Items.LIME_CONCRETE || item == Items.LIME_STAINED_GLASS_PANE)
            return CONFIRM;
        if (item == Items.BRICKS || item == Items.IRON_BLOCK || item == Items.OAK_SIGN) return INSTITUTION;
        if (item == Items.BEACON) return ADMIN;
        return HOME;
    }
}
