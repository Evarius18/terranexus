package net.evarius.terranexus.election;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;import java.util.Map;

public record Election(String id,String type,String title,String areaId,String status,List<ElectionOption> options,
                       Map<String,String> votes,String createdBy,long createdAt,long opensAt,long closesAt,String resultingRole){
    public static final String DRAFT="draft",ACTIVE="active",CLOSED="closed";
    public static final Codec<Election> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("id").forGetter(Election::id),Codec.STRING.fieldOf("type").forGetter(Election::type),Codec.STRING.fieldOf("title").forGetter(Election::title),Codec.STRING.optionalFieldOf("area_id","").forGetter(Election::areaId),Codec.STRING.fieldOf("status").forGetter(Election::status),ElectionOption.CODEC.listOf().fieldOf("options").forGetter(Election::options),Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("votes",Map.of()).forGetter(Election::votes),Codec.STRING.fieldOf("created_by").forGetter(Election::createdBy),Codec.LONG.fieldOf("created_at").forGetter(Election::createdAt),Codec.LONG.optionalFieldOf("opens_at",0L).forGetter(Election::opensAt),Codec.LONG.optionalFieldOf("closes_at",0L).forGetter(Election::closesAt),Codec.STRING.optionalFieldOf("resulting_role","").forGetter(Election::resultingRole)
    ).apply(i,Election::new));
}
