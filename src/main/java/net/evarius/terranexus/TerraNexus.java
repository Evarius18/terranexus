package net.evarius.terranexus;

import net.evarius.terranexus.block.ModBlocks;
import net.evarius.terranexus.item.ModItemGroups;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.identity.IdentityCommands;
import net.evarius.terranexus.identity.RoleplayNames;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.AuthorityCommands;
import net.evarius.terranexus.economy.EconomyCommands;
import net.evarius.terranexus.landlord.LandlordProtection;
import net.evarius.terranexus.landlord.LandlordScheduler;
import net.evarius.terranexus.landlord.LandSurveyEvents;
import net.evarius.terranexus.management.AdminTestCommands;
import net.evarius.terranexus.management.CustomGuiService;
import net.evarius.terranexus.management.CustomSearchService;
import net.evarius.terranexus.shop.ShopService;
import net.evarius.terranexus.institution.TimeClockService;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraNexus implements ModInitializer {
    public static final String MOD_ID = "terranexus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ConfigManager.load();
        CustomGuiService.register();
        CustomSearchService.register();
        ModItemGroups.registerItemGroups();

        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        IdentityCommands.register();
        RoleplayNames.register();
        AuthorityCommands.register();
        EconomyCommands.register();
        LandlordProtection.register();
        ShopService.register();
        TimeClockService.register();
        LandlordScheduler.register();
        LandSurveyEvents.register();
        AdminTestCommands.register();
		LOGGER.info("Modifikation wurde initialisiert");
	}
}
