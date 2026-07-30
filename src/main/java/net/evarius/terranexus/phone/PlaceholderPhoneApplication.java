package net.evarius.terranexus.phone;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public record PlaceholderPhoneApplication(String id, Text title, Text description,
                                          Item icon) implements PhoneApplication {
    @Override public boolean available(ServerPlayerEntity player) { return false; }
    @Override public void open(ServerPlayerEntity player) { }
}
