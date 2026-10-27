/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 */
package dev.lumalight.client;

import dev.lumalight.LumaLight;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

final class ShaderPackCompatibility {
    private static boolean initialized;
    private static boolean irisLoaded;
    private static Method getInstance;
    private static Method isShaderPackInUse;
    private static boolean warned;

    private ShaderPackCompatibility() {
    }

    static boolean isShaderPackActive() {
        ShaderPackCompatibility.initialize();
        if (!irisLoaded) {
            return false;
        }
        if (getInstance != null && isShaderPackInUse != null) {
            try {
                return Boolean.TRUE.equals(isShaderPackInUse.invoke(getInstance.invoke(null, new Object[0]), new Object[0]));
            }
            catch (ReflectiveOperationException | RuntimeException exception) {
                if (!warned) {
                    warned = true;
                    LumaLight.LOGGER.warn("Iris is active but its API could not be queried; LumaLight post effects were disabled safely.");
                }
                return true;
            }
        }
        return true;
    }

    private static void initialize() {
        if (!initialized) {
            initialized = true;
            irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
            if (irisLoaded) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShaderPackCompatibility.class.getClassLoader());
                    getInstance = api.getMethod("getInstance", new Class[0]);
                    isShaderPackInUse = api.getMethod("isShaderPackInUse", new Class[0]);
                }
                catch (ClassNotFoundException | NoSuchMethodException reflectiveOperationException) {
                    // empty catch block
                }
            }
        }
    }
}

