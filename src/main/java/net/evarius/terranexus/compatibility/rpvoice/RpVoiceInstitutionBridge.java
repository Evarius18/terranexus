package net.evarius.terranexus.compatibility.rpvoice;

import com.evarius.rpvca.api.DeviceCapabilities;
import com.evarius.rpvca.api.RpVcaApi;
import com.evarius.rpvca.api.PhoneApplicationProvider;
import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.item.ModItems;

/** Direct registration against the optional RP-VCA SPI; loaded only after the Fabric mod check. */
final class RpVoiceInstitutionBridge {
    private RpVoiceInstitutionBridge() {}

    static void register() {
        RpVcaApi.registerInstitutionMembershipProvider(
                TerraNexus.MOD_ID, InstitutionAccess::rpVoiceMembershipKeys);
        RpVcaApi.registerDeviceCapabilityProvider(TerraNexus.MOD_ID, stack ->
                stack.isOf(ModItems.MOBILE_PHONE)
                        ? java.util.Optional.of(DeviceCapabilities.externalPhone())
                        : java.util.Optional.empty());
        RpVcaApi.registerPhoneApplication("terranexus", new PhoneApplicationProvider() {
            @Override public String title() { return "TerraNexus"; }
            @Override public boolean available(net.minecraft.server.network.ServerPlayerEntity player) {
                return true;
            }
            @Override public boolean open(net.minecraft.server.network.ServerPlayerEntity player) {
                net.evarius.terranexus.phone.PhoneScreen.open(player);
                return true;
            }
        });
        TerraNexus.LOGGER.info("TerraNexus direkt an die öffentlichen RP-VCA-Schnittstellen angebunden");
    }
}
