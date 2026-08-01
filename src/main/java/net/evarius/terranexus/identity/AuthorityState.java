package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthorityState extends PersistentState {
    public static final String ADMIN = "admin";
    public static final String CIVIL_REGISTRAR = "civil_registrar";
    public static final String IMMIGRATION_OFFICER = "immigration_officer";
    public static final String SUPPORTER = "supporter";
    public static final String WHITELISTER = "whitelister";
    public static final String BUILDER = "builder";
    public static final String LAND_REGISTRAR = "land_registrar";
    public static final String LAND_SURVEYOR = "land_surveyor";
    public static final String LAND_CLERK = "land_clerk";
    public static final String LAND_ADMINISTRATOR = "land_administrator";
    public static final String LAND_HIERARCHY_ADMINISTRATOR = "land_hierarchy_administrator";
    public static final String MODERATOR = "moderator";
    public static final String MAYOR = "mayor";
    public static final String CITY_COUNCIL = "city_council";
    public static final String TN_ADMIN_TEST = "tn_admin_test";

    private static final Codec<AuthorityState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("roles", Map.of()).forGetter(state -> state.roles)
    ).apply(instance, AuthorityState::new));
    private static final PersistentStateType<AuthorityState> TYPE =
            new PersistentStateType<>("terranexus_authorities", AuthorityState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, List<String>> roles;

    public AuthorityState() { this(new HashMap<>()); }
    private AuthorityState(Map<String, List<String>> roles) {
        this.roles = new HashMap<>();
        roles.forEach((key, value) -> this.roles.put(key, new ArrayList<>(value)));
    }

    public static AuthorityState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public boolean has(UUID player, String role) {
        return roles.getOrDefault(player.toString(), List.of()).contains(role);
    }

    public List<String> roles(UUID player) {
        return List.copyOf(roles.getOrDefault(player.toString(), List.of()));
    }

    public void grant(UUID player, String role) {
        List<String> assigned = roles.computeIfAbsent(player.toString(), ignored -> new ArrayList<>());
        if (!assigned.contains(role)) assigned.add(role);
        markDirty();
    }

    public void revoke(UUID player, String role) {
        List<String> assigned = roles.get(player.toString());
        if (assigned != null && assigned.remove(role)) markDirty();
    }

    public void clear(UUID player) {
        if (roles.remove(player.toString()) != null) markDirty();
    }

    public static boolean mayProcessOfficialDeparture(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state = get(player.getServer());
        UUID id = player.getUuid();
        return state.has(id, CIVIL_REGISTRAR) || state.has(id, IMMIGRATION_OFFICER) || state.has(id, WHITELISTER);
    }

    public static boolean mayDeleteCitizenAsSupport(ServerPlayerEntity player) {
        return isAdministrator(player) || hasActiveTeamRole(player, SUPPORTER, TeamModeType.SUPPORT);
    }

    public static boolean mayProcessImmigration(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
        return mayManageIdentity(player);
    }

    public static boolean mayManageIdentity(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state = get(player.getServer());
        return state.has(player.getUuid(), CIVIL_REGISTRAR)
                || state.has(player.getUuid(), IMMIGRATION_OFFICER)
                || hasActiveTeamRole(player, SUPPORTER, TeamModeType.SUPPORT)
                || state.has(player.getUuid(), WHITELISTER);
    }

    public static boolean mayManageLand(ServerPlayerEntity player) {
        return mayUseLandOffice(player);
    }

    public static boolean mayUseLandOffice(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();
        return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_SURVEYOR)||state.has(id,LAND_CLERK)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean maySurveyLand(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_SURVEYOR)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayCreateLand(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();
        return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_SURVEYOR)
                ||state.has(id,LAND_CLERK)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayProcessLandRecords(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_CLERK)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayAdministerLand(ServerPlayerEntity player) {
        if (isAdministrator(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayManageLandHierarchy(ServerPlayerEntity player) {
        return isAdministrator(player) || get(player.getServer()).has(player.getUuid(), LAND_HIERARCHY_ADMINISTRATOR);
    }

    public static boolean isTnAdmin(ServerPlayerEntity player) {
        return get(player.getServer()).has(player.getUuid(), TN_ADMIN_TEST);
    }

    public static boolean isAdministrator(ServerPlayerEntity player) {
        AuthorityState state = get(player.getServer());
        return state.has(player.getUuid(), ADMIN) || state.has(player.getUuid(), TN_ADMIN_TEST);
    }

    public static boolean isBuilder(ServerPlayerEntity player) {
        return hasActiveTeamRole(player, BUILDER, TeamModeType.BUILDER) || isAdministrator(player);
    }

    public static boolean isModerator(ServerPlayerEntity player) {
        return hasActiveTeamRole(player, MODERATOR, TeamModeType.MODERATION) || isAdministrator(player);
    }

    private static boolean hasActiveTeamRole(ServerPlayerEntity player,String role,TeamModeType mode){
        return get(player.getServer()).has(player.getUuid(),role)&&TeamModeState.get(player.getServer()).isActive(player.getUuid(),mode);
    }

    public static boolean isKnownRole(String role) {
        return knownRoles().contains(role);
    }

    public static List<String> knownRoles() {
        return List.of(ADMIN, SUPPORTER, WHITELISTER, BUILDER, MODERATOR, CIVIL_REGISTRAR, IMMIGRATION_OFFICER,
                LAND_SURVEYOR, LAND_CLERK, LAND_ADMINISTRATOR, LAND_HIERARCHY_ADMINISTRATOR, LAND_REGISTRAR,
                MAYOR, CITY_COUNCIL, TN_ADMIN_TEST);
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case ADMIN -> "Administrator/in";
            case CIVIL_REGISTRAR -> "Verwaltungs-/Standesamtsbedienstete Person";
            case IMMIGRATION_OFFICER -> "Bedienstete Person der Einreisebehörde";
            case SUPPORTER -> "Supporter/in";
            case WHITELISTER -> "Whitelister/in der Einreisebehörde";
            case BUILDER -> "Builder/in";
            case LAND_REGISTRAR -> "Bedienstete Person der Grundstücksverwaltung";
            case LAND_SURVEYOR -> "Vermessungspersonal des Bauamts";
            case LAND_CLERK -> "Sachbearbeitung des Bauamts";
            case LAND_ADMINISTRATOR -> "Bauamtsleitung";
            case LAND_HIERARCHY_ADMINISTRATOR -> "Leitung der Verwaltungshierarchie";
            case MODERATOR -> "Moderator/in";
            case MAYOR -> "Bürgermeister/in";
            case CITY_COUNCIL -> "Stadtratsmitglied";
            case TN_ADMIN_TEST -> "TNAdmin (nur Entwicklung/Test)";
            default -> role;
        };
    }
}
