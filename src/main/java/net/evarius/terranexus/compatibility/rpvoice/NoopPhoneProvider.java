package net.evarius.terranexus.compatibility.rpvoice;

import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneActionResult;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

public final class NoopPhoneProvider implements PhoneFeatureProvider {
    @Override public boolean installed() { return false; }
    @Override public boolean healthy() { return true; }
    @Override public PhoneSnapshot snapshot(ServerPlayerEntity player) { return PhoneSnapshot.unavailable(); }
    @Override public PhoneActionResult execute(ServerPlayerEntity player, PhoneAction action,
                                               String value, String secondaryValue) {
        return PhoneActionResult.rejected("Telefonintegration nicht verfügbar.");
    }
}
