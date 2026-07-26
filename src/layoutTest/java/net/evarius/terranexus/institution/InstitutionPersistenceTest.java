package net.evarius.terranexus.institution;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

public final class InstitutionPersistenceTest {
    private InstitutionPersistenceTest() {}

    public static void run() {
        InstitutionEmployee employee = new InstitutionEmployee("player", InstitutionRole.ACCOUNTANT.id(),
                1_000L, 50_000L, "Vollzeit", 2_000L, "Personalvermerk");
        var encoded = InstitutionEmployee.CODEC.encodeStart(JsonOps.INSTANCE, employee).getOrThrow();
        InstitutionEmployee decoded = InstitutionEmployee.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        require(employee.equals(decoded), "Gehaltsgruppe oder Personaldaten gehen beim Speichern verloren");

        var legacy = JsonParser.parseString("""
                {
                  "player_uuid":"legacy",
                  "role":"employee",
                  "joined_at":1000,
                  "salary":15000,
                  "next_pay_at":2000,
                  "personnel_note":"Altbestand"
                }
                """);
        InstitutionEmployee migrated = InstitutionEmployee.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        require("Individuell".equals(migrated.salaryGroup()) && migrated.salary() == 15_000L,
                "Bestehende Mitarbeiterdaten werden nicht rückwärtskompatibel migriert");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
