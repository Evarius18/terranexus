package net.evarius.terranexus.phone;

import net.evarius.terranexus.compatibility.rpvoice.RpVoiceCompatibility;
import net.evarius.terranexus.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VoicePhoneApplication implements PhoneApplication {
    @Override public String id() { return "terranexus:phone"; }
    @Override public String title() { return "Telefon"; }
    @Override public String description() { return "Anrufe, Kontakte und Notruf"; }
    @Override public Item icon() { return ModItems.MOBILE_PHONE; }
    @Override public boolean available(ServerPlayerEntity player) {
        return RpVoiceCompatibility.provider().snapshot(player).available();
    }
    @Override public void open(ServerPlayerEntity player) { PhoneGuiService.open(player); }
}
