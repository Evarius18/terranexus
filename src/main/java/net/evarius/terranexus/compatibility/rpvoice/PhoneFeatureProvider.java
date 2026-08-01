package net.evarius.terranexus.compatibility.rpvoice;

import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneActionResult;
import net.evarius.terranexus.phone.model.PhoneDirectoryContact;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public interface PhoneFeatureProvider {
    boolean installed();
    boolean healthy();
    PhoneSnapshot snapshot(ServerPlayerEntity player);
    PhoneActionResult execute(ServerPlayerEntity player, PhoneAction action, String value, String secondaryValue);
    List<PhoneDirectoryContact> messengerContacts(ServerPlayerEntity player);
}
