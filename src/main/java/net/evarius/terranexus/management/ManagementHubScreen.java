package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityScreen;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ManagementHubScreen {
    private ManagementHubScreen() {}

    public static void open(ServerPlayerEntity player) {
        CitizenIdentity identity = IdentityState.get(player.getServer()).get(player.getUuid());
        if (identity == null) {
            player.sendMessage(Text.literal("Noch keine Bürgerakte vorhanden. Bitte an die Einreisebehörde wenden.").formatted(Formatting.YELLOW), false);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.NAME_TAG, identity.firstName() + " " + identity.lastName(), identity.citizenNumber());

        display(inventory, 20, Items.WRITABLE_BOOK, "Bürgerverwaltung", "Bürgerakte und Einreiseverwaltung");
        actions.put(20, ignored -> {
            if (mayManage(player)) ImmigrationScreen.open(player); else IdentityScreen.open(player, identity);
        });
        display(inventory, 22, Items.GOLD_INGOT, "Bank und Zahlungen",
                "Kontostand: " + EconomyState.format(EconomyState.get(player.getServer()).balance(player.getUuid())));
        actions.put(22, ignored -> EconomyScreen.open(player));
        display(inventory, 24, Items.BRICKS, "Institutionen", "Firmen, Behörden, Vereine und Gruppen");
        actions.put(24, ignored -> InstitutionScreen.open(player));
        display(inventory, 40, Items.FILLED_MAP, "Grundstücke", "Grundstücke, Wohnungen und Mietverträge");
        actions.put(40, ignored -> PropertyScreen.open(player));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new ActionMenuScreenHandler(syncId, playerInventory, inventory, actions),
                Text.literal("TerraNexus Verwaltung").formatted(Formatting.DARK_AQUA)));
    }

    private static boolean mayManage(ServerPlayerEntity player) {
        return AuthorityState.mayManageIdentity(player);
    }

    static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(detail).formatted(Formatting.GRAY))));
        inventory.setStack(slot, stack);
    }
}
