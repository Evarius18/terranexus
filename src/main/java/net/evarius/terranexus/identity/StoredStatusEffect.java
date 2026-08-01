package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StoredStatusEffect(String id,int duration,int amplifier,boolean ambient,boolean particles,boolean icon){
    public static final Codec<StoredStatusEffect> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("id").forGetter(StoredStatusEffect::id),Codec.INT.fieldOf("duration").forGetter(StoredStatusEffect::duration),
            Codec.INT.fieldOf("amplifier").forGetter(StoredStatusEffect::amplifier),Codec.BOOL.fieldOf("ambient").forGetter(StoredStatusEffect::ambient),
            Codec.BOOL.fieldOf("particles").forGetter(StoredStatusEffect::particles),Codec.BOOL.fieldOf("icon").forGetter(StoredStatusEffect::icon)
    ).apply(i,StoredStatusEffect::new));
}
