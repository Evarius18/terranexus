package net.evarius.terranexus.election;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ElectionOption(String id,String label,String candidateId){
    public static final Codec<ElectionOption> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(ElectionOption::id),Codec.STRING.fieldOf("label").forGetter(ElectionOption::label),Codec.STRING.optionalFieldOf("candidate_id","").forGetter(ElectionOption::candidateId)).apply(i,ElectionOption::new));
}
