package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CitizenDepartureRecord(long timestamp, String actorId, String citizenId, String citizenNumber,
                                     String citizenName, String mode, String reason, long closedBalance,
                                     int releasedProperties, int removedEmployments, int removedShops) {
    public static final Codec<CitizenDepartureRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("timestamp").forGetter(CitizenDepartureRecord::timestamp),
            Codec.STRING.fieldOf("actor_id").forGetter(CitizenDepartureRecord::actorId),
            Codec.STRING.fieldOf("citizen_id").forGetter(CitizenDepartureRecord::citizenId),
            Codec.STRING.fieldOf("citizen_number").forGetter(CitizenDepartureRecord::citizenNumber),
            Codec.STRING.fieldOf("citizen_name").forGetter(CitizenDepartureRecord::citizenName),
            Codec.STRING.fieldOf("mode").forGetter(CitizenDepartureRecord::mode),
            Codec.STRING.fieldOf("reason").forGetter(CitizenDepartureRecord::reason),
            Codec.LONG.optionalFieldOf("closed_balance", 0L).forGetter(CitizenDepartureRecord::closedBalance),
            Codec.INT.optionalFieldOf("released_properties", 0).forGetter(CitizenDepartureRecord::releasedProperties),
            Codec.INT.optionalFieldOf("removed_employments", 0).forGetter(CitizenDepartureRecord::removedEmployments),
            Codec.INT.optionalFieldOf("removed_shops", 0).forGetter(CitizenDepartureRecord::removedShops)
    ).apply(instance, CitizenDepartureRecord::new));
}
