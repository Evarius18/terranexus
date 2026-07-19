package net.evarius.terranexus.landlord;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PropertyDrafts {
    private PropertyDrafts() {}

    public static final Map<UUID, BlockPos> POS1 = new HashMap<>();
    private static final Map<UUID, EditDraft> EDITS = new HashMap<>();

    public static EditDraft edit(UUID player) { return EDITS.get(player); }
    public static void cancelEdit(UUID player) { EDITS.remove(player); }

    public static EditDraft begin(UUID player, LandProperty property) {
        List<BlockPos> points = new ArrayList<>();
        if (property.regionType().equals("polygon")) {
            for (String encoded : property.polygonPoints()) {
                String[] pair = encoded.split(",");
                points.add(new BlockPos(Integer.parseInt(pair[0]), property.minY(), Integer.parseInt(pair[1])));
            }
        } else {
            points.add(new BlockPos(property.minX(), property.minY(), property.minZ()));
            points.add(new BlockPos(property.maxX(), property.minY(), property.minZ()));
            points.add(new BlockPos(property.maxX(), property.minY(), property.maxZ()));
            points.add(new BlockPos(property.minX(), property.minY(), property.maxZ()));
        }
        EditDraft draft = new EditDraft(property.id(), points);
        EDITS.put(player, draft);
        return draft;
    }

    public static final class EditDraft {
        private final String propertyId;
        private final List<BlockPos> points;
        private final List<List<BlockPos>> history = new ArrayList<>();
        private EditDraft(String propertyId, List<BlockPos> points) { this.propertyId = propertyId; this.points = points; }
        public String propertyId() { return propertyId; }
        public List<BlockPos> points() { return List.copyOf(points); }
        private void snapshot() { history.add(new ArrayList<>(points)); }
        public void add(BlockPos pos) { snapshot(); points.add(pos); }
        public void removeLast() { if (!points.isEmpty()) { snapshot(); points.remove(points.size() - 1); } }
        public void moveNearest(BlockPos pos) {
            if (points.isEmpty()) return;
            snapshot(); int nearest = 0; double distance = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) { double current = points.get(i).getSquaredDistance(pos); if (current < distance) { distance = current; nearest = i; } }
            points.set(nearest, pos);
        }
        public void scale(int blocks) {
            if (points.size() < 3) return;
            snapshot(); double cx = points.stream().mapToInt(BlockPos::getX).average().orElse(0), cz = points.stream().mapToInt(BlockPos::getZ).average().orElse(0);
            for (int i = 0; i < points.size(); i++) { BlockPos p = points.get(i); int x = p.getX() + Integer.signum((int)Math.signum(p.getX() - cx)) * blocks; int z = p.getZ() + Integer.signum((int)Math.signum(p.getZ() - cz)) * blocks; points.set(i, new BlockPos(x, p.getY(), z)); }
        }
        public void undo() { if (!history.isEmpty()) { points.clear(); points.addAll(history.remove(history.size() - 1)); } }
    }
}
