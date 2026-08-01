package net.evarius.terranexus.phone.messenger;

import net.evarius.terranexus.phone.PhoneApplication;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class MessengerApplication implements PhoneApplication {
    @Override public String id() { return "terranexus:messages"; }
    @Override public Text title() { return Text.translatable("gui.terranexus.phone.messages"); }
    @Override public Text description() { return Text.translatable("gui.terranexus.phone.messages.description"); }
    @Override public Item icon() { return Items.ECHO_SHARD; }
    @Override public void open(ServerPlayerEntity player) { MessengerScreen.open(player, 0); }
}
