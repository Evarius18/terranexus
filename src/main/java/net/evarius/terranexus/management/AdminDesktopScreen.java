package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public final class AdminDesktopScreen {
    private AdminDesktopScreen() {}

    public static void open(ServerPlayerEntity player) {
        boolean identity = AuthorityState.mayManageIdentity(player);
        boolean institution = AuthorityState.isAdministrator(player)
                || !InstitutionState.get(player.getServer()).forMember(player.getUuid()).isEmpty();
        boolean bank = InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_VIEW_ACCOUNTS);
        boolean centralBank = InstitutionAccess.hasCentralBankPermission(player, InstitutionPermission.CENTRAL_BANK_VIEW);
        boolean areaFinance = AreaFinanceScreen.hasManagedArea(player);
        if (!identity && !institution && !bank && !centralBank && !areaFinance) {
            player.sendMessage(Text.translatable("message.terranexus.admin_desktop.denied").formatted(Formatting.RED), false);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        Text desktopName = Text.translatable("gui.terranexus.admin_desktop.title",
                ConfigManager.general().serverDisplayName);
        ManagementHubScreen.display(inventory, 4, Items.COMPARATOR, desktopName,
                Text.translatable(AuthorityState.isTnAdmin(player) ? "gui.terranexus.admin_desktop.access.tnadmin"
                        : AuthorityState.isAdministrator(player) ? "gui.terranexus.admin_desktop.access.admin"
                        : "gui.terranexus.admin_desktop.access.roles"));
        if (identity) {
            ManagementHubScreen.display(inventory, 19, Items.WRITABLE_BOOK,
                    Text.translatable("gui.terranexus.admin_desktop.citizens"),
                    Text.translatable("gui.terranexus.admin_desktop.citizens.description"));
            actions.put(19, ignored -> ImmigrationScreen.open(player));
        }
        if (bank) {
            ManagementHubScreen.display(inventory, 21, Items.GOLD_BLOCK,
                    Text.translatable("gui.terranexus.admin_desktop.bank"),
                    Text.translatable("gui.terranexus.admin_desktop.bank.description"));
            actions.put(21, ignored -> BankManagementScreen.open(player));
        }
        if (institution) {
            ManagementHubScreen.display(inventory, 23, Items.BRICKS,
                    Text.translatable("gui.terranexus.admin.institutions"),
                    Text.translatable("gui.terranexus.admin_desktop.institutions.description"));
            actions.put(23, ignored -> InstitutionScreen.open(player));
        }
        if (centralBank) {
            ManagementHubScreen.display(inventory, 33, Items.BEACON,
                    Text.translatable("gui.terranexus.admin_desktop.central_bank"),
                    Text.translatable("gui.terranexus.admin_desktop.central_bank.description"));
            actions.put(33, ignored -> CentralBankScreen.open(player));
        }
        if (areaFinance) {
            ManagementHubScreen.display(inventory, 35, Items.MAP,
                    Text.translatable("gui.terranexus.admin_desktop.area_finance"),
                    Text.translatable("gui.terranexus.admin_desktop.area_finance.description"));
            actions.put(35, ignored -> AreaFinanceScreen.open(player));
        }
        CustomGuiService.open(player, inventory, actions, desktopName.copy().formatted(Formatting.DARK_AQUA));
    }
}
