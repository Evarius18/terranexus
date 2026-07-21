package net.evarius.terranexus.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopState extends PersistentState {
    private static final Codec<ShopState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ShopRecord.CODEC.listOf().optionalFieldOf("shops", List.of()).forGetter(state -> List.copyOf(state.shops.values()))
    ).apply(instance, ShopState::new));
    private static final PersistentStateType<ShopState> TYPE = new PersistentStateType<>(
            "terranexus_shops", ShopState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, ShopRecord> shops = new HashMap<>();
    private final Map<String, String> signIndex = new HashMap<>();
    private final Map<String, String> containerIndex = new HashMap<>();

    public ShopState() {}
    private ShopState(List<ShopRecord> records) { records.forEach(this::index); }

    public static ShopState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public ShopRecord atSign(String dimension, BlockPos pos) { return byIndex(signIndex, dimension, pos); }
    public ShopRecord atContainer(String dimension, BlockPos pos) { return byIndex(containerIndex, dimension, pos); }
    public Collection<ShopRecord> all() { return List.copyOf(shops.values()); }
    public List<ShopRecord> ownedBy(String ownerType, String ownerId) {
        List<ShopRecord> result = new ArrayList<>();
        for (ShopRecord shop : shops.values())
            if (shop.ownerType().equals(ownerType) && shop.ownerId().equals(ownerId)) result.add(shop);
        return List.copyOf(result);
    }

    public boolean add(ShopRecord record) {
        if (shops.containsKey(record.id()) || atSign(record.dimension(), record.signPos()) != null
                || atContainer(record.dimension(), record.containerPos()) != null) return false;
        index(record);
        markDirty();
        return true;
    }

    public boolean remove(String id) {
        ShopRecord removed = shops.remove(id);
        if (removed == null) return false;
        signIndex.remove(key(removed.dimension(), removed.signPos()));
        containerIndex.remove(key(removed.dimension(), removed.containerPos()));
        markDirty();
        return true;
    }

    private ShopRecord byIndex(Map<String, String> index, String dimension, BlockPos pos) {
        String id = index.get(key(dimension, pos));
        return id == null ? null : shops.get(id);
    }
    private void index(ShopRecord record) {
        shops.put(record.id(), record);
        signIndex.put(key(record.dimension(), record.signPos()), record.id());
        containerIndex.put(key(record.dimension(), record.containerPos()), record.id());
    }
    private static String key(String dimension, BlockPos pos) { return dimension + '|' + pos.asLong(); }
}
