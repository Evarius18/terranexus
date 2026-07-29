package net.evarius.terranexus.phone;

import com.mojang.serialization.JsonOps;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;

public final class PhoneModelPersistenceTest {
    private PhoneModelPersistenceTest() {}

    public static void run() {
        PhoneHistoryEntry entry = new PhoneHistoryEntry(
                1_725_000_000_000L, "Max Mustermann", "TN-12345",
                "INCOMING", "ANSWERED", 95L);
        var encoded = PhoneHistoryEntry.CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow();
        PhoneHistoryEntry decoded = PhoneHistoryEntry.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        if (!entry.equals(decoded))
            throw new AssertionError("Telefonhistorie verliert Daten beim Speichern und Laden");
    }
}
