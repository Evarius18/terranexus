package net.evarius.terranexus.management;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class LandAdministrationScreen {
    private LandAdministrationScreen() {}
    public static void open(ServerPlayerEntity player){LandManagementState state=LandManagementState.get(player.getServer());SimpleInventory inv=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inv,4,Items.BELL,"Verwaltungsebenen","Region → Landkreis → Gemeinde → Stadtteil");ManagementHubScreen.display(inv,8,Items.ARROW,"Zurück","Grundstücksverwaltung");actions.put(8,x->PropertyScreen.open(player));if(AuthorityState.mayManageLand(player)){createButton(inv,actions,10,Items.MAP,"Region",player);createButton(inv,actions,11,Items.PAPER,"Landkreis",player);createButton(inv,actions,12,Items.BRICKS,"Gemeinde",player);createButton(inv,actions,13,Items.OAK_SIGN,"Stadtteil",player);}int slot=18;for(AdministrativeArea area:state.areas()){if(slot>=54)break;ManagementHubScreen.display(inv,slot++,Items.FILLED_MAP,area.name(),area.type()+(area.parentId().isBlank()?"":" · in "+parentName(state,area.parentId()))+" · Konto "+EconomyState.format(EconomyState.get(player.getServer()).balance(EconomyState.areaAccount(area.id()))));}openMenu(player,inv,actions,"Gebietsverwaltung");}
    public static void selectForProperty(ServerPlayerEntity player,LandProperty property){LandManagementState state=LandManagementState.get(player.getServer());SimpleInventory inv=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inv,4,Items.MAP,"Ebene zuweisen",property.name());ManagementHubScreen.display(inv,8,Items.ARROW,"Zurück","Verträge und Rechte");actions.put(8,x->PropertyFinanceScreen.open(player,property));int slot=9;for(AdministrativeArea area:state.areas()){if(slot>=54)break;ManagementHubScreen.display(inv,slot,Items.FILLED_MAP,area.name(),area.type());String id=area.id();actions.put(slot++,x->{state.assignArea(property.id(),id);PropertyFinanceScreen.open(player,property);});}openMenu(player,inv,actions,"Verwaltungsebene wählen");}
    private static void createButton(SimpleInventory inv,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,int slot,net.minecraft.item.Item item,String type,ServerPlayerEntity player){ManagementHubScreen.display(inv,slot,item,type+" anlegen",type.equals("Region")?"Oberste Ebene":"Übergeordnete Ebene wird ausgewählt");actions.put(slot,x->{if(type.equals("Region"))askName(player,type,"");else selectParent(player,type);});}
    private static void selectParent(ServerPlayerEntity player,String childType){String required=switch(childType){case "Landkreis"->"Region";case "Gemeinde"->"Landkreis";default->"Gemeinde";};SimpleInventory inv=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inv,4,Items.COMPASS,"Übergeordnete Ebene",required+" für neuen "+childType+" wählen");int slot=9;for(AdministrativeArea area:LandManagementState.get(player.getServer()).areas()){if(!area.type().equals(required)||slot>=54)continue;ManagementHubScreen.display(inv,slot,Items.FILLED_MAP,area.name(),area.type());String parent=area.id();actions.put(slot++,x->askName(player,childType,parent));}ManagementHubScreen.display(inv,8,Items.ARROW,"Zurück","Gebietsverwaltung");actions.put(8,x->open(player));openMenu(player,inv,actions,"Überordnung wählen");}
    private static void askName(ServerPlayerEntity player,String type,String parent){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,ignored)->new TextInputScreenHandler(id,inventory,name->{if(!name.isBlank())LandManagementState.get(player.getServer()).createArea(name.trim(),type,parent,"player",player.getUuidAsString());open(player);}),Text.literal(type+" benennen")));}
    private static String parentName(LandManagementState state,String id){AdministrativeArea parent=state.area(id);return parent==null?"Unbekannt":parent.name();}
    private static void openMenu(ServerPlayerEntity player,SimpleInventory inventory,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,String title){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,pi,x)->new ActionMenuScreenHandler(id,pi,inventory,actions),Text.literal(title).formatted(Formatting.DARK_GREEN)));}
}
