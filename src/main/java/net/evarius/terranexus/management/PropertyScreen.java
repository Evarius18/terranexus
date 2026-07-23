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
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
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
    private PropertyScreen() {}

    public static void open(ServerPlayerEntity player) {
        if(!AuthorityState.mayUseLandOffice(player)){player.sendMessage(Text.literal("Zugriff verweigert: Bauamtsberechtigung erforderlich.").formatted(Formatting.RED),false);return;}
        PropertyDrafts.EditDraft draft = PropertyDrafts.edit(player.getUuid());
        if (draft != null) { openEditor(player); return; }
        LandlordState state = LandlordState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        String dimension = dimension(player);

        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, "Grundstücksverwaltung",
                "Position: " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Verwaltungsübersicht");
        actions.put(8, ignored -> AdminDesktopScreen.open(player));
        ManagementHubScreen.display(inventory,7,Items.COMPASS,"Grundstück suchen","ID, Name oder Besitzer");actions.put(7,x->LandSearchScreen.ask(player));
        if(AuthorityState.mayAdministerLand(player)){ManagementHubScreen.display(inventory,6,Items.WRITABLE_BOOK,"Audit-Log","Erstellungen, Änderungen und Eigentümerwechsel");actions.put(6,x->LandSearchScreen.audit(player));}
        if(AuthorityState.mayAdministerLand(player)){ManagementHubScreen.display(inventory, 17, Items.BELL, "Verwaltungshierarchie", "Wilderness, Ebenen und Zuständigkeiten organisieren");actions.put(17, ignored -> LandAdministrationScreen.open(player));}

        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int slot = 19 + (dz + 1) * 9 + dx + 1;
            int cx = (player.getBlockX() >> 4) + dx, cz = (player.getBlockZ() >> 4) + dz;
            LandProperty at = state.inChunk(dimension,cx,cz);
            ManagementHubScreen.display(inventory, slot, at == null ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
                    "Chunk " + cx + " / " + cz, at == null ? "Frei" : "Belegt: " + at.name());
            if (at != null) actions.put(slot, ignored -> { if (mayEdit(player, at)) beginEdit(player, at); else PropertyFinanceScreen.open(player, at); });
        }

        if (AuthorityState.maySurveyLand(player)) {
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

        int slot = 45;
        for (LandProperty property : state.all()) {
            if (slot >= 54) break;
            LandManagementState management = LandManagementState.get(player.getServer());
            LandSaleOffer offer = management.sale(property.id()); LandLease lease = management.lease(property.id());
            if (!mayEdit(player, property) && offer == null && (lease == null || !lease.tenantId().equals(player.getUuidAsString()))) continue;
            ManagementHubScreen.display(inventory, slot, Items.PAPER, property.name(),
                    type(property) + " · " + property.minX() + "," + property.minZ() + " bis " + property.maxX() + "," + property.maxZ() + " · Anklicken zum Bearbeiten");
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
        button(inventory, actions, 45, Items.NAME_TAG, "Umbenennen", property.name(), ignored -> askName(player, value -> { state.update(property.withName(value)); LandAuditState.get(player.getServer()).log(player.getUuid(),"RENAME",property,property.name()+" -> "+value); openEditor(player); }));
        if (AuthorityState.mayProcessLandRecords(player)) button(inventory, actions, 47, Items.PLAYER_HEAD, "Eigentümer zuweisen", ownerLabel(player, property), ignored -> openOwnerSelection(player, property));
        button(inventory, actions, 51, Items.GOLD_INGOT, "Verträge und Rechte", "Verkauf, Vermietung, Zugriff und Verwaltungsebene", ignored -> PropertyFinanceScreen.open(player, property));
        if(AuthorityState.mayAdministerLand(player))button(inventory,actions,43,Items.LAVA_BUCKET,"Grundstück löschen","Nur Bauamtsleitung · mit Bestätigung",ignored->confirmDelete(player,property));
        if(AuthorityState.mayProcessLandRecords(player))button(inventory,actions,41,Items.OAK_SIGN,"Adresse eintragen",LandManagementState.get(player.getServer()).address(property.id()),ignored->askAddress(player,property));
        button(inventory, actions, 49, Items.EMERALD, "Änderungen speichern", "Prüft Form und Überschneidungen", ignored -> saveEdit(player, state, property, draft));
        button(inventory, actions, 53, Items.BARRIER, "Abbrechen", "Entwurf verwerfen", ignored -> { PropertyDrafts.cancelEdit(player.getUuid()); open(player); });
        openMenu(player, inventory, actions, "Grundstück bearbeiten");
    }

    private static void refreshEditor(ServerPlayerEntity player) { preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static void saveEdit(ServerPlayerEntity player, LandlordState state, LandProperty property, PropertyDrafts.EditDraft draft) {
        if(!AuthorityState.maySurveyLand(player)){error(player,"Deine Vermessungsberechtigung ist nicht mehr gültig.");PropertyDrafts.cancelEdit(player.getUuid());return;}
        String validation=LandGeometry.validatePolygon(draft.points());if(validation!=null){error(player,validation);openEditor(player);return;}
        LandProperty changed = LandlordState.editedPolygon(property, draft.points());
        if (!state.update(changed)) { error(player, "Die neue Form überschneidet sich mit einem anderen Grundstück."); openEditor(player); return; }
        LandAuditState.get(player.getServer()).log(player.getUuid(),"GEOMETRY_UPDATE",changed,"Eckpunkte: "+draft.points().size());
        PropertyDrafts.cancelEdit(player.getUuid());
        player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde gespeichert.").formatted(Formatting.GREEN), false);
        open(player);
    }

    private static void beginEdit(ServerPlayerEntity player, LandProperty property) { PropertyDrafts.begin(player.getUuid(), property); preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static boolean mayEdit(ServerPlayerEntity player, LandProperty property) { return AuthorityState.maySurveyLand(player); }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
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
    private static void finishCreate(ServerPlayerEntity player, LandlordState state, LandProperty property) {LandProperty conflict=state.conflict(property);if(conflict!=null){error(player,"Überschneidung mit „"+conflict.name()+"“ (ID "+conflict.id()+").");open(player);return;}SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.FILLED_MAP,"Vorschau: "+property.name(),type(property)+" · "+property.minX()+","+property.minZ()+" bis "+property.maxX()+","+property.maxZ());ManagementHubScreen.display(inventory,20,Items.EMERALD,"Endgültig eintragen","Grundstück wird persistent gespeichert");actions.put(20,x->commitCreate(player,state,property));ManagementHubScreen.display(inventory,24,Items.BARRIER,"Abbrechen","Auswahl bleibt für Korrekturen erhalten");actions.put(24,x->open(player));openMenu(player,inventory,actions,"Grundstück bestätigen");}
    private static void commitCreate(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        if(!AuthorityState.maySurveyLand(player)){error(player,"Deine Vermessungsberechtigung ist nicht mehr gültig.");return;}
        if (state.add(property)) {
            LandManagementState management = LandManagementState.get(player.getServer());
            management.assignArea(property.id(), LandManagementState.ROOT_AREA_ID);
            management.setLandUse(property.id(), ConfigManager.administration().privateLandUse);
            PropertyDrafts.POS1.remove(player.getUuid());
            if(property.regionType().equals("polygon"))LandSelectionState.get(player.getServer()).clear(player.getUuid());
            player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde angelegt.").formatted(Formatting.GREEN), false);
            LandAuditState.get(player.getServer()).log(player.getUuid(),"CREATE",property,type(property));
        } else error(player, "Dieser Bereich überschneidet sich mit einem bestehenden Grundstück.");
        open(player);
    }

    private static void startSurvey(ServerPlayerEntity player,String dimension){net.minecraft.item.ItemStack tool=new net.minecraft.item.ItemStack(ModItems.LAND_SURVEY_TOOL);if(!player.getInventory().contains(tool)){error(player,"Kein Landvermessungsgerät im Inventar. Hole die Hardware vor dem Einsatz aus dem Lager.");open(player);return;}LandSelectionState selections=LandSelectionState.get(player.getServer());LandSelection current=selections.get(player.getUuid());if(current==null||!current.dimension().equals(dimension))selections.start(player.getUuid(),dimension);player.closeHandledScreen();player.sendMessage(Text.literal("Vermessung aktiv: Rechtsklick setzt einen Eckpunkt, Linksklick entfernt den letzten. Öffne zum Abschluss wieder das Bauamt-Tablet.").formatted(Formatting.GREEN),false);}

    private static void preview(ServerPlayerEntity player, List<BlockPos> points) {
        LandVisuals.preview(player, points);
    }

    private static void openOwnerSelection(ServerPlayerEntity player, LandProperty property) {
        if (!AuthorityState.mayProcessLandRecords(player)) { openEditor(player); return; }
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Eigentümer: " + property.name(), "Eigentümerart auswählen");
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zum Flächeneditor"); actions.put(8, ignored -> openEditor(player));
        button(inventory, actions, 20, Items.PLAYER_HEAD, "Bürger", "Freigeschaltete Bürgerakte", ignored -> playerOwners(player, property, 0));
        button(inventory, actions, 22, Items.BRICKS, "Institution", "Unternehmen, Behörde oder Organisation", ignored -> institutionOwners(player, property, 0));
        button(inventory, actions, 24, Items.FILLED_MAP, "Verwaltungseinheit", "Stadt, Gemeinde, Landkreis oder andere Ebene", ignored -> areaOwners(player, property, 0));
        openMenu(player, inventory, actions, "Eigentümer zuweisen");
    }

    private static void playerOwners(ServerPlayerEntity player, LandProperty property, int requestedPage) {
        List<CitizenIdentity> values = IdentityState.get(player.getServer()).allApproved();
        int page = page(requestedPage, values.size());
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        int start = page * 36;
        for (int index = start; index < Math.min(start + 36, values.size()); index++) {
            CitizenIdentity identity = values.get(index); String rpName = identity.firstName() + " " + identity.lastName();
            button(inventory, actions, 9 + index - start, Items.PLAYER_HEAD, rpName, identity.citizenNumber(),
                    ignored -> assignOwner(player, property, "player", identity.playerUuid(), rpName));
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
                    ignored -> assignOwner(player, property, "institution", institution.id(), institution.name()));
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
                    ignored -> assignOwner(player, property, LandManagementState.AREA_OWNER_TYPE, area.id(), area.name()));
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

    private static void assignOwner(ServerPlayerEntity player, LandProperty property, String type, String id, String label) {
        if(!AuthorityState.mayProcessLandRecords(player)){error(player,"Deine Grundbuchberechtigung ist nicht mehr gültig.");return;}
        LandlordState state = LandlordState.get(player.getServer()); LandProperty current = state.get(property.id());
        boolean valid = current != null && id != null && !id.isBlank()
                && (!type.equals(LandManagementState.AREA_OWNER_TYPE) || LandManagementState.get(player.getServer()).area(id) != null)
                && (!type.equals("institution") || InstitutionState.get(player.getServer()).get(id) != null)
                && (!type.equals("player") || approvedIdentity(player, id));
        if (valid && state.update(current.withOwner(type, id))){LandAuditState.get(player.getServer()).owner(player.getUuid(),current,type,id);notifyOwnerChange(player,current,type,id);player.sendMessage(Text.literal("„" + current.name() + "“ gehört nun " + label + ".").formatted(Formatting.GREEN), false);}
        else if (!valid) error(player, "Der ausgewählte Eigentümer ist nicht mehr verfügbar.");
        PropertyDrafts.cancelEdit(player.getUuid()); open(player);
    }
    private static boolean approvedIdentity(ServerPlayerEntity player,String id){try{return IdentityState.get(player.getServer()).isApproved(UUID.fromString(id));}catch(IllegalArgumentException ignored){return false;}}
    private static void confirmDelete(ServerPlayerEntity player,LandProperty property){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.BARRIER,"Wirklich löschen?",property.name()+" · "+property.id());ManagementHubScreen.display(inventory,20,Items.LAVA_BUCKET,"Endgültig löschen","Dieser Schritt kann nicht rückgängig gemacht werden");actions.put(20,x->{if(!AuthorityState.mayAdministerLand(player)){error(player,"Bauamtsleitung erforderlich.");return;}LandAuditState.get(player.getServer()).log(player.getUuid(),"DELETE",property,property.name());LandlordState.get(player.getServer()).remove(property.id());LandManagementState.get(player.getServer()).removePropertyData(property.id());PropertyDrafts.cancelEdit(player.getUuid());open(player);});ManagementHubScreen.display(inventory,24,Items.ARROW,"Abbrechen","Grundstück behalten");actions.put(24,x->openEditor(player));openMenu(player,inventory,actions,"Löschen bestätigen");}
    private static void notifyOwnerChange(ServerPlayerEntity actor,LandProperty old,String newType,String newId){if(old.ownerType().equals("player"))notifyPlayer(actor,old.ownerId(),"Dein Eigentum an „"+old.name()+"“ wurde übertragen.");if(newType.equals("player"))notifyPlayer(actor,newId,"Du bist nun als Eigentümer von „"+old.name()+"“ eingetragen.");}
    private static void notifyPlayer(ServerPlayerEntity actor,String uuid,String message){try{ServerPlayerEntity target=actor.getServer().getPlayerManager().getPlayer(UUID.fromString(uuid));if(target!=null){target.sendMessage(Text.literal(message).formatted(Formatting.GOLD),false);net.minecraft.item.ItemStack extract=new net.minecraft.item.ItemStack(ModItems.LAND_REGISTRY_EXTRACT);if(!target.getInventory().contains(extract))target.giveItemStack(extract);}}catch(Exception ignored){}}
    private static String ownerLabel(ServerPlayerEntity player, LandProperty property) {
        if (property.ownerType().equals("institution")) { Institution institution = InstitutionState.get(player.getServer()).get(property.ownerId()); return institution == null ? "Unbekannte Institution" : institution.name(); }
        if (property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)) { AdministrativeArea area = LandManagementState.get(player.getServer()).area(property.ownerId()); return area == null ? ConfigManager.administration().wildernessName : area.name(); }
        try { CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(property.ownerId())); return identity == null ? "Bürgerkonto" : identity.firstName() + " " + identity.lastName(); } catch (IllegalArgumentException ignored) { return "Unbekannt"; }
    }

    private static void askName(ServerPlayerEntity player, Consumer<String> done) { player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) -> new TextInputScreenHandler(id, inventory, value -> { String name=value==null?"":value.trim();if(name.isBlank()){open(player);return;}if(name.length()>ConfigManager.claims().maximumPropertyNameLength){error(player,"Der Grundstücksname darf maximal "+ConfigManager.claims().maximumPropertyNameLength+" Zeichen lang sein.");open(player);return;}done.accept(name); }), Text.literal("Grundstücksname"))); }
    private static void askAddress(ServerPlayerEntity player,LandProperty property){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,ignored)->new TextInputScreenHandler(id,inventory,value->{LandManagementState.get(player.getServer()).setAddress(property.id(),value.trim());LandAuditState.get(player.getServer()).log(player.getUuid(),"ADDRESS_UPDATE",property,value.trim());openEditor(player);}),Text.literal("Grundstücksadresse")));}
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) { ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action); }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) { CustomGuiService.open(player, inventory, actions, Text.literal(title).formatted(Formatting.DARK_GREEN)); }
}
