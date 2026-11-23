package dev.nyx.loader.launch;

import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.AccessWidener;
import dev.nyx.loader.fabric.EnvironmentStripper;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class NyxLaunch {

    private static final Map<String, Object> PROPERTIES = new HashMap<>();
    private static final byte[] INTERMEDIARY_MARKER = "net/minecraft/class_".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

    private static GameLoader loader;
    private static TinyMappings mappings;
    private static RuntimeNames names = RuntimeNames.IDENTITY;
    private static final AccessWidener ACCESS_WIDENER = new AccessWidener();
    private static volatile IMixinTransformer mixinTransformer;
    private static volatile boolean mixinReady;
    private static boolean forge;

    private NyxLaunch() {
    }

    public static ClassLoader gameClassLoader() {
        return loader.asClassLoader();
    }

    public static GameLoader gameLoader() {
        return loader;
    }

    static void setGameLoader(GameLoader value) {
        loader = value;
    }

    public static TinyMappings mappings() {
        return mappings;
    }

    static void setMappings(TinyMappings value) {
        mappings = value;
    }

    public static RuntimeNames names() {
        return names;
    }

    static void setNames(RuntimeNames value) {
        names = value;
    }

    public static boolean isForge() {
        return forge;
    }

    static void setForge(boolean value) {
        forge = value;
    }

    public static AccessWidener accessWidener() {
        return ACCESS_WIDENER;
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
        return loader.getClassBytesForMixin(name, runTransformers);
    }

    public static byte[] preTransform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || names == RuntimeNames.IDENTITY || !contains(bytes, INTERMEDIARY_MARKER)) {
            return bytes;
        }
        if (transformedName.startsWith("net.minecraft.") && !transformedName.startsWith("net.minecraft.class_")) {
            return bytes;
        }
        return names.remapClass(bytes);
    }

    public static byte[] fixExcluded(String name, byte[] bytes) {
        if (!forge) {
            return bytes;
        }
        return dev.nyx.loader.forge.ForgeFixes.transform(name.replace('.', '/'), bytes);
    }

    public static byte[] postTransform(String name, byte[] bytes, boolean withMixin) {
        if (bytes == null) {
            IMixinTransformer generator = mixinTransformer;
            if (withMixin && mixinReady && generator != null && !name.startsWith("org.spongepowered.")
                    && !name.startsWith("com.llamalad7.mixinextras.")) {
                ClassCache.taint();
                return generator.transformClassBytes(name, name, null);
            }
            return null;
        }
        String internal = name.replace('.', '/');
        byte[] result = forge ? dev.nyx.loader.forge.ForgeFixes.transform(internal, bytes) : bytes;
        result = GamePatcher.transform(internal, result);
        result = ACCESS_WIDENER.apply(internal, result);
        result = EnvironmentStripper.strip(internal, result);
        IMixinTransformer transformer = mixinTransformer;
        if (withMixin && mixinReady && transformer != null && !name.startsWith("org.spongepowered.")
                && !name.startsWith("com.llamalad7.mixinextras.")) {
            result = applyMixin(name, result, true);
        }
        if (withMixin && dev.nyx.loader.hot.HotInterfaces.active()) {
            result = dev.nyx.loader.hot.HotInterfaces.transform(result);
        }
        return result;
    }

    public static IMixinTransformer mixinTransformer() {
        return mixinTransformer;
    }

    public static byte[] applyMixin(String name, byte[] before, boolean live) {
        IMixinTransformer transformer = mixinTransformer;
        try {
            byte[] result = transformer.transformClassBytes(name, name, before);
            boolean changed = result != before && !java.util.Arrays.equals(result, before);
            if (forge) {
                result = dev.nyx.loader.forge.ForgeFixes.removeUnusedMixinHandlers(name, before, result);
            }
            if (live && changed) {
                ClassCache.taint();
                dev.nyx.loader.hot.HotState.record(name, result);
            }
            return result;
        } catch (RuntimeException | Error error) {
            NyxRuntime.log("Mixin failed to transform " + name + ": " + error);
            throw error;
        }
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
