package net.evarius.terranexus;

import net.evarius.terranexus.item.ModItems;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraNexus implements ModInitializer {
    public static final String MOD_ID = "terranexus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ModItems.registerModItems();
		LOGGER.info("Hello Fabric world!");
	}
}