package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.config.AdministrationConfig;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LandManagementState extends PersistentState {
    public static final String ROOT_AREA_ID = "terranexus:wilderness";
    public static final String AREA_OWNER_TYPE = "administrative_area";
    public static final String SYSTEM_OWNER_TYPE = "system";

    private static final Codec<LandManagementState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, LandAccess.CODEC).optionalFieldOf("access", Map.of()).forGetter(state -> state.access),
            Codec.unboundedMap(Codec.STRING, LandSaleOffer.CODEC).optionalFieldOf("sales", Map.of()).forGetter(state -> state.sales),
            Codec.unboundedMap(Codec.STRING, LandLease.CODEC).optionalFieldOf("leases", Map.of()).forGetter(state -> state.leases),
            Codec.unboundedMap(Codec.STRING, AdministrativeArea.CODEC).optionalFieldOf("areas", Map.of()).forGetter(state -> state.areas),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("property_areas", Map.of()).forGetter(state -> state.propertyAreas),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("addresses", Map.of()).forGetter(state -> state.addresses),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("property_uses", Map.of()).forGetter(state -> state.propertyUses),
            Codec.unboundedMap(Codec.STRING, AreaEmployment.CODEC).optionalFieldOf("area_employees", Map.of()).forGetter(state -> state.areaEmployees)
    ).apply(instance, LandManagementState::new));
    private static final PersistentStateType<LandManagementState> TYPE = new PersistentStateType<>(
            "terranexus_land_management", LandManagementState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, LandAccess> access;
    private final Map<String, LandSaleOffer> sales;
    private final Map<String, LandLease> leases;
    private final Map<String, AdministrativeArea> areas;
    private final Map<String, String> propertyAreas;
    private final Map<String, String> addresses;
    private final Map<String, String> propertyUses;
    private final Map<String, AreaEmployment> areaEmployees;
    private List<AdministrativeArea> areaCache;
    private final Map<String, List<AdministrativeArea>> childrenCache = new HashMap<>();
    private boolean runtimeInitialized;

    public LandManagementState() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private LandManagementState(Map<String, LandAccess> access, Map<String, LandSaleOffer> sales,
                                Map<String, LandLease> leases, Map<String, AdministrativeArea> areas,
                                Map<String, String> propertyAreas, Map<String, String> addresses,
                                Map<String, String> propertyUses, Map<String, AreaEmployment> areaEmployees) {
        this.access = new HashMap<>(access);
        this.sales = new HashMap<>(sales);
        this.leases = new HashMap<>(leases);
        this.areas = new HashMap<>(areas);
        this.propertyAreas = new HashMap<>(propertyAreas);
        this.addresses = new HashMap<>(addresses);
        this.propertyUses = new HashMap<>(propertyUses);
        this.areaEmployees = new HashMap<>(areaEmployees);
        migrateHierarchy();
    }

    public static LandManagementState get(MinecraftServer server) {
        LandManagementState state = server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
        if (!state.runtimeInitialized) {
            LandlordState lands = LandlordState.get(server);
            state.ensurePropertyAssignments(lands.all());
            lands.normalizeOwnerships(state);
            state.runtimeInitialized = true;
        }
        return state;
    }

    public void refreshConfiguredHierarchy() {
        migrateHierarchy();
        runtimeInitialized = false;
    }

    public AdministrativeArea rootArea() { return areas.get(ROOT_AREA_ID); }
    public AdministrativeArea area(String id) { return areas.get(id); }

    public List<AdministrativeArea> areas() {
        if (areaCache == null) {
            areaCache = areas.values().stream().sorted(Comparator
                    .comparing((AdministrativeArea area) -> !ROOT_AREA_ID.equals(area.id()))
                    .thenComparing(AdministrativeArea::level, Comparator.reverseOrder())
                    .thenComparing(AdministrativeArea::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
        return areaCache;
    }

    public List<AdministrativeArea> children(String parentId) {
        return childrenCache.computeIfAbsent(parentId, id -> areas.values().stream()
                .filter(area -> area.parentId().equals(id))
                .sorted(Comparator.comparing(AdministrativeArea::name, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    public List<AdministrativeArea> areasAtLevel(int level) {
        return areas().stream().filter(area -> area.level() == level && !ROOT_AREA_ID.equals(area.id())).toList();
    }

    public String levelName(int level) {
        List<String> levels = ConfigManager.administration().hierarchyLevels;
        return level >= 0 && level < levels.size() ? levels.get(level) : ConfigManager.administration().wildernessLevelName;
    }

    public AdministrativeArea createArea(String name, int level, String parentId, String ownerType, String ownerId) {
        AdministrationConfig config = ConfigManager.administration();
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank() || normalizedName.length() > config.maximumAreaNameLength
                || areas.size() - 1 >= config.maximumAdministrativeAreas || level < 0
                || level >= config.hierarchyLevels.size() || ownerType == null || ownerType.isBlank()
                || ownerId == null || ownerId.isBlank()) return null;

        String normalizedParent = parentId == null || parentId.isBlank() ? ROOT_AREA_ID : parentId;
        AdministrativeArea parent = areas.get(normalizedParent);
        int topLevel = config.hierarchyLevels.size() - 1;
        boolean validParent = level == topLevel
                ? ROOT_AREA_ID.equals(normalizedParent)
                : parent != null && parent.level() == level + 1;
        if (!validParent || areas.values().stream().anyMatch(area -> area.level() == level
                && area.parentId().equals(normalizedParent) && area.name().equalsIgnoreCase(normalizedName))) return null;

        String id = UUID.randomUUID().toString();
        AdministrativeArea area = new AdministrativeArea(id, normalizedName, levelName(level), normalizedParent,
                ownerType, ownerId, level);
        areas.put(id, area);
        invalidateAreas();
        markDirty();
        return area;
    }

    public boolean setAreaOwner(MinecraftServer server, String areaId, String ownerType, String ownerId) {
        AdministrativeArea area = areas.get(areaId);
        if (area == null || ROOT_AREA_ID.equals(areaId) || ownerId == null || ownerId.isBlank()
                || !("player".equals(ownerType) || "institution".equals(ownerType))) return false;
        if ("institution".equals(ownerType) && InstitutionState.get(server).get(ownerId) == null) return false;
        if ("player".equals(ownerType)) {
            try {
                if (!IdentityState.get(server).isApproved(UUID.fromString(ownerId))) return false;
            } catch (IllegalArgumentException ignored) { return false; }
        }
        areas.put(areaId, area.withOwner(ownerType, ownerId));
        invalidateAreas();
        markDirty();
        return true;
    }

    /** Compatibility entry point for older callers and data-oriented integrations. */
    public AdministrativeArea createArea(String name, String type, String parent, String ownerType, String ownerId) {
        return createArea(name, configuredLevel(type), parent, ownerType, ownerId);
    }

    public boolean assignArea(String propertyId, String areaId) {
        String target = areaId == null || areaId.isBlank() ? ROOT_AREA_ID : areaId;
        if (!areas.containsKey(target)) return false;
        if (target.equals(propertyAreas.put(propertyId, target))) return true;
        markDirty();
        return true;
    }

    public String propertyArea(String propertyId) {
        String assigned = propertyAreas.get(propertyId);
        return assigned != null && areas.containsKey(assigned) ? assigned : ROOT_AREA_ID;
    }

    public AdministrativeArea jurisdiction(LandProperty property) {
        return property == null ? rootArea() : areas.getOrDefault(propertyArea(property.id()), rootArea());
    }

    public LandResolution resolve(LandProperty property) {
        AdministrativeArea jurisdiction = jurisdiction(property);
        if (property == null) return new LandResolution(null, jurisdiction, AREA_OWNER_TYPE, ROOT_AREA_ID,
                ConfigManager.administration().wildernessName);
        return new LandResolution(property, jurisdiction, property.ownerType(), property.ownerId(), landUse(property.id()));
    }

    public boolean mayManageArea(String areaId, ServerPlayerEntity player) {
        if (AuthorityState.mayAdministerLand(player)) return true;
        AdministrativeArea area = areas.get(areaId);
        if (area == null || SYSTEM_OWNER_TYPE.equals(area.ownerType())) return false;
        if ("player".equals(area.ownerType())) return area.ownerId().equals(player.getUuidAsString());
        return "institution".equals(area.ownerType())
                && InstitutionState.get(player.getServer()).mayManage(area.ownerId(), player.getUuid());
    }

    public boolean mayManageAreaFinances(String areaId, ServerPlayerEntity player) {
        if (AuthorityState.isTnAdmin(player) || player.hasPermissionLevel(2)) return true;
        AdministrativeArea area = areas.get(areaId);
        if (area == null || SYSTEM_OWNER_TYPE.equals(area.ownerType())) return false;
        if ("player".equals(area.ownerType())) return area.ownerId().equals(player.getUuidAsString());
        return "institution".equals(area.ownerType())
                && InstitutionAccess.has(player, area.ownerId(), InstitutionPermission.MANAGE_FINANCES);
    }

    public boolean wildernessPermits(String permission) {
        AdministrationConfig config = ConfigManager.administration();
        return switch (permission) {
            case LandAccess.INTERACT -> config.wildernessPublicInteractionAllowed;
            case LandAccess.CONTAINERS -> config.wildernessPublicContainerAccess;
            case LandAccess.REDSTONE -> config.wildernessPublicRedstoneAccess;
            case LandAccess.BUILD -> config.wildernessPublicBuildingAllowed;
            default -> false;
        };
    }

    public String landUse(String propertyId) {
        String use = propertyUses.get(propertyId);
        return use != null && ConfigManager.administration().landUseTypes.contains(use)
                ? use : ConfigManager.administration().privateLandUse;
    }

    public boolean isPublicLandUse(String propertyId) {
        return ConfigManager.administration().publicLandUseTypes.contains(landUse(propertyId));
    }

    public boolean setLandUse(String propertyId, String use) {
        AdministrationConfig config = ConfigManager.administration();
        if (use == null || !config.landUseTypes.contains(use)) return false;
        propertyUses.put(propertyId, use);
        LandAccess old = access(propertyId);
        Map<String, Boolean> rules = new HashMap<>(old.publicRules());
        boolean publicUse = config.publicLandUseTypes.contains(use);
        rules.put(LandAccess.INTERACT, publicUse && config.defaultPublicInteractionAllowed);
        rules.put(LandAccess.CONTAINERS, publicUse && config.defaultPublicContainerAccess);
        rules.put(LandAccess.REDSTONE, publicUse && config.defaultPublicRedstoneAccess);
        rules.put(LandAccess.BUILD, publicUse && config.defaultPublicBuildingAllowed);
        access.put(propertyId, new LandAccess(propertyId, old.grants(), rules));
        markDirty();
        return true;
    }

    public LandAccess access(String propertyId) {
        return access.getOrDefault(propertyId, new LandAccess(propertyId, Map.of(), Map.of()));
    }
    public void setAccess(LandAccess value) { access.put(value.propertyId(), value); markDirty(); }
    public LandSaleOffer sale(String propertyId) { return sales.get(propertyId); }
    public Collection<LandSaleOffer> sales() { return List.copyOf(sales.values()); }
    public void offerSale(LandSaleOffer offer) { sales.put(offer.propertyId(), offer); markDirty(); }
    public void cancelSale(String propertyId) { if (sales.remove(propertyId) != null) markDirty(); }
    public LandLease lease(String propertyId) { return leases.get(propertyId); }
    public Collection<LandLease> leases() { return List.copyOf(leases.values()); }
    public void setLease(LandLease lease) { leases.put(lease.propertyId(), lease); markDirty(); }
    public void endLease(String propertyId) { if (leases.remove(propertyId) != null) markDirty(); }
    public boolean isTenant(String propertyId, UUID player) {
        LandLease lease = leases.get(propertyId);
        return lease != null && lease.active() && lease.tenantId().equals(player.toString());
    }
    public String address(String propertyId) { return addresses.getOrDefault(propertyId, "Nicht eingetragen"); }
    public void setAddress(String propertyId, String address) {
        if (address.isBlank()) addresses.remove(propertyId); else addresses.put(propertyId, address);
        markDirty();
    }

    public void removePropertyData(String propertyId) {
        access.remove(propertyId);
        sales.remove(propertyId);
        leases.remove(propertyId);
        propertyAreas.remove(propertyId);
        addresses.remove(propertyId);
        propertyUses.remove(propertyId);
        markDirty();
    }

    public static long periodMillis(int days) {
        return (long) Math.max(1, days) * ConfigManager.claims().rentDayDurationMinutes * 60_000L;
    }

    public void processRents(MinecraftServer server, long now) {
        LandTradeService.processRents(server, now);
    }

    public List<AreaEmployment> areaEmployees(String areaId) {
        return areaEmployees.values().stream().filter(employee -> employee.areaId().equals(areaId))
                .sorted(Comparator.comparing(AreaEmployment::joinedAt)).toList();
    }

    public AreaEmployment areaEmployee(String areaId, UUID playerId) {
        return areaEmployees.get(areaEmployeeKey(areaId, playerId.toString()));
    }

    public boolean hireAreaEmployee(ServerPlayerEntity actor, String areaId, UUID playerId, String salaryGroup) {
        if (!mayManageAreaFinances(areaId, actor) || ROOT_AREA_ID.equals(areaId) || !areas.containsKey(areaId)
                || !ConfigManager.salary().administrationSalaryGroups.containsKey(salaryGroup)
                || areaEmployees(areaId).size() >= ConfigManager.salary().maximumAdministrationEmployees
                || !IdentityState.get(actor.getServer()).isApproved(playerId)) return false;
        String key = areaEmployeeKey(areaId, playerId.toString());
        if (areaEmployees.containsKey(key)) return false;
        long now = System.currentTimeMillis();
        areaEmployees.put(key, new AreaEmployment(areaId, playerId.toString(), salaryGroup,
                ConfigManager.salary().administrationSalaryGroups.get(salaryGroup), now, now + salaryInterval()));
        markDirty();
        return true;
    }

    public boolean setAreaSalaryGroup(ServerPlayerEntity actor, String areaId, UUID playerId, String salaryGroup) {
        AreaEmployment current = areaEmployee(areaId, playerId);
        Long salary = ConfigManager.salary().administrationSalaryGroups.get(salaryGroup);
        if (current == null || salary == null || !mayManageAreaFinances(areaId, actor)) return false;
        areaEmployees.put(areaEmployeeKey(areaId, playerId.toString()), current.withSalaryGroup(salaryGroup, salary));
        markDirty();
        return true;
    }

    public boolean dismissAreaEmployee(ServerPlayerEntity actor, String areaId, UUID playerId) {
        if (!mayManageAreaFinances(areaId, actor)) return false;
        boolean removed = areaEmployees.remove(areaEmployeeKey(areaId, playerId.toString())) != null;
        if (removed) markDirty();
        return removed;
    }

    public void processAreaPayroll(MinecraftServer server, long now) {
        if (!ConfigManager.salary().automaticPaymentsEnabled) return;
        EconomyState economy = EconomyState.get(server);
        for (AreaEmployment snapshot : new ArrayList<>(areaEmployees.values())) {
            if (snapshot.salary() <= 0 || snapshot.nextPayAt() > now) continue;
            AdministrativeArea area = areas.get(snapshot.areaId());
            UUID employeeId;
            try { employeeId = UUID.fromString(snapshot.playerUuid()); }
            catch (IllegalArgumentException ignored) { areaEmployees.remove(areaEmployeeKey(snapshot.areaId(), snapshot.playerUuid())); markDirty(); continue; }
            if (area == null) { areaEmployees.remove(areaEmployeeKey(snapshot.areaId(), snapshot.playerUuid())); markDirty(); continue; }
            long next = now + salaryInterval();
            boolean paid = economy.transferConditional(EconomyState.areaAccount(area.id()), EconomyState.playerAccount(employeeId),
                    snapshot.salary(), "Verwaltungsgehalt · " + area.name(), "SYSTEM", "area:" + area.id(), "ADMIN_SALARY", () -> {
                        String key = areaEmployeeKey(snapshot.areaId(), snapshot.playerUuid());
                        if (!snapshot.equals(areaEmployees.get(key))) return false;
                        areaEmployees.put(key, snapshot.withNextPayAt(next)); markDirty(); return true;
                    });
            if (!paid) {
                String key = areaEmployeeKey(snapshot.areaId(), snapshot.playerUuid());
                if (snapshot.equals(areaEmployees.get(key))) { areaEmployees.put(key, snapshot.withNextPayAt(next)); markDirty(); }
            }
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(employeeId);
            if (online != null && ConfigManager.salary().notifyEmployees)
                online.sendMessage(net.minecraft.text.Text.literal(paid
                        ? "Gehalt von " + area.name() + ": " + EconomyState.format(snapshot.salary())
                        : "Gehaltszahlung von " + area.name() + " ist mangels Deckung fehlgeschlagen.")
                        .formatted(paid ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.RED), false);
        }
    }

    private static String areaEmployeeKey(String areaId, String playerId) { return areaId + '|' + playerId; }
    private static long salaryInterval() { return (long) ConfigManager.salary().paymentIntervalMinutes * 60_000L; }

    private void migrateHierarchy() {
        AdministrationConfig config = ConfigManager.administration();
        boolean changed = false;
        AdministrativeArea desiredRoot = new AdministrativeArea(ROOT_AREA_ID, config.wildernessName,
                config.wildernessLevelName, "", SYSTEM_OWNER_TYPE, ROOT_AREA_ID, config.hierarchyLevels.size());
        if (!desiredRoot.equals(areas.get(ROOT_AREA_ID))) { areas.put(ROOT_AREA_ID, desiredRoot); changed = true; }

        for (AdministrativeArea old : new ArrayList<>(areas.values())) {
            if (ROOT_AREA_ID.equals(old.id())) continue;
            int level = old.level() >= 0 && old.level() < config.hierarchyLevels.size()
                    ? old.level() : configuredLevel(old.type());
            if (level < 0) level = inferLegacyLevel(old.type(), config.hierarchyLevels.size());
            level = Math.max(0, Math.min(level, config.hierarchyLevels.size() - 1));
            String parentId = old.parentId();
            if (parentId.isBlank() || parentId.equals(old.id()) || !areas.containsKey(parentId)) parentId = ROOT_AREA_ID;
            AdministrativeArea migrated = old.withHierarchy(levelName(level), parentId, level);
            if (!migrated.equals(old)) { areas.put(old.id(), migrated); changed = true; }
        }
        // Validate parents only after every legacy area has received its stable numeric level.
        for (AdministrativeArea area : new ArrayList<>(areas.values())) {
            if (ROOT_AREA_ID.equals(area.id()) || ROOT_AREA_ID.equals(area.parentId())) continue;
            AdministrativeArea parent = areas.get(area.parentId());
            if (parent == null || parent.level() <= area.level()) {
                areas.put(area.id(), area.withHierarchy(levelName(area.level()), ROOT_AREA_ID, area.level()));
                changed = true;
            }
        }
        if (breakCycles()) changed = true;
        if (changed) { invalidateAreas(); markDirty(); }
    }

    private boolean breakCycles() {
        boolean changed = false;
        for (AdministrativeArea area : new ArrayList<>(areas.values())) {
            if (ROOT_AREA_ID.equals(area.id())) continue;
            Set<String> visited = new HashSet<>();
            String current = area.id();
            boolean cycle = false;
            while (!ROOT_AREA_ID.equals(current)) {
                if (!visited.add(current)) { cycle = true; break; }
                AdministrativeArea node = areas.get(current);
                if (node == null || node.parentId().isBlank()) break;
                current = node.parentId();
            }
            if (cycle) {
                areas.put(area.id(), area.withHierarchy(levelName(area.level()), ROOT_AREA_ID, area.level()));
                changed = true;
            }
        }
        return changed;
    }

    private void ensurePropertyAssignments(Collection<LandProperty> properties) {
        boolean changed = false;
        Set<String> currentIds = new HashSet<>();
        for (LandProperty property : properties) {
            currentIds.add(property.id());
            String assigned = propertyAreas.get(property.id());
            if (assigned == null || !areas.containsKey(assigned)) {
                propertyAreas.put(property.id(), ROOT_AREA_ID);
                changed = true;
            }
            String use = propertyUses.get(property.id());
            if (use == null || !ConfigManager.administration().landUseTypes.contains(use)) {
                propertyUses.put(property.id(), ConfigManager.administration().privateLandUse);
                changed = true;
            }
        }
        if (propertyAreas.keySet().removeIf(id -> !currentIds.contains(id))) changed = true;
        if (propertyUses.keySet().removeIf(id -> !currentIds.contains(id))) changed = true;
        if (changed) markDirty();
    }

    private int configuredLevel(String type) {
        if (type == null) return -1;
        List<String> levels = ConfigManager.administration().hierarchyLevels;
        for (int index = 0; index < levels.size(); index++)
            if (levels.get(index).equalsIgnoreCase(type.trim())) return index;
        return -1;
    }

    private static int inferLegacyLevel(String type, int levelCount) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        int legacy = switch (normalized) {
            case "stadtteil", "ort", "stadt", "ort / stadt" -> 0;
            case "gemeinde" -> 1;
            case "landkreis" -> 2;
            case "region" -> 3;
            case "bundesland" -> 4;
            case "staat" -> 5;
            default -> 0;
        };
        return Math.min(legacy, Math.max(0, levelCount - 1));
    }

    private void invalidateAreas() {
        areaCache = null;
        childrenCache.clear();
    }
}
