package net.evarius.terranexus.phone;

import com.google.gson.Gson;
import net.evarius.terranexus.phone.model.PhoneHistoryEntry;

public final class PhoneModelPersistenceTest {
    private PhoneModelPersistenceTest() {}

    public static void run() {
        PhoneHistoryEntry entry = new PhoneHistoryEntry(
                "8c39b92f-08f3-48b6-b760-e7ad30e4cf91",
                1_725_000_000_000L, "Max Mustermann", "TN-12345",
                "INCOMING", "COMPLETED", 95L);
        Gson gson = new Gson();
        PhoneHistoryEntry decoded = gson.fromJson(gson.toJson(entry), PhoneHistoryEntry.class);
        if (!entry.equals(decoded))
            throw new AssertionError("PhoneApi-Historienansicht verliert Daten beim Netzwerktransport");
    }
}
