package net.evarius.terranexus.identity;

import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.institution.InstitutionState;
import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandAuditState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandTransferState;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.phone.messenger.MessengerState;
import net.evarius.terranexus.shop.ShopState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/** One server-authoritative transaction boundary for both official departure paths. */
public final class CitizenDepartureService {
    public enum Mode { OFFICIAL_DEPARTURE, SUPPORT_REMOVAL }
    public record Result(boolean success, String message) {}
    private CitizenDepartureService() {}

    public static synchronized Result process(ServerPlayerEntity actor, UUID citizenId, Mode mode, String reason) {
        boolean permitted = mode == Mode.OFFICIAL_DEPARTURE
                ? AuthorityState.mayProcessOfficialDeparture(actor)
                : AuthorityState.mayDeleteCitizenAsSupport(actor);
        if (!permitted) return new Result(false, mode == Mode.OFFICIAL_DEPARTURE
                ? "Keine Berechtigung für amtliche Ausreisen." : "Keine Support-Berechtigung zum Löschen von Bürgerakten.");
        if (actor.getUuid().equals(citizenId) && !AuthorityState.isAdministrator(actor))
            return new Result(false, "Die eigene Bürgerakte kann nicht auf diesem Weg entfernt werden.");
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < 3 || cleanReason.length() > 160)
            return new Result(false, "Bitte einen Grund mit 3 bis 160 Zeichen angeben.");

        MinecraftServer server = actor.getServer();
        IdentityState identities = IdentityState.get(server);
        CitizenIdentity identity = identities.get(citizenId);
        if (identity == null) return new Result(false, "Die Bürgerakte existiert nicht mehr.");
        InstitutionState institutions = InstitutionState.get(server);
        if (institutions.ownsInstitution(citizenId))
            return new Result(false, "Vor der Ausreise muss das Eigentum an allen Institutionen übertragen werden.");
        LandManagementState management = LandManagementState.get(server);
        if (management.hasCitizenBlockingLinks(citizenId))
            return new Result(false, "Vor der Ausreise müssen aktive Mietverträge und eigene Verwaltungsebenen beendet werden.");
        EconomyState economy = EconomyState.get(server);
        if (!economy.canClosePlayerAccountOnDeparture(citizenId))
            return new Result(false, "Das Bankkonto kann wegen eines Zahlenüberlaufs nicht geschlossen werden.");

        LandlordState lands = LandlordState.get(server);
        List<LandProperty> owned = List.copyOf(lands.owned(citizenId));
        for (LandProperty property : owned)
            LandAuditState.get(server).owner(actor.getUuid(), property, LandManagementState.AREA_OWNER_TYPE,
                    LandManagementState.ROOT_AREA_ID);
        int employments = institutions.removeEmployment(server, citizenId);
        management.removeCitizenReferences(citizenId, owned.stream().map(LandProperty::id).toList());
        int properties = lands.releasePlayerOwnership(citizenId);
        LandTransferState.get(server).removeForCitizen(citizenId.toString());
        int shops = ShopState.get(server).removeOwnedBy("player", citizenId.toString());
        MessengerState.get(server).removeCitizen(citizenId);
        long closedBalance = economy.closePlayerAccountOnDeparture(citizenId, actor.getUuid());
        AuthorityState.get(server).clear(citizenId);
        identities.remove(citizenId);
        CitizenDepartureAuditState.get(server).append(new CitizenDepartureRecord(System.currentTimeMillis(),
                actor.getUuidAsString(), citizenId.toString(), identity.citizenNumber(),
                identity.firstName() + " " + identity.lastName(), mode.name(), cleanReason,
                closedBalance, properties, employments, shops));
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(citizenId);
        if (online != null) {
            RoleplayNames.apply(online);
            online.sendMessage(net.minecraft.text.Text.literal(mode == Mode.OFFICIAL_DEPARTURE
                    ? "Deine Ausreise wurde amtlich abgeschlossen." : "Deine Bürgerakte wurde durch den Support entfernt."), false);
        }
        return new Result(true, "Bürgerakte wurde kontrolliert entfernt und revisionssicher protokolliert.");
    }
}
