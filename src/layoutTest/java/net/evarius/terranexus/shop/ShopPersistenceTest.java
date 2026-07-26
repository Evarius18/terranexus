package net.evarius.terranexus.shop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

public final class ShopPersistenceTest {
    private ShopPersistenceTest() {}

    public static void run() {
        ShopRecord shop = new ShopRecord("id", "minecraft:overworld", 12L, 13L,
                "minecraft:stone", 250L, 100L, false, true,
                "player", "owner", "account", 42L);
        var encoded = ShopRecord.CODEC.encodeStart(JsonOps.INSTANCE, shop).getOrThrow();
        ShopRecord decoded = ShopRecord.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        require(shop.equals(decoded), "Shop verliert Handelsrichtungen beim Speichern und Laden");
        require(!decoded.sellsToPlayers() && decoded.buysFromPlayers(),
                "Shop wertet getrennte An-/Verkaufsschalter falsch aus");

        var legacy = JsonParser.parseString("""
                {
                  "id":"legacy",
                  "dimension":"minecraft:overworld",
                  "sign_position":12,
                  "container_position":13,
                  "item_id":"minecraft:stone",
                  "buy_price":250,
                  "sell_price":100,
                  "owner_type":"player",
                  "owner_id":"owner",
                  "account":"account",
                  "created_at":42
                }
                """);
        ShopRecord migrated = ShopRecord.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        require(migrated.salesEnabled() && migrated.purchasesEnabled(),
                "Bestehende Shops werden nicht rückwärtskompatibel aktiviert");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
