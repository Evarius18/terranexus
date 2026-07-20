package net.evarius.terranexus.institution;

import net.evarius.terranexus.identity.AuthorityState;
import net.minecraft.server.network.ServerPlayerEntity;

public final class InstitutionAccess {
    private InstitutionAccess() {}

    public static boolean has(ServerPlayerEntity player, String institutionId, InstitutionPermission permission) {
        if (AuthorityState.isTnAdmin(player)) return true;
        InstitutionEmployee employee = InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid());
        return employee != null && employee.institutionRole().permits(permission);
    }

    public static boolean mayView(ServerPlayerEntity player, String institutionId) {
        return AuthorityState.isTnAdmin(player)
                || InstitutionState.get(player.getServer()).employee(institutionId, player.getUuid()) != null;
    }

    public static boolean hasBankPermission(ServerPlayerEntity player, InstitutionPermission permission) {
        if (AuthorityState.isTnAdmin(player)) return true;
        InstitutionState state = InstitutionState.get(player.getServer());
        for (Institution institution : state.forMember(player.getUuid())) {
            String type = institution.type().toLowerCase(java.util.Locale.ROOT);
            if ((type.contains("bank") || type.contains("finanz")) && has(player, institution.id(), permission)) return true;
        }
        return false;
    }
}
