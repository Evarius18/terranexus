package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InstitutionScreen {
    private InstitutionScreen() {}

    public static void open(ServerPlayerEntity player) {
        InstitutionState state = InstitutionState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Verwaltungsübersicht");
        actions.put(8, ignored -> home(player));
        int slot = 9;
        for (Institution institution : state.forMember(player.getUuid())) {
            if (slot >= 45) break;
            ManagementHubScreen.display(inventory, slot, Items.BRICKS, institution.name(), institution.type());
            actions.put(slot, ignored -> openDetails(player, institution)); slot++;
        }
        if (mayCreate(player)) {
            ManagementHubScreen.display(inventory, 49, Items.EMERALD, "Institution gründen", "Behörde, Firma, Verein oder Gruppe anlegen");
            actions.put(49, ignored -> createStep(player, new ArrayList<>(), 0));
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, ignored) -> new ActionMenuScreenHandler(syncId, inv, inventory, actions),
                Text.literal("TerraNexus Institutionen").formatted(Formatting.DARK_AQUA)));
    }

    private static void createStep(ServerPlayerEntity player, List<String> values, int step) {
        if(!mayCreate(player)){player.sendMessage(Text.literal("Keine Berechtigung zur Institutionsgründung.").formatted(Formatting.RED),false);home(player);return;}
        if(step==1){selectType(player,values);return;}
        String label = step == 0 ? "Name der Institution" : "Art (Behörde/Firma/Verein/Gruppe)";
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, ignored) ->
                new TextInputScreenHandler(syncId, inv, value -> {
                    value=value==null?"":value.trim();
                    if(value.isBlank()||value.length()>80){
                        player.sendMessage(Text.literal(value.isBlank()?"Der Institutionsname darf nicht leer sein.":"Der Institutionsname darf maximal 80 Zeichen lang sein.").formatted(Formatting.RED),false);
                        createStep(player,new ArrayList<>(),0);
                        return;
                    }
                    values.add(value);
                    if (step == 0) createStep(player, values, 1);
                    else { InstitutionState.get(player.getServer()).create(values.get(0), values.get(1), player.getUuid()); open(player); }
                }), Text.literal(label).formatted(Formatting.DARK_AQUA)));
    }

    private static void selectType(ServerPlayerEntity player,List<String> values){List<SelectionMenuScreen.Option> options=List.of(new SelectionMenuScreen.Option("Behörde","Behörde","Öffentliche Verwaltung",Items.IRON_BLOCK),new SelectionMenuScreen.Option("Unternehmen","Unternehmen","Privatwirtschaftlicher Betrieb",Items.GOLD_INGOT),new SelectionMenuScreen.Option("Verein","Verein","Eingetragene Gemeinschaft",Items.OAK_SIGN),new SelectionMenuScreen.Option("Partei","Partei","Politische Organisation",Items.PAPER),new SelectionMenuScreen.Option("Bildungseinrichtung","Bildungseinrichtung","Schule, Hochschule oder Forschung",Items.BOOKSHELF),new SelectionMenuScreen.Option("Rettungsorganisation","Rettungsorganisation","Feuerwehr, Rettungsdienst oder Hilfswerk",Items.RED_DYE),new SelectionMenuScreen.Option("Sonstige Institution","Sonstige Institution","Andere offizielle Organisation",Items.BRICKS));SelectionMenuScreen.open(player,"Institutionsart auswählen",options,type->{if(!mayCreate(player)){player.sendMessage(Text.literal("Deine Berechtigung hat sich geändert.").formatted(Formatting.RED),false);home(player);return;}if(values.isEmpty()||values.get(0).isBlank()){player.sendMessage(Text.literal("Der Institutionsname darf nicht leer sein.").formatted(Formatting.RED),false);open(player);return;}InstitutionState.get(player.getServer()).create(values.get(0).trim(),type,player.getUuid());open(player);},()->createStep(player,new ArrayList<>(),0));}

    private static void openDetails(ServerPlayerEntity player, Institution institution) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.BRICKS, institution.name(), institution.type());
        ManagementHubScreen.display(inventory, 13, Items.GOLD_BLOCK, "Institutionskonto",
                EconomyState.format(EconomyState.get(player.getServer()).balance(EconomyState.institutionAccount(institution.id()))));
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Institutionsübersicht");
        actions.put(8, ignored -> open(player));
        int slot=18;if(!InstitutionState.get(player.getServer()).mayManage(institution.id(),player.getUuid())){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,inv,ignored)->new ActionMenuScreenHandler(syncId,inv,inventory,actions),Text.literal(institution.name())));return;}
        for(ServerPlayerEntity candidate: player.getServer().getPlayerManager().getPlayerList()) {
            if(slot>=45) break;
            if(institution.members().containsKey(candidate.getUuid().toString())) continue;
            ManagementHubScreen.display(inventory,slot,Items.PLAYER_HEAD,"Als Mitglied aufnehmen","Online-Bürger auswählen");
            actions.put(slot, ignored->{InstitutionState current=InstitutionState.get(player.getServer());if(!current.mayManage(institution.id(),player.getUuid())){player.sendMessage(Text.literal("Deine Berechtigung hat sich geändert.").formatted(Formatting.RED),false);open(player);return;}current.addMember(institution.id(),candidate.getUuid(),"member");openDetails(player,current.get(institution.id()));});slot++;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,inv,ignored)->new ActionMenuScreenHandler(syncId,inv,inventory,actions),Text.literal(institution.name())));
    }

    private static boolean mayCreate(ServerPlayerEntity player) {
        if(player.hasPermissionLevel(2)) return true;
        return AuthorityState.get(player.getServer()).has(player.getUuid(),AuthorityState.CIVIL_REGISTRAR);
    }
    private static void home(ServerPlayerEntity player){if(AuthorityState.mayManageIdentity(player)||AuthorityState.mayUseLandOffice(player))AdminDesktopScreen.open(player);else ManagementHubScreen.open(player);}
}
