package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Central boundary between public-authority powers and private ownership rights.
 * Bauamt roles may administer records without implicitly becoming property owners.
 */
public final class LandPermissionService {
    private LandPermissionService() {}

    public static boolean mayCreateProperty(ServerPlayerEntity player) {
        return AuthorityState.mayCreateLand(player);
    }

    public static boolean mayTransferProperty(ServerPlayerEntity player) {
        return AuthorityState.maySurveyLand(player);
    }

    public static String transferDenial(ServerPlayerEntity player) {
        if (AuthorityState.mayProcessLandRecords(player))
            return "Grundstücksübertragungen erfordern zusätzlich die Berechtigung Land Survey.";
        return "Für Grundstücksübertragungen ist die Berechtigung Land Survey erforderlich.";
    }

    public static boolean isSystemAdministrator(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || AuthorityState.isTnAdmin(player);
    }

    public static boolean mayExerciseOwnerRights(ServerPlayerEntity player, LandProperty property) {
        if (property == null) return false;
        if (isSystemAdministrator(player) || property.isOwnedBy(player.getUuid())) return true;
        if (property.ownerType().equals("institution"))
            return InstitutionState.get(player.getServer()).mayManage(property.ownerId(), player.getUuid());
        return property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && mayExerciseAreaOwnerRights(player, property.ownerId());
    }

    public static boolean mayExerciseAreaOwnerRights(ServerPlayerEntity player, String areaId) {
        if (isSystemAdministrator(player)) return true;
        AdministrativeArea area = LandManagementState.get(player.getServer()).area(areaId);
        if (area == null || LandManagementState.SYSTEM_OWNER_TYPE.equals(area.ownerType())) return false;
        if ("player".equals(area.ownerType())) return area.ownerId().equals(player.getUuidAsString());
        return "institution".equals(area.ownerType())
                && InstitutionState.get(player.getServer()).mayManage(area.ownerId(), player.getUuid());
    }
}
