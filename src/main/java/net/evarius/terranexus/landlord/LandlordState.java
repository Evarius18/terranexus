package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LandlordState extends PersistentState {
    private static final Codec<LandlordState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, LandProperty.CODEC).optionalFieldOf("properties", Map.of()).forGetter(state -> state.properties)
    ).apply(instance, LandlordState::new));
    private static final PersistentStateType<LandlordState> TYPE =
            new PersistentStateType<>("terranexus_properties", LandlordState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, LandProperty> properties;
    private final Map<String, Set<String>> chunkIndex = new HashMap<>();
    private final Map<String, List<String>> propertyChunkKeys = new HashMap<>();
    private final Set<String> broadProperties = new HashSet<>();
    private final Map<String, PolygonShape> polygonCache = new HashMap<>();
    private final Map<String, List<LandProperty>> ownedCache = new HashMap<>();
    private List<LandProperty> sortedAllCache;

    public LandlordState() { this(new HashMap<>()); }
    private LandlordState(Map<String, LandProperty> properties) {
        this.properties = new HashMap<>(properties);
        rebuildIndex();
    }

    public static LandlordState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<LandProperty> owned(UUID owner) {
        return ownedCache.computeIfAbsent(owner.toString(), id -> properties.values().stream()
                .filter(property -> property.ownerType().equals("player") && property.ownerId().equals(id))
                .sorted(Comparator.comparing(LandProperty::name)).toList());
    }
    public List<LandProperty> all() {
        if (sortedAllCache == null) sortedAllCache = properties.values().stream().sorted(Comparator.comparing(LandProperty::name)).toList();
        return sortedAllCache;
    }
    public LandProperty get(String id) { return properties.get(id); }
    public void refreshRuntimeIndexes() { rebuildIndex(); invalidateViews(); }

    public int normalizeOwnerships(LandManagementState management) {
        int changed = 0;
        for (Map.Entry<String, LandProperty> entry : properties.entrySet()) {
            LandProperty property = entry.getValue();
            boolean valid = property.ownerId() != null && !property.ownerId().isBlank() && switch (property.ownerType()) {
                case "player", "institution" -> true;
                case LandManagementState.AREA_OWNER_TYPE -> management.area(property.ownerId()) != null;
                default -> false;
            };
            if (!valid) {
                entry.setValue(property.withOwner(LandManagementState.AREA_OWNER_TYPE, LandManagementState.ROOT_AREA_ID));
                changed++;
            }
        }
        if (changed > 0) { invalidateViews(); markDirty(); }
        return changed;
    }

    public LandProperty at(String dimension, BlockPos pos) {
        LandProperty indexed = firstContaining(chunkIndex.get(key(dimension, pos.getX() >> 4, pos.getZ() >> 4)), dimension,
                pos.getX(), pos.getY(), pos.getZ());
        return indexed != null ? indexed : firstContaining(broadProperties, dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    public LandProperty inChunk(String dimension, int chunkX, int chunkZ) {
        int minX = chunkX * 16, minZ = chunkZ * 16, maxX = minX + 15, maxZ = minZ + 15;
        LandProperty indexed = firstInChunk(chunkIndex.get(key(dimension, chunkX, chunkZ)), dimension, minX, minZ, maxX, maxZ);
        return indexed != null ? indexed : firstInChunk(broadProperties, dimension, minX, minZ, maxX, maxZ);
    }

    public boolean add(LandProperty property) {
        if (properties.containsKey(property.id()) || overlapsOther(property, property.id())) return false;
        properties.put(property.id(), property);
        index(property);
        invalidateViews();
        markDirty();
        return true;
    }
    public LandProperty conflict(LandProperty property) {
        for (LandProperty old : overlapCandidates(property))
            if (!old.id().equals(property.id()) && old.dimension().equals(property.dimension()) && boxesOverlap(old, property)) return old;
        return null;
    }
    public boolean update(LandProperty property) {
        if (!properties.containsKey(property.id()) || overlapsOther(property, property.id())) return false;
        unindex(property.id());
        properties.put(property.id(), property);
        index(property);
        invalidateViews();
        markDirty();
        return true;
    }
    public boolean remove(String id) {
        if (properties.remove(id) == null) return false;
        unindex(id);
        invalidateViews();
        markDirty();
        return true;
    }

    private LandProperty firstContaining(Collection<String> ids, String dimension, int x, int y, int z) {
        if (ids == null) return null;
        for (String id : ids) {
            LandProperty property = properties.get(id);
            if (property != null && contains(property, dimension, x, y, z)) return property;
        }
        return null;
    }
    private LandProperty firstInChunk(Collection<String> ids, String dimension, int minX, int minZ, int maxX, int maxZ) {
        if (ids == null) return null;
        for (String id : ids) {
            LandProperty property = properties.get(id);
            if (property == null || !property.dimension().equals(dimension) || property.maxX() < minX || property.minX() > maxX
                    || property.maxZ() < minZ || property.minZ() > maxZ) continue;
            if (!property.regionType().equals("polygon")) return property;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                if (contains(property, dimension, x, property.minY(), z)) return property;
        }
        return null;
    }
    private boolean contains(LandProperty property, String dimension, int x, int y, int z) {
        if (!property.dimension().equals(dimension) || y < property.minY() || y > property.maxY()
                || x < property.minX() || x > property.maxX() || z < property.minZ() || z > property.maxZ()) return false;
        if (!property.regionType().equals("polygon")) return true;
        PolygonShape shape = polygonCache.get(property.id());
        if (shape == null) shape = parsePolygon(property);
        if (shape == null || shape.x.length < 3) return false;
        boolean inside = false;
        for (int i = 0, j = shape.x.length - 1; i < shape.x.length; j = i++) {
            int xi = shape.x[i], zi = shape.z[i], xj = shape.x[j], zj = shape.z[j];
            if ((zi > z) != (zj > z) && x < (double) (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside;
        }
        return inside;
    }

    private void rebuildIndex() {
        chunkIndex.clear();
        propertyChunkKeys.clear();
        broadProperties.clear();
        polygonCache.clear();
        properties.values().forEach(this::index);
    }
    private void index(LandProperty property) {
        cachePolygon(property);
        int minCx = property.minX() >> 4, maxCx = property.maxX() >> 4;
        int minCz = property.minZ() >> 4, maxCz = property.maxZ() >> 4;
        long chunks = (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
        if (chunks > ConfigManager.performance().maximumIndexedChunksPerProperty) {
            broadProperties.add(property.id());
            return;
        }
        List<String> keys = new ArrayList<>((int) chunks);
        for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++) {
            String key = key(property.dimension(), cx, cz);
            chunkIndex.computeIfAbsent(key, ignored -> new HashSet<>()).add(property.id());
            keys.add(key);
        }
        propertyChunkKeys.put(property.id(), keys);
    }
    private void unindex(String id) {
        broadProperties.remove(id);
        polygonCache.remove(id);
        for (String key : propertyChunkKeys.getOrDefault(id, List.of())) {
            Set<String> ids = chunkIndex.get(key);
            if (ids != null && ids.remove(id) && ids.isEmpty()) chunkIndex.remove(key);
        }
        propertyChunkKeys.remove(id);
    }
    private void cachePolygon(LandProperty property) {
        if (!property.regionType().equals("polygon")) return;
        polygonCache.put(property.id(), parsePolygon(property));
    }
    private static PolygonShape parsePolygon(LandProperty property) {
        int[] x = new int[property.polygonPoints().size()], z = new int[property.polygonPoints().size()];
        int count = 0;
        for (String encoded : property.polygonPoints()) {
            int separator = encoded.indexOf(',');
            if (separator < 1) continue;
            try {
                x[count] = Integer.parseInt(encoded.substring(0, separator));
                z[count] = Integer.parseInt(encoded.substring(separator + 1));
                count++;
            } catch (NumberFormatException ignored) {}
        }
        if (count != x.length) {
            int[] resizedX = new int[count], resizedZ = new int[count];
            System.arraycopy(x, 0, resizedX, 0, count); System.arraycopy(z, 0, resizedZ, 0, count);
            x = resizedX; z = resizedZ;
        }
        return new PolygonShape(x, z);
    }
    private void invalidateViews() { sortedAllCache = null; ownedCache.clear(); }
    private static String key(String dimension, int chunkX, int chunkZ) { return dimension + '|' + chunkX + '|' + chunkZ; }

    private boolean overlapsOther(LandProperty property, String ignoredId) {
        for (LandProperty old : overlapCandidates(property))
            if (!old.id().equals(ignoredId) && old.dimension().equals(property.dimension()) && boxesOverlap(old, property)) return true;
        return false;
    }
    private Collection<LandProperty> overlapCandidates(LandProperty property) {
        int minCx = property.minX() >> 4, maxCx = property.maxX() >> 4;
        int minCz = property.minZ() >> 4, maxCz = property.maxZ() >> 4;
        long chunks = (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
        if (chunks > ConfigManager.performance().maximumIndexedChunksPerProperty) return properties.values();
        Set<String> ids = new HashSet<>(broadProperties);
        for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++) {
            Set<String> indexed = chunkIndex.get(key(property.dimension(), cx, cz));
            if (indexed != null) ids.addAll(indexed);
        }
        List<LandProperty> result = new ArrayList<>(ids.size());
        for (String id : ids) { LandProperty candidate = properties.get(id); if (candidate != null) result.add(candidate); }
        return result;
    }
    private boolean boxesOverlap(LandProperty a, LandProperty b) {
        if (!(a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minY() <= b.maxY() && a.maxY() >= b.minY()
                && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ())) return false;
        if (!a.regionType().equals("polygon") && !b.regionType().equals("polygon")) return true;
        int minX = Math.max(a.minX(), b.minX()), maxX = Math.min(a.maxX(), b.maxX());
        int minZ = Math.max(a.minZ(), b.minZ()), maxZ = Math.min(a.maxZ(), b.maxZ());
        long columns = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (columns > ConfigManager.performance().maximumExactOverlapColumns) return true;
        int y = Math.max(a.minY(), b.minY());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (contains(a, a.dimension(), x, y, z) && contains(b, b.dimension(), x, y, z)) return true;
        return false;
    }

    public static LandProperty chunk(String name, UUID owner, String dimension, int chunkX, int chunkZ) {
        var config = ConfigManager.claims();
        return new LandProperty(UUID.randomUUID().toString(), name, "player", owner.toString(), dimension, "chunk",
                chunkX * 16, config.defaultMinimumY, chunkZ * 16, chunkX * 16 + 15, config.defaultMaximumY, chunkZ * 16 + 15, List.of());
    }
    public static LandProperty cuboid(String name, UUID owner, String dimension, BlockPos a, BlockPos b) {
        return new LandProperty(UUID.randomUUID().toString(), name, "player", owner.toString(), dimension, "cuboid",
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), List.of());
    }
    public static LandProperty polygon(String name, UUID owner, String dimension, List<BlockPos> points) {
        int minX = points.stream().mapToInt(BlockPos::getX).min().orElse(0), maxX = points.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElse(0), maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        var config = ConfigManager.claims();
        return new LandProperty(UUID.randomUUID().toString(), name, "player", owner.toString(), dimension, "polygon",
                minX, config.defaultMinimumY, minZ, maxX, config.defaultMaximumY, maxZ, encode(points));
    }
    public static LandProperty editedPolygon(LandProperty old, List<BlockPos> points) {
        int minX = points.stream().mapToInt(BlockPos::getX).min().orElse(0), maxX = points.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElse(0), maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        return new LandProperty(old.id(), old.name(), old.ownerType(), old.ownerId(), old.dimension(), "polygon",
                minX, old.minY(), minZ, maxX, old.maxY(), maxZ, encode(points));
    }
    private static List<String> encode(List<BlockPos> points) { return points.stream().map(point -> point.getX() + "," + point.getZ()).toList(); }
    private record PolygonShape(int[] x, int[] z) {}
}
