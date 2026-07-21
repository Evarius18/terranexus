package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public final class AdminDesktopScreen {
    private AdminDesktopScreen() {}

    public static void open(ServerPlayerEntity player) {
        boolean identity = AuthorityState.mayManageIdentity(player);
        boolean land = AuthorityState.mayUseLandOffice(player);
        boolean institution = AuthorityState.isTnAdmin(player)
                || !InstitutionState.get(player.getServer()).forMember(player.getUuid()).isEmpty();
        boolean bank = InstitutionAccess.hasBankPermission(player, InstitutionPermission.BANK_VIEW_ACCOUNTS);
        boolean centralBank = InstitutionAccess.hasCentralBankPermission(player, InstitutionPermission.CENTRAL_BANK_VIEW);
        boolean areaFinance = AreaFinanceScreen.hasManagedArea(player);
        if (!identity && !land && !institution && !bank && !centralBank && !areaFinance) {
            player.sendMessage(Text.literal("Dieser Verwaltungs-PC ist nur für autorisierte Bedienstete freigeschaltet.").formatted(Formatting.RED), false);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        String desktopName = ConfigManager.general().serverDisplayName + " Admin-Desktop";
        ManagementHubScreen.display(inventory, 4, Items.COMPARATOR, desktopName,
                AuthorityState.isTnAdmin(player) ? "TNAdmin-Entwicklungszugriff aktiv" : "Fachmodule entsprechend deiner Rollen");
        if (identity) {
            ManagementHubScreen.display(inventory, 19, Items.WRITABLE_BOOK, "Bürger- und Einreiseverwaltung", "Akten anlegen, bearbeiten und freischalten");
            actions.put(19, ignored -> ImmigrationScreen.open(player));
        }
        if (bank) {
            ManagementHubScreen.display(inventory, 21, Items.GOLD_BLOCK, "Bankverwaltung", "Konten, Schalter, Sperren und Revision");
            actions.put(21, ignored -> BankManagementScreen.open(player));
        }
        if (institution) {
            ManagementHubScreen.display(inventory, 23, Items.BRICKS, "Institutionen", "Personal, Rollen, Gehälter und Finanzen");
            actions.put(23, ignored -> InstitutionScreen.open(player));
        }
        if (land) {
            ManagementHubScreen.display(inventory, 25, Items.FILLED_MAP, "Grundbuch und Landlord", "Flächen, Kauf, Miete, Rechte und Gebiete");
            actions.put(25, ignored -> PropertyScreen.open(player));
        }
        if (AuthorityState.mayAdministerLand(player)) {
            ManagementHubScreen.display(inventory, 31, Items.WRITABLE_BOOK, "Grundstücks-Audit", "Persistente Änderungen und Eigentümerwechsel");
            actions.put(31, ignored -> LandSearchScreen.audit(player));
        }
        if (centralBank) {
            ManagementHubScreen.display(inventory, 33, Items.BEACON, "Zentralbank", "Geldmenge, Geldflüsse und Geldpolitik");
            actions.put(33, ignored -> CentralBankScreen.open(player));
        }
        if (areaFinance) {
            ManagementHubScreen.display(inventory, 35, Items.MAP, "Verwaltungsfinanzen", "Gebietskonten, Personal und Gehälter");
            actions.put(35, ignored -> AreaFinanceScreen.open(player));
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (id, inventory1, ignored) -> new ActionMenuScreenHandler(id, inventory1, inventory, actions),
                Text.literal(desktopName).formatted(Formatting.DARK_AQUA)));
    }
}
