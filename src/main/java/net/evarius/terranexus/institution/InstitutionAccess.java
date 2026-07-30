package net.evarius.terranexus.institution;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class InstitutionAccess {
    private InstitutionAccess() {}

    public static boolean has(ServerPlayerEntity player, String institutionId, InstitutionPermission permission) {
        if (AuthorityState.isAdministrator(player)) return true;
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid());
        return employee != null && employee.institutionRole().permits(permission);
    }

    public static boolean mayView(ServerPlayerEntity player, String institutionId) {
        return AuthorityState.isAdministrator(player)
                || InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid()) != null;
    }

    public static boolean hasBankPermission(ServerPlayerEntity player, InstitutionPermission permission) {
        if (AuthorityState.isAdministrator(player)) return true;
        InstitutionState state = InstitutionState.get(player.getServer());
        for (Institution institution : state.forMember(player.getUuid())) {
            String type = institution.type().toLowerCase(java.util.Locale.ROOT);
            if ((type.contains("bank") || type.contains("finanz")) && has(player, institution.id(), permission)) return true;
        }
        return false;
    }

    public static boolean hasCentralBankPermission(ServerPlayerEntity player, InstitutionPermission permission) {
        if (AuthorityState.isAdministrator(player)) return true;
        InstitutionState state = InstitutionState.get(player.getServer());
        for (Institution institution : state.forMember(player.getUuid())) {
            String type = institution.type().toLowerCase(java.util.Locale.ROOT);
            if (ConfigManager.institutions().centralBankTypeKeywords.stream()
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(type::contains)
                    && has(player, institution.id(), permission)) return true;
        }
        return false;
    }

    public static Set<String> rpVoiceMembershipKeys(ServerPlayerEntity player) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Institution institution : InstitutionState.get(player.getServer()).forMember(player.getUuid())) {
            keys.add(institution.id());
            keys.add(institution.name());
            keys.add(institution.type());
            String searchable = (institution.id() + " " + institution.name() + " " + institution.type())
                    .toLowerCase(Locale.ROOT);
            ConfigManager.institutions().emergencyOrganizationMappings.forEach((key, matchers) -> {
                if (matchers.stream().map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(searchable::contains)) keys.add(key);
            });
        }
        return Set.copyOf(keys);
    }
}
