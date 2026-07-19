package net.evarius.terranexus;

import net.evarius.terranexus.block.ModBlocks;
import net.evarius.terranexus.item.ModItemGroups;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.identity.IdentityCommands;
import net.evarius.terranexus.identity.RoleplayNames;
import net.evarius.terranexus.config.TerraNexusConfig;
import net.evarius.terranexus.identity.AuthorityCommands;
import net.evarius.terranexus.economy.EconomyCommands;
import net.evarius.terranexus.landlord.LandlordProtection;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraNexus implements ModInitializer {
    public static final String MOD_ID = "terranexus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        TerraNexusConfig.load();
        ModItemGroups.registerItemGroups();

        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        IdentityCommands.register();
        RoleplayNames.register();
        AuthorityCommands.register();
        EconomyCommands.register();
        LandlordProtection.register();
		LOGGER.info("Modifikation wurde initialisiert");
	}
}
