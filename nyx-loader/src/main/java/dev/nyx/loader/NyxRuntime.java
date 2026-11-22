package dev.nyx.loader;

import dev.nyx.api.HudRenderer;
import dev.nyx.api.Nyx;
import dev.nyx.api.NyxConfig;
import dev.nyx.api.NyxGraphics;
import dev.nyx.api.NyxMod;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import dev.nyx.loader.fabric.FabricModContainer;
import dev.nyx.loader.fabric.NyxFabricLoader;
import dev.nyx.loader.launch.GameLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import org.lwjgl.input.Keyboard;

public final class NyxRuntime {

    private static final String METADATA_FILE = "nyx.mod.properties";

    private static final List<ModContainer> MODS = new ArrayList<ModContainer>();
    private static final List<ModContainer> BUILTIN = new ArrayList<ModContainer>();
    private static final List<Runnable> TICK_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final List<HudRenderer> HUD_LISTENERS = new CopyOnWriteArrayList<HudRenderer>();
    private static final List<KeyBinding> KEYS = new CopyOnWriteArrayList<KeyBinding>();

    private static File gameDirectory = new File(".");
    private static GameLoader classLoader;
    private static boolean started;
    private static volatile int fps;
    private static volatile long frameTimeMillis;

    private NyxRuntime() {
    }

    static {
        BUILTIN.add(new ModContainer("nyx", "Nyx Loader", Nyx.VERSION, null,
                "Mod loader focado no cliente para Minecraft Java.", "Wanders_Test3",
                "Mod Test3 License V1.0", "https://github.com/WandersTest3/Nyx-Loader",
                "https://github.com/WandersTest3/Nyx-Loader/issues", "classpath:/assets/nyx/icon.png", null, true,
                "builtin"));
        BUILTIN.add(new ModContainer("minecraft", "Minecraft", Nyx.MINECRAFT_VERSION, null,
                "Minecraft Java Edition " + Nyx.MINECRAFT_VERSION + ".", "Mojang Studios", null, null, null,
                null, null, true, "builtin"));
        BUILTIN.add(new ModContainer("fabricloader", "Fabric Loader (Nyx)", NyxFabricLoader.FABRIC_LOADER_VERSION,
                null, "Camada do Nyx compativel com mods Fabric (mapeamentos intermediary do Legacy Fabric).",
                "Wanders_Test3", "Mod Test3 License V1.0", "https://github.com/WandersTest3/Nyx-Loader",
                "https://github.com/WandersTest3/Nyx-Loader/issues", "classpath:/assets/nyx/icon.png", null, true,
                "builtin"));
        BUILTIN.add(new ModContainer("java", "Java", System.getProperty("java.version"), null,
                System.getProperty("java.vm.name") + " (" + System.getProperty("java.vendor") + ")", null, null,
                null, null, null, null, true, "builtin"));
    }

    public static List<ModContainer> getMods() {
        return Collections.unmodifiableList(MODS);
    }

    public static List<ModContainer> getBuiltinEntries() {
        return Collections.unmodifiableList(BUILTIN);
    }

    private static final List<ModContainer> FABRIC = new ArrayList<ModContainer>();

    public static void setFabricMods(List<FabricModContainer> mods) {
        FABRIC.clear();
        for (FabricModContainer mod : mods) {
            if (mod.isBuiltin()) {
                continue;
            }
            ModMetadata meta = mod.getMetadata();
            List<String> authors = new ArrayList<String>();
            for (Person person : meta.getAuthors()) {
                authors.add(person.getName());
            }
            boolean library = mod.isNested() || isLibraryBadge(mod);
            FABRIC.add(new ModContainer(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), null,
                    meta.getDescription().isEmpty() ? null : meta.getDescription(),
                    authors.isEmpty() ? null : String.join(", ", authors),
                    meta.getLicense().isEmpty() ? null : String.join(", ", meta.getLicense()),
                    meta.getContact().get("homepage").orElse(null), meta.getContact().get("issues").orElse(null),
                    meta.getIconPath(64).orElse(null), mod.getJarPath() == null ? null : mod.getJarPath().toFile(),
                    library, "fabric"));
        }
    }

    private static boolean isLibraryBadge(FabricModContainer mod) {
        try {
            net.fabricmc.loader.api.metadata.CustomValue modmenu = mod.getMetadata().getCustomValue("modmenu");
            if (modmenu == null || modmenu.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.OBJECT) {
                return false;
            }
            net.fabricmc.loader.api.metadata.CustomValue badges = modmenu.getAsObject().get("badges");
            if (badges == null || badges.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.ARRAY) {
                return false;
            }
            for (net.fabricmc.loader.api.metadata.CustomValue badge : badges.getAsArray()) {
                if (badge.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING
                        && "library".equals(badge.getAsString())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public static List<ModContainer> getForgeEntries() {
        List<ModContainer> result = new ArrayList<ModContainer>();
        if (!dev.nyx.loader.launch.NyxLaunch.isForge()) {
            return result;
        }
        try {
            ClassLoader loader = dev.nyx.loader.launch.NyxLaunch.gameClassLoader();
            Class<?> loaderClass = Class.forName("cpw.mods.fml.common.Loader", true, loader);
            Object instance = loaderClass.getMethod("instance").invoke(null);
            List<?> mods = (List<?>) loaderClass.getMethod("getActiveModList").invoke(instance);
            for (Object mod : mods) {
                Class<?> type = Class.forName("cpw.mods.fml.common.ModContainer", true, loader);
                String id = String.valueOf(type.getMethod("getModId").invoke(mod));
                String name = String.valueOf(type.getMethod("getName").invoke(mod));
                String version = String.valueOf(type.getMethod("getVersion").invoke(mod));
                Object meta = type.getMethod("getMetadata").invoke(mod);
                String description = null;
                String authors = null;
                String url = null;
                if (meta != null) {
                    Object desc = meta.getClass().getField("description").get(meta);
                    description = desc == null || String.valueOf(desc).isEmpty() ? null : String.valueOf(desc);
                    Object list = meta.getClass().getField("authorList").get(meta);
                    if (list instanceof List && !((List<?>) list).isEmpty()) {
                        List<String> names = new ArrayList<String>();
                        for (Object author : (List<?>) list) {
                            names.add(String.valueOf(author));
                        }
                        authors = String.join(", ", names);
                    }
                    Object link = meta.getClass().getField("url").get(meta);
                    if (link != null && !String.valueOf(link).isEmpty()) {
                        url = String.valueOf(link);
                        if (!url.startsWith("http")) {
                            url = "http://" + url;
                        }
                    }
                }
                Object source = type.getMethod("getSource").invoke(mod);
                File file = source instanceof File && ((File) source).isFile() ? (File) source : null;
                boolean library = "mcp".equalsIgnoreCase(id) || "FML".equals(id) || "Forge".equals(id);
                result.add(new ModContainer(id.toLowerCase(java.util.Locale.ROOT), name, version, null, description,
                        authors, null, url, null, null, file, library, "forge"));
            }
        } catch (Throwable error) {
            log("Could not list Forge mods: " + error);
        }
        return result;
    }

    public static List<ModContainer> getFabricEntries() {
        return Collections.unmodifiableList(FABRIC);
    }

    public static GameLoader getClassLoader() {
        return classLoader;
    }

    public static boolean isStarted() {
        return started;
    }

    public static int getFps() {
        return fps;
    }

    public static long getFrameTimeMillis() {
        return frameTimeMillis;
    }

    public static void updateFrameStats(int newFps, long newFrameTimeMillis) {
        fps = newFps;
        frameTimeMillis = newFrameTimeMillis;
    }

    public static void registerHudRenderer(HudRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("renderer");
        }
        HUD_LISTENERS.add(renderer);
    }

    public static boolean hasHudRenderers() {
        return !HUD_LISTENERS.isEmpty();
    }

    public static void renderHud(NyxGraphics graphics) {
        for (HudRenderer renderer : HUD_LISTENERS) {
            try {
                renderer.render(graphics);
            } catch (Throwable error) {
                HUD_LISTENERS.remove(renderer);
                log("HUD renderer removed after error: " + error);
                error.printStackTrace();
            }
        }
    }

    public static void registerKey(String modId, String name, int defaultKey, Runnable action) {
        if (modId == null || name == null || action == null) {
            throw new IllegalArgumentException("modId, name and action are required");
        }
        NyxConfig keys = Nyx.getConfig("keys");
        String configKey = modId + "." + name;
        String configured = keys.get(configKey, null);
        int key = defaultKey;
        if (configured != null) {
            int parsed = Keyboard.getKeyIndex(configured);
            if (parsed == Keyboard.KEY_NONE) {
                try {
                    parsed = Integer.parseInt(configured);
                } catch (NumberFormatException error) {
                    parsed = defaultKey;
                }
            }
            key = parsed;
        } else {
            keys.set(configKey, Keyboard.getKeyName(defaultKey)).save();
        }
        KEYS.add(new KeyBinding(modId, name, key, action));
    }

    public static List<KeyBinding> getKeyBindings() {
        return KEYS;
    }

    public static File getGameDirectory() {
        return gameDirectory;
    }

    public static File getModsDirectory() {
        return new File(gameDirectory, "mods");
    }

    public static boolean isModLoaded(String id) {
        if (isNyxModLoaded(id)) {
            return true;
        }
        return NyxFabricLoader.isReady() && NyxFabricLoader.get().getModContainer(id).isPresent();
    }

    public static boolean isNyxModLoaded(String id) {
        for (ModContainer mod : MODS) {
            if (mod.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static void registerTickListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        TICK_LISTENERS.add(listener);
    }

    public static void log(String message) {
        System.out.println("[Nyx] " + message);
    }

    public static void setGameDirectory(File directory) {
        if (directory != null) {
            gameDirectory = directory;
        }
    }

    public static void setClassLoader(GameLoader loader) {
        classLoader = loader;
    }

    public static boolean isNyxModJar(File file) {
        try (JarFile jar = new JarFile(file)) {
            return jar.getEntry(METADATA_FILE) != null;
        } catch (IOException error) {
            return false;
        }
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        log("Nyx Loader " + Nyx.VERSION + " running on Minecraft " + Nyx.MINECRAFT_VERSION);
        for (ModContainer mod : MODS) {
            initialize(mod);
        }
    }

    public static void tick() {
        for (Runnable listener : TICK_LISTENERS) {
            try {
                listener.run();
            } catch (Throwable error) {
                TICK_LISTENERS.remove(listener);
                log("Tick listener removed after error: " + error);
                error.printStackTrace();
            }
        }
    }

    public static void readMod(File file, GameLoader loader) {
        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry(METADATA_FILE);
            if (entry == null) {
                log("Skipping " + file.getName() + ": missing " + METADATA_FILE);
                return;
            }
            Properties properties = new Properties();
            try (InputStream input = jar.getInputStream(entry)) {
                properties.load(input);
            }
            String id = value(properties, "id");
            String entrypoint = value(properties, "entrypoint");
            if (id == null || !id.matches("[a-z0-9_-]+")) {
                log("Skipping " + file.getName() + ": invalid or missing id");
                return;
            }
            if (entrypoint == null) {
                log("Skipping " + file.getName() + ": missing entrypoint");
                return;
            }
            if (isNyxModLoaded(id)) {
                log("Skipping " + file.getName() + ": duplicate mod id " + id);
                return;
            }
            String name = value(properties, "name");
            String version = value(properties, "version");
            loader.addURL(file.toURI().toURL());
            MODS.add(new ModContainer(id, name != null ? name : id, version != null ? version : "unknown", entrypoint,
                    value(properties, "description"), value(properties, "authors"), value(properties, "license"),
                    value(properties, "website"), value(properties, "issues"), value(properties, "icon"), file, false, "nyx"));
        } catch (IOException error) {
            log("Skipping " + file.getName() + ": " + error);
        }
    }

    private static void initialize(ModContainer mod) {
        try {
            Object instance = Class.forName(mod.entrypoint, true, classLoader.asClassLoader()).getDeclaredConstructor().newInstance();
            if (!(instance instanceof NyxMod)) {
                log("Mod " + mod.id + " entrypoint does not implement dev.nyx.api.NyxMod");
                return;
            }
            ((NyxMod) instance).onInitialize();
            log("Loaded " + mod.name + " " + mod.version + " (" + mod.id + ")");
        } catch (Throwable error) {
            log("Failed to initialize mod " + mod.id + ": " + error);
            error.printStackTrace();
        }
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
