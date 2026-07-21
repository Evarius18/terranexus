package net.evarius.terranexus.landlord;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.institution.InstitutionAccess;
import net.evarius.terranexus.institution.InstitutionPermission;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.UUID;

public final class LandTradeService {
    private LandTradeService() {}

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    public static boolean mayManageCommercially(ServerPlayerEntity player, LandProperty property) {
        if (property == null) return false;
        if (player.hasPermissionLevel(2) || AuthorityState.mayAdministerLand(player) || property.isOwnedBy(player.getUuid())) return true;
        if (property.ownerType().equals("institution"))
            return InstitutionAccess.has(player, property.ownerId(), InstitutionPermission.MANAGE_PROPERTY);
        return property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)
                && LandManagementState.get(player.getServer()).mayManageAreaFinances(property.ownerId(), player);
    }

    public static String ownerAccount(LandProperty property) {
        if (property.ownerType().equals("institution")) return EconomyState.institutionAccount(property.ownerId());
        if (property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE)) return EconomyState.areaAccount(property.ownerId());
        return property.ownerId();
    }

    public static Result offerSale(ServerPlayerEntity actor, String propertyId, long price) {
        LandlordState lands = LandlordState.get(actor.getServer());
        LandManagementState management = LandManagementState.get(actor.getServer());
        LandProperty property = lands.get(propertyId);
        LandLease lease = management.lease(propertyId);
        if (!mayManageCommercially(actor, property)) return Result.fail("Keine Verkaufsberechtigung.");
        if (price <= 0 || price > ConfigManager.economy().maximumTransferAmount) return Result.fail("Ungültiger Kaufpreis.");
        if (lease != null && lease.active()) return Result.fail("Ein aktiv vermietetes Grundstück kann nicht verkauft werden.");
        management.offerSale(new LandSaleOffer(propertyId, ownerAccount(property), price, System.currentTimeMillis()));
        return Result.ok("Grundstück wurde zum Verkauf angeboten.");
    }

    public static Result cancelSale(ServerPlayerEntity actor, String propertyId) {
        LandProperty property = LandlordState.get(actor.getServer()).get(propertyId);
        if (!mayManageCommercially(actor, property)) return Result.fail("Keine Verkaufsberechtigung.");
        LandManagementState.get(actor.getServer()).cancelSale(propertyId);
        return Result.ok("Kaufangebot wurde entfernt.");
    }

    public static Result buy(ServerPlayerEntity buyer, String propertyId) {
        MinecraftServer server = buyer.getServer();
        LandlordState lands = LandlordState.get(server);
        LandManagementState management = LandManagementState.get(server);
        LandProperty property = lands.get(propertyId);
        LandSaleOffer offer = management.sale(propertyId);
        if (property == null || offer == null) return Result.fail("Dieses Kaufangebot existiert nicht mehr.");
        if (property.isOwnedBy(buyer.getUuid())) return Result.fail("Du besitzt dieses Grundstück bereits.");
        if (IdentityState.get(server).get(buyer.getUuid()) == null || !IdentityState.get(server).isApproved(buyer.getUuid()))
            return Result.fail("Für den Erwerb ist eine freigeschaltete Bürgerakte erforderlich.");
        if (!offer.sellerAccount().equals(ownerAccount(property))) return Result.fail("Der eingetragene Verkäufer hat sich geändert.");
        LandLease lease = management.lease(propertyId);
        if (lease != null && lease.active()) return Result.fail("Das Grundstück ist derzeit vermietet.");

        EconomyState economy = EconomyState.get(server);
        boolean paid = economy.transferConditional(EconomyState.playerAccount(buyer.getUuid()), offer.sellerAccount(), offer.price(),
                "Grundstückskauf · " + property.name(), buyer.getUuidAsString(), scopeId(property), "PROPERTY_SALE", () -> {
                    LandProperty latest = lands.get(propertyId);
                    LandSaleOffer latestOffer = management.sale(propertyId);
                    if (latest == null || latestOffer == null || !latest.equals(property)
                            || !latestOffer.equals(offer) || !lands.update(latest.withOwner("player", buyer.getUuidAsString()))) return false;
                    management.cancelSale(propertyId);
                    if (lease != null && !lease.active()) management.endLease(propertyId);
                    return true;
                });
        if (!paid) return Result.fail("Kauf abgelehnt: Kontodeckung, Kontostatus oder Grundbuchstand prüfen.");
        LandAuditState.get(server).owner(buyer.getUuid(), property, "player", buyer.getUuidAsString());
        LandAuditState.get(server).log(buyer.getUuid(), "SALE", property, EconomyState.format(offer.price()));
        notifyPlayer(server, property, "Dein Grundstück „" + property.name() + "“ wurde verkauft.");
        return Result.ok("Du hast „" + property.name() + "“ für " + EconomyState.format(offer.price()) + " gekauft.");
    }

    public static Result offerLease(ServerPlayerEntity actor, String propertyId, UUID tenantId, long rent,
                                    long deposit, int periodDays, int termPayments, boolean autoRenew) {
        LandProperty property = LandlordState.get(actor.getServer()).get(propertyId);
        if (!mayManageCommercially(actor, property)) return Result.fail("Keine Vermietungsberechtigung.");
        if (rent <= 0 || deposit < 0 || rent > ConfigManager.economy().maximumTransferAmount
                || deposit > ConfigManager.economy().maximumTransferAmount || periodDays <= 0
                || termPayments < 0 || termPayments > 10_000 || tenantId.equals(actor.getUuid()))
            return Result.fail("Ungültige Vertragswerte.");
        if (!IdentityState.get(actor.getServer()).isApproved(tenantId)) return Result.fail("Der Mieter besitzt keine freigeschaltete Bürgerakte.");
        LandManagementState management = LandManagementState.get(actor.getServer());
        if (management.lease(propertyId) != null) return Result.fail("Für dieses Grundstück besteht bereits ein Mietangebot oder Vertrag.");
        management.setLease(LandLease.offer(propertyId, ownerAccount(property), tenantId.toString(), rent, deposit,
                periodDays, termPayments, autoRenew));
        return Result.ok("Mietangebot wurde erstellt.");
    }

    public static Result acceptLease(ServerPlayerEntity tenant, String propertyId) {
        LandManagementState management = LandManagementState.get(tenant.getServer());
        LandLease lease = management.lease(propertyId);
        LandProperty property = LandlordState.get(tenant.getServer()).get(propertyId);
        if (property == null || lease == null || lease.active() || !lease.tenantId().equals(tenant.getUuidAsString()))
            return Result.fail("Dieses Mietangebot ist nicht mehr gültig.");
        if (!lease.landlordAccount().equals(ownerAccount(property))) return Result.fail("Der Vermieter hat sich geändert.");
        String escrow = leaseEscrowAccount(propertyId);
        LandLease activated = lease.activate(System.currentTimeMillis(), escrow);
        if (lease.deposit() == 0) {
            management.setLease(activated);
            return Result.ok("Mietvertrag wurde aktiviert.");
        }
        boolean paid = EconomyState.get(tenant.getServer()).transferConditional(
                EconomyState.playerAccount(tenant.getUuid()), escrow, lease.deposit(),
                "Mietkaution · " + property.name(), tenant.getUuidAsString(), scopeId(property), "LEASE_DEPOSIT",
                () -> management.lease(propertyId) != null && management.lease(propertyId).equals(lease)
                        && setLeaseAndReturn(management, activated));
        return paid ? Result.ok("Mietvertrag wurde aktiviert; die Kaution liegt auf einem Treuhandkonto.")
                : Result.fail("Die Kaution konnte nicht hinterlegt werden.");
    }

    public static Result terminateLease(ServerPlayerEntity actor, String propertyId) {
        LandManagementState management = LandManagementState.get(actor.getServer());
        LandLease lease = management.lease(propertyId);
        LandProperty property = LandlordState.get(actor.getServer()).get(propertyId);
        if (lease == null) return Result.fail("Kein Mietverhältnis vorhanden.");
        boolean tenant = lease.tenantId().equals(actor.getUuidAsString());
        if (!tenant && !mayManageCommercially(actor, property)) return Result.fail("Keine Kündigungsberechtigung.");
        return finishLease(actor.getServer(), property, lease, actor.getUuidAsString(), "LEASE_TERMINATION");
    }

    public static void processRents(MinecraftServer server, long now) {
        LandManagementState management = LandManagementState.get(server);
        EconomyState economy = EconomyState.get(server);
        for (LandLease snapshot : new ArrayList<>(management.leases())) {
            if (!snapshot.active() || snapshot.nextDueAt() > now) continue;
            LandProperty property = LandlordState.get(server).get(snapshot.propertyId());
            LandLease current = management.lease(snapshot.propertyId());
            if (current == null || !current.equals(snapshot)) continue;
            if (property == null || !snapshot.landlordAccount().equals(ownerAccount(property))) {
                finishLease(server, property, snapshot, "SYSTEM", "LEASE_INVALIDATED");
                continue;
            }
            UUID tenantId;
            try { tenantId = UUID.fromString(snapshot.tenantId()); }
            catch (IllegalArgumentException ignored) { management.endLease(snapshot.propertyId()); continue; }
            boolean paid = economy.transferConditional(EconomyState.playerAccount(tenantId), snapshot.landlordAccount(),
                    snapshot.rent(), "Grundstücksmiete · " + property.name(), "SYSTEM", scopeId(property), "RENT", () -> {
                        if (!snapshot.equals(management.lease(snapshot.propertyId()))) return false;
                        management.setLease(snapshot.afterSuccessfulPayment(now));
                        return true;
                    });
            if (paid) {
                notify(server, tenantId, "Miete für „" + property.name() + "“: " + EconomyState.format(snapshot.rent()), Formatting.GREEN);
                if (snapshot.termCompletedAfterNextPayment())
                    finishLease(server, property, management.lease(snapshot.propertyId()), "SYSTEM", "LEASE_EXPIRED");
            } else {
                LandLease failed = snapshot.afterFailedPayment(now);
                management.setLease(failed);
                notify(server, tenantId, "Mietzahlung für „" + property.name() + "“ fehlgeschlagen.", Formatting.RED);
                if (failed.missedPayments() >= ConfigManager.claims().maximumMissedRentPayments)
                    finishLease(server, property, failed, "SYSTEM", "LEASE_DEFAULTED");
            }
        }
    }

    private static Result finishLease(MinecraftServer server, LandProperty property, LandLease lease,
                                      String actor, String type) {
        if (lease == null) return Result.fail("Kein Mietverhältnis vorhanden.");
        LandManagementState management = LandManagementState.get(server);
        UUID tenant;
        try { tenant = UUID.fromString(lease.tenantId()); }
        catch (IllegalArgumentException ignored) { management.endLease(lease.propertyId()); return Result.ok("Ungültiger Altvertrag wurde beendet."); }
        if (lease.deposit() <= 0) {
            management.endLease(lease.propertyId());
            notify(server, tenant, "Mietverhältnis für „" + propertyName(property) + "“ wurde beendet.", Formatting.YELLOW);
            return Result.ok("Mietverhältnis wurde beendet.");
        }
        String source = lease.depositAccount().isBlank() ? lease.landlordAccount() : lease.depositAccount();
        boolean refunded = EconomyState.get(server).transferConditional(source, EconomyState.playerAccount(tenant), lease.deposit(),
                "Kautionsrückzahlung · " + propertyName(property), actor, property == null ? "" : scopeId(property), type,
                () -> lease.equals(management.lease(lease.propertyId())) && removeLeaseAndReturn(management, lease.propertyId()));
        if (!refunded) return Result.fail("Mietvertrag kann nicht beendet werden, weil die Kaution nicht vollständig verfügbar ist.");
        notify(server, tenant, "Mietverhältnis beendet; Kaution zurückgezahlt: " + EconomyState.format(lease.deposit()), Formatting.YELLOW);
        return Result.ok("Mietverhältnis beendet und Kaution zurückgezahlt.");
    }

    private static boolean setLeaseAndReturn(LandManagementState management, LandLease lease) { management.setLease(lease); return true; }
    private static boolean removeLeaseAndReturn(LandManagementState management, String id) { management.endLease(id); return true; }
    private static String leaseEscrowAccount(String propertyId) { return "system:lease_escrow:" + propertyId; }
    private static String propertyName(LandProperty property) { return property == null ? "gelöschte Fläche" : property.name(); }
    private static String scopeId(LandProperty property) {
        return property.ownerType().equals("institution") ? property.ownerId()
                : property.ownerType().equals(LandManagementState.AREA_OWNER_TYPE) ? "area:" + property.ownerId() : "";
    }
    private static void notifyPlayer(MinecraftServer server, LandProperty oldProperty, String message) {
        if (!oldProperty.ownerType().equals("player")) return;
        try { notify(server, UUID.fromString(oldProperty.ownerId()), message, Formatting.GOLD); }
        catch (IllegalArgumentException ignored) {}
    }
    private static void notify(MinecraftServer server, UUID playerId, String message, Formatting formatting) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) player.sendMessage(Text.literal(message).formatted(formatting), false);
    }
}
