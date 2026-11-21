package dev.nyx.loader.launch;

import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.AccessWidener;
import dev.nyx.loader.fabric.EnvironmentStripper;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class NyxLaunch {

    private static final Map<String, Object> PROPERTIES = new HashMap<>();

    private static NyxClassLoader classLoader;
    private static TinyMappings mappings;
    private static AccessWidener accessWidener = new AccessWidener();
    private static volatile IMixinTransformer mixinTransformer;
    private static volatile boolean mixinReady;

    private NyxLaunch() {
    }

    public static ClassLoader gameClassLoader() {
        return classLoader;
    }

    public static NyxClassLoader nyxClassLoader() {
        return classLoader;
    }

    static void setClassLoader(NyxClassLoader loader) {
        classLoader = loader;
    }

    public static TinyMappings mappings() {
        return mappings;
    }

    static void setMappings(TinyMappings value) {
        mappings = value;
    }

    public static AccessWidener accessWidener() {
        return accessWidener;
    }

    public static Map<String, Object> properties() {
        return PROPERTIES;
    }

    public static void setMixinTransformer(IMixinTransformer transformer) {
        mixinTransformer = transformer;
    }

    static void setMixinReady() {
        mixinReady = true;
    }

    public static boolean isMixinReady() {
        return mixinReady;
    }

    public static byte[] getClassBytes(String name, boolean runTransformers) throws java.io.IOException {
        byte[] bytes = classLoader.getRawClassBytes(name);
        if (bytes == null || !runTransformers) {
            return bytes;
        }
        return preMixin(name, bytes);
    }

    private static byte[] preMixin(String name, byte[] bytes) {
        String internal = name.replace('.', '/');
        byte[] result = GamePatcher.transform(internal, bytes);
        result = accessWidener.apply(internal, result);
        result = EnvironmentStripper.strip(internal, result);
        return result;
    }

    static byte[] transformClass(String name, byte[] bytes) {
        byte[] result = preMixin(name, bytes);
        if (result == null) {
            return null;
        }
        IMixinTransformer transformer = mixinTransformer;
        if (mixinReady && transformer != null && !name.startsWith("org.spongepowered.")
                && !name.startsWith("com.llamalad7.mixinextras.")) {
            try {
                result = transformer.transformClassBytes(name, name, result);
            } catch (RuntimeException | Error error) {
                NyxRuntime.log("Mixin failed to transform " + name + ": " + error);
                throw error;
            }
        }
        return result;
    }
}
