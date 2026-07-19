package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
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
import java.util.UUID;
import java.util.function.Consumer;

public final class EconomyScreen {
    private static final Map<UUID, String> SELECTED_ACCOUNTS = new HashMap<>();
    private EconomyScreen() {}

    public static void open(ServerPlayerEntity player) {
        EconomyState economy = EconomyState.get(player.getServer()); IdentityState identities = IdentityState.get(player.getServer());
        String source = selectedAccount(player); SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.GOLD_INGOT, accountLabel(player, source), EconomyState.format(economy.balance(source)));
        ManagementHubScreen.display(inventory, 7, Items.COMPASS, "Konto wechseln", "Persönliches oder verfügbares Institutionskonto"); actions.put(7, ignored -> openAccountSelection(player));
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Verwaltungsübersicht"); actions.put(8, ignored -> ManagementHubScreen.open(player));
        int slot = 9;
        for (ServerPlayerEntity recipient : player.getServer().getPlayerManager().getPlayerList()) {
            if (recipient == player || slot >= 36) continue; CitizenIdentity identity = identities.get(recipient.getUuid());
            if (identity == null || !identities.isApproved(recipient.getUuid())) continue; String rpName = identity.firstName() + " " + identity.lastName();
            add(inventory, actions, slot++, Items.PLAYER_HEAD, "Überweisen an " + rpName, identity.citizenNumber(), ignored -> openAmountInput(player, source, EconomyState.playerAccount(recipient.getUuid()), rpName));
        }
        slot = 36;
        for (Institution institution : InstitutionState.get(player.getServer()).all()) {
            if (slot >= 54) break; String target = EconomyState.institutionAccount(institution.id()); if (target.equals(source)) continue;
            add(inventory, actions, slot++, Items.BRICKS, "Überweisen an " + institution.name(), institution.type(), ignored -> openAmountInput(player, source, target, institution.name()));
        }
        openMenu(player, inventory, actions, "TerraNexus Bank");
    }

    private static void openAccountSelection(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>(); EconomyState economy = EconomyState.get(player.getServer());
        String personal = EconomyState.playerAccount(player.getUuid()); add(inventory, actions, 10, Items.PLAYER_HEAD, "Persönliches Konto", EconomyState.format(economy.balance(personal)), ignored -> select(player, personal));
        int slot = 18; InstitutionState institutions = InstitutionState.get(player.getServer());
        for (Institution institution : institutions.all()) {
            if (!institutions.mayManage(institution.id(), player.getUuid()) || slot >= 54) continue; String account = EconomyState.institutionAccount(institution.id());
            add(inventory, actions, slot++, Items.BRICKS, institution.name(), EconomyState.format(economy.balance(account)), ignored -> select(player, account));
        }
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Bank"); actions.put(8, ignored -> open(player)); openMenu(player, inventory, actions, "Konto auswählen");
    }
    private static void select(ServerPlayerEntity player, String account) { SELECTED_ACCOUNTS.put(player.getUuid(), account); open(player); }
    private static String selectedAccount(ServerPlayerEntity player) {
        String selected = SELECTED_ACCOUNTS.getOrDefault(player.getUuid(), EconomyState.playerAccount(player.getUuid()));
        if (selected.startsWith("institution:")) { String id = selected.substring("institution:".length()); if (!InstitutionState.get(player.getServer()).mayManage(id, player.getUuid())) return EconomyState.playerAccount(player.getUuid()); }
        return selected;
    }
    private static String accountLabel(ServerPlayerEntity player, String account) {
        if (!account.startsWith("institution:")) return "Persönliches Konto";
        Institution institution = InstitutionState.get(player.getServer()).get(account.substring("institution:".length())); return institution == null ? "Institutionskonto" : "Konto · " + institution.name();
    }
    private static void openAmountInput(ServerPlayerEntity sender, String source, String target, String recipientName) {
        sender.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, ignored) -> new TextInputScreenHandler(syncId, inventory, value -> {
            try { long cents = new BigDecimal(value.replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact(); if (cents <= 0) throw new NumberFormatException();
                if (EconomyState.get(sender.getServer()).transfer(source, target, cents)) sender.sendMessage(Text.literal(EconomyState.format(cents) + " wurden an " + recipientName + " überwiesen.").formatted(Formatting.GREEN), false);
                else sender.sendMessage(Text.literal("Nicht genügend Guthaben oder ungültiges Zielkonto.").formatted(Formatting.RED), false);
            } catch (Exception exception) { sender.sendMessage(Text.literal("Ungültiger Betrag. Beispiel: 125,50").formatted(Formatting.RED), false); }
            open(sender);
        }), Text.literal("Betrag an " + recipientName).formatted(Formatting.GOLD)));
    }
    private static void add(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) { ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action); }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) { player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, playerInventory, ignored) -> new ActionMenuScreenHandler(id, playerInventory, inventory, actions), Text.literal(title).formatted(Formatting.GOLD))); }
}
