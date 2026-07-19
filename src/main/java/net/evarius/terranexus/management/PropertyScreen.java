package net.evarius.terranexus.management;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.PropertyDrafts;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
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
        PropertyDrafts.EditDraft draft = PropertyDrafts.edit(player.getUuid());
        if (draft != null) { openEditor(player); return; }
        LandlordState state = LandlordState.get(player.getServer());
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        String dimension = dimension(player);

        ManagementHubScreen.display(inventory, 4, Items.FILLED_MAP, "Grundstücksverwaltung",
                "Position: " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Verwaltungsübersicht");
        actions.put(8, ignored -> ManagementHubScreen.open(player));

        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int slot = 19 + (dz + 1) * 9 + dx + 1;
            int cx = (player.getBlockX() >> 4) + dx, cz = (player.getBlockZ() >> 4) + dz;
            LandProperty at = state.at(dimension, new BlockPos(cx * 16 + 8, player.getBlockY(), cz * 16 + 8));
            ManagementHubScreen.display(inventory, slot, at == null ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
                    "Chunk " + cx + " / " + cz, at == null ? "Frei" : "Belegt: " + at.name());
            if (at != null && mayEdit(player, at)) actions.put(slot, ignored -> beginEdit(player, at));
        }

        if (AuthorityState.mayManageLand(player)) {
            ManagementHubScreen.display(inventory, 10, Items.GRASS_BLOCK, "Chunk anlegen", "Aktueller 16×16-Bereich");
            actions.put(10, ignored -> askName(player, name -> finishCreate(player, state,
                    LandlordState.chunk(name, player.getUuid(), dimension, player.getBlockX() >> 4, player.getBlockZ() >> 4))));
            ManagementHubScreen.display(inventory, 12, Items.WOODEN_AXE, "Quader auswählen", "Punkt 1 an aktueller Position");
            actions.put(12, ignored -> { PropertyDrafts.POS1.put(player.getUuid(), player.getBlockPos()); player.sendMessage(Text.literal("Punkt 1 gesetzt. Stelle dich nun an Punkt 2."), true); open(player); });
            ManagementHubScreen.display(inventory, 13, Items.IRON_AXE, "Quader abschließen", "Aktuelle Position als Punkt 2");
            actions.put(13, ignored -> finishCuboid(player, state, dimension));
            int points = PropertyDrafts.POLYGONS.getOrDefault(player.getUuid(), List.of()).size();
            ManagementHubScreen.display(inventory, 15, Items.GOLDEN_HOE, "Freiform-Punkt setzen", "Aktuelle Position · Punkte: " + points);
            actions.put(15, ignored -> { PropertyDrafts.POLYGONS.computeIfAbsent(player.getUuid(), key -> new ArrayList<>()).add(player.getBlockPos()); preview(player, PropertyDrafts.POLYGONS.get(player.getUuid())); open(player); });
            ManagementHubScreen.display(inventory, 16, Items.EMERALD, "Freiform abschließen", "Mindestens 3 Punkte");
            actions.put(16, ignored -> finishPolygon(player, state, dimension));
        } else {
            ManagementHubScreen.display(inventory, 13, Items.BARRIER, "Nur Grundbuchverwaltung",
                    "Neue Flächen legt eine autorisierte Stelle an");
        }

        int slot = 45;
        for (LandProperty property : state.all()) {
            if (slot >= 54) break;
            if (!mayEdit(player, property)) continue;
            ManagementHubScreen.display(inventory, slot, Items.PAPER, property.name(),
                    type(property) + " · " + property.minX() + "," + property.minZ() + " bis " + property.maxX() + "," + property.maxZ() + " · Anklicken zum Bearbeiten");
            actions.put(slot++, ignored -> beginEdit(player, property));
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
        button(inventory, actions, 45, Items.NAME_TAG, "Umbenennen", property.name(), ignored -> askName(player, value -> { state.update(property.withName(value)); openEditor(player); }));
        if (AuthorityState.mayManageLand(player)) button(inventory, actions, 47, Items.PLAYER_HEAD, "Eigentümer zuweisen", ownerLabel(player, property), ignored -> openOwnerSelection(player, property));
        button(inventory, actions, 49, Items.EMERALD, "Änderungen speichern", "Prüft Form und Überschneidungen", ignored -> saveEdit(player, state, property, draft));
        button(inventory, actions, 53, Items.BARRIER, "Abbrechen", "Entwurf verwerfen", ignored -> { PropertyDrafts.cancelEdit(player.getUuid()); open(player); });
        openMenu(player, inventory, actions, "Grundstück bearbeiten");
    }

    private static void refreshEditor(ServerPlayerEntity player) { preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static void saveEdit(ServerPlayerEntity player, LandlordState state, LandProperty property, PropertyDrafts.EditDraft draft) {
        if (!valid(draft.points())) { error(player, "Die Fläche benötigt mindestens drei unterschiedliche Eckpunkte und darf nicht auf einer Linie liegen."); openEditor(player); return; }
        LandProperty changed = LandlordState.editedPolygon(property, draft.points());
        if (!state.update(changed)) { error(player, "Die neue Form überschneidet sich mit einem anderen Grundstück."); openEditor(player); return; }
        PropertyDrafts.cancelEdit(player.getUuid());
        player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde gespeichert.").formatted(Formatting.GREEN), false);
        open(player);
    }

    private static void beginEdit(ServerPlayerEntity player, LandProperty property) { PropertyDrafts.begin(player.getUuid(), property); preview(player, PropertyDrafts.edit(player.getUuid()).points()); openEditor(player); }
    private static boolean mayEdit(ServerPlayerEntity player, LandProperty property) { return property.isOwnedBy(player.getUuid()) || AuthorityState.mayManageLand(player) || (property.ownerType().equals("institution") && InstitutionState.get(player.getServer()).mayManage(property.ownerId(), player.getUuid())); }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static String type(LandProperty property) { return switch (property.regionType()) { case "chunk" -> "Chunk"; case "cuboid" -> "Quader"; default -> "Freiform"; }; }

    private static void finishCuboid(ServerPlayerEntity player, LandlordState state, String dimension) {
        BlockPos first = PropertyDrafts.POS1.get(player.getUuid());
        if (first == null) { error(player, "Setze zuerst Punkt 1."); open(player); return; }
        BlockPos second = player.getBlockPos();
        askName(player, name -> finishCreate(player, state, LandlordState.cuboid(name, player.getUuid(), dimension, first, second)));
    }
    private static void finishPolygon(ServerPlayerEntity player, LandlordState state, String dimension) {
        List<BlockPos> points = PropertyDrafts.POLYGONS.getOrDefault(player.getUuid(), List.of());
        if (!valid(points)) { error(player, "Es werden mindestens drei unterschiedliche, nicht geradlinige Punkte benötigt."); open(player); return; }
        askName(player, name -> finishCreate(player, state, LandlordState.polygon(name, player.getUuid(), dimension, points)));
    }
    private static boolean valid(List<BlockPos> points) {
        if (points.stream().map(p -> p.getX() + "," + p.getZ()).distinct().count() < 3) return false;
        long twiceArea = 0;
        for (int i = 0; i < points.size(); i++) { BlockPos a = points.get(i), b = points.get((i + 1) % points.size()); twiceArea += (long)a.getX() * b.getZ() - (long)b.getX() * a.getZ(); }
        return twiceArea != 0;
    }
    private static void finishCreate(ServerPlayerEntity player, LandlordState state, LandProperty property) {
        if (state.add(property)) {
            PropertyDrafts.POS1.remove(player.getUuid()); PropertyDrafts.POS2.remove(player.getUuid()); PropertyDrafts.POLYGONS.remove(player.getUuid());
            player.sendMessage(Text.literal("Grundstück „" + property.name() + "“ wurde angelegt.").formatted(Formatting.GREEN), false);
        } else error(player, "Dieser Bereich überschneidet sich mit einem bestehenden Grundstück.");
        open(player);
    }

    private static void preview(ServerPlayerEntity player, List<BlockPos> points) {
        if (points == null || points.isEmpty()) return;
        int edges = points.size() > 2 ? points.size() : points.size() - 1;
        for (int i = 0; i < edges; i++) {
            BlockPos a = points.get(i), b = points.get((i + 1) % points.size()); double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ(); int samples = Math.max(1, (int)Math.ceil(Math.hypot(dx, dz) * 2));
            for (int sample = 0; sample <= samples; sample++) { double ratio = sample / (double)samples; player.getWorld().spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, a.getX() + .5 + dx * ratio, player.getY() + .3, a.getZ() + .5 + dz * ratio, 1, 0, 0, 0, 0); }
        }
    }

    private static void openOwnerSelection(ServerPlayerEntity player, LandProperty property) {
        if (!AuthorityState.mayManageLand(player)) { openEditor(player); return; }
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.WRITABLE_BOOK, "Eigentümer: " + property.name(), "Bürger oder Institution auswählen");
        ManagementHubScreen.display(inventory, 8, Items.ARROW, "Zurück", "Zum Flächeneditor"); actions.put(8, ignored -> openEditor(player));
        IdentityState identities = IdentityState.get(player.getServer()); int slot = 9;
        for (ServerPlayerEntity citizen : player.getServer().getPlayerManager().getPlayerList()) {
            CitizenIdentity identity = identities.get(citizen.getUuid()); if (identity == null || !identities.isApproved(citizen.getUuid()) || slot >= 36) continue;
            String rpName = identity.firstName() + " " + identity.lastName();
            button(inventory, actions, slot++, Items.PLAYER_HEAD, rpName, identity.citizenNumber(), ignored -> assignOwner(player, property, "player", citizen.getUuidAsString(), rpName));
        }
        slot = 36;
        for (Institution institution : InstitutionState.get(player.getServer()).all()) {
            if (slot >= 54) break;
            button(inventory, actions, slot++, Items.BRICKS, institution.name(), institution.type(), ignored -> assignOwner(player, property, "institution", institution.id(), institution.name()));
        }
        openMenu(player, inventory, actions, "Eigentümer zuweisen");
    }
    private static void assignOwner(ServerPlayerEntity player, LandProperty property, String type, String id, String label) {
        if (LandlordState.get(player.getServer()).update(property.withOwner(type, id))) player.sendMessage(Text.literal("„" + property.name() + "“ gehört nun " + label + ".").formatted(Formatting.GREEN), false);
        PropertyDrafts.cancelEdit(player.getUuid()); open(player);
    }
    private static String ownerLabel(ServerPlayerEntity player, LandProperty property) {
        if (property.ownerType().equals("institution")) { Institution institution = InstitutionState.get(player.getServer()).get(property.ownerId()); return institution == null ? "Unbekannte Institution" : institution.name(); }
        try { CitizenIdentity identity = IdentityState.get(player.getServer()).get(UUID.fromString(property.ownerId())); return identity == null ? "Bürgerkonto" : identity.firstName() + " " + identity.lastName(); } catch (IllegalArgumentException ignored) { return "Unbekannt"; }
    }

    private static void askName(ServerPlayerEntity player, Consumer<String> done) { player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, ignored) -> new TextInputScreenHandler(id, inventory, value -> { if (!value.isBlank()) done.accept(value.trim()); else open(player); }), Text.literal("Grundstücksname"))); }
    private static void error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message).formatted(Formatting.RED), false); }
    private static void button(SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, int slot, net.minecraft.item.Item item, String name, String detail, Consumer<net.minecraft.entity.player.PlayerEntity> action) { ManagementHubScreen.display(inventory, slot, item, name, detail); actions.put(slot, action); }
    private static void openMenu(ServerPlayerEntity player, SimpleInventory inventory, Map<Integer, Consumer<net.minecraft.entity.player.PlayerEntity>> actions, String title) { player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, playerInventory, ignored) -> new ActionMenuScreenHandler(id, playerInventory, inventory, actions), Text.literal(title).formatted(Formatting.DARK_GREEN))); }
}
