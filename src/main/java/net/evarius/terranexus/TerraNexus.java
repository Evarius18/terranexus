package net.evarius.terranexus;

import net.evarius.terranexus.block.ModBlocks;
import net.evarius.terranexus.item.ModItemGroups;
import net.evarius.terranexus.item.ModItems;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraNexus implements ModInitializer {
    public static final String MOD_ID = "terranexus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ModItemGroups.registerItemGroups();

        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
		LOGGER.info("Modifikation wurde initialisiert");
	}
}