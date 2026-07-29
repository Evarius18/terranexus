package net.evarius.terranexus.phone.model;

import java.util.Locale;

public enum PhoneAction {
    CALL,
    ANSWER,
    DECLINE,
    HANGUP,
    TOGGLE_SPEAKER,
    RETURN_TO_APPS,
    CLOSE;

    public static PhoneAction parse(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
