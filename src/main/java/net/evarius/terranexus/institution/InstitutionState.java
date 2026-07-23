package net.evarius.terranexus.institution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InstitutionState extends PersistentState {
    private static final Codec<InstitutionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Institution.CODEC).optionalFieldOf("institutions", Map.of()).forGetter(state -> state.institutions)
    ).apply(instance, InstitutionState::new));
    private static final PersistentStateType<InstitutionState> TYPE =
            new PersistentStateType<>("terranexus_institutions", InstitutionState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, Institution> institutions;
    private final Map<String, List<Institution>> memberCache = new HashMap<>();
    private final Map<String, List<InstitutionEmployee>> employeeCache = new HashMap<>();
    private List<Institution> allCache;

    public InstitutionState() { this(new HashMap<>()); }
    private InstitutionState(Map<String, Institution> institutions) {
        this.institutions = new HashMap<>();
        institutions.forEach((id, institution) -> this.institutions.put(id, normalize(institution)));
    }
    public static InstitutionState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }

    public synchronized Institution create(String name, String type, UUID owner) {
        String cleanName = name == null ? "" : name.trim();
        if (!isNameAvailable(cleanName) || cleanName.length() > ConfigManager.institutions().maximumNameLength
                || !ConfigManager.institutions().allowedTypes.contains(type)) return null;
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        InstitutionEmployee ownerEmployee = new InstitutionEmployee(owner.toString(), InstitutionRole.OWNER.id(), now, 0,
                now + payrollPeriodMillis(), "Gründer/in");
        Map<String, InstitutionEmployee> employees = Map.of(owner.toString(), ownerEmployee);
        Institution institution = build(id, cleanName, type, owner.toString(), employees);
        institutions.put(id, institution);
        invalidateCaches();
        markDirty();
        return institution;
    }

    public List<Institution> forMember(UUID player) {
        return memberCache.computeIfAbsent(player.toString(), id -> institutions.values().stream()
                .filter(institution -> institution.employees().containsKey(id))
                .sorted(Comparator.comparing(Institution::name)).toList());
    }
    public List<Institution> all() {
        if (allCache == null) allCache = institutions.values().stream().sorted(Comparator.comparing(Institution::name)).toList();
        return allCache;
    }
    public Institution get(String id) { return institutions.get(id); }
    public boolean isNameAvailable(String name) {
        String normalized = name == null ? "" : name.trim();
        return !normalized.isBlank() && institutions.values().stream().noneMatch(value -> value.name().equalsIgnoreCase(normalized));
    }
    public InstitutionEmployee employee(String institutionId, UUID player) {
        Institution institution = institutions.get(institutionId);
        return institution == null ? null : institution.employees().get(player.toString());
    }
    public List<InstitutionEmployee> employees(String institutionId) {
        Institution institution = institutions.get(institutionId);
        return institution == null ? List.of() : employeeCache.computeIfAbsent(institutionId, ignored -> institution.employees().values().stream()
                .sorted(Comparator.comparingInt((InstitutionEmployee employee) -> employee.institutionRole().level()).reversed()
                        .thenComparing(InstitutionEmployee::joinedAt)).toList());
    }
    public boolean mayManage(String institutionId, UUID player) {
        InstitutionEmployee employee = employee(institutionId, player);
        if (employee == null) return false;
        InstitutionRole role = employee.institutionRole();
        return role == InstitutionRole.OWNER || role == InstitutionRole.DIRECTOR || role == InstitutionRole.MANAGER;
    }

    public boolean hire(ServerPlayerEntity actor, String institutionId, UUID target, InstitutionRole role) {
        Institution institution = institutions.get(institutionId);
        if (institution == null || institution.employees().containsKey(target.toString())
                || institution.employees().size() >= ConfigManager.institutions().maximumEmployees
                || IdentityState.get(actor.getServer()).get(target) == null
                || !isRoleEnabled(role)
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_EMPLOYEES)
                || !mayAssign(actor, institutionId, null, role)) return false;
        long now = System.currentTimeMillis();
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        employees.put(target.toString(), new InstitutionEmployee(target.toString(), role.id(), now, ConfigManager.salary().defaultSalary,
                now + payrollPeriodMillis(), ""));
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
        return true;
    }

    public boolean fire(ServerPlayerEntity actor, String institutionId, UUID target) {
        Institution institution = institutions.get(institutionId);
        InstitutionEmployee current = employee(institutionId, target);
        if (institution == null || current == null || target.toString().equals(institution.ownerUuid())
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_EMPLOYEES)
                || !mayAssign(actor, institutionId, current.institutionRole(), InstitutionRole.EMPLOYEE)) return false;
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        employees.remove(target.toString());
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
        TimeClockState.get(actor.getServer()).employmentEnded(actor.getServer(), institutionId, target, System.currentTimeMillis());
        return true;
    }

    public boolean setRole(ServerPlayerEntity actor, String institutionId, UUID target, InstitutionRole role) {
        Institution institution = institutions.get(institutionId);
        InstitutionEmployee current = employee(institutionId, target);
        if (institution == null || current == null || current.institutionRole() == InstitutionRole.OWNER
                || role == InstitutionRole.OWNER || !isRoleEnabled(role)
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_ROLES)
                || !mayAssign(actor, institutionId, current.institutionRole(), role)) return false;
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        employees.put(target.toString(), current.withRole(role));
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
        return true;
    }

    public boolean canAssignRole(ServerPlayerEntity actor, String institutionId, UUID target, InstitutionRole role) {
        InstitutionEmployee current = employee(institutionId, target);
        return current != null && current.institutionRole() != InstitutionRole.OWNER && role != InstitutionRole.OWNER
                && isRoleEnabled(role)
                && InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_ROLES)
                && mayAssign(actor, institutionId, current.institutionRole(), role);
    }

    public boolean setSalary(ServerPlayerEntity actor, String institutionId, UUID target, long salary) {
        Institution institution = institutions.get(institutionId);
        InstitutionEmployee current = employee(institutionId, target);
        if (institution == null || current == null || salary < 0 || salary > ConfigManager.salary().maximumSalary
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_SALARIES)) return false;
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        employees.put(target.toString(), current.withSalary(salary));
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
        return true;
    }

    public boolean setPersonnelNote(ServerPlayerEntity actor, String institutionId, UUID target, String note) {
        Institution institution = institutions.get(institutionId);
        InstitutionEmployee current = employee(institutionId, target);
        String normalized = note == null ? "" : note.trim();
        if (institution == null || current == null || normalized.length() > ConfigManager.institutions().maximumPersonnelNoteLength
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.MANAGE_EMPLOYEES)) return false;
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        employees.put(target.toString(), current.withPersonnelNote(normalized));
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
        return true;
    }

    public boolean transferOwnership(ServerPlayerEntity actor, String institutionId, UUID newOwner) {
        Institution institution = institutions.get(institutionId);
        if (institution == null || institution.ownerUuid().equals(newOwner.toString())
                || IdentityState.get(actor.getServer()).get(newOwner) == null
                || !InstitutionAccess.has(actor, institutionId, InstitutionPermission.TRANSFER_OWNERSHIP)) return false;
        long now = System.currentTimeMillis();
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        InstitutionEmployee oldOwner = employees.get(institution.ownerUuid());
        if (oldOwner != null) employees.put(institution.ownerUuid(), oldOwner.withRole(InstitutionRole.DIRECTOR));
        InstitutionEmployee incoming = employees.get(newOwner.toString());
        if (incoming == null) incoming = new InstitutionEmployee(newOwner.toString(), InstitutionRole.OWNER.id(), now, 0,
                now + payrollPeriodMillis(), "Eigentum übernommen");
        else incoming = incoming.withRole(InstitutionRole.OWNER);
        employees.put(newOwner.toString(), incoming);
        institutions.put(institutionId, build(institution, newOwner.toString(), employees));
        invalidateCaches();
        markDirty();
        return true;
    }

    public void addMember(String institutionId, UUID player, String role) {
        Institution institution = institutions.get(institutionId);
        if (institution == null) return;
        if (!institution.employees().containsKey(player.toString())
                && institution.employees().size() >= ConfigManager.institutions().maximumEmployees) return;
        long now = System.currentTimeMillis();
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        InstitutionRole parsedRole = InstitutionRole.fromId(role);
        if (!isRoleEnabled(parsedRole) || parsedRole == InstitutionRole.OWNER) parsedRole = configuredDefaultRole();
        employees.put(player.toString(), new InstitutionEmployee(player.toString(), parsedRole.id(), now, ConfigManager.salary().defaultSalary,
                now + payrollPeriodMillis(), ""));
        institutions.put(institutionId, build(institution, institution.ownerUuid(), employees));
        invalidateCaches();
        markDirty();
    }

    public void processPayroll(MinecraftServer server, long now) {
        if (!ConfigManager.salary().automaticPaymentsEnabled) return;
        EconomyState economy = EconomyState.get(server);
        boolean changed = false;
        for (Institution institution : new ArrayList<>(institutions.values())) {
            Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
            boolean institutionChanged = false;
            for (InstitutionEmployee employee : institution.employees().values()) {
                if (employee.salary() <= 0 || employee.nextPayAt() > now) continue;
                UUID employeeId;
                try { employeeId = UUID.fromString(employee.playerUuid()); }
                catch (IllegalArgumentException ignored) { continue; }
                long nextPayAt = now + payrollPeriodMillis();
                boolean paid = economy.transferConditional(EconomyState.institutionAccount(institution.id()),
                        EconomyState.playerAccount(employeeId), employee.salary(), "Gehalt · " + institution.name(),
                        "SYSTEM", institution.id(), "SALARY", () -> {
                            Institution latest = institutions.get(institution.id());
                            if (latest == null || !employee.equals(latest.employees().get(employee.playerUuid()))) return false;
                            Map<String, InstitutionEmployee> latestEmployees = new HashMap<>(latest.employees());
                            latestEmployees.put(employee.playerUuid(), employee.withNextPayAt(nextPayAt));
                            institutions.put(latest.id(), build(latest, latest.ownerUuid(), latestEmployees));
                            invalidateCaches(); markDirty();
                            return true;
                        });
                employees.put(employee.playerUuid(), employee.withNextPayAt(nextPayAt));
                ServerPlayerEntity online = server.getPlayerManager().getPlayer(employeeId);
                if (online != null && ConfigManager.salary().notifyEmployees) online.sendMessage(Text.literal(paid
                        ? "Gehalt von " + institution.name() + ": " + EconomyState.format(employee.salary())
                        : "Gehalt von " + institution.name() + " konnte wegen fehlender Kontodeckung nicht ausgezahlt werden.")
                        .formatted(paid ? Formatting.GREEN : Formatting.RED), false);
                if (!paid && ConfigManager.salary().notifyResponsibleStaffOnFailure) notifyPayrollFailure(server, institution, employee);
                institutionChanged = true;
            }
            if (institutionChanged) {
                institutions.put(institution.id(), build(institution, institution.ownerUuid(), employees));
                changed = true;
            }
        }
        if (changed) { invalidateCaches(); markDirty(); }
    }

    public static long payrollPeriodMillis() {
        return (long) ConfigManager.salary().paymentIntervalMinutes * 60_000L;
    }

    private static void notifyPayrollFailure(MinecraftServer server, Institution institution, InstitutionEmployee unpaid) {
        for (InstitutionEmployee responsible : institution.employees().values()) {
            InstitutionRole role = responsible.institutionRole();
            if (role != InstitutionRole.OWNER && role != InstitutionRole.DIRECTOR && role != InstitutionRole.ACCOUNTANT) continue;
            try {
                ServerPlayerEntity online = server.getPlayerManager().getPlayer(UUID.fromString(responsible.playerUuid()));
                if (online != null && !responsible.playerUuid().equals(unpaid.playerUuid()))
                    online.sendMessage(Text.literal("Gehaltszahlung bei " + institution.name() + " fehlgeschlagen: Kontodeckung prüfen.")
                            .formatted(Formatting.RED), false);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private boolean mayAssign(ServerPlayerEntity actor, String institutionId, InstitutionRole current, InstitutionRole desired) {
        if (AuthorityState.isTnAdmin(actor)) return desired != InstitutionRole.OWNER;
        InstitutionEmployee acting = employee(institutionId, actor.getUuid());
        if (acting == null) return false;
        InstitutionRole actorRole = acting.institutionRole();
        if (actorRole == InstitutionRole.OWNER) return desired != InstitutionRole.OWNER;
        if (actorRole == InstitutionRole.HR) return desired.level() <= InstitutionRole.HR.level()
                && (current == null || current.level() <= InstitutionRole.HR.level());
        return desired != InstitutionRole.OWNER && actorRole.level() > desired.level()
                && (current == null || actorRole.level() > current.level());
    }

    public static boolean isRoleEnabled(InstitutionRole role) {
        return role == InstitutionRole.OWNER || ConfigManager.institutions().allowedRoles.contains(role.id());
    }

    public static InstitutionRole configuredDefaultRole() {
        InstitutionRole role = InstitutionRole.fromId(ConfigManager.institutions().defaultEmployeeRole);
        return isRoleEnabled(role) && role != InstitutionRole.OWNER ? role : InstitutionRole.EMPLOYEE;
    }

    private void invalidateCaches() { allCache = null; memberCache.clear(); employeeCache.clear(); }

    private static Institution normalize(Institution institution) {
        Map<String, InstitutionEmployee> employees = new HashMap<>(institution.employees());
        long fallbackJoinedAt = 0L;
        institution.members().forEach((player, roles) -> {
            if (employees.containsKey(player)) return;
            InstitutionRole role = roles.stream().map(InstitutionRole::fromId)
                    .max(Comparator.comparingInt(InstitutionRole::level)).orElse(InstitutionRole.EMPLOYEE);
            employees.put(player, new InstitutionEmployee(player, role.id(), fallbackJoinedAt, 0, 0, "Migrierter Datensatz"));
        });
        InstitutionEmployee owner = employees.get(institution.ownerUuid());
        if (owner == null) owner = new InstitutionEmployee(institution.ownerUuid(), InstitutionRole.OWNER.id(), 0, 0, 0, "Migrierter Eigentümer");
        employees.put(institution.ownerUuid(), owner.withRole(InstitutionRole.OWNER));
        return build(institution.id(), institution.name(), institution.type(), institution.ownerUuid(), employees);
    }

    private static Institution build(Institution old, String owner, Map<String, InstitutionEmployee> employees) {
        return build(old.id(), old.name(), old.type(), owner, employees);
    }
    private static Institution build(String id, String name, String type, String owner, Map<String, InstitutionEmployee> employees) {
        Map<String, InstitutionEmployee> normalized = new HashMap<>();
        employees.forEach((player, employee) -> normalized.put(player,
                !player.equals(owner) && employee.institutionRole() == InstitutionRole.OWNER
                        ? employee.withRole(InstitutionRole.DIRECTOR) : employee));
        InstitutionEmployee ownerEmployee = normalized.get(owner);
        if (ownerEmployee != null) normalized.put(owner, ownerEmployee.withRole(InstitutionRole.OWNER));
        Map<String, List<String>> members = new HashMap<>();
        normalized.forEach((player, employee) -> members.put(player, List.of(employee.institutionRole().id())));
        return new Institution(id, name, type, owner, Map.copyOf(members), Map.copyOf(normalized));
    }
}
