package net.evarius.terranexus.landlord;

import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.evarius.terranexus.institution.InstitutionEmployee;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.item.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class LandTransferService {
    private LandTransferService() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            int pending = pendingFor(player).size();
            if (pending > 0) player.sendMessage(Text.literal("Du hast " + pending
                    + " offene Grundstücksübertragung(en). Nutze /uebertragungen oder deinen Grundbuchauszug.")
                    .formatted(Formatting.GOLD), false);
            int extracts = LandTransferState.get(server).takeExtracts(player.getUuidAsString());
            for (int index = 0; index < extracts; index++) player.giveItemStack(new ItemStack(ModItems.LAND_REGISTRY_EXTRACT));
            if (extracts > 0) player.sendMessage(Text.literal("Dir wurden " + extracts
                    + " neue(r) Grundbuchauszug/Grundbuchauszüge aus abgeschlossenen Umschreibungen zugestellt.")
                    .formatted(Formatting.GREEN), false);
        });
    }

    public record Result(boolean success, boolean completed, String message) {
        static Result fail(String message) { return new Result(false, false, message); }
        static Result pending(String message) { return new Result(true, false, message); }
        static Result completed(String message) { return new Result(true, true, message); }
    }

    public static boolean mayInitiate(ServerPlayerEntity player) {
        return LandPermissionService.mayTransferProperty(player);
    }

    public static Result request(ServerPlayerEntity actor, String propertyId, String targetType, String targetId) {
        if (!mayInitiate(actor)) return Result.fail(LandPermissionService.transferDenial(actor));
        MinecraftServer server = actor.getServer();
        LandProperty property = LandlordState.get(server).get(propertyId);
        if (property == null) return Result.fail("Das Grundstück existiert nicht mehr.");
        String validation = validateTarget(server, targetType, targetId);
        if (validation != null) return Result.fail(validation);
        if (property.ownerType().equals(targetType) && property.ownerId().equals(targetId))
            return Result.fail("Der ausgewählte Empfänger ist bereits Eigentümer.");
        LandManagementState management = LandManagementState.get(server);
        if (management.sale(propertyId) != null || management.lease(propertyId) != null)
            return Result.fail("Vor der Übertragung müssen bestehende Kauf- oder Mietangebote und Verträge beendet werden.");

        boolean higherRank = AuthorityState.mayAdministerLand(actor) || actor.hasPermissionLevel(2);
        boolean ownerApproved = higherRank
                || property.ownerType().equals("player") && property.ownerId().equals(actor.getUuidAsString())
                || property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && management.mayManageArea(property.ownerId(), actor);
        boolean recipientApproved = targetType.equals("institution")
                || targetType.equals("player") && targetId.equals(actor.getUuidAsString())
                || targetType.equals(LandManagementState.AREA_OWNER_TYPE) && isAreaApprover(actor, targetId);
        boolean institutionApproved = !property.ownerType().equals("institution")
                || isInstitutionApprover(actor, property.ownerId());

        LandTransferState state = LandTransferState.get(server);
        state.purgeExpired(oldestAllowedRequestTime());
        LandTransferRequest existing = state.forProperty(propertyId);
        if (existing != null && !existing.initiatorId().equals(actor.getUuidAsString()) && !higherRank)
            return Result.fail("Für dieses Grundstück läuft bereits eine Übertragung. Nur die anlegende Stelle oder die Bauamtsleitung darf sie ersetzen.");
        state.removeForProperty(propertyId);
        LandTransferRequest request = new LandTransferRequest(UUID.randomUUID().toString(), property.id(),
                property.ownerType(), property.ownerId(), targetType, targetId, actor.getUuidAsString(),
                ownerApproved, recipientApproved, institutionApproved, System.currentTimeMillis());
        state.put(request);
        notifyRequiredApprovers(server, property, request);
        return completeIfReady(server, actor.getUuid(), request);
    }

    public static Result approve(ServerPlayerEntity player, String requestId) {
        LandTransferState state = LandTransferState.get(player.getServer());
        state.purgeExpired(oldestAllowedRequestTime());
        LandTransferRequest request = state.get(requestId);
        if (request == null) return Result.fail("Diese Übertragungsanfrage ist abgelaufen oder wurde zurückgezogen.");
        LandProperty property = LandlordState.get(player.getServer()).get(request.propertyId());
        if (!matchesSnapshot(property, request)) {
            state.remove(request.id());
            return Result.fail("Der Grundbuchstand hat sich geändert; die Anfrage wurde aus Sicherheitsgründen verworfen.");
        }

        boolean owner = request.ownerApproved();
        boolean recipient = request.recipientApproved();
        boolean institution = request.institutionApproved();
        boolean authorized = false;
        if (request.oldOwnerType().equals("player") && request.oldOwnerId().equals(player.getUuidAsString())) {
            owner = true;
            authorized = true;
        }
        if (request.oldOwnerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && isAreaApprover(player, request.oldOwnerId())) {
            owner = true;
            authorized = true;
        }
        if (request.newOwnerType().equals("player") && request.newOwnerId().equals(player.getUuidAsString())) {
            recipient = true;
            authorized = true;
        }
        if (request.newOwnerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && isAreaApprover(player, request.newOwnerId())) {
            recipient = true;
            authorized = true;
        }
        if (request.oldOwnerType().equals("institution") && isInstitutionApprover(player, request.oldOwnerId())) {
            institution = true;
            authorized = true;
        }
        if (!authorized) return Result.fail("Du bist für keine ausstehende Zustimmung dieser Übertragung berechtigt.");
        LandTransferRequest approved = request.withApprovals(owner, recipient, institution);
        state.put(approved);
        return completeIfReady(player.getServer(), player.getUuid(), approved);
    }

    public static List<LandTransferRequest> pendingFor(ServerPlayerEntity player) {
        LandTransferState state = LandTransferState.get(player.getServer());
        state.purgeExpired(oldestAllowedRequestTime());
        return state.all().stream().filter(request ->
                        request.newOwnerType().equals("player") && request.newOwnerId().equals(player.getUuidAsString())
                                || request.oldOwnerType().equals("player") && request.oldOwnerId().equals(player.getUuidAsString())
                                || request.oldOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && isAreaApprover(player, request.oldOwnerId())
                                || request.oldOwnerType().equals("institution") && isInstitutionApprover(player, request.oldOwnerId())
                                || request.newOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && isAreaApprover(player, request.newOwnerId())
                                || request.initiatorId().equals(player.getUuidAsString()))
                .sorted(Comparator.comparingLong(LandTransferRequest::createdAt).reversed()).toList();
    }

    public static boolean canApprove(ServerPlayerEntity player, LandTransferRequest request) {
        return request.oldOwnerType().equals("player") && request.oldOwnerId().equals(player.getUuidAsString())
                || request.oldOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && isAreaApprover(player, request.oldOwnerId())
                || request.newOwnerType().equals("player") && request.newOwnerId().equals(player.getUuidAsString())
                || request.newOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && isAreaApprover(player, request.newOwnerId())
                || request.oldOwnerType().equals("institution") && isInstitutionApprover(player, request.oldOwnerId());
    }

    public static boolean applySaleTransfer(MinecraftServer server, UUID buyer, LandProperty expected, LandSaleOffer offer) {
        LandProperty current = LandlordState.get(server).get(expected.id());
        if (!expected.equals(current) || !IdentityState.get(server).isApproved(buyer)
                || current.ownerType().equals("institution")
                && !isInstitutionApprover(server, current.ownerId(), offer.approvedBy())) return false;
        return LandlordState.get(server).update(current.withOwner("player", buyer.toString()));
    }

    public static void finishSale(MinecraftServer server, UUID actor, LandProperty oldProperty, String newOwnerId) {
        recordCompleted(server, actor, oldProperty, "player", newOwnerId);
        issueExtract(server, actor.toString());
    }

    public static void cancelForProperty(MinecraftServer server, String propertyId) {
        LandTransferState.get(server).removeForProperty(propertyId);
    }

    private static Result completeIfReady(MinecraftServer server, UUID actor, LandTransferRequest request) {
        boolean sourceReady = request.oldOwnerType().equals("institution")
                ? request.institutionApproved() : request.ownerApproved();
        boolean recipientReady = request.newOwnerType().equals("institution") || request.recipientApproved();
        if (!ready(request)) {
            return Result.pending("Übertragung vorgemerkt. Fehlende Zustimmung: "
                    + missingApprovals(request, sourceReady, recipientReady) + ".");
        }
        LandProperty current = LandlordState.get(server).get(request.propertyId());
        if (!matchesSnapshot(current, request)) {
            LandTransferState.get(server).remove(request.id());
            return Result.fail("Der Grundbuchstand hat sich geändert; die Übertragung wurde nicht ausgeführt.");
        }
        String targetValidation = validateTarget(server, request.newOwnerType(), request.newOwnerId());
        if (targetValidation != null) {
            LandTransferState.get(server).remove(request.id());
            return Result.fail(targetValidation + " Die Übertragungsanfrage wurde verworfen.");
        }
        LandManagementState management = LandManagementState.get(server);
        if (management.sale(current.id()) != null || management.lease(current.id()) != null)
            return Result.fail("Die Übertragung ist blockiert, solange ein Kauf- oder Mietvorgang für das Grundstück besteht.");
        if (!LandlordState.get(server).update(current.withOwner(request.newOwnerType(), request.newOwnerId())))
            return Result.fail("Die Eigentumsänderung konnte nicht gespeichert werden.");
        LandTransferState.get(server).removeForProperty(current.id());
        recordCompleted(server, actor, current, request.newOwnerType(), request.newOwnerId());
        issueExtract(server, request.initiatorId());
        notifyCompletion(server, current, request);
        return Result.completed("Grundstück „" + current.name() + "“ wurde rechtswirksam übertragen.");
    }

    static boolean ready(LandTransferRequest request) {
        boolean sourceReady = request.oldOwnerType().equals("institution")
                ? request.institutionApproved() : request.ownerApproved();
        boolean recipientReady = request.newOwnerType().equals("institution") || request.recipientApproved();
        return sourceReady && recipientReady;
    }

    private static void recordCompleted(MinecraftServer server, UUID actor, LandProperty oldProperty,
                                        String newType, String newId) {
        LandAuditState.get(server).owner(actor, oldProperty, newType, newId);
        LandTransferState.get(server).removeForProperty(oldProperty.id());
    }

    private static String validateTarget(MinecraftServer server, String type, String id) {
        if (id == null || id.isBlank()) return "Der neue Eigentümer ist ungültig.";
        if (type.equals("institution"))
            return InstitutionState.get(server).get(id) == null ? "Die ausgewählte Institution existiert nicht mehr." : null;
        if (type.equals("player")) {
            try {
                return IdentityState.get(server).isApproved(UUID.fromString(id)) ? null
                        : "Der neue Eigentümer benötigt eine freigeschaltete Bürgerakte.";
            } catch (IllegalArgumentException ignored) {
                return "Die Bürger-ID des neuen Eigentümers ist ungültig.";
            }
        }
        if (type.equals(LandManagementState.AREA_OWNER_TYPE))
            return LandManagementState.get(server).area(id) == null ? "Die ausgewählte Verwaltungseinheit existiert nicht mehr." : null;
        return "Diese Eigentümerart wird nicht unterstützt.";
    }

    public static boolean isInstitutionApprover(ServerPlayerEntity player, String institutionId) {
        return isInstitutionApprover(player.getServer(), institutionId, player.getUuidAsString());
    }

    private static boolean isInstitutionApprover(MinecraftServer server, String institutionId, String playerId) {
        try {
            InstitutionEmployee employee = InstitutionState.get(server).employee(institutionId, UUID.fromString(playerId));
            return employee != null && employee.institutionRole().permits(InstitutionPermission.MANAGE_PROPERTY);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isAreaApprover(ServerPlayerEntity player, String areaId) {
        AdministrativeArea area = LandManagementState.get(player.getServer()).area(areaId);
        if (area == null) return false;
        if (area.ownerType().equals("player")) return area.ownerId().equals(player.getUuidAsString());
        return area.ownerType().equals("institution")
                && InstitutionState.get(player.getServer()).mayManage(area.ownerId(), player.getUuid());
    }

    private static boolean matchesSnapshot(LandProperty property, LandTransferRequest request) {
        return property != null && property.ownerType().equals(request.oldOwnerType())
                && property.ownerId().equals(request.oldOwnerId());
    }

    private static long oldestAllowedRequestTime() {
        return System.currentTimeMillis() - ConfigManager.claims().transferRequestLifetimeHours * 60L * 60L * 1000L;
    }

    private static String missingApprovals(LandTransferRequest request, boolean sourceReady, boolean recipientReady) {
        if (!sourceReady && !recipientReady) return request.oldOwnerType().equals("institution")
                ? "Institutionsleitung und neuer Eigentümer" : "aktueller und neuer Eigentümer";
        if (!sourceReady) return request.oldOwnerType().equals("institution")
                ? "Institutionsleitung oder berechtigte Führungskraft" : "aktueller Eigentümer";
        return "neuer Eigentümer";
    }

    private static void notifyRequiredApprovers(MinecraftServer server, LandProperty property, LandTransferRequest request) {
        if (request.oldOwnerType().equals("player") && !request.ownerApproved())
            notifyPlayer(server, request.oldOwnerId(), "Zustimmung zur Übertragung von „" + property.name()
                    + "“ erforderlich. Öffne deinen Grundbuchauszug.");
        if (request.newOwnerType().equals("player") && !request.recipientApproved())
            notifyPlayer(server, request.newOwnerId(), "Dir soll „" + property.name()
                    + "“ übertragen werden. Öffne deinen Grundbuchauszug und stimme zu.");
        if (request.oldOwnerType().equals("institution") && !request.institutionApproved()) {
            for (var employee : InstitutionState.get(server).employees(request.oldOwnerId()))
                if (employee.institutionRole().permits(InstitutionPermission.MANAGE_PROPERTY))
                    notifyPlayer(server, employee.playerUuid(), "Die Übertragung des Institutionsgrundstücks „"
                            + property.name() + "“ benötigt deine Zustimmung.");
        }
        if (request.oldOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && !request.ownerApproved())
            notifyAreaApprovers(server, request.oldOwnerId(), "Die Übertragung von „" + property.name()
                    + "“ aus deiner Verwaltung benötigt Zustimmung.");
        if (request.newOwnerType().equals(LandManagementState.AREA_OWNER_TYPE) && !request.recipientApproved())
            notifyAreaApprovers(server, request.newOwnerId(), "Das Grundstück „" + property.name()
                    + "“ soll deiner Verwaltung übertragen werden.");
    }

    private static void notifyCompletion(MinecraftServer server, LandProperty oldProperty, LandTransferRequest request) {
        if (oldProperty.ownerType().equals("player"))
            notifyPlayer(server, oldProperty.ownerId(), "Dein Eigentum an „" + oldProperty.name() + "“ wurde übertragen.");
        if (request.newOwnerType().equals("player"))
            notifyPlayer(server, request.newOwnerId(), "Du bist nun als Eigentümer von „" + oldProperty.name() + "“ eingetragen.");
    }

    private static void notifyAreaApprovers(MinecraftServer server, String areaId, String message) {
        AdministrativeArea area = LandManagementState.get(server).area(areaId);
        if (area == null) return;
        if (area.ownerType().equals("player")) {
            notifyPlayer(server, area.ownerId(), message);
            return;
        }
        if (!area.ownerType().equals("institution")) return;
        for (InstitutionEmployee employee : InstitutionState.get(server).employees(area.ownerId())) {
            var role = employee.institutionRole();
            if (role == net.evarius.terranexus.institution.InstitutionRole.OWNER
                    || role == net.evarius.terranexus.institution.InstitutionRole.DIRECTOR
                    || role == net.evarius.terranexus.institution.InstitutionRole.MANAGER)
                notifyPlayer(server, employee.playerUuid(), message);
        }
    }

    private static void notifyPlayer(MinecraftServer server, String playerId, String message) {
        try {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(UUID.fromString(playerId));
            if (player != null) player.sendMessage(Text.literal(message).formatted(Formatting.GOLD), false);
        } catch (IllegalArgumentException ignored) {}
    }

    private static void issueExtract(MinecraftServer server, String playerId) {
        try {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(UUID.fromString(playerId));
            if (player != null) {
                player.giveItemStack(new ItemStack(ModItems.LAND_REGISTRY_EXTRACT));
                player.sendMessage(Text.literal("Der neue Grundbuchauszug wurde dir als umschreibender Person ausgehändigt.")
                        .formatted(Formatting.GREEN), false);
            } else LandTransferState.get(server).queueExtract(playerId);
        } catch (IllegalArgumentException ignored) {}
    }
}
