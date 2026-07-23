package net.evarius.terranexus.client.gui;

public enum LayoutMode {
    LARGE,
    MEDIUM,
    COMPACT;

    public static LayoutMode detect(int guiWidth, int guiHeight) {
        if (guiWidth >= 700 && guiHeight >= 400) return LARGE;
        if (guiWidth >= 420 && guiHeight >= 240) return MEDIUM;
        return COMPACT;
    }
}
