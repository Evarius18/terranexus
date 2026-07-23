package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DutySession(long startedAt, long endedAt) {
    public static final Codec<DutySession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("started_at").forGetter(DutySession::startedAt),
            Codec.LONG.fieldOf("ended_at").forGetter(DutySession::endedAt)
    ).apply(instance, DutySession::new));

    public long durationMillis() { return Math.max(0L, endedAt - startedAt); }
}
