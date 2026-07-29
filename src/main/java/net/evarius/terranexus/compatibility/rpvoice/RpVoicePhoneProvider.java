package net.evarius.terranexus.compatibility.rpvoice;

import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.phone.model.EmergencyNumber;
import net.evarius.terranexus.phone.model.PhoneAction;
import net.evarius.terranexus.phone.model.PhoneCallState;
import net.evarius.terranexus.phone.model.PhoneContact;
import net.evarius.terranexus.phone.model.PhoneSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reflection-only bridge. Its construction is guarded by FabricLoader before this class is loaded,
 * therefore TerraNexus has no linkage-time dependency on RP Voice Additions.
 */
public final class RpVoicePhoneProvider implements PhoneFeatureProvider {
    private static final String SERVICES_CLASS = "com.evarius.rpvca.RpVoiceServices";
    private final Method servicesGet;
    private final Method servicesPhones;
    private final Method servicesConfigs;
    private volatile boolean healthy = true;

    private RpVoicePhoneProvider(Method servicesGet, Method servicesPhones, Method servicesConfigs) {
        this.servicesGet = servicesGet;
        this.servicesPhones = servicesPhones;
        this.servicesConfigs = servicesConfigs;
    }

    static PhoneFeatureProvider create() {
        try {
            Class<?> services = Class.forName(SERVICES_CLASS, false, RpVoicePhoneProvider.class.getClassLoader());
            PhoneFeatureProvider result = new RpVoicePhoneProvider(
                    services.getMethod("get"), services.getMethod("phones"), services.getMethod("configs"));
            TerraNexus.LOGGER.info("Optionale RP-Voice-Telefonintegration aktiviert");
            return result;
        } catch (ReflectiveOperationException | LinkageError exception) {
            TerraNexus.LOGGER.error("RP Voice Additions wurde erkannt, besitzt aber keine kompatible öffentliche Telefon-API", exception);
            return new NoopPhoneProvider();
        }
    }

    @Override public boolean installed() { return true; }
    @Override public boolean healthy() { return healthy; }

    @Override
    public PhoneSnapshot snapshot(ServerPlayerEntity player) {
        if (!healthy) return PhoneSnapshot.unavailable();
        try {
            Object services = servicesGet.invoke(null);
            if (services == null) return PhoneSnapshot.unavailable();
            Object phones = servicesPhones.invoke(services);
            Object view = phones.getClass().getMethod("clientView", ServerPlayerEntity.class).invoke(phones, player);
            Class<?> type = view.getClass();
            @SuppressWarnings("unchecked")
            Map<Object, Object> contactMap = (Map<Object, Object>) type.getMethod("contacts").invoke(view);
            List<PhoneContact> contacts = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : contactMap.entrySet()) {
                if (contacts.size() >= 256) break;
                contacts.add(new PhoneContact(limit(entry.getKey(), 80), limit(entry.getValue(), 64)));
            }
            contacts.sort(Comparator.comparing(PhoneContact::name, String.CASE_INSENSITIVE_ORDER));
            return new PhoneSnapshot(true,
                    PhoneCallState.parse(string(type.getMethod("state").invoke(view))),
                    limit(type.getMethod("peer").invoke(view), 80),
                    limit(type.getMethod("number").invoke(view), 64),
                    (boolean) type.getMethod("speaker").invoke(view),
                    (boolean) type.getMethod("coverage").invoke(view),
                    limit(type.getMethod("notice").invoke(view), 160),
                    contacts, emergencyNumbers(services));
        } catch (InvocationTargetException exception) {
            TerraNexus.LOGGER.warn("RP-Voice-Telefonstatus konnte vorübergehend nicht gelesen werden", exception.getCause());
            return PhoneSnapshot.unavailable();
        } catch (ReflectiveOperationException | LinkageError exception) {
            fail(exception);
            return PhoneSnapshot.unavailable();
        }
    }

    @Override
    public boolean execute(ServerPlayerEntity player, PhoneAction action, String value) {
        if (!healthy || action == null) return false;
        try {
            Object services = servicesGet.invoke(null);
            if (services == null) return false;
            Object phones = servicesPhones.invoke(services);
            String methodName = switch (action) {
                case CALL -> "call";
                case ANSWER -> "answer";
                case DECLINE -> "decline";
                case HANGUP -> "hangup";
                case TOGGLE_SPEAKER -> "toggleSpeaker";
                default -> null;
            };
            if (methodName == null) return false;
            Method method = action == PhoneAction.CALL
                    ? phones.getClass().getMethod(methodName, ServerPlayerEntity.class, String.class)
                    : phones.getClass().getMethod(methodName, ServerPlayerEntity.class);
            Object result = action == PhoneAction.CALL
                    ? method.invoke(phones, player, value)
                    : method.invoke(phones, player);
            // toggleSpeaker returns the resulting state, not an operation-success flag.
            return action == PhoneAction.TOGGLE_SPEAKER || Boolean.TRUE.equals(result);
        } catch (InvocationTargetException exception) {
            TerraNexus.LOGGER.warn("RP-Voice-Telefonaktion {} wurde abgelehnt", action, exception.getCause());
            return false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    private List<EmergencyNumber> emergencyNumbers(Object services) throws ReflectiveOperationException {
        Object configs = servicesConfigs.invoke(services);
        Object emergency = configs.getClass().getMethod("emergency").invoke(configs);
        Field enabled = emergency.getClass().getField("enabled");
        if (!enabled.getBoolean(emergency)) return List.of();
        Field numbers = emergency.getClass().getField("numbers");
        if (!(numbers.get(emergency) instanceof Iterable<?> entries)) return List.of();
        List<EmergencyNumber> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry == null || result.size() >= 64) continue;
            String number = limit(entry.getClass().getField("number").get(entry), 64);
            String label = limit(entry.getClass().getField("displayName").get(entry), 80);
            if (!number.isBlank()) result.add(new EmergencyNumber(label, number));
        }
        return List.copyOf(result);
    }

    private void fail(Throwable exception) {
        healthy = false;
        TerraNexus.LOGGER.error("RP-Voice-Telefonintegration wurde nach einem API-Fehler sicher deaktiviert", exception);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String limit(Object value, int maximum) {
        String text = string(value).trim();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
