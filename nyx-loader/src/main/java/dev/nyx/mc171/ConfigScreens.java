package dev.nyx.mc171;

import dev.nyx.api.ConfigScreenFactory;
import dev.nyx.api.NyxOptions;
import dev.nyx.loader.ModContainer;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.NyxFabricLoader;
import dev.nyx.loader.launch.NyxLaunch;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.class_1600;

final class ConfigScreens {

    private static final String[] FABRIC_METHODS = {"getModConfigScreenFactory", "getConfigScreenFactory"};

    private interface Opener {
        Object open(Object parent) throws Exception;
    }

    private static final Map<String, Opener> CACHE = new HashMap<String, Opener>();
    private static final Opener NONE = parent -> null;

    private ConfigScreens() {
    }

    static boolean has(ModContainer mod) {
        return opener(mod) != NONE;
    }

    static Object open(ModContainer mod, Object parent) throws Exception {
        try {
            return opener(mod).open(parent);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
        }
    }

    private static synchronized Opener opener(ModContainer mod) {
        String key = mod.kind + ":" + mod.id;
        Opener cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Opener opener = NONE;
        try {
            if ("nyx".equals(mod.kind)) {
                opener = nyx(mod.id);
            } else if ("fabric".equals(mod.kind)) {
                opener = fabric(mod.id);
            } else if ("forge".equals(mod.kind)) {
                opener = "optifine".equals(mod.id) ? optifine() : forge(mod.id);
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not look up the config screen of " + mod.id + ": " + error);
            opener = NONE;
        }
        CACHE.put(key, opener == null ? NONE : opener);
        return CACHE.get(key);
    }

    private static Opener nyx(String id) {
        ConfigScreenFactory factory = NyxRuntime.getConfigScreen(id);
        if (factory != null) {
            return factory::create;
        }
        NyxOptions options = NyxRuntime.getOptions(id);
        if (options != null) {
            return parent -> new NyxOptionsScreen((net.minecraft.class_388) parent, options);
        }
        return NONE;
    }

    private static Class<?> screenClass() throws ClassNotFoundException {
        return Mc.cls("class_388");
    }

    private static Opener fabric(String id) throws Exception {
        if (!NyxFabricLoader.isReady()) {
            return NONE;
        }
        List<Object> entrypoints = NyxFabricLoader.get().getModEntrypoints("modmenu", id);
        Class<?> screen = screenClass();
        for (Object entrypoint : entrypoints) {
            for (String name : FABRIC_METHODS) {
                for (Method method : entrypoint.getClass().getMethods()) {
                    if (!method.getName().equals(name)) {
                        continue;
                    }
                    method.setAccessible(true);
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(screen)) {
                        return parent -> method.invoke(entrypoint, parent);
                    }
                    if (method.getParameterCount() == 0 && screen.isAssignableFrom(method.getReturnType())) {
                        return parent -> method.invoke(entrypoint);
                    }
                    if (method.getParameterCount() == 0) {
                        return parent -> fromFactory(method.invoke(entrypoint), parent, screen);
                    }
                }
            }
        }
        return NONE;
    }

    @SuppressWarnings("unchecked")
    private static Object fromFactory(Object factory, Object parent, Class<?> screen) throws Exception {
        if (factory == null) {
            return null;
        }
        if (screen.isInstance(factory)) {
            return factory;
        }
        if (factory instanceof Function) {
            return ((Function<Object, Object>) factory).apply(parent);
        }
        for (Method method : factory.getClass().getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 1) {
                method.setAccessible(true);
                return method.invoke(factory, parent);
            }
        }
        return null;
    }

    private static Opener forge(String id) throws Exception {
        if (!NyxLaunch.isForge()) {
            return NONE;
        }
        ClassLoader loader = NyxLaunch.gameClassLoader();
        Class<?> loaderClass = Class.forName("cpw.mods.fml.common.Loader", true, loader);
        Object instance = loaderClass.getMethod("instance").invoke(null);
        Class<?> containerType = Class.forName("cpw.mods.fml.common.ModContainer", true, loader);
        for (Object container : (List<?>) loaderClass.getMethod("getActiveModList").invoke(instance)) {
            String modId = String.valueOf(containerType.getMethod("getModId").invoke(container));
            if (!modId.equalsIgnoreCase(id)) {
                continue;
            }
            Object guiClass = containerType.getMethod("getGuiClassName").invoke(container);
            if (guiClass == null || String.valueOf(guiClass).isEmpty()) {
                return NONE;
            }
            Class<?> factoryClass = Class.forName(String.valueOf(guiClass), true, loader);
            Object factory = factoryClass.getDeclaredConstructor().newInstance();
            Method initialize = null;
            Method mainClass = null;
            for (Method method : factoryClass.getMethods()) {
                if (method.getName().equals("initialize") && method.getParameterCount() == 1) {
                    initialize = method;
                } else if (method.getName().equals("mainConfigGuiClass") && method.getParameterCount() == 0) {
                    mainClass = method;
                }
            }
            if (initialize == null || mainClass == null) {
                return NONE;
            }
            initialize.invoke(factory, class_1600.method_2965());
            Class<?> type = (Class<?>) mainClass.invoke(factory);
            if (type == null) {
                return NONE;
            }
            Class<?> screen = screenClass();
            return parent -> type.getConstructor(screen).newInstance(parent);
        }
        return NONE;
    }

    private static Opener optifine() throws Exception {
        Class<?> video = Mc.cls("class_398");
        Class<?> settings = Mc.cls("class_347");
        Class<?> screen = screenClass();
        return parent -> video.getConstructor(screen, settings).newInstance(parent,
                Mc.get(class_1600.method_2965(), "field_3823"));
    }
}
