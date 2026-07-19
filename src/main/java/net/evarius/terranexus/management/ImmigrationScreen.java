package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityScreen;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
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

public final class ImmigrationScreen {
    private ImmigrationScreen() {}

    public static void open(ServerPlayerEntity officer) {
        if (!mayUse(officer)) {
            officer.sendMessage(Text.literal("Keine Berechtigung für die Einreiseverwaltung.").formatted(Formatting.RED), false);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        IdentityState identities = IdentityState.get(officer.getServer());
        display(inventory, 4, Items.WRITABLE_BOOK, "Einreiseverwaltung", "Bürger prüfen, freigeben und Ausweise ausstellen");

        int row = 0;
        for (ServerPlayerEntity target : officer.getServer().getPlayerManager().getPlayerList()) {
            CitizenIdentity identity = identities.get(target.getUuid());
            if (row >= 15) continue;
            int infoSlot = 9 + row * 3;
            int approveSlot = infoSlot + 1;
            int issueSlot = infoSlot + 2;
            if (identity == null) {
                display(inventory, infoSlot, Items.PLAYER_HEAD, "Noch nicht registriert", "Technische Zuordnung: " + target.getGameProfile().getName());
                display(inventory, approveSlot, Items.EMERALD, "Bürgerakte anlegen", "Geführten Identitätsassistenten starten");
                actions.put(approveSlot, ignored -> IdentityCreationWizard.start(officer, target));
                row++;
                continue;
            }
            String rpName = identity.firstName() + " " + identity.lastName();
            display(inventory, infoSlot, Items.PLAYER_HEAD, rpName, identity.citizenNumber());
            actions.put(infoSlot, ignored -> IdentityScreen.open(officer, identity));
            if (identities.isApproved(target.getUuid())) {
                display(inventory, approveSlot, Items.LIME_DYE, "Freigegeben", "Einreise ist bestätigt");
                display(inventory, issueSlot, Items.PAPER, "Ausweis ausstellen", "Dokument an " + rpName + " übergeben");
                actions.put(issueSlot, ignored -> {
                    target.giveItemStack(CitizenIdCardItem.createCard(ModItems.CITIZEN_ID_CARD, identity));
                    open(officer);
                });
            } else {
                display(inventory, approveSlot, Items.YELLOW_DYE, "Einreise freigeben", "Als Bediensteter verbindlich bestätigen");
                actions.put(approveSlot, ignored -> {
                    identities.approve(target.getUuid(), officer.getUuid());
                    open(officer);
                });
            }
            row++;
        }

        officer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new ActionMenuScreenHandler(syncId, playerInventory, inventory, actions),
                Text.literal("TerraNexus Einreisebehörde").formatted(Formatting.DARK_AQUA)));
    }

    private static boolean mayUse(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(2)) return true;
        AuthorityState state = AuthorityState.get(player.getServer());
        return state.has(player.getUuid(), AuthorityState.CIVIL_REGISTRAR)
                || state.has(player.getUuid(), AuthorityState.IMMIGRATION_OFFICER)
                || state.has(player.getUuid(), AuthorityState.SUPPORTER);
    }

    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(detail).formatted(Formatting.GRAY))));
        inventory.setStack(slot, stack);
    }
}
