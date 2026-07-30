package net.evarius.terranexus.phone.model;

import java.util.Locale;

public enum PhoneAction {
    ALLOCATE_NUMBER,
    CALL,
    ANSWER,
    DECLINE,
    HANGUP,
    TOGGLE_SPEAKER,
    UPSERT_CONTACT,
    REMOVE_CONTACT,
    REMOVE_HISTORY_ENTRY,
    CLEAR_OWN_HISTORY,
    CLEAR_HISTORY,
    CLEAR_ALL_HISTORIES,
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
