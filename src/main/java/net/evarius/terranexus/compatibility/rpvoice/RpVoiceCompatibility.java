package net.evarius.terranexus.compatibility.rpvoice;

import net.evarius.terranexus.TerraNexus;
import net.fabricmc.loader.api.FabricLoader;

public final class RpVoiceCompatibility {
    public static final String MOD_ID = "rp-vca";
    private static PhoneFeatureProvider provider = new NoopPhoneProvider();

    private RpVoiceCompatibility() {}

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            provider = new NoopPhoneProvider();
            TerraNexus.LOGGER.info("Optionale Telefonintegration {} ist nicht installiert", MOD_ID);
            return;
        }
        provider = RpVoicePhoneProvider.create();
        RpVoiceInstitutionBridge.register();
    }

    public static PhoneFeatureProvider provider() {
        return provider;
    }
}
