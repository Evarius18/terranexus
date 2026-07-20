package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record InstitutionEmployee(String playerUuid, String role, long joinedAt, long salary,
                                  long nextPayAt, String personnelNote) {
    public static final Codec<InstitutionEmployee> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("player_uuid").forGetter(InstitutionEmployee::playerUuid),
            Codec.STRING.optionalFieldOf("role", InstitutionRole.EMPLOYEE.id()).forGetter(InstitutionEmployee::role),
            Codec.LONG.optionalFieldOf("joined_at", 0L).forGetter(InstitutionEmployee::joinedAt),
            Codec.LONG.optionalFieldOf("salary", 0L).forGetter(InstitutionEmployee::salary),
            Codec.LONG.optionalFieldOf("next_pay_at", 0L).forGetter(InstitutionEmployee::nextPayAt),
            Codec.STRING.optionalFieldOf("personnel_note", "").forGetter(InstitutionEmployee::personnelNote)
    ).apply(instance, InstitutionEmployee::new));

    public InstitutionRole institutionRole() { return InstitutionRole.fromId(role); }
    public InstitutionEmployee withRole(InstitutionRole value) {
        return new InstitutionEmployee(playerUuid, value.id(), joinedAt, salary, nextPayAt, personnelNote);
    }
    public InstitutionEmployee withSalary(long value) {
        return new InstitutionEmployee(playerUuid, role, joinedAt, value, nextPayAt, personnelNote);
    }
    public InstitutionEmployee withNextPayAt(long value) {
        return new InstitutionEmployee(playerUuid, role, joinedAt, salary, value, personnelNote);
    }
    public InstitutionEmployee withPersonnelNote(String value) {
        return new InstitutionEmployee(playerUuid, role, joinedAt, salary, nextPayAt, value);
    }
}
