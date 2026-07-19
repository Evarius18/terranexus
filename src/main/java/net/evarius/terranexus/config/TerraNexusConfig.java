package net.evarius.terranexus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.evarius.terranexus.TerraNexus;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerraNexusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TerraNexusConfig INSTANCE = new TerraNexusConfig();

    public String currencyName = "TerraNexus Euro";
    public String currencySymbol = "TN€";
    public int currencyDecimals = 2;
    public int rentDayDurationMinutes = 1440;

    public static TerraNexusConfig get() {
        return INSTANCE;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("terranexus.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    TerraNexusConfig loaded = GSON.fromJson(reader, TerraNexusConfig.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            }
            INSTANCE.validate();
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException exception) {
            TerraNexus.LOGGER.error("Could not load TerraNexus configuration", exception);
        }
    }

    private void validate() {
        if (currencyName == null || currencyName.isBlank()) currencyName = "TerraNexus Euro";
        if (currencySymbol == null || currencySymbol.isBlank()) currencySymbol = "TN€";
        currencyDecimals = Math.max(0, Math.min(currencyDecimals, 2));
        rentDayDurationMinutes = Math.max(1, rentDayDurationMinutes);
    }
}
