package net.evarius.terranexus.landlord;

import com.mojang.serialization.JsonOps;

import java.util.List;

public final class LandPersistenceTest {
    private LandPersistenceTest() {}

    public static void run() {
        LandLease lease = new LandLease("property", "landlord", "tenant", 12_500L, 50_000L,
                30, 2_000_000L, 1, true, 12, 4, false,
                1_000_000L, 5_000_000L, "system:escrow");
        var encodedLease = LandLease.CODEC.encodeStart(JsonOps.INSTANCE, lease).getOrThrow();
        LandLease decodedLease = LandLease.CODEC.parse(JsonOps.INSTANCE, encodedLease).getOrThrow();
        require(lease.equals(decodedLease), "Mietvertrag verliert Felder beim Speichern und Laden");

        LandLease publicOffer = LandLease.offer("hotel-room", "hotel", "", 7_500L,
                15_000L, 1, 3, false);
        var encodedOffer = LandLease.CODEC.encodeStart(JsonOps.INSTANCE, publicOffer).getOrThrow();
        LandLease decodedOffer = LandLease.CODEC.parse(JsonOps.INSTANCE, encodedOffer).getOrThrow();
        require(decodedOffer.publicOffer(), "Öffentliches Zimmerangebot verliert den Self-Check-in-Status");
        LandLease claimed = decodedOffer.withTenant("guest");
        require(!claimed.publicOffer() && "guest".equals(claimed.tenantId()),
                "Öffentliches Zimmerangebot kann nicht atomar einem Gast zugewiesen werden");

        ContainerLock lock = new ContainerLock("minecraft:overworld", 123456L, "owner",
                List.of("trusted"), 42L);
        var encodedLock = ContainerLock.CODEC.encodeStart(JsonOps.INSTANCE, lock).getOrThrow();
        ContainerLock decodedLock = ContainerLock.CODEC.parse(JsonOps.INSTANCE, encodedLock).getOrThrow();
        require(lock.equals(decodedLock), "Containerberechtigung verliert Felder beim Speichern und Laden");
        require(lock.permits("owner") && lock.permits("trusted") && !lock.permits("stranger"),
                "Containerberechtigung wertet Eigentümer oder Freigaben falsch aus");
        require(lock.grant("second").permits("second") && !lock.revoke("trusted").permits("trusted"),
                "Erteilen oder Entziehen einer Containerberechtigung ist inkonsistent");

        ResidentialBuilding building = new ResidentialBuilding("building", "Haus am Markt", "owner", 123L);
        var encodedBuilding = ResidentialBuilding.CODEC.encodeStart(JsonOps.INSTANCE, building).getOrThrow();
        require(building.equals(ResidentialBuilding.CODEC.parse(JsonOps.INSTANCE, encodedBuilding).getOrThrow()),
                "Mehrfamilienhaus verliert Stammdaten beim Speichern und Laden");

        ResidentialUnit apartment = new ResidentialUnit("property-apartment", "building", "Wohnung 7", false,
                List.of("Haus A", "Flur 1", "Wohnung 7"));
        ResidentialUnit hallway = new ResidentialUnit("property-hallway", "building", "Treppenhaus", true);
        var encodedApartment = ResidentialUnit.CODEC.encodeStart(JsonOps.INSTANCE, apartment).getOrThrow();
        var encodedHallway = ResidentialUnit.CODEC.encodeStart(JsonOps.INSTANCE, hallway).getOrThrow();
        require(apartment.equals(ResidentialUnit.CODEC.parse(JsonOps.INSTANCE, encodedApartment).getOrThrow()),
                "Wohnungszuordnung verliert Daten beim Speichern und Laden");
        require("Haus A · Flur 1 · Wohnung 7".equals(apartment.pathLabel()),
                "Mehrstufige Wohnungsuntergliederung wird nicht stabil dargestellt");
        require(hallway.equals(ResidentialUnit.CODEC.parse(JsonOps.INSTANCE, encodedHallway).getOrThrow())
                        && "Gemeinschaftsbereich".equals(hallway.typeLabel()),
                "Gemeinschaftsbereich verliert seine getrennte Nutzungsart");
        var legacyUnit = encodedHallway.deepCopy();
        legacyUnit.getAsJsonObject().remove("path_segments");
        require("Treppenhaus".equals(
                        ResidentialUnit.CODEC.parse(JsonOps.INSTANCE, legacyUnit).getOrThrow().pathLabel()),
                "Bestehende flache Wohneinheiten werden nicht rückwärtskompatibel migriert");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
