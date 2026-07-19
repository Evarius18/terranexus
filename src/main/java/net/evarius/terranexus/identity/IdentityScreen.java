package net.evarius.terranexus.identity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.evarius.terranexus.economy.EconomyState;

import java.util.List;

public final class IdentityScreen {
    private IdentityScreen() {}

    public static void open(ServerPlayerEntity player, CitizenIdentity identity) {
        ReadOnlyIdentityInventory inventory = new ReadOnlyIdentityInventory();
        inventory.display(4, "Bürgernummer", identity.citizenNumber(), Items.NAME_TAG);
        inventory.display(10, "Vorname", identity.firstName(), Items.PAPER);
        inventory.display(12, "Nachname", identity.lastName(), Items.PAPER);
        inventory.display(14, "Geburtsdatum", identity.birthDate(), Items.CLOCK);
        inventory.display(16, "Geburtsort / -land", identity.birthPlace() + ", " + identity.birthCountry(), Items.MAP);
        inventory.display(20, "Nationalität", identity.nationality(), Items.WRITABLE_BOOK);
        inventory.display(22, "Geschlecht", identity.gender(), Items.PLAYER_HEAD);
        inventory.display(24, "Meldeadresse", identity.address(), Items.OAK_DOOR);
        java.util.UUID citizenUuid = java.util.UUID.fromString(identity.playerUuid());
        inventory.display(26, "Kontostand", EconomyState.format(EconomyState.get(player.getServer()).balance(citizenUuid)), Items.GOLD_INGOT);
        boolean approved = IdentityState.get(player.getServer()).isApproved(citizenUuid);
        inventory.display(0, "Einreisestatus", approved ? "Freigegeben" : "In behördlicher Prüfung",
                approved ? Items.LIME_DYE : Items.YELLOW_DYE);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory),
                Text.literal("TerraNexus Bürgerinformation").formatted(Formatting.DARK_AQUA)));
    }

    private static final class ReadOnlyIdentityInventory extends SimpleInventory {
        private ReadOnlyIdentityInventory() {
            super(27);
        }

        private void display(int slot, String label, String value, net.minecraft.item.Item item) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(Formatting.AQUA));
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(value).formatted(Formatting.WHITE))));
            super.setStack(slot, stack);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            // Display inventory is server-controlled and read-only.
        }

        @Override
        public boolean isValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
