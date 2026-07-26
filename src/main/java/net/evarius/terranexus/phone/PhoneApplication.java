package net.evarius.terranexus.phone;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

/** Extension point for future phone, messaging and Simple Voice Chat companion modules. */
public interface PhoneApplication {
    String id();
    String title();
    String description();
    Item icon();
    default boolean available(ServerPlayerEntity player) { return true; }
    void open(ServerPlayerEntity player);
}
