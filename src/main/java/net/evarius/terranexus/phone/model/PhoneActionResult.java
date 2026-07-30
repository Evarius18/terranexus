package net.evarius.terranexus.phone.model;

public record PhoneActionResult(boolean successful, String notice) {
    public PhoneActionResult {
        notice = notice == null ? "" : notice;
    }

    public static PhoneActionResult accepted(String notice) {
        return new PhoneActionResult(true, notice);
    }

    public static PhoneActionResult rejected(String notice) {
        return new PhoneActionResult(false, notice);
    }
}
