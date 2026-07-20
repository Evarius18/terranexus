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
    public static final String CIVIL_REGISTRAR = "civil_registrar";
    public static final String IMMIGRATION_OFFICER = "immigration_officer";
    public static final String SUPPORTER = "supporter";
    public static final String LAND_REGISTRAR = "land_registrar";
    public static final String LAND_SURVEYOR = "land_surveyor";
    public static final String LAND_CLERK = "land_clerk";
    public static final String LAND_ADMINISTRATOR = "land_administrator";
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

    public void grant(UUID player, String role) {
        List<String> assigned = roles.computeIfAbsent(player.toString(), ignored -> new ArrayList<>());
        if (!assigned.contains(role)) assigned.add(role);
        markDirty();
    }

    public void revoke(UUID player, String role) {
        List<String> assigned = roles.get(player.toString());
        if (assigned != null && assigned.remove(role)) markDirty();
    }

    public static boolean mayProcessImmigration(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
        return mayManageIdentity(player);
    }

    public static boolean mayManageIdentity(ServerPlayerEntity player) {
        if (isTnAdmin(player)) return true;
        AuthorityState state = get(player.getServer());
        return state.has(player.getUuid(), CIVIL_REGISTRAR)
                || state.has(player.getUuid(), IMMIGRATION_OFFICER)
                || state.has(player.getUuid(), SUPPORTER);
    }

    public static boolean mayManageLand(ServerPlayerEntity player) {
        return mayUseLandOffice(player);
    }

    public static boolean mayUseLandOffice(ServerPlayerEntity player) {
        if (isTnAdmin(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();
        return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_SURVEYOR)||state.has(id,LAND_CLERK)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean maySurveyLand(ServerPlayerEntity player) {
        if (isTnAdmin(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_SURVEYOR)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayProcessLandRecords(ServerPlayerEntity player) {
        if (isTnAdmin(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_CLERK)||state.has(id,LAND_ADMINISTRATOR);
    }
    public static boolean mayAdministerLand(ServerPlayerEntity player) {
        if (isTnAdmin(player)) return true;
        AuthorityState state=get(player.getServer());UUID id=player.getUuid();return state.has(id,LAND_REGISTRAR)||state.has(id,LAND_ADMINISTRATOR);
    }

    public static boolean isTnAdmin(ServerPlayerEntity player) {
        return get(player.getServer()).has(player.getUuid(), TN_ADMIN_TEST);
    }

    public static boolean isKnownRole(String role) {
        return CIVIL_REGISTRAR.equals(role) || IMMIGRATION_OFFICER.equals(role) || SUPPORTER.equals(role) || LAND_REGISTRAR.equals(role)
                || LAND_SURVEYOR.equals(role)||LAND_CLERK.equals(role)||LAND_ADMINISTRATOR.equals(role)||TN_ADMIN_TEST.equals(role);
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case CIVIL_REGISTRAR -> "Verwaltungs-/Standesamtsbedienstete Person";
            case IMMIGRATION_OFFICER -> "Bedienstete Person der Einreisebehörde";
            case SUPPORTER -> "Supporter/Whitelister im Einreisedienst";
            case LAND_REGISTRAR -> "Bedienstete Person der Grundstücksverwaltung";
            case LAND_SURVEYOR -> "Vermessungspersonal des Bauamts";
            case LAND_CLERK -> "Sachbearbeitung des Bauamts";
            case LAND_ADMINISTRATOR -> "Bauamtsleitung";
            case TN_ADMIN_TEST -> "TNAdmin (nur Entwicklung/Test)";
            default -> role;
        };
    }
}
