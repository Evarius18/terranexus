package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandLease;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandSaleOffer;
import net.evarius.terranexus.landlord.LandAuditState;
import net.evarius.terranexus.landlord.LandGeometry;
import net.evarius.terranexus.landlord.LandSelection;
import net.evarius.terranexus.landlord.LandSelectionState;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.PropertyDrafts;
import net.evarius.terranexus.landlord.LandVisuals;
import net.evarius.terranexus.landlord.AdministrativeArea;
import net.evarius.terranexus.landlord.LandTransferService;
import net.evarius.terranexus.landlord.LandTransferState;
import net.evarius.terranexus.landlord.LandPermissionService;
import net.evarius.terranexus.landlord.LandSurveyTask;
import net.evarius.terranexus.landlord.LandSurveyTaskState;
import net.evarius.terranexus.landlord.PropertySubarea;
import net.evarius.terranexus.landlord.PropertySubareaState;
import net.evarius.terranexus.landlord.ResidentialBuilding;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PropertyScreen {
    private static final java.util.Set<UUID> DESKTOP_OWNER_SELECTIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private PropertyScreen() {}

    public static void open(ServerPlayerEntity player) {
        open(player, 0);
    }

    private static void open(ServerPlayerEntity player, int requestedPage) {
        if(!AuthorityState.mayUseLandOffice(player)){player.sendMessage(Text.literal("Zugriff verweigert: Bauamtsberechtigung erforderlich.").formatted(Formatting.RED),false);return;}
        if (!hasBuildingAuthorityTablet(player)) {
            player.sendMessage(Text.literal("Die Grundstücksverwaltung ist ausschließlich über das Bauamts-Tablet verfügbar.")
                    .formatted(Formatting.RED), false);
            player.closeHandledScreen();
            return;
        }
        LandSurveyTask surveyTask = LandSurveyTaskState.get(player.getServer()).get(player.getUuid());
        if (surveyTask != null) { openSurveyTask(player, surveyTask); return; }
        PropertyDrafts.EditDraft draft = PropertyDrafts.edit(player.getUuid());
        if (draft != null) { openEditor(player); return; }
        LandlordState state = LandlordState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        String dimension = dimension(player);

        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, "Grundstücksverwaltung",
                "Position: " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
        ManagementHubScreen.display(inventory, 8, Items.BARRIER, "Schließen", "Bauamts-Tablet schließen");
        actions.put(8, ignored -> player.closeHandledScreen());
        int pendingTransfers = LandTransferService.pendingFor(player).size();
        ManagementHubScreen.display(inventory, 5, pendingTransfers > 0 ? Items.LIME_DYE : Items.PAPER,
                "Umschreibungen", pendingTransfers + " offene Vorgänge · neue Übertragung anlegen");
        actions.put(5, ignored -> LandTransferScreen.openLandOffice(player));
        ManagementHubScreen.display(inventory,7,Items.COMPASS,"Grundstück suchen","ID, Name oder Besitzer");actions.put(7,x->LandSearchScreen.ask(player));
        if(AuthorityState.mayAdministerLand(player)){ManagementHubScreen.display(inventory,6,Items.WRITABLE_BOOK,"Audit-Log","Erstellungen, Änderungen und Eigentümerwechsel");actions.put(6,x->LandSearchScreen.audit(player));}
        if(AuthorityState.mayManageLandHierarchy(player)){ManagementHubScreen.display(inventory, 17, Items.BELL, "Verwaltungshierarchie", "Wilderness, Ebenen und Zuständigkeiten organisieren");actions.put(17, ignored -> LandAdministrationScreen.open(player));}

        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int slot = 19 + (dz + 1) * 9 + dx + 1;
            int cx = (player.getBlockX() >> 4) + dx, cz = (player.getBlockZ() >> 4) + dz;
            LandProperty at = state.inChunk(dimension,cx,cz);
            ManagementHubScreen.display(inventory, slot, at == null ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
                    "Chunk " + cx + " / " + cz, at == null ? "Frei" : "Belegt: " + at.name());
            if (at != null) actions.put(slot, ignored -> { if (mayEdit(player, at)) beginEdit(player, at); else PropertyFinanceScreen.open(player, at); });
        }

        if (LandPermissionService.mayCreateProperty(player)) {
            ManagementHubScreen.display(inventory, 10, Items.GRASS_BLOCK, "Chunk anlegen", "Aktueller 16×16-Bereich");
            actions.put(10, ignored -> askName(player, name -> finishCreate(player, state,
                    LandlordState.chunk(name, player.getUuid(), dimension, player.getBlockX() >> 4, player.getBlockZ() >> 4))));
            ManagementHubScreen.display(inventory, 12, Items.WOODEN_AXE, "Quader auswählen", "Punkt 1 an aktueller Position");
            actions.put(12, ignored -> { PropertyDrafts.POS1.put(player.getUuid(), player.getBlockPos()); player.sendMessage(Text.literal("Punkt 1 gesetzt. Stelle dich nun an Punkt 2."), true); open(player); });
            ManagementHubScreen.display(inventory, 13, Items.IRON_AXE, "Quader abschließen", "Aktuelle Position als Punkt 2");
            actions.put(13, ignored -> finishCuboid(player, state, dimension));
            LandSelectionState selections=LandSelectionState.get(player.getServer());LandSelection selection=selections.get(player.getUuid());int points=selections.points(player.getUuid()).size();
            ManagementHubScreen.display(inventory, 15, Items.GOLDEN_HOE, selection==null?"Freiformvermessung starten":"Vermessung fortsetzen", "Werkzeug verwenden · Punkte: " + points);
            actions.put(15, ignored -> startSurvey(player, dimension));
            ManagementHubScreen.display(inventory, 14, Items.BARRIER, "Vermessung verwerfen", "Gespeicherten Entwurf löschen");
            actions.put(14, ignored -> { selections.clear(player.getUuid()); open(player); });
            ManagementHubScreen.display(inventory, 16, Items.EMERALD, "Freiform abschließen", "Persistente Auswahl · mindestens 3 Punkte");
            actions.put(16, ignored -> finishPolygon(player, state, dimension));
        } else {
            ManagementHubScreen.display(inventory, 13, Items.BARRIER, "Nur Grundbuchverwaltung",
                    "Neue Flächen legt eine autorisierte Stelle an");
        }

        List<LandProperty> visibleProperties = state.all().stream().filter(property -> {
            LandManagementState management = LandManagementState.get(player.getServer());
            LandSaleOffer offer = management.sale(property.id()); LandLease lease = management.lease(property.id());
            return AuthorityState.mayUseLandOffice(player) || mayEdit(player, property) || offer != null
                    || lease != null && lease.tenantId().equals(player.getUuidAsString());
        }).toList();
        int pageSize = 7;
        int pages = Math.max(1, (visibleProperties.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite",
                "Grundstücke · Seite " + page, ignored -> open(player, page - 1));
        if (page + 1 < pages) button(inventory, actions, 53, Items.ARROW, "Nächste Seite",
                "Grundstücke · Seite " + (page + 2), ignored -> open(player, page + 1));
        int slot = 46;
        for (LandProperty property : visibleProperties.subList(page * pageSize,
                Math.min(visibleProperties.size(), (page + 1) * pageSize))) {
            ManagementHubScreen.display(inventory, slot, Items.PAPER, property.name(),
                    type(property) + " · " + property.minX() + "," + property.minZ() + " bis "
                            + property.maxX() + "," + property.maxZ() + " · Seite " + (page + 1) + "/" + pages);
            actions.put(slot++, ignored -> { if (mayEdit(player, property)) beginEdit(player, property); else PropertyFinanceScreen.open(player, property); });
        }
        openMenu(player, inventory, actions, "TerraNexus Grundstücke");
    }

    private static void openEditor(ServerPlayerEntity player) {
        PropertyDrafts.EditDraft draft = PropertyDrafts.edit(player.getUuid());
        LandlordState state = LandlordState.get(player.getServer());
        LandProperty property = draft == null ? null : state.get(draft.propertyId());
        if (draft == null || property == null || !mayEdit(player, property)) { PropertyDrafts.cancelEdit(player.getUuid()); open(player); return; }
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.MAP, "Bearbeiten: " + property.name(),
                "Freiform · " + draft.points().size() + " Eckpunkte");
        button(inventory, actions, 10, Items.GOLDEN_HOE, "Eckpunkt hinzufügen", "Aktuelle Position wird angehängt", ignored -> { draft.add(player.getBlockPos()); refreshEditor(player); });
        button(inventory, actions, 12, Items.COMPASS, "Nächsten Eckpunkt verschieben", "Nächster Punkt → aktuelle Position", ignored -> { draft.moveNearest(player.getBlockPos()); refreshEditor(player); });
        button(inventory, actions, 14, Items.SHEARS, "Letzten Eckpunkt entfernen", "Kann rückgängig gemacht werden", ignored -> { draft.removeLast(); refreshEditor(player); });
        button(inventory, actions, 16, Items.CLOCK, "Rückgängig", "Letzten Bearbeitungsschritt zurücknehmen", ignored -> { draft.undo(); refreshEditor(player); });
        button(inventory, actions, 29, Items.LIME_DYE, "Um 1 Block vergrößern", "Eckpunkte vom Mittelpunkt wegbewegen", ignored -> { draft.scale(1); refreshEditor(player); });
        button(inventory, actions, 31, Items.RED_DYE, "Um 1 Block verkleinern", "Eckpunkte zum Mittelpunkt bewegen", ignored -> { draft.scale(-1); refreshEditor(player); });
        button(inventory, actions, 33, Items.ENDER_EYE, "Grenze anzeigen", "Partikel markieren alle Kanten", ignored -> { preview(player, draft.points()); openEditor(player); });
        button(inventory, actions, 19, Items.GOLDEN_HOE, "Form neu vermessen", "Bestehendes Grundstück mit dem Werkzeug vergrößern oder verkleinern", ignored -> startPropertySurvey(player, property, LandSurveyTask.RESHAPE, "", ""));
        button(inventory, actions, 21, Items.SHEARS, "Teilfläche abtrennen", "Bereich als eigenes Grundstück aus der Hauptfläche lösen", ignored -> askDetachedName(player, property));
        button(inventory, actions, 23, Items.BRICKS, "Unterbereich anlegen", "Wohnung, Keller, Garage oder anderer interner Bereich", ignored -> selectSubareaType(player, property));
        button(inventory, actions, 25, Items.BOOKSHELF, "Unterbereiche", PropertySubareaState.get(player.getServer()).forProperty(property.id()).size()+" eingetragen", ignored -> openSubareas(player, property, 0));
        button(inventory, actions, 45, Items.NAME_TAG, "Umbenennen", property.name(), ignored -> askName(player, value -> { state.update(property.withName(value)); LandAuditState.get(player.getServer()).log(player.getUuid(),"RENAME",property,property.name()+" -> "+value); openEditor(player); }));
        if (LandTransferService.mayInitiate(player)) button(inventory, actions, 47, Items.PLAYER_HEAD,
                "Eigentümer übertragen", ownerLabel(player, property), ignored -> {
                    PropertyDrafts.cancelEdit(player.getUuid());
                    openOwnerSelection(player, property);
                });
        button(inventory, actions, 51, Items.GOLD_INGOT, "Verträge und Rechte",
                "Verkauf, Vermietung, Zugriff und Verwaltungsebene", ignored -> {
                    PropertyDrafts.cancelEdit(player.getUuid());
                    PropertyFinanceScreen.open(player, property);
                });
        if(AuthorityState.mayAdministerLand(player))button(inventory,actions,43,Items.LAVA_BUCKET,"Grundstück löschen","Nur Bauamtsleitung · mit Bestätigung",ignored->confirmDelete(player,property));
        if(AuthorityState.mayProcessLandRecords(player))button(inventory,actions,41,Items.OAK_SIGN,"Adresse eintragen",LandManagementState.get(player.getServer()).address(property.id()),ignored->askAddress(player,property));
        button(inventory, actions, 49, Items.EMERALD, "Änderungen speichern", "Prüft Form und Überschneidungen", ignored -> saveEdit(player, state, property, draft));
        button(inventory, actions, 53, Items.BARRIER, "Abbrechen", "Entwurf verwerfen", ignored -> { PropertyDrafts.cancelEdit(player.getUuid()); open(player); });
        CustomGuiService.openWithCloseHandler(player, inventory, actions,
                Text.literal("Grundstück bearbeiten").formatted(Formatting.DARK_GREEN),
                () -> PropertyDrafts.cancelEdit(player.getUuid()));
    }

    private static void refreshEditor(ServerPlayerEntity player) { preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static void saveEdit(ServerPlayerEntity player, LandlordState state, LandProperty property, PropertyDrafts.EditDraft draft) {
        if(!AuthorityState.maySurveyLand(player)){error(player,"Deine Vermessungsberechtigung ist nicht mehr gültig.");PropertyDrafts.cancelEdit(player.getUuid());return;}
        String validation=LandGeometry.validatePolygon(draft.points());if(validation!=null){error(player,validation);openEditor(player);return;}
        LandProperty changed = LandlordState.editedPolygon(property, draft.points());
        if(!PropertySubareaState.get(player.getServer()).allInside(changed)){error(player,"Mindestens ein Unterbereich läge anschließend außerhalb des Grundstücks.");openEditor(player);return;}
        LandlordState.ReshapeResult result=reshape(player,state,changed);
        if (!result.success()) { error(player, result.message()); openEditor(player); return; }
        changed=result.property();
        LandAuditState.get(player.getServer()).log(player.getUuid(),"GEOMETRY_UPDATE",changed,"Eckpunkte: "+draft.points().size());
        PropertyDrafts.cancelEdit(player.getUuid());
        player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde gespeichert.").formatted(Formatting.GREEN), false);
        open(player);
    }

    private static void startPropertySurvey(ServerPlayerEntity player, LandProperty property, String mode, String name, String type) {
        if(!AuthorityState.maySurveyLand(player)||!hasBuildingAuthorityTablet(player)){error(player,"Vermessungsberechtigung und Bauamts-Tablet erforderlich.");return;}
        if(!player.getInventory().contains(new net.minecraft.item.ItemStack(ModItems.LAND_SURVEY_TOOL))){error(player,"Kein Landvermessungsgerät im Inventar.");openEditor(player);return;}
        PropertyDrafts.cancelEdit(player.getUuid());
        LandSelectionState selections=LandSelectionState.get(player.getServer());selections.start(player.getUuid(),property.dimension());
        LandSurveyTaskState.get(player.getServer()).put(player.getUuid(),new LandSurveyTask(property.id(),mode,name,type));
        player.closeHandledScreen();
        player.sendMessage(Text.literal("Vermessung gestartet: Rechtsklick setzt Punkte, Linksklick entfernt den letzten. Öffne danach erneut das Bauamts-Tablet.").formatted(Formatting.GREEN),false);
    }

    private static void openSurveyTask(ServerPlayerEntity player,LandSurveyTask task){LandProperty property=LandlordState.get(player.getServer()).get(task.propertyId());if(property==null||!AuthorityState.maySurveyLand(player)){cancelSurveyTask(player);open(player);return;}List<BlockPos> points=LandSelectionState.get(player.getServer()).points(player.getUuid());SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();String mode=switch(task.mode()){case LandSurveyTask.RESHAPE->"Grundstück neu vermessen";case LandSurveyTask.DETACH->"Teilfläche abtrennen";default->"Unterbereich anlegen";};ManagementHubScreen.display(inventory,4,Items.FILLED_MAP,mode,property.name()+" · "+points.size()+" Eckpunkte");button(inventory,actions,20,Items.GOLDEN_HOE,"Vermessung fortsetzen","Weitere Eckpunkte mit dem Werkzeug setzen",ignored->{player.closeHandledScreen();preview(player,points);});button(inventory,actions,22,Items.ENDER_EYE,"Vorschau",points.size()+" Punkte",ignored->{preview(player,points);openSurveyTask(player,task);});button(inventory,actions,24,Items.LIME_DYE,"Auswahl abschließen","Form validieren und übernehmen",ignored->finishSurveyTask(player,task,property,points));button(inventory,actions,32,Items.BARRIER,"Vermessung verwerfen","Auswahl und Vorgang löschen",ignored->{cancelSurveyTask(player);beginEdit(player,property);});openMenu(player,inventory,actions,"Freiform-Vermessung");}

    private static void finishSurveyTask(ServerPlayerEntity player,LandSurveyTask task,LandProperty property,List<BlockPos> points){
        String validation=LandGeometry.validatePolygon(points);if(validation!=null){error(player,validation);openSurveyTask(player,task);return;}
        LandlordState lands=LandlordState.get(player.getServer());
        if(task.mode().equals(LandSurveyTask.RESHAPE)){
            LandProperty changed=LandlordState.editedPolygon(property,points);
            if(!PropertySubareaState.get(player.getServer()).allInside(changed)){error(player,"Unterbereiche dürfen durch die neue Grenze nicht außerhalb liegen.");openSurveyTask(player,task);return;}
            LandlordState.ReshapeResult result=reshape(player,lands,changed);
            if(!result.success()){error(player,result.message());openSurveyTask(player,task);return;}
            changed=result.property();LandAuditState.get(player.getServer()).log(player.getUuid(),"GEOMETRY_RESURVEY",changed,"Eckpunkte: "+points.size()+" · aufgenommen: "+result.absorbedPropertyIds().size());cancelSurveyTask(player);beginEdit(player,changed);return;
        }
        if(!LandlordState.selectionInside(property,points)){error(player,"Die markierte Fläche liegt nicht vollständig innerhalb des Hauptgrundstücks oder ist zu groß.");openSurveyTask(player,task);return;}
        if(task.mode().equals(LandSurveyTask.SUBAREA)){PropertySubarea created=PropertySubareaState.get(player.getServer()).create(property,task.name(),task.type(),points,player.getUuid());if(created==null){error(player,"Unterbereich überschneidet sich, ist zu groß oder ungültig.");openSurveyTask(player,task);return;}LandAuditState.get(player.getServer()).log(player.getUuid(),"SUBAREA_CREATE",property,created.type()+": "+created.name());cancelSurveyTask(player);beginEdit(player,property);return;}
        LandProperty detached=lands.detachArea(property.id(),task.name(),points);if(detached==null){error(player,"Teilfläche konnte nicht abgetrennt werden.");openSurveyTask(player,task);return;}LandManagementState management=LandManagementState.get(player.getServer());management.assignArea(detached.id(),management.propertyArea(property.id()));management.setLandUse(detached.id(),management.landUse(property.id()));LandAuditState.get(player.getServer()).log(player.getUuid(),"AREA_DETACHED",property,"Neues Grundstück: "+detached.id());cancelSurveyTask(player);detachedResult(player,detached);
    }

    private static void detachedResult(ServerPlayerEntity player,LandProperty detached){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.MAP,"Teilfläche erstellt",detached.name()+" · "+detached.id());button(inventory,actions,20,Items.PAPER,"Als eigenes Grundstück behalten","Aktueller Eigentümer bleibt bestehen",ignored->beginEdit(player,detached));if(LandTransferService.mayInitiate(player))button(inventory,actions,24,Items.PLAYER_HEAD,"Direkt übertragen","Zustimmungsregeln der Grundbuchübertragung gelten",ignored->openOwnerSelection(player,detached));openMenu(player,inventory,actions,"Abtrennung abgeschlossen");}

    private static void askDetachedName(ServerPlayerEntity player,LandProperty property){TextPromptService.open(player,"Name der abgetrennten Fläche","",value->{String name=value==null?"":value.trim();if(name.length()<3||name.length()>ConfigManager.claims().maximumPropertyNameLength){error(player,"Ungültiger Grundstücksname.");openEditor(player);return;}startPropertySurvey(player,property,LandSurveyTask.DETACH,name,"");},()->openEditor(player));}
    private static void selectSubareaType(ServerPlayerEntity player,LandProperty property){List<SelectionMenuScreen.Option> options=ConfigManager.claims().subareaTypes.stream().map(type->new SelectionMenuScreen.Option(type,type,"Interner Bereich des Hauptgrundstücks",Items.BRICKS)).toList();SelectionMenuScreen.open(player,"Art des Unterbereichs",options,type->TextPromptService.open(player,"Name des Unterbereichs","",name->{String clean=name==null?"":name.trim();if(clean.length()<2){error(player,"Name ist zu kurz.");openEditor(player);return;}startPropertySurvey(player,property,LandSurveyTask.SUBAREA,clean,type);},()->openEditor(player)),()->openEditor(player));}
    private static void openSubareas(ServerPlayerEntity player,LandProperty property,int requestedPage){List<PropertySubarea> values=PropertySubareaState.get(player.getServer()).forProperty(property.id());int pageSize=28,pages=Math.max(1,(values.size()+pageSize-1)/pageSize),page=Math.max(0,Math.min(requestedPage,pages-1));SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.BOOKSHELF,"Unterbereiche",property.name());int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};for(int i=0;i<slots.length;i++){int index=page*pageSize+i;if(index>=values.size())break;PropertySubarea value=values.get(index);ManagementHubScreen.display(inventory,slots[i],Items.PAPER,value.name(),value.type());actions.put(slots[i],ignored->subareaDetails(player,property,value,page));}if(page>0)button(inventory,actions,45,Items.ARROW,"Vorherige Seite","",ignored->openSubareas(player,property,page-1));if(page+1<pages)button(inventory,actions,53,Items.ARROW,"Nächste Seite","",ignored->openSubareas(player,property,page+1));button(inventory,actions,49,Items.ARROW,"Zurück","Grundstück bearbeiten",ignored->beginEdit(player,property));openMenu(player,inventory,actions,"Unterbereiche");}
    private static void subareaDetails(ServerPlayerEntity player,LandProperty property,PropertySubarea value,int page){
        PropertySubarea current=PropertySubareaState.get(player.getServer()).get(value.id());if(current==null){openSubareas(player,property,page);return;}
        SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();
        ManagementHubScreen.display(inventory,4,Items.PAPER,current.name(),current.type()+" · Bestandteil von "+property.name());
        ResidentialBuilding building=current.buildingId().isBlank()?null:LandManagementState.get(player.getServer()).residentialBuilding(current.buildingId());
        ManagementHubScreen.display(inventory,22,Items.BRICKS,"Mehrfamilienhaus",building==null?"Noch nicht zugeordnet":building.name());
        button(inventory,actions,20,Items.NAME_TAG,"Umbenennen",current.name(),ignored->TextPromptService.open(player,"Unterbereich umbenennen",current.name(),name->{PropertySubareaState.get(player.getServer()).rename(current.id(),name);LandAuditState.get(player.getServer()).log(player.getUuid(),"SUBAREA_RENAME",property,current.name()+" -> "+name);openSubareas(player,property,page);},()->subareaDetails(player,property,current,page)));
        button(inventory,actions,31,Items.COMPASS,"Mehrfamilienhaus zuordnen","Wohnung oder Gemeinschaftsbereich einer Gebäudeakte zuweisen",ignored->selectSubareaBuilding(player,property,current,page,0));
        if(building!=null)button(inventory,actions,33,Items.SHEARS,"Gebäudezuordnung entfernen",building.name(),ignored->{PropertySubareaState.get(player.getServer()).assignBuilding(current.id(),"");LandAuditState.get(player.getServer()).log(player.getUuid(),"SUBAREA_BUILDING_CLEAR",property,current.name());subareaDetails(player,property,current,page);});
        button(inventory,actions,24,Items.BARRIER,"Unterbereich löschen","Hauptgrundstück bleibt unverändert",ignored->{PropertySubareaState.get(player.getServer()).remove(current.id());LandAuditState.get(player.getServer()).log(player.getUuid(),"SUBAREA_DELETE",property,current.name());openSubareas(player,property,page);});
        button(inventory,actions,49,Items.ARROW,"Zurück","Unterbereiche",ignored->openSubareas(player,property,page));openMenu(player,inventory,actions,"Unterbereich");
    }
    private static void selectSubareaBuilding(ServerPlayerEntity player,LandProperty property,PropertySubarea subarea,int backPage,int requestedPage){
        LandManagementState management=LandManagementState.get(player.getServer());List<ResidentialBuilding> buildings=management.residentialBuildings().stream().filter(value->value.propertyId().isBlank()||value.propertyId().equals(property.id())).filter(value->management.mayManageResidentialBuilding(player,value.id())).toList();int pageSize=28,pages=Math.max(1,(buildings.size()+pageSize-1)/pageSize),page=Math.max(0,Math.min(requestedPage,pages-1));SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.BRICKS,"Mehrfamilienhaus auswählen",subarea.name()+" · "+property.name());button(inventory,actions,6,Items.EMERALD,"Neue Gebäudeakte","Direkt diesem Hauptgrundstück zuordnen",ignored->TextPromptService.open(player,"Name des Mehrfamilienhauses","",name->{ResidentialBuilding created=management.createResidentialBuilding(player,name,property.id());if(created==null)error(player,"Gebäudeakte konnte nicht erstellt werden.");else assignSubareaBuilding(player,property,subarea,created,backPage);},()->subareaDetails(player,property,subarea,backPage)));int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};for(int i=0;i<slots.length;i++){int index=page*pageSize+i;if(index>=buildings.size())break;ResidentialBuilding building=buildings.get(index);button(inventory,actions,slots[i],Items.BRICKS,building.name(),PropertySubareaState.get(player.getServer()).forBuilding(building.id()).size()+" interne Bereiche",ignored->assignSubareaBuilding(player,property,subarea,building,backPage));}if(page>0)button(inventory,actions,45,Items.ARROW,"Vorherige Seite","",ignored->selectSubareaBuilding(player,property,subarea,backPage,page-1));if(page+1<pages)button(inventory,actions,53,Items.ARROW,"Nächste Seite","",ignored->selectSubareaBuilding(player,property,subarea,backPage,page+1));button(inventory,actions,49,Items.ARROW,"Zurück","Unterbereich",ignored->subareaDetails(player,property,subarea,backPage));openMenu(player,inventory,actions,"Mehrfamilienhaus zuordnen");
    }
    private static void assignSubareaBuilding(ServerPlayerEntity player,LandProperty property,PropertySubarea subarea,ResidentialBuilding building,int backPage){if(!building.propertyId().isBlank()&&!building.propertyId().equals(property.id())){error(player,"Das Mehrfamilienhaus gehört zu einem anderen Hauptgrundstück.");subareaDetails(player,property,subarea,backPage);return;}PropertySubareaState.get(player.getServer()).assignBuilding(subarea.id(),building.id());LandAuditState.get(player.getServer()).log(player.getUuid(),"SUBAREA_BUILDING",property,subarea.name()+" -> "+building.name());subareaDetails(player,property,subarea,backPage);}
    private static LandlordState.ReshapeResult reshape(ServerPlayerEntity player,LandlordState state,LandProperty changed){
        LandManagementState management=LandManagementState.get(player.getServer());PropertySubareaState subareas=PropertySubareaState.get(player.getServer());LandTransferState transfers=LandTransferState.get(player.getServer());Map<String,LandProperty> before=new HashMap<>();state.all().forEach(value->before.put(value.id(),value));
        LandlordState.ReshapeResult result=state.reshape(changed,value->management.lease(value.id())==null&&management.sale(value.id())==null&&management.residentialUnit(value.id())==null&&subareas.forProperty(value.id()).isEmpty()&&transfers.forProperty(value.id())==null);
        if(result.success())for(String absorbedId:result.absorbedPropertyIds()){LandProperty absorbed=before.get(absorbedId);if(absorbed!=null)management.removeContainerLocks(absorbed);subareas.removeForProperty(absorbedId);management.removePropertyData(absorbedId);transfers.removeForProperty(absorbedId);LandAuditState.get(player.getServer()).log(player.getUuid(),"DETACHED_AREA_REABSORBED",result.property(),absorbedId);}
        return result;
    }
    private static void cancelSurveyTask(ServerPlayerEntity player){LandSurveyTaskState.get(player.getServer()).remove(player.getUuid());LandSelectionState.get(player.getServer()).clear(player.getUuid());}

    private static void beginEdit(ServerPlayerEntity player, LandProperty property) { PropertyDrafts.begin(player.getUuid(), property); preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static boolean mayEdit(ServerPlayerEntity player, LandProperty property) { return AuthorityState.maySurveyLand(player); }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static boolean hasBuildingAuthorityTablet(ServerPlayerEntity player) {
        return player.getInventory().contains(new net.minecraft.item.ItemStack(ModItems.BUILDING_AUTHORITY_TABLET));
    }
    private static String type(LandProperty property) { return switch (property.regionType()) { case "chunk" -> "Chunk"; case "cuboid" -> "Quader"; default -> "Freiform"; }; }

    private static void finishCuboid(ServerPlayerEntity player, LandlordState state, String dimension) {
        BlockPos first = PropertyDrafts.POS1.get(player.getUuid());
        if (first == null) { error(player, "Setze zuerst Punkt 1."); open(player); return; }
        BlockPos second = player.getBlockPos();
        String validation=LandGeometry.validateCuboid(first,second);if(validation!=null){error(player,validation);open(player);return;}
        askName(player, name -> finishCreate(player, state, LandlordState.cuboid(name, player.getUuid(), dimension, first, second)));
    }
    private static void finishPolygon(ServerPlayerEntity player, LandlordState state, String dimension) {
        LandSelectionState selections=LandSelectionState.get(player.getServer());LandSelection selection=selections.get(player.getUuid());
        List<BlockPos> points = selections.points(player.getUuid());
        if(selection==null||!selection.dimension().equals(dimension)){error(player,"Keine Vermessung in dieser Dimension aktiv.");open(player);return;}
        String validation=LandGeometry.validatePolygon(points);if(validation!=null){error(player,validation);open(player);return;}
        askName(player, name -> finishCreate(player, state, LandlordState.polygon(name, player.getUuid(), dimension, points)));
    }
    private static void finishCreate(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        LandProperty conflict = state.conflict(property);
        if (conflict != null) {
            error(player, "Überschneidung mit „" + conflict.name() + "“ (ID " + conflict.id() + ").");
            open(player);
            return;
        }
        PropertyDrafts.CreateOptions options = PropertyDrafts.createOptions(player.getUuid(), property,
                ConfigManager.administration().privateLandUse);
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, "Vorschau: " + property.name(),
                type(property) + " · " + property.minX() + "," + property.minZ() + " bis "
                        + property.maxX() + "," + property.maxZ());
        button(inventory, actions, 12, Items.OAK_SIGN, "Adresse",
                options.address().isBlank() ? "Noch nicht eingetragen" : options.address(),
                ignored -> askCreationAddress(player, state, property));
        button(inventory, actions, 14, Items.COMPASS, "Nutzungsart", options.landUse(),
                ignored -> selectCreationLandUse(player, state, property));
        ManagementHubScreen.display(inventory, 20, Items.EMERALD, "Endgültig eintragen",
                "Danach öffnet sich die vollständige Grundstücksakte");
        actions.put(20, ignored -> commitCreate(player, state, property));
        ManagementHubScreen.display(inventory, 24, Items.BARRIER, "Abbrechen", "Auswahl bleibt für Korrekturen erhalten");
        actions.put(24, ignored -> {
            PropertyDrafts.cancelCreate(player.getUuid());
            open(player);
        });
        openMenu(player, inventory, actions, "Grundstück konfigurieren");
    }

    private static void askCreationAddress(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        TextPromptService.open(player, "Grundstücksadresse", "", value -> {
                    PropertyDrafts.CreateOptions current = PropertyDrafts.createOptions(player.getUuid(), property,
                            ConfigManager.administration().privateLandUse);
                    PropertyDrafts.updateCreateOptions(player.getUuid(), current.withAddress(value == null ? "" : value.trim()));
                    finishCreate(player, state, property);
                }, () -> finishCreate(player, state, property));
    }

    private static void selectCreationLandUse(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        List<SelectionMenuScreen.Option> options = ConfigManager.administration().landUseTypes.stream()
                .map(value -> new SelectionMenuScreen.Option(value, value, "Nutzungsart des Grundstücks", Items.COMPASS))
                .toList();
        SelectionMenuScreen.open(player, "Nutzungsart auswählen", options, selected -> {
            PropertyDrafts.CreateOptions current = PropertyDrafts.createOptions(player.getUuid(), property,
                    ConfigManager.administration().privateLandUse);
            PropertyDrafts.updateCreateOptions(player.getUuid(), current.withLandUse(selected));
            finishCreate(player, state, property);
        }, () -> finishCreate(player, state, property));
    }
    private static void commitCreate(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        if(!LandPermissionService.mayCreateProperty(player)){error(player,"Deine Berechtigung zum Anlegen von Grundstücken ist nicht mehr gültig.");return;}
        if (state.add(property)) {
            LandManagementState management = LandManagementState.get(player.getServer());
            PropertyDrafts.CreateOptions options = PropertyDrafts.createOptions(player.getUuid(), property,
                    ConfigManager.administration().privateLandUse);
            management.assignArea(property.id(), LandManagementState.ROOT_AREA_ID);
            management.setLandUse(property.id(), options.landUse());
            if (!options.address().isBlank()) management.setAddress(property.id(), options.address());
            PropertyDrafts.cancelCreate(player.getUuid());
            PropertyDrafts.POS1.remove(player.getUuid());
            if(property.regionType().equals("polygon"))LandSelectionState.get(player.getServer()).clear(player.getUuid());
            player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde angelegt.").formatted(Formatting.GREEN), false);
            LandAuditState.get(player.getServer()).log(player.getUuid(),"CREATE",property,type(property));
            beginEdit(player, property);
        } else {
            error(player, "Dieser Bereich überschneidet sich mit einem bestehenden Grundstück.");
            finishCreate(player, state, property);
        }
    }

    private static void startSurvey(ServerPlayerEntity player,String dimension){if(!LandPermissionService.mayCreateProperty(player)){error(player,"Keine Berechtigung zum Anlegen von Grundstücken.");return;}net.minecraft.item.ItemStack tool=new net.minecraft.item.ItemStack(ModItems.LAND_SURVEY_TOOL);if(!player.getInventory().contains(tool)){error(player,"Kein Landvermessungsgerät im Inventar. Hole die Hardware vor dem Einsatz aus dem Lager.");open(player);return;}LandSelectionState selections=LandSelectionState.get(player.getServer());LandSelection current=selections.get(player.getUuid());if(current==null||!current.dimension().equals(dimension))selections.start(player.getUuid(),dimension);player.closeHandledScreen();player.sendMessage(Text.literal("Vermessung aktiv: Rechtsklick setzt einen Eckpunkt, Linksklick entfernt den letzten. Öffne zum Abschluss wieder das Bauamt-Tablet.").formatted(Formatting.GREEN),false);}

    private static void preview(ServerPlayerEntity player, List<BlockPos> points) {
        LandVisuals.preview(player, points);
    }

    static void openOwnerSelection(ServerPlayerEntity player, LandProperty property) {
        DESKTOP_OWNER_SELECTIONS.remove(player.getUuid());
        openOwnerSelectionMenu(player, property);
    }

    static void openOwnerSelectionFromDesktop(ServerPlayerEntity player, LandProperty property) {
        DESKTOP_OWNER_SELECTIONS.add(player.getUuid());
        openOwnerSelectionMenu(player, property);
    }

    private static void openOwnerSelectionMenu(ServerPlayerEntity player, LandProperty property) {
        if (!LandTransferService.mayInitiate(player)) {
            error(player, LandPermissionService.transferDenial(player));
            PropertyFinanceScreen.open(player, property);
            return;
        }
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Eigentümer: " + property.name(), "Eigentümerart auswählen");
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zur Grundstücksakte"); actions.put(8, ignored -> backFromOwnerSelection(player, property));
        button(inventory, actions, 20, Items.PLAYER_HEAD, "Bürger", "Freigeschaltete Bürgerakte", ignored -> playerOwners(player, property, 0));
        button(inventory, actions, 22, Items.BRICKS, "Institution", "Unternehmen, Behörde oder Organisation", ignored -> institutionOwners(player, property, 0));
        button(inventory, actions, 24, Items.FILLED_MAP, "Verwaltungseinheit", "Stadt, Gemeinde, Landkreis oder andere Ebene", ignored -> areaOwners(player, property, 0));
        openMenu(player, inventory, actions, "Eigentümer zuweisen");
    }

    private static void backFromOwnerSelection(ServerPlayerEntity player, LandProperty property) {
        if (DESKTOP_OWNER_SELECTIONS.remove(player.getUuid())) {
            LandOfficeOverviewScreen.details(player, property.id());
            return;
        }
        PropertyDrafts.EditDraft draft = PropertyDrafts.edit(player.getUuid());
        if (draft != null && draft.propertyId().equals(property.id())) {
            openEditor(player);
            return;
        }
        LandProperty current = LandlordState.get(player.getServer()).get(property.id());
        if (current == null) open(player); else PropertyFinanceScreen.open(player, current);
    }

    private static void playerOwners(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        List<CitizenIdentity> values = IdentityState.get(player.getServer()).allApproved();
        int page = page(requestedPage, values.size());
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        int start = page * 36;
        for (int index = start; index < Math.min(start + 36, values.size()); index++) {
            CitizenIdentity identity = values.get(index); String rpName = identity.firstName() + " " + identity.lastName();
            button(inventory, actions, 9 + index - start, Items.PLAYER_HEAD, rpName, identity.citizenNumber(),
                    ignored -> assignOwner(player, property, "player", identity.playerUuid()));
        }
        ownerNavigation(player, property, inventory, actions, page, values.size(), p -> playerOwners(player, property, p));
        openMenu(player, inventory, actions, "Bürger als Eigentümer");
    }

    private static void institutionOwners(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        List<Institution> values = InstitutionState.get(player.getServer()).all(); int page = page(requestedPage, values.size());
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>(); int start = page * 36;
        for (int index = start; index < Math.min(start + 36, values.size()); index++) {
            Institution institution = values.get(index);
            button(inventory, actions, 9 + index - start, Items.BRICKS, institution.name(), institution.type(),
                    ignored -> assignOwner(player, property, "institution", institution.id()));
        }
        ownerNavigation(player, property, inventory, actions, page, values.size(), p -> institutionOwners(player, property, p));
        openMenu(player, inventory, actions, "Institution als Eigentümer");
    }

    private static void areaOwners(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        LandManagementState management = LandManagementState.get(player.getServer()); List<AdministrativeArea> values = management.areas(); int page = page(requestedPage, values.size());
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>(); int start = page * 36;
        for (int index = start; index < Math.min(start + 36, values.size()); index++) {
            AdministrativeArea area = values.get(index);
            button(inventory, actions, 9 + index - start, Items.FILLED_MAP, area.name(), management.levelName(area.level()),
                    ignored -> assignOwner(player, property, LandManagementState.AREA_OWNER_TYPE, area.id()));
        }
        ownerNavigation(player, property, inventory, actions, page, values.size(), p -> areaOwners(player, property, p));
        openMenu(player, inventory, actions, "Verwaltung als Eigentümer");
    }

    private static int page(int requested, int size) { return Math.max(0, Math.min(requested, Math.max(0, (size - 1) / 36))); }
    private static void ownerNavigation(ServerPlayerEntity player, LandProperty property, SimpleInventory inventory,
                                        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions,
                                        int page, int size, java.util.function.IntConsumer opener) {
        button(inventory, actions, 49, Items.ARROW, "Eigentümerart", "Zurück", ignored -> openOwnerSelection(player, property));
        if (page > 0) button(inventory, actions, 45, Items.ARROW, "Vorherige Seite", String.valueOf(page), ignored -> opener.accept(page - 1));
        if ((page + 1) * 36 < size) button(inventory, actions, 53, Items.ARROW, "Nächste Seite", String.valueOf(page + 2), ignored -> opener.accept(page + 1));
    }

    private static void assignOwner(ServerPlayerEntity player, LandProperty property, String type, String id) {
        LandTransferService.Result result = LandTransferService.request(player, property.id(), type, id);
        player.sendMessage(Text.literal(result.message()).formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
        PropertyDrafts.cancelEdit(player.getUuid());
        if (DESKTOP_OWNER_SELECTIONS.remove(player.getUuid())) LandOfficeOverviewScreen.open(player); else open(player);
    }
    private static void confirmDelete(ServerPlayerEntity player,LandProperty property){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.BARRIER,"Wirklich löschen?",property.name()+" · "+property.id());ManagementHubScreen.display(inventory,20,Items.LAVA_BUCKET,"Endgültig löschen","Dieser Schritt kann nicht rückgängig gemacht werden");actions.put(20,x->{if(!AuthorityState.mayAdministerLand(player)){error(player,"Bauamtsleitung erforderlich.");return;}LandAuditState.get(player.getServer()).log(player.getUuid(),"DELETE",property,property.name());LandTransferService.cancelForProperty(player.getServer(),property.id());LandManagementState.get(player.getServer()).removeContainerLocks(property);PropertySubareaState.get(player.getServer()).removeForProperty(property.id());LandlordState.get(player.getServer()).remove(property.id());LandManagementState.get(player.getServer()).removePropertyData(property.id());PropertyDrafts.cancelEdit(player.getUuid());open(player);});ManagementHubScreen.display(inventory,24,Items.ARROW,"Abbrechen","Grundstück behalten");actions.put(24,x->openEditor(player));openMenu(player,inventory,actions,"Löschen bestätigen");}
    private static String ownerLabel(ServerPlayerEntity player, LandProperty property) {
        if (property.ownerType().equals("institution")) { Institution institution = InstitutionState.get(player.getServer()).get(property.ownerId()); return institution == null ? "Unbekannte Institution" : institution.name(); }
        if (property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)) { AdministrativeArea area = LandManagementState.get(player.getServer()).area(property.ownerId()); return area == null ? ConfigManager.administration().wildernessName : area.name(); }
        try { CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(property.ownerId())); return identity == null ? "Bürgerkonto" : identity.firstName() + " " + identity.lastName(); } catch (IllegalArgumentException ignored) { return "Unbekannt"; }
    }

    private static void askName(ServerPlayerEntity player, Consumer<String> done) { TextPromptService.open(player,"Grundstücksname","",value->{String name=value==null?"":value.trim();if(name.isBlank()){open(player);return;}if(name.length()>ConfigManager.claims().maximumPropertyNameLength){error(player,"Der Grundstücksname darf maximal "+ConfigManager.claims().maximumPropertyNameLength+" Zeichen lang sein.");open(player);return;}done.accept(name);},()->open(player)); }
    private static void askAddress(ServerPlayerEntity player,LandProperty property){String current=LandManagementState.get(player.getServer()).address(property.id());TextPromptService.open(player,"Grundstücksadresse",current,value->{LandManagementState.get(player.getServer()).setAddress(property.id(),value.trim());LandAuditState.get(player.getServer()).log(player.getUuid(),"ADDRESS_UPDATE",property,value.trim());openEditor(player);},()->openEditor(player));}
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) { ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action); }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) { CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN)); }
}
