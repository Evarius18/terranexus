package net.evarius.terranexus.landlord;

public final class LandGeometryBoundaryTest {
    private LandGeometryBoundaryTest() {}

    public static void run() {
        int[] x = {10, 14, 14, 10};
        int[] z = {20, 20, 24, 24};
        require(LandGeometry.containsMarkedBlock(x, z, 4, 10, 22), "Westgrenze fehlt");
        require(LandGeometry.containsMarkedBlock(x, z, 4, 14, 22), "Ostgrenze fehlt");
        require(LandGeometry.containsMarkedBlock(x, z, 4, 12, 20), "Nordgrenze fehlt");
        require(LandGeometry.containsMarkedBlock(x, z, 4, 12, 24), "Südgrenze fehlt");
        require(LandGeometry.containsMarkedBlock(x, z, 4, 12, 22), "Innenfläche fehlt");
        require(!LandGeometry.containsMarkedBlock(x, z, 4, 15, 22), "Block östlich der Grenze wurde eingeschlossen");
        require(!LandGeometry.containsMarkedBlock(x, z, 4, 12, 25), "Block südlich der Grenze wurde eingeschlossen");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
