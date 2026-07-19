package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LandLease(String propertyId, String landlordAccount, String tenantId, long rent, long deposit,
                        int periodDays, long nextDueAt, int missedPayments, boolean active) {
    public static final Codec<LandLease> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(LandLease::propertyId), Codec.STRING.fieldOf("landlord_account").forGetter(LandLease::landlordAccount),
            Codec.STRING.fieldOf("tenant_id").forGetter(LandLease::tenantId), Codec.LONG.fieldOf("rent").forGetter(LandLease::rent),
            Codec.LONG.fieldOf("deposit").forGetter(LandLease::deposit), Codec.INT.fieldOf("period_days").forGetter(LandLease::periodDays),
            Codec.LONG.fieldOf("next_due_at").forGetter(LandLease::nextDueAt), Codec.INT.fieldOf("missed_payments").forGetter(LandLease::missedPayments),
            Codec.BOOL.fieldOf("active").forGetter(LandLease::active)
    ).apply(instance, LandLease::new));
}
