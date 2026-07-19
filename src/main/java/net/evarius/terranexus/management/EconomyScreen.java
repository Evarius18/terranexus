package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public final class EconomyScreen {
    private EconomyScreen() {}

    public static void open(ServerPlayerEntity player) {
        EconomyState economy = EconomyState.get(player.getServer());
        IdentityState identities = IdentityState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_INGOT, "Persönliches Konto", EconomyState.format(economy.balance(player.getUuid())));
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Verwaltungsübersicht");
        actions.put(8, ignored -> ManagementHubScreen.open(player));
        int slot = 9;
        for (ServerPlayerEntity recipient : player.getServer().getPlayerManager().getPlayerList()) {
            if (recipient == player || slot >= 54) continue;
            CitizenIdentity identity = identities.get(recipient.getUuid());
            if (identity == null || !identities.isApproved(recipient.getUuid())) continue;
            String rpName = identity.firstName() + " " + identity.lastName();
            ManagementHubScreen.display(inventory, slot, Items.PLAYER_HEAD, "Überweisen an " + rpName, identity.citizenNumber());
            actions.put(slot, ignored -> openAmountInput(player, recipient, rpName));
            slot++;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new ActionMenuScreenHandler(syncId, playerInventory, inventory, actions),
                Text.literal("TerraNexus Bank").formatted(Formatting.GOLD)));
    }

    private static void openAmountInput(ServerPlayerEntity sender, ServerPlayerEntity recipient, String rpName) {
        sender.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
                    try {
                        long cents = new BigDecimal(value.replace(',', '.')).movePointRight(2)
                                .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
                        if (cents <= 0) throw new NumberFormatException();
                        EconomyState economy = EconomyState.get(sender.getServer());
                        if (economy.transfer(sender.getUuid(), recipient.getUuid(), cents)) {
                            sender.sendMessage(Text.literal(EconomyState.format(cents) + " wurden an " + rpName + " überwiesen.").formatted(Formatting.GREEN), false);
                            recipient.sendMessage(Text.literal("Du hast " + EconomyState.format(cents) + " erhalten.").formatted(Formatting.GREEN), false);
                        } else {
                            sender.sendMessage(Text.literal("Nicht genügend Guthaben.").formatted(Formatting.RED), false);
                        }
                    } catch (Exception exception) {
                        sender.sendMessage(Text.literal("Ungültiger Betrag. Beispiel: 125.50").formatted(Formatting.RED), false);
                    }
                    open(sender);
                }), Text.literal("Betrag an " + rpName).formatted(Formatting.GOLD)));
    }
}
