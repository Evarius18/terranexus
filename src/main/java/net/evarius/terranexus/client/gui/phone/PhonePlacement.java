package net.evarius.terranexus.client.gui.phone;

import net.evarius.terranexus.client.gui.GuiBounds;

/** Computes one shared, resolution-independent placement for every TerraNexus handheld screen. */
public final class PhonePlacement {
    public static final int DESIGN_WIDTH = 236;
    public static final int DESIGN_HEIGHT = 420;
    public static final int EDGE_MARGIN = 10;
    private static final int MIN_MARGIN = 6;
    private static final int MIN_WIDTH = 176;
    private static final int MIN_HEIGHT = 312;
    private static final float MAX_SCALE = 1.08F;

    private PhonePlacement() {}

    public static GuiBounds calculate(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(1, screenWidth - EDGE_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - EDGE_MARGIN * 2);
        float scale = Math.min(MAX_SCALE, Math.min(
                availableWidth / (float) DESIGN_WIDTH,
                availableHeight / (float) DESIGN_HEIGHT));
        int deviceWidth = Math.min(availableWidth, Math.max(MIN_WIDTH, Math.round(DESIGN_WIDTH * scale)));
        int deviceHeight = Math.min(availableHeight, Math.max(MIN_HEIGHT, Math.round(DESIGN_HEIGHT * scale)));
        int left = Math.max(MIN_MARGIN, screenWidth - deviceWidth - EDGE_MARGIN);
        int top = Math.max(MIN_MARGIN, screenHeight - deviceHeight - EDGE_MARGIN);
        return new GuiBounds(left, top, deviceWidth, deviceHeight);
    }
}
