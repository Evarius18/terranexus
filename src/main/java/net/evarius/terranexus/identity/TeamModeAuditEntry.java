package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TeamModeAuditEntry(long timestamp,String playerId,String role,String oldMode,String newMode,long durationMillis,String reason){
    public static final Codec<TeamModeAuditEntry> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.fieldOf("timestamp").forGetter(TeamModeAuditEntry::timestamp),Codec.STRING.fieldOf("player_id").forGetter(TeamModeAuditEntry::playerId),
            Codec.STRING.fieldOf("role").forGetter(TeamModeAuditEntry::role),Codec.STRING.fieldOf("old_mode").forGetter(TeamModeAuditEntry::oldMode),
            Codec.STRING.fieldOf("new_mode").forGetter(TeamModeAuditEntry::newMode),Codec.LONG.fieldOf("duration_ms").forGetter(TeamModeAuditEntry::durationMillis),
            Codec.STRING.optionalFieldOf("reason","").forGetter(TeamModeAuditEntry::reason)
    ).apply(i,TeamModeAuditEntry::new));
}
