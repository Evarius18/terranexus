package net.evarius.terranexus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.evarius.terranexus.TerraNexus;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Object LOCK = new Object();
    private static Path directory;
    private static GeneralConfig general = new GeneralConfig();
    private static EconomyConfig economy = new EconomyConfig();
    private static BankConfig bank = new BankConfig();
    private static InstitutionConfig institutions = new InstitutionConfig();
    private static SalaryConfig salary = new SalaryConfig();
    private static ClaimsConfig claims = new ClaimsConfig();
    private static AdministrationConfig administration = new AdministrationConfig();
    private static ShopConfig shops = new ShopConfig();
    private static ImmigrationConfig immigration = new ImmigrationConfig();
    private static DesktopConfig desktop = new DesktopConfig();
    private static PerformanceConfig performance = new PerformanceConfig();
    private static LoggingConfig logging = new LoggingConfig();

    private ConfigManager() {}

    public static void load() {
        synchronized (LOCK) {
            directory = FabricLoader.getInstance().getConfigDir().resolve("TerraNexus");
            try {
                Files.createDirectories(directory);
                LegacyValues legacy = readLegacy();
                EconomyConfig economyDefaults = new EconomyConfig();
                SalaryConfig salaryDefaults = new SalaryConfig();
                ClaimsConfig claimsDefaults = new ClaimsConfig();
                if (legacy != null) {
                    economyDefaults.currencyName = legacy.currencyName;
                    economyDefaults.currencySymbol = legacy.currencySymbol;
                    economyDefaults.currencyDecimals = legacy.currencyDecimals;
                    salaryDefaults.paymentIntervalMinutes = legacy.salaryPeriodMinutes;
                    claimsDefaults.rentDayDurationMinutes = legacy.rentDayDurationMinutes;
                }
                general = read("general.json", new GeneralConfig(), GeneralConfig.class, GeneralConfig::validate);
                economy = read("economy.json", economyDefaults, EconomyConfig.class, EconomyConfig::validate);
                bank = read("bank.json", new BankConfig(), BankConfig.class, BankConfig::validate);
                institutions = read("institutions.json", new InstitutionConfig(), InstitutionConfig.class, InstitutionConfig::validate);
                salary = read("salary.json", salaryDefaults, SalaryConfig.class, SalaryConfig::validate);
                claims = read("claims.json", claimsDefaults, ClaimsConfig.class, ClaimsConfig::validate);
                administration = read("administration.json", new AdministrationConfig(), AdministrationConfig.class, AdministrationConfig::validate);
                shops = read("shops.json", new ShopConfig(), ShopConfig.class, ShopConfig::validate);
                immigration = read("immigration.json", new ImmigrationConfig(), ImmigrationConfig.class, ImmigrationConfig::validate);
                desktop = read("desktop.json", new DesktopConfig(), DesktopConfig.class, DesktopConfig::validate);
                performance = read("performance.json", new PerformanceConfig(), PerformanceConfig.class, PerformanceConfig::validate);
                logging = read("logging.json", new LoggingConfig(), LoggingConfig.class, LoggingConfig::validate);
                if (logging.logConfigLoading) TerraNexus.LOGGER.info("TerraNexus-Konfiguration aus {} geladen", directory);
            } catch (IOException | RuntimeException exception) {
                TerraNexus.LOGGER.error("TerraNexus-Konfiguration konnte nicht vollständig geladen werden; sichere Standardwerte bleiben aktiv", exception);
            }
        }
    }

    public static void reload() { load(); }
    public static Path directory() { return directory; }
    public static GeneralConfig general() { return general; }
    public static EconomyConfig economy() { return economy; }
    public static BankConfig bank() { return bank; }
    public static InstitutionConfig institutions() { return institutions; }
    public static SalaryConfig salary() { return salary; }
    public static ClaimsConfig claims() { return claims; }
    public static AdministrationConfig administration() { return administration; }
    public static ShopConfig shops() { return shops; }
    public static ImmigrationConfig immigration() { return immigration; }
    public static DesktopConfig desktop() { return desktop; }
    public static PerformanceConfig performance() { return performance; }
    public static LoggingConfig logging() { return logging; }

    private static <T> T read(String fileName, T defaults, Class<T> type, Consumer<T> validator) throws IOException {
        Path path = directory.resolve(fileName);
        JsonObject merged = GSON.toJsonTree(defaults).getAsJsonObject();
        JsonObject existingFile = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    existingFile = parsed.getAsJsonObject();
                    merged = merge(merged, existingFile);
                }
                else TerraNexus.LOGGER.warn("{} enthält kein JSON-Objekt und wurde mit Standardwerten repariert", path);
            } catch (RuntimeException exception) {
                backupInvalid(path);
                TerraNexus.LOGGER.error("Ungültige Konfiguration {} wurde gesichert und neu erstellt", path, exception);
            }
        }
        T value;
        try {
            value = GSON.fromJson(merged, type);
            if (value == null) value = defaults;
            validator.accept(value);
        } catch (RuntimeException exception) {
            backupInvalid(path);
            TerraNexus.LOGGER.error("Konfiguration {} enthält ungültige Werttypen und wurde mit Standardwerten repariert", path, exception);
            value = defaults;
            validator.accept(value);
            merged = GSON.toJsonTree(defaults).getAsJsonObject();
            existingFile = null;
        }
        JsonObject validated = GSON.toJsonTree(value).getAsJsonObject();
        JsonObject output = merge(merged, validated);
        if (existingFile == null || !existingFile.equals(output)) writeAtomic(path, output);
        return value;
    }

    private static JsonObject merge(JsonObject defaults, JsonObject overrides) {
        JsonObject result = defaults.deepCopy();
        for (var entry : overrides.entrySet()) {
            JsonElement existing = result.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject())
                result.add(entry.getKey(), merge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject()));
            else result.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return result;
    }

    private static void writeAtomic(Path path, JsonObject data) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(data, writer); }
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void backupInvalid(Path path) {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis());
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            TerraNexus.LOGGER.error("Fehlerhafte Konfiguration {} konnte nicht gesichert werden", path, backupFailure);
        }
    }

    private static LegacyValues readLegacy() {
        Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve("terranexus.json");
        if (!Files.exists(legacyPath) || Files.exists(directory.resolve("economy.json"))
                && Files.exists(directory.resolve("salary.json")) && Files.exists(directory.resolve("claims.json"))) return null;
        try (Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            JsonObject value = JsonParser.parseReader(reader).getAsJsonObject();
            EconomyConfig economyDefaults = new EconomyConfig();
            SalaryConfig salaryDefaults = new SalaryConfig();
            String name = string(value, "currencyName", economyDefaults.currencyName);
            String symbol = string(value, "currencySymbol", economyDefaults.currencySymbol);
            int decimals = integer(value, "currencyDecimals", economyDefaults.currencyDecimals);
            int rentMinutes = integer(value, "rentDayDurationMinutes", 1_440);
            int salaryMinutes = integer(value, "salaryPeriodMinutes", salaryDefaults.paymentIntervalMinutes);
            TerraNexus.LOGGER.info("Werte aus der bisherigen config/terranexus.json werden als Startwerte übernommen");
            return new LegacyValues(name, symbol, decimals, salaryMinutes, rentMinutes);
        } catch (Exception exception) {
            TerraNexus.LOGGER.warn("Bisherige config/terranexus.json konnte nicht migriert werden", exception);
            return null;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(value, maximum)); }
    static long clamp(long value, long minimum, long maximum) { return Math.max(minimum, Math.min(value, maximum)); }
    static String text(String value, String fallback, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) normalized = fallback;
        return normalized.length() > maximumLength ? normalized.substring(0, maximumLength) : normalized;
    }
    static List<String> uniqueText(List<String> values, int maximumEntries, int maximumLength) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = text(value, "", maximumLength);
            if (!normalized.isBlank()) result.add(normalized);
            if (result.size() >= maximumEntries) break;
        }
        return new ArrayList<>(result);
    }

    private record LegacyValues(String currencyName, String currencySymbol, int currencyDecimals,
                                int salaryPeriodMinutes, int rentDayDurationMinutes) {}
}
