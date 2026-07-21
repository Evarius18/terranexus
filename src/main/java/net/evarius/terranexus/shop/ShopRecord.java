package net.evarius.terranexus.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

public record ShopRecord(String id, String dimension, long signPosition, long containerPosition,
                         String itemId, long buyPrice, long sellPrice, String ownerType,
                         String ownerId, String account, long createdAt) {
    public static final Codec<ShopRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ShopRecord::id),
            Codec.STRING.fieldOf("dimension").forGetter(ShopRecord::dimension),
            Codec.LONG.fieldOf("sign_position").forGetter(ShopRecord::signPosition),
            Codec.LONG.fieldOf("container_position").forGetter(ShopRecord::containerPosition),
            Codec.STRING.fieldOf("item_id").forGetter(ShopRecord::itemId),
            Codec.LONG.fieldOf("buy_price").forGetter(ShopRecord::buyPrice),
            Codec.LONG.fieldOf("sell_price").forGetter(ShopRecord::sellPrice),
            Codec.STRING.fieldOf("owner_type").forGetter(ShopRecord::ownerType),
            Codec.STRING.fieldOf("owner_id").forGetter(ShopRecord::ownerId),
            Codec.STRING.fieldOf("account").forGetter(ShopRecord::account),
            Codec.LONG.fieldOf("created_at").forGetter(ShopRecord::createdAt)
    ).apply(instance, ShopRecord::new));

    public BlockPos signPos() { return BlockPos.fromLong(signPosition); }
    public BlockPos containerPos() { return BlockPos.fromLong(containerPosition); }
}
