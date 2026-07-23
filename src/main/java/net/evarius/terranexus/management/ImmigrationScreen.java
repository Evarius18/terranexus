package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.CitizenIdCardItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImmigrationScreen {
    private ImmigrationScreen() {}

    public static void open(ServerPlayerEntity officer) {
        open(officer, 0);
    }

    public static void open(ServerPlayerEntity officer, int requestedPage) {
        if (!mayUse(officer)) {
            officer.sendMessage(Text.literal("Keine Berechtigung für die Einreiseverwaltung.").formatted(Formatting.RED), false);
            return;
        }

        IdentityState identities = IdentityState.get(officer.getServer());
        List<CitizenEntry> citizens = collectCitizens(officer, identities);
        int pageSize = ConfigManager.desktop().immigrationEntriesPerPage;
        int pageCount = Math.max(1, (citizens.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = page * pageSize;
        int to = Math.min(citizens.size(), from + pageSize);

        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        display(inventory, 4, Items.WRITABLE_BOOK, "Einreiseverwaltung",
                citizens.size() + " Bürger · Seite " + (page + 1) + "/" + pageCount);
        display(inventory, 8, Items.ARROW, "Zurück", "Zum Admin-Desktop");
        actions.put(8, ignored -> AdminDesktopScreen.open(officer));
        if (page > 0) {
            display(inventory, 0, Items.ARROW, "Vorherige Seite", "Seite " + page);
            actions.put(0, ignored -> open(officer, page - 1));
        }
        if (page + 1 < pageCount) {
            display(inventory, 7, Items.ARROW, "Nächste Seite", "Seite " + (page + 2));
            actions.put(7, ignored -> open(officer, page + 1));
        }

        int row = 0;
        for (CitizenEntry entry : citizens.subList(from, to)) {
            int infoSlot = 9 + row * 3;
            int approveSlot = infoSlot + 1;
            int issueSlot = infoSlot + 2;
            CitizenIdentity identity = entry.identity();
            ServerPlayerEntity target = entry.onlinePlayer();

            if (identity == null) {
                display(inventory, infoSlot, Items.PLAYER_HEAD, "Noch nicht registriert",
                        "Technische Zuordnung: " + target.getGameProfile().getName());
                display(inventory, approveSlot, Items.EMERALD, "Bürgerakte anlegen", "Geführten Identitätsassistenten starten");
                actions.put(approveSlot, ignored -> IdentityCreationWizard.start(officer, target));
                row++;
                continue;
            }

            UUID citizenId = UUID.fromString(identity.playerUuid());
            String rpName = identity.firstName() + " " + identity.lastName();
            display(inventory, infoSlot, Items.PLAYER_HEAD, rpName,
                    identity.citizenNumber() + (target == null ? " · offline" : " · online"));
            actions.put(infoSlot, ignored -> CitizenRecordScreen.open(officer, citizenId, page));
            if (identities.isApproved(citizenId)) {
                display(inventory, approveSlot, Items.LIME_DYE, "Freigegeben", "Einreise ist bestätigt");
                if (target != null) {
                    display(inventory, issueSlot, Items.PAPER, "Ausweis ausstellen", "Dokument an " + rpName + " übergeben");
                    actions.put(issueSlot, ignored -> issueCard(officer, citizenId, page));
                } else {
                    display(inventory, issueSlot, Items.GRAY_DYE, "Bürger offline", "Ausstellung ist beim nächsten Online-Termin möglich");
                }
            } else {
                display(inventory, approveSlot, Items.YELLOW_DYE, "Einreise freigeben", "Als Bediensteter verbindlich bestätigen");
                actions.put(approveSlot, ignored -> approve(officer, citizenId, page));
            }
            row++;
        }

        CustomGuiService.open(officer, inventory, actions,
                Text.literal("TerraNexus Einreisebehörde").formatted(Formatting.DARK_AQUA));
    }

    private static List<CitizenEntry> collectCitizens(ServerPlayerEntity officer, IdentityState identities) {
        Map<UUID, CitizenEntry> entries = new LinkedHashMap<>();
        for (CitizenIdentity identity : identities.all()) {
            try {
                UUID id = UUID.fromString(identity.playerUuid());
                entries.put(id, new CitizenEntry(identity, officer.getServer().getPlayerManager().getPlayer(id)));
            } catch (IllegalArgumentException ignored) {
                // Legacy/corrupt records without a valid player UUID cannot be operated on safely.
            }
        }
        for (ServerPlayerEntity online : officer.getServer().getPlayerManager().getPlayerList()) {
            entries.putIfAbsent(online.getUuid(), new CitizenEntry(null, online));
        }
        return new ArrayList<>(entries.values());
    }

    private static void approve(ServerPlayerEntity officer, UUID citizenId, int page) {
        if (!mayUse(officer)) return;
        IdentityState state = IdentityState.get(officer.getServer());
        if (state.get(citizenId) != null) state.approve(citizenId, officer.getUuid());
        open(officer, page);
    }

    private static void issueCard(ServerPlayerEntity officer, UUID citizenId, int page) {
        if (!mayUse(officer)) return;
        IdentityState state = IdentityState.get(officer.getServer());
        CitizenIdentity current = state.get(citizenId);
        ServerPlayerEntity target = officer.getServer().getPlayerManager().getPlayer(citizenId);
        if (current != null && state.isApproved(citizenId) && target != null) {
            target.giveItemStack(CitizenIdCardItem.createCard(ModItems.CITIZEN_ID_CARD, current));
        }
        open(officer, page);
    }

    private static boolean mayUse(ServerPlayerEntity player) {
        return AuthorityState.mayManageIdentity(player);
    }

    private static void display(SimpleInventory inventory, int slot, Item item, String name, String detail) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(detail).formatted(Formatting.GRAY))));
        inventory.setStack(slot, stack);
    }

    private record CitizenEntry(CitizenIdentity identity, ServerPlayerEntity onlinePlayer) {}
}
