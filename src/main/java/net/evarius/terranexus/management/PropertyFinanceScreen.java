package net.evarius.terranexus.management;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.Consumer;

public final class PropertyFinanceScreen {
    private PropertyFinanceScreen() {}
    public static void open(ServerPlayerEntity player, LandProperty property) {
        LandManagementState management=LandManagementState.get(player.getServer());boolean manager=mayManage(player,property);boolean commercialManager=LandTradeService.mayManageCommercially(player,property);SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();
        ManagementHubScreen.display(inventory,4,Items.FILLED_MAP,property.name(),"Eigentum, Miete und Zugriffsrechte");ManagementHubScreen.display(inventory,8,Items.ARROW,"Zurück","Zur vorherigen Übersicht");actions.put(8,ignored->back(player,property));
        LandSaleOffer sale=management.sale(property.id());
        if(sale==null&&commercialManager){button(inventory,actions,10,Items.EMERALD,"Zum Verkauf anbieten","Kaufpreis eingeben",ignored->money(player,"Kaufpreis",price->{showResult(player,LandTradeService.offerSale(player,property.id(),price));openLatest(player,property.id());}));}
        else if(sale!=null){button(inventory,actions,10,Items.EMERALD,"Kaufangebot",EconomyState.format(sale.price()),ignored->{if(LandTradeService.mayManageCommercially(player,LandlordState.get(player.getServer()).get(property.id())))showResult(player,LandTradeService.cancelSale(player,property.id()));else showResult(player,LandTradeService.buy(player,property.id()));openLatest(player,property.id());});}
        LandLease lease=management.lease(property.id());
        if(lease==null&&commercialManager)button(inventory,actions,12,Items.WRITABLE_BOOK,"Mietangebot erstellen","Mieter, Intervall, Laufzeit, Verlängerung und Kaution",ignored->selectTenant(player,property));
        else if(lease!=null){String term=lease.termPayments()<=0?"unbefristet":lease.termPayments()+" Zahlung(en)";String detail=(lease.active()?"Aktiv":"Zur Annahme")+" · "+EconomyState.format(lease.rent())+" / "+lease.periodDays()+" Tag(e) · "+term+" · "+(lease.autoRenew()?"Verlängerung":"keine Verlängerung");button(inventory,actions,12,Items.WRITTEN_BOOK,"Mietvertrag",detail,ignored->{if(!lease.active()&&lease.tenantId().equals(player.getUuidAsString()))showResult(player,LandTradeService.acceptLease(player,property.id()));else if(commercialManager||lease.tenantId().equals(player.getUuidAsString()))showResult(player,LandTradeService.terminateLease(player,property.id()));openLatest(player,property.id());});}
        LandAccess access=management.access(property.id());
        if(manager){toggle(inventory,actions,28,Items.OAK_DOOR,"Öffentliche Interaktion",access,LandAccess.INTERACT,player,property);toggle(inventory,actions,30,Items.CHEST,"Öffentliche Container",access,LandAccess.CONTAINERS,player,property);toggle(inventory,actions,32,Items.REDSTONE,"Öffentliches Redstone",access,LandAccess.REDSTONE,player,property);toggle(inventory,actions,34,Items.BRICKS,"Öffentliches Bauen",access,LandAccess.BUILD,player,property);button(inventory,actions,40,Items.NAME_TAG,"Person berechtigen","Rechte einzeln vergeben",ignored->selectTrusted(player,property));if(AuthorityState.mayAdministerLand(player))button(inventory,actions,42,Items.MAP,"Verwaltungszuständigkeit",areaLabel(management,property),ignored->LandAdministrationScreen.selectForProperty(player,property));if(AuthorityState.mayProcessLandRecords(player))button(inventory,actions,44,Items.OAK_SIGN,"Flächennutzung",management.landUse(property.id()),ignored->LandAdministrationScreen.selectLandUse(player,property));}
        openMenu(player,inventory,actions,"Grundstück · Verträge");
    }
    private static boolean mayManage(ServerPlayerEntity p,LandProperty prop){return prop!=null&&(AuthorityState.mayProcessLandRecords(p)||prop.isOwnedBy(p.getUuid())||(prop.ownerType().equals("institution")&&InstitutionState.get(p.getServer()).mayManage(prop.ownerId(),p.getUuid()))||(prop.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)&&LandManagementState.get(p.getServer()).mayManageArea(prop.ownerId(),p)));}
    private static void selectTenant(ServerPlayerEntity owner,LandProperty property){selectTenant(owner,property,0);}
    private static void selectTenant(ServerPlayerEntity owner,LandProperty property,int requestedPage){
        List<CitizenIdentity> citizens=IdentityState.get(owner.getServer()).allApproved().stream()
                .filter(identity->!identity.playerUuid().equals(owner.getUuidAsString())).toList();
        int pageSize=ConfigManager.desktop().standardEntriesPerPage;int pages=Math.max(1,(citizens.size()+pageSize-1)/pageSize);
        int page=Math.max(0,Math.min(requestedPage,pages-1));SimpleInventory inventory=new SimpleInventory(54);
        Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();
        ManagementHubScreen.display(inventory,4,Items.PLAYER_HEAD,"Mieter auswählen",citizens.size()+" Bürger · Seite "+(page+1)+"/"+pages);
        if(page>0)button(inventory,actions,0,Items.ARROW,"Vorherige Seite","Seite "+page,ignored->selectTenant(owner,property,page-1));
        if(page+1<pages)button(inventory,actions,7,Items.ARROW,"Nächste Seite","Seite "+(page+2),ignored->selectTenant(owner,property,page+1));
        ManagementHubScreen.display(inventory,8,Items.ARROW,"Zurück","Vertragsansicht");actions.put(8,ignored->open(owner,property));
        int slot=9;for(CitizenIdentity identity:citizens.subList(page*pageSize,Math.min(citizens.size(),(page+1)*pageSize))){
            String name=identity.firstName()+" "+identity.lastName();button(inventory,actions,slot++,Items.PLAYER_HEAD,name,identity.citizenNumber(),
                    ignored->leaseMoney(owner,property,identity.playerUuid(),name));}
        openMenu(owner,inventory,actions,"Mieter auswählen");
    }
    private static void leaseMoney(ServerPlayerEntity owner,LandProperty property,String tenantId,String tenantName){money(owner,"Miete pro Zeitraum",rent->money(owner,"Kaution",deposit->integer(owner,"Zahlungsintervall in Tagen",days->integerAllowZero(owner,"Vertragszahlungen (0 = unbefristet)",payments->chooseRenewal(owner,property,tenantId,tenantName,rent,deposit,days,payments)))));}
    private static void chooseRenewal(ServerPlayerEntity owner,LandProperty property,String tenantId,String tenantName,long rent,long deposit,int days,int payments){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.CLOCK,"Automatische Verlängerung?",payments==0?"Unbefristeter Vertrag":payments+" Zahlung(en)");button(inventory,actions,20,Items.LIME_DYE,"Ja","Vertrag erneuert sich nach der Laufzeit",ignored->createLease(owner,property,tenantId,tenantName,rent,deposit,days,payments,true));button(inventory,actions,24,Items.RED_DYE,"Nein","Vertrag endet nach der Laufzeit",ignored->createLease(owner,property,tenantId,tenantName,rent,deposit,days,payments,false));button(inventory,actions,8,Items.ARROW,"Zurück","Vertragsansicht",ignored->open(owner,property));openMenu(owner,inventory,actions,"Mietverlängerung");}
    private static void createLease(ServerPlayerEntity owner,LandProperty property,String tenantId,String tenantName,long rent,long deposit,int days,int payments,boolean renew){LandTradeService.Result result;try{result=LandTradeService.offerLease(owner,property.id(),UUID.fromString(tenantId),rent,deposit,days,payments,renew);}catch(IllegalArgumentException exception){result=new LandTradeService.Result(false,"Ungültige Mieter-ID.");}showResult(owner,result);openLatest(owner,property.id());}
    private static void selectTrusted(ServerPlayerEntity manager,LandProperty property){LandProperty current=LandlordState.get(manager.getServer()).get(property.id());if(current==null||!mayManage(manager,current)){error(manager,"Keine aktuelle Berechtigung für dieses Grundstück.");back(manager);return;}SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();int slot=9;for(CitizenIdentity identity:IdentityState.get(manager.getServer()).allApproved()){if(slot>=54)continue;button(inventory,actions,slot++,Items.PLAYER_HEAD,identity.firstName()+" "+identity.lastName(),identity.citizenNumber(),ignored->trustedPermissions(manager,current,identity));}openMenu(manager,inventory,actions,"Person berechtigen");}
    private static void trustedPermissions(ServerPlayerEntity manager,LandProperty property,CitizenIdentity identity){LandManagementState state=LandManagementState.get(manager.getServer());LandAccess access=state.access(property.id());SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.PLAYER_HEAD,identity.firstName()+" "+identity.lastName(),"Individuelle Grundstücksrechte");individualToggle(inventory,actions,19,Items.BRICKS,"Bauen",LandAccess.BUILD,manager,property,identity,access);individualToggle(inventory,actions,21,Items.OAK_DOOR,"Interaktion",LandAccess.INTERACT,manager,property,identity,access);individualToggle(inventory,actions,23,Items.CHEST,"Container",LandAccess.CONTAINERS,manager,property,identity,access);individualToggle(inventory,actions,25,Items.REDSTONE,"Redstone",LandAccess.REDSTONE,manager,property,identity,access);ManagementHubScreen.display(inventory,8,Items.ARROW,"Zurück","Personenauswahl");actions.put(8,x->selectTrusted(manager,property));openMenu(manager,inventory,actions,"Einzelrechte");}
    private static void individualToggle(SimpleInventory inventory,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,int slot,net.minecraft.item.Item item,String label,String permission,ServerPlayerEntity manager,LandProperty property,CitizenIdentity identity,LandAccess access){List<String> current=access.grants().getOrDefault(identity.playerUuid(),List.of());boolean enabled=current.contains(permission);button(inventory,actions,slot,item,label,enabled?"Erlaubt":"Gesperrt",ignored->{LandProperty latestProperty=LandlordState.get(manager.getServer()).get(property.id());if(latestProperty==null||!mayManage(manager,latestProperty)){error(manager,"Berechtigung oder Eigentum hat sich geändert.");back(manager);return;}LandManagementState state=LandManagementState.get(manager.getServer());LandAccess latest=state.access(property.id());List<String> latestRules=latest.grants().getOrDefault(identity.playerUuid(),List.of());Map<String,List<String>> grants=new HashMap<>(latest.grants());List<String> changed=new ArrayList<>(latestRules);if(changed.contains(permission))changed.remove(permission);else changed.add(permission);if(changed.isEmpty())grants.remove(identity.playerUuid());else grants.put(identity.playerUuid(),changed);state.setAccess(new LandAccess(property.id(),grants,latest.publicRules()));trustedPermissions(manager,latestProperty,identity);});}
    private static void toggle(SimpleInventory inv,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,int slot,net.minecraft.item.Item item,String name,LandAccess access,String rule,ServerPlayerEntity player,LandProperty property){boolean enabled=access.publicRules().getOrDefault(rule,false);button(inv,actions,slot,item,name,enabled?"Erlaubt":"Gesperrt",ignored->{LandProperty current=LandlordState.get(player.getServer()).get(property.id());if(current==null||!mayManage(player,current)){error(player,"Berechtigung oder Eigentum hat sich geändert.");back(player);return;}LandAccess latest=LandManagementState.get(player.getServer()).access(property.id());Map<String,Boolean> rules=new HashMap<>(latest.publicRules());rules.put(rule,!latest.publicRules().getOrDefault(rule,false));LandManagementState.get(player.getServer()).setAccess(new LandAccess(property.id(),latest.grants(),rules));open(player,current);});}
    private static String areaLabel(LandManagementState state,LandProperty property){AdministrativeArea area=state.jurisdiction(property);return area==null?ConfigManager.administration().wildernessName:state.levelName(area.level())+" · "+area.name();}
    private static void money(ServerPlayerEntity player,String title,Consumer<Long> done){input(player,title,value->{Long amount=EconomyState.parseAmount(value,true);if(amount==null){error(player,"Ungültiger Geldbetrag.");back(player);return;}done.accept(amount);});}
    private static void integer(ServerPlayerEntity player,String title,Consumer<Integer> done){input(player,title,value->{try{int parsed=Integer.parseInt(value);if(parsed<=0)throw new NumberFormatException();done.accept(parsed);}catch(Exception e){error(player,"Bitte eine positive ganze Zahl eingeben.");back(player);}});}
    private static void integerAllowZero(ServerPlayerEntity player,String title,Consumer<Integer> done){input(player,title,value->{try{int parsed=Integer.parseInt(value);if(parsed<0)throw new NumberFormatException();done.accept(parsed);}catch(Exception e){error(player,"Bitte null oder eine positive ganze Zahl eingeben.");back(player);}});}
    private static void showResult(ServerPlayerEntity player,LandTradeService.Result result){player.sendMessage(Text.literal(result.message()).formatted(result.success()?Formatting.GREEN:Formatting.RED),false);}
    private static void openLatest(ServerPlayerEntity player,String propertyId){LandProperty current=LandlordState.get(player.getServer()).get(propertyId);if(current==null)back(player);else open(player,current);}
    private static void input(ServerPlayerEntity player,String title,Consumer<String> done){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,ignored)->new TextInputScreenHandler(id,inventory,done),Text.literal(title)));}
    private static void error(ServerPlayerEntity player,String message){player.sendMessage(Text.literal(message).formatted(Formatting.RED),false);}
    private static void button(SimpleInventory inventory,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,int slot,net.minecraft.item.Item item,String name,String detail,Consumer<net.minecraft.entity.player.PlayerEntity> action){ManagementHubScreen.display(inventory,slot,item,name,detail);actions.put(slot,action);}
    private static void openMenu(ServerPlayerEntity player,SimpleInventory inventory,Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions,String title){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,playerInventory,ignored)->new ActionMenuScreenHandler(id,playerInventory,inventory,actions),Text.literal(title).formatted(Formatting.DARK_GREEN)));}
    private static void back(ServerPlayerEntity player){if(AuthorityState.mayUseLandOffice(player))PropertyScreen.open(player);else LandRegistryScreen.open(player);}
    private static void back(ServerPlayerEntity player,LandProperty property){
        if(AuthorityState.mayUseLandOffice(player)){PropertyScreen.open(player);return;}
        if(property!=null&&property.ownerType().equals("institution")
                &&(AuthorityState.isTnAdmin(player)||InstitutionState.get(player.getServer()).mayManage(property.ownerId(),player.getUuid()))){
            InstitutionManagementScreen.open(player,property.ownerId());return;
        }
        if(property!=null&&property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                &&LandManagementState.get(player.getServer()).mayManageAreaFinances(property.ownerId(),player)){
            AreaFinanceScreen.openArea(player,property.ownerId());return;
        }
        LandRegistryScreen.open(player);
    }
}
