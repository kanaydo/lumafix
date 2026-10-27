/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 */
package dev.lumalight.client;

import dev.lumalight.LumaLight;
import dev.lumalight.client.ConfigValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;

final class SimpleConfigFile {
    private final Path path;
    private final List<ConfigValue<?>> values = new ArrayList();

    SimpleConfigFile(String filename) {
        this.path = FabricLoader.getInstance().getConfigDir().resolve(filename);
    }

    ConfigValue<Boolean> bool(String key, boolean value) {
        return this.add(key, value, Boolean::parseBoolean, Object::toString);
    }

    ConfigValue<Integer> integer(String key, int value, int min, int max) {
        return this.add(key, value, raw -> Math.max(min, Math.min(max, Integer.parseInt(raw))), Object::toString);
    }

    ConfigValue<Double> decimal(String key, double value, double min, double max) {
        return this.add(key, value, raw -> Math.max(min, Math.min(max, Double.parseDouble(raw))), Object::toString);
    }

    private <T> ConfigValue<T> add(String key, T value, Function<String, T> parser, Function<T, String> formatter) {
        ConfigValue<T> result = new ConfigValue<T>(key, value, parser, formatter);
        this.values.add(result);
        return result;
    }

    void load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(this.path, new LinkOption[0])) {
            try (InputStream input = Files.newInputStream(this.path, new OpenOption[0]);){
                properties.load(input);
            }
            catch (IOException exception) {
                LumaLight.LOGGER.warn("Could not read config {}", (Object)this.path, (Object)exception);
            }
        }
        this.values.forEach(value -> value.read(properties.getProperty(value.key())));
        this.save();
    }

    void save() {
        Properties properties = new Properties();
        this.values.forEach(value -> properties.setProperty(value.key(), value.write()));
        try {
            Files.createDirectories(this.path.getParent(), new FileAttribute[0]);
            try (OutputStream output = Files.newOutputStream(this.path, new OpenOption[0]);){
                properties.store(output, "LumaLight client configuration");
            }
        }
        catch (IOException exception) {
            LumaLight.LOGGER.warn("Could not save config {}", (Object)this.path, (Object)exception);
        }
    }

    void reset() {
        this.values.forEach(ConfigValue::reset);
    }
}

