package net.evarius.terranexus.phone.model;

import java.util.Locale;

public enum PhoneCallState {
    IDLE,
    INCOMING,
    RINGING,
    ACTIVE;

    public static PhoneCallState parse(String value) {
        try {
            return valueOf(value == null ? "IDLE" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return IDLE;
        }
    }
}
