package net.evarius.terranexus.phone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PhoneAppRegistry {
    private static final Map<String, PhoneApplication> APPLICATIONS = new LinkedHashMap<>();

    private PhoneAppRegistry() {}

    public static synchronized void register(PhoneApplication application) {
        if (application == null || application.id() == null || application.id().isBlank())
            throw new IllegalArgumentException("Telefon-App benötigt eine stabile ID");
        if (APPLICATIONS.putIfAbsent(application.id(), application) != null)
            throw new IllegalArgumentException("Telefon-App bereits registriert: " + application.id());
    }

    public static synchronized List<PhoneApplication> applications() {
        return List.copyOf(APPLICATIONS.values());
    }
}
