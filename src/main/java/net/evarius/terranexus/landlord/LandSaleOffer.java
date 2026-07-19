package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LandSaleOffer(String propertyId, String sellerAccount, long price, long createdAt) {
    public static final Codec<LandSaleOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(LandSaleOffer::propertyId), Codec.STRING.fieldOf("seller_account").forGetter(LandSaleOffer::sellerAccount),
            Codec.LONG.fieldOf("price").forGetter(LandSaleOffer::price), Codec.LONG.fieldOf("created_at").forGetter(LandSaleOffer::createdAt)
    ).apply(instance, LandSaleOffer::new));
}
