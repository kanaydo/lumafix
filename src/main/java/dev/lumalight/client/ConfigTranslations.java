/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.minecraft.client.Minecraft
 *  net.minecraft.network.chat.Component
 */
package dev.lumalight.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumalight.LumaLight;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ConfigTranslations {
    private static final Map<String, String> ENGLISH = ConfigTranslations.load("en_us");
    private static final Map<String, String> RUSSIAN = ConfigTranslations.load("ru_ru");

    private ConfigTranslations() {
    }

    static Component text(String suffix) {
        String key = "lumalight.config." + suffix;
        Map<String, String> table = switch (ConfigTranslations.selectedLanguage()) {
            case "ru_ru" -> RUSSIAN;
            case "en_us" -> ENGLISH;
            default -> null;
        };
        if (table != null) {
            String value = table.get(key);
            if (value == null) {
                value = ENGLISH.get(key);
            }
            if (value != null) {
                return Component.literal((String)value);
            }
        }
        return Component.translatable((String)key);
    }

    private static String selectedLanguage() {
        String selected;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getLanguageManager() != null && (selected = minecraft.getLanguageManager().getSelected()) != null && !selected.isBlank()) {
            return selected.toLowerCase(Locale.ROOT);
        }
        return "en_us";
    }

    /*
     * Enabled aggressive exception aggregation
     */
    private static Map<String, String> load(String language) {
        String path = "/assets/lumalight/lang/" + language + ".json";
        try (InputStream input = ConfigTranslations.class.getResourceAsStream(path);){
            Map map;
            if (input == null) {
                LumaLight.LOGGER.warn("Missing bundled configuration language {}", (Object)path);
                Map<String, String> map2 = Map.of();
                return map2;
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);){
                JsonObject root = JsonParser.parseReader((Reader)reader).getAsJsonObject();
                HashMap<String, String> result = new HashMap<String, String>();
                for (Map.Entry entry : root.entrySet()) {
                    if (!((JsonElement)entry.getValue()).isJsonPrimitive() || !((JsonElement)entry.getValue()).getAsJsonPrimitive().isString()) continue;
                    result.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString());
                }
                map = Map.copyOf(result);
            }
            return map;
        }
        catch (IOException | RuntimeException exception) {
            LumaLight.LOGGER.warn("Could not load bundled configuration language {}", (Object)path, (Object)exception);
            return Map.of();
        }
    }
}

