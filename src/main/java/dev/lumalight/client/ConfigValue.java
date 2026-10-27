/*
 * Decompiled with CFR 0.152.
 */
package dev.lumalight.client;

import java.util.function.Function;

public final class ConfigValue<T> {
    private final String key;
    private final T defaultValue;
    private final Function<String, T> parser;
    private final Function<T, String> formatter;
    private T value;

    ConfigValue(String key, T defaultValue, Function<String, T> parser, Function<T, String> formatter) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.parser = parser;
        this.formatter = formatter;
        this.value = defaultValue;
    }

    public T get() {
        return this.value;
    }

    public void set(T value) {
        this.value = value;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    public void reset() {
        this.value = this.defaultValue;
    }

    String key() {
        return this.key;
    }

    void read(String raw) {
        if (raw == null) {
            this.value = this.defaultValue;
        } else {
            try {
                this.value = this.parser.apply(raw.trim());
            }
            catch (RuntimeException ignored) {
                this.value = this.defaultValue;
            }
        }
    }

    String write() {
        return this.formatter.apply(this.value);
    }
}

