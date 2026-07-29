package net.evarius.terranexus.phone;

import net.evarius.terranexus.management.CustomGuiService;
import net.evarius.terranexus.management.ManagementHubScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public final class PhoneScreen {
    private PhoneScreen() {}

    public static void registerBuiltInApps() {
        PhoneAppRegistry.register(new BankPhoneApplication());
        PhoneAppRegistry.register(new RealEstatePhoneApplication());
        PhoneAppRegistry.register(new VoicePhoneApplication());
        PhoneAppRegistry.register(new PlaceholderPhoneApplication("terranexus:messages",
                "Nachrichten", "Noch nicht verbunden", Items.PAPER));
        PhoneAppRegistry.register(new PlaceholderPhoneApplication("terranexus:companies",
                "Firmen", "Institutionsverwaltung · vorbereitet", Items.BRICKS));
        PhoneAppRegistry.register(new PlaceholderPhoneApplication("terranexus:authorities",
                "Behörden", "Behördendienste · vorbereitet", Items.IRON_BLOCK));
    }

    public static void open(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.CLOCK, "TN-Handy", "RP-Telefon · Prototyp");
        int slot = 20;
        for (PhoneApplication application : PhoneAppRegistry.applications()) {
            if (!application.available(player)) continue;
            if (slot >= 35) break;
            ManagementHubScreen.display(inventory, slot, application.icon(),
                    application.title(), application.description());
            actions.put(slot, ignored -> application.open(player));
            slot++;
        }
        CustomGuiService.open(player, inventory, actions,
                Text.literal("TN-Handy").formatted(Formatting.DARK_AQUA));
    }
}
