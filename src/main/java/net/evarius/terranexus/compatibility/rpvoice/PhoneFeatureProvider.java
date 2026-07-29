package net.evarius.terranexus.compatibility.rpvoice;

import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PhoneFeatureProvider {
    boolean installed();
    boolean healthy();
    PhoneSnapshot snapshot(ServerPlayerEntity player);
    boolean execute(ServerPlayerEntity player, PhoneAction action, String value);
}
