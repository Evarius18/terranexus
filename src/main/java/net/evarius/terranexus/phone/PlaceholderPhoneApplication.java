package net.evarius.terranexus.phone;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

public record PlaceholderPhoneApplication(String id, String title, String description,
                                          Item icon) implements PhoneApplication {
    @Override public boolean available(ServerPlayerEntity player) { return false; }
    @Override public void open(ServerPlayerEntity player) { }
}
