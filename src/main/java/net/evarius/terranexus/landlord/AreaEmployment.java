package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AreaEmployment(String areaId, String playerUuid, String salaryGroup, long salary,
                             long joinedAt, long nextPayAt) {
    public static final Codec<AreaEmployment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("area_id").forGetter(AreaEmployment::areaId),
            Codec.STRING.fieldOf("player_uuid").forGetter(AreaEmployment::playerUuid),
            Codec.STRING.optionalFieldOf("salary_group", "Angestellte").forGetter(AreaEmployment::salaryGroup),
            Codec.LONG.optionalFieldOf("salary", 0L).forGetter(AreaEmployment::salary),
            Codec.LONG.optionalFieldOf("joined_at", 0L).forGetter(AreaEmployment::joinedAt),
            Codec.LONG.optionalFieldOf("next_pay_at", 0L).forGetter(AreaEmployment::nextPayAt)
    ).apply(instance, AreaEmployment::new));

    public AreaEmployment withSalaryGroup(String group, long newSalary) {
        return new AreaEmployment(areaId, playerUuid, group, newSalary, joinedAt, nextPayAt);
    }
    public AreaEmployment withNextPayAt(long value) {
        return new AreaEmployment(areaId, playerUuid, salaryGroup, salary, joinedAt, value);
    }
}
