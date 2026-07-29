package net.evarius.terranexus.phone.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PhoneHistoryEntry(long timestamp, String peer, String number, String direction,
                                String outcome, long durationSeconds) {
    public static final Codec<PhoneHistoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("timestamp").forGetter(PhoneHistoryEntry::timestamp),
            Codec.STRING.fieldOf("peer").forGetter(PhoneHistoryEntry::peer),
            Codec.STRING.fieldOf("number").forGetter(PhoneHistoryEntry::number),
            Codec.STRING.fieldOf("direction").forGetter(PhoneHistoryEntry::direction),
            Codec.STRING.fieldOf("outcome").forGetter(PhoneHistoryEntry::outcome),
            Codec.LONG.optionalFieldOf("duration_seconds", 0L).forGetter(PhoneHistoryEntry::durationSeconds)
    ).apply(instance, PhoneHistoryEntry::new));
}
