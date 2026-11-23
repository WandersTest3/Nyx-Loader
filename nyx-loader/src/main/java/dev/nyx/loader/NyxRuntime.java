package dev.nyx.loader;

import dev.nyx.api.ConfigScreenFactory;
import dev.nyx.api.HudRenderer;
import dev.nyx.api.NyxOptions;
import dev.nyx.api.ScreenListener;
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

    private static final List<ModContainer> MODS = new CopyOnWriteArrayList<ModContainer>();
    private static final List<ModContainer> BUILTIN = new ArrayList<ModContainer>();
    private static final List<Runnable> TICK_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final List<HudRenderer> HUD_LISTENERS = new CopyOnWriteArrayList<HudRenderer>();
    private static final List<KeyBinding> KEYS = new CopyOnWriteArrayList<KeyBinding>();
    private static final List<Runnable> FRAME_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final List<Runnable> JOIN_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final List<Runnable> LEAVE_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final List<ScreenListener> SCREEN_LISTENERS = new CopyOnWriteArrayList<ScreenListener>();
    private static final java.util.Queue<Runnable> RENDER_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    private static final java.util.Map<String, ConfigScreenFactory> CONFIG_SCREENS = new java.util.concurrent.ConcurrentHashMap<String, ConfigScreenFactory>();
    private static final java.util.Map<String, NyxOptions> OPTIONS = new java.util.concurrent.ConcurrentHashMap<String, NyxOptions>();
    private static final java.util.Map<String, String[]> NYX_MIXINS = new java.util.concurrent.ConcurrentHashMap<String, String[]>();
    private static final java.util.Map<String, String> NYX_WIDENERS = new java.util.concurrent.ConcurrentHashMap<String, String>();
    private static volatile ClientBridge bridge;
    private static volatile Thread renderThread;
    private static boolean worldPresent;
    private static Object lastScreen;

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

    private static final List<ModContainer> FABRIC = new CopyOnWriteArrayList<ModContainer>();

    public static void addFabricEntry(ModContainer entry) {
        FABRIC.add(entry);
    }

    public static void setFabricMods(List<FabricModContainer> mods) {
        FABRIC.clear();
        for (FabricModContainer mod : mods) {
            if (!mod.isBuiltin()) {
                FABRIC.add(toEntry(mod));
            }
        }
    }

    public static ModContainer toEntry(FabricModContainer mod) {
        ModMetadata meta = mod.getMetadata();
        List<String> authors = new ArrayList<String>();
        for (Person person : meta.getAuthors()) {
            authors.add(person.getName());
        }
        boolean library = mod.isNested() || isLibraryBadge(mod);
        return new ModContainer(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), null,
                meta.getDescription().isEmpty() ? null : meta.getDescription(),
                authors.isEmpty() ? null : String.join(", ", authors),
                meta.getLicense().isEmpty() ? null : String.join(", ", meta.getLicense()),
                meta.getContact().get("homepage").orElse(null), meta.getContact().get("issues").orElse(null),
                meta.getIconPath(64).orElse(null), mod.getJarPath() == null ? null : mod.getJarPath().toFile(),
                library, "fabric");
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

    public static void setBridge(ClientBridge value) {
        bridge = value;
        renderThread = Thread.currentThread();
    }

    public static Object getGame() {
        ClientBridge current = bridge;
        return current == null ? null : current.game();
    }

    public static boolean isRenderThread() {
        return renderThread != null && Thread.currentThread() == renderThread;
    }

    public static void runOnRenderThread(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task");
        }
        if (isRenderThread()) {
            task.run();
        } else {
            RENDER_QUEUE.add(task);
        }
    }

    public static void sendClientMessage(String message) {
        if (message == null) {
            return;
        }
        runOnRenderThread(() -> {
            ClientBridge current = bridge;
            if (current != null) {
                current.chat(message);
            } else {
                log("[chat] " + message);
            }
        });
    }

    public static void registerFrameListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        FRAME_LISTENERS.add(listener);
    }

    public static void registerWorldListener(Runnable listener, boolean join) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        (join ? JOIN_LISTENERS : LEAVE_LISTENERS).add(listener);
    }

    public static void registerScreenListener(ScreenListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        SCREEN_LISTENERS.add(listener);
    }

    public static void registerConfigScreen(String modId, ConfigScreenFactory factory) {
        if (modId == null || factory == null) {
            throw new IllegalArgumentException("modId and factory are required");
        }
        CONFIG_SCREENS.put(modId, factory);
    }

    public static void registerOptions(String modId, NyxOptions options) {
        OPTIONS.put(modId, options);
    }

    public static ConfigScreenFactory getConfigScreen(String modId) {
        return CONFIG_SCREENS.get(modId);
    }

    public static NyxOptions getOptions(String modId) {
        return OPTIONS.get(modId);
    }

    public static String[] getNyxMixins(String modId) {
        return NYX_MIXINS.get(modId);
    }

    public static String getNyxAccessWidener(String modId) {
        return NYX_WIDENERS.get(modId);
    }

    private static final String HOT_TEST = System.getProperty("nyx.hottest");
    private static long hotTestPoll;

    private static void pollHotTest() {
        long now = System.currentTimeMillis();
        if (now - hotTestPoll < 500L) {
            return;
        }
        hotTestPoll = now;
        File trigger = new File(HOT_TEST);
        if (!trigger.isFile()) {
            return;
        }
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(trigger.toPath());
            trigger.delete();
            for (String line : lines) {
                if (line.trim().startsWith("screen:")) {
                    try {
                        Class.forName("dev.nyx.mc171.NyxPackHook", true, classLoader.asClassLoader())
                                .getDeclaredMethod("devOpen", String.class).invoke(null, line.trim().substring(7));
                        log("[hottest] opened " + line.trim());
                    } catch (Throwable error) {
                        log("[hottest] could not open " + line.trim() + ": " + error);
                    }
                } else if (line.trim().startsWith("new:")) {
                    try {
                        Object created = Class.forName(line.trim().substring(4), true, classLoader.asClassLoader())
                                .getDeclaredConstructor().newInstance();
                        log("[hottest] created " + created.getClass().getName());
                    } catch (Throwable error) {
                        log("[hottest] could not create " + line.trim() + ": " + error);
                    }
                } else if (!line.trim().isEmpty()) {
                    HotLoader.Result result = HotLoader.load(new File(line.trim()));
                    log("[hottest] " + line.trim() + " -> loaded=" + result.loaded + " reason=" + result.reason);
                }
            }
        } catch (java.io.IOException error) {
            log("[hottest] " + error);
        }
    }

    public static void frame(boolean inWorld, Object screen) {
        if (HOT_TEST != null) {
            pollHotTest();
        }
        Runnable task;
        int budget = 256;
        while (budget-- > 0 && (task = RENDER_QUEUE.poll()) != null) {
            try {
                task.run();
            } catch (Throwable error) {
                log("Render thread task failed: " + error);
                error.printStackTrace();
            }
        }
        dispatch(FRAME_LISTENERS, "Frame");
        if (inWorld != worldPresent) {
            worldPresent = inWorld;
            dispatch(inWorld ? JOIN_LISTENERS : LEAVE_LISTENERS, inWorld ? "World join" : "World leave");
        }
        if (screen != lastScreen) {
            Object previous = lastScreen;
            lastScreen = screen;
            for (ScreenListener listener : SCREEN_LISTENERS) {
                try {
                    listener.onScreenChange(previous, screen);
                } catch (Throwable error) {
                    SCREEN_LISTENERS.remove(listener);
                    log("Screen listener removed after error: " + error);
                    error.printStackTrace();
                }
            }
        }
    }

    private static void dispatch(List<Runnable> listeners, String kind) {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable error) {
                listeners.remove(listener);
                log(kind + " listener removed after error: " + error);
                error.printStackTrace();
            }
        }
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
        readMod(file, loader, false);
    }

    public static synchronized ModContainer loadAtRuntime(File file) throws IOException {
        ModContainer mod = readMod(file, classLoader, true);
        if (mod == null) {
            throw new IOException("not a loadable Nyx mod");
        }
        String blocker = null;
        String widener = NYX_WIDENERS.get(mod.id);
        String[] mixins = NYX_MIXINS.get(mod.id);
        if (widener != null) {
            blocker = dev.nyx.loader.hot.HotAccess.apply(file, widener, mod.id);
        }
        if (blocker == null && mixins != null) {
            java.util.List<dev.nyx.loader.hot.HotMixin.Spec> specs = new java.util.ArrayList<dev.nyx.loader.hot.HotMixin.Spec>();
            for (String config : mixins) {
                if (!config.isEmpty()) {
                    specs.add(new dev.nyx.loader.hot.HotMixin.Spec(config, mod.id,
                            org.spongepowered.asm.mixin.FabricUtil.COMPATIBILITY_LATEST));
                }
            }
            blocker = dev.nyx.loader.hot.HotMixin.apply(specs);
        }
        if (blocker != null) {
            MODS.remove(mod);
            NYX_MIXINS.remove(mod.id);
            NYX_WIDENERS.remove(mod.id);
            throw new HotLoader.Blocked("mixin: " + blocker);
        }
        initialize(mod);
        return mod;
    }

    public static String runtimeBlocker(File file) {
        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry(METADATA_FILE);
            if (entry == null) {
                return "missing " + METADATA_FILE;
            }
            Properties properties = new Properties();
            try (InputStream input = jar.getInputStream(entry)) {
                properties.load(input);
            }
            String id = value(properties, "id");
            if (id != null && isNyxModLoaded(id)) {
                return "already loaded";
            }
            return null;
        } catch (IOException error) {
            return error.toString();
        }
    }

    private static ModContainer readMod(File file, GameLoader loader, boolean runtime) {
        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry(METADATA_FILE);
            if (entry == null) {
                log("Skipping " + file.getName() + ": missing " + METADATA_FILE);
                return null;
            }
            Properties properties = new Properties();
            try (InputStream input = jar.getInputStream(entry)) {
                properties.load(input);
            }
            String id = value(properties, "id");
            String entrypoint = value(properties, "entrypoint");
            if (id == null || !id.matches("[a-z0-9_-]+")) {
                log("Skipping " + file.getName() + ": invalid or missing id");
                return null;
            }
            if (entrypoint == null) {
                log("Skipping " + file.getName() + ": missing entrypoint");
                return null;
            }
            if (isNyxModLoaded(id)) {
                log("Skipping " + file.getName() + ": duplicate mod id " + id);
                return null;
            }
            String mixins = value(properties, "mixins");
            String widener = value(properties, "accessWidener");
            if (mixins != null) {
                NYX_MIXINS.put(id, mixins.split("\\s*,\\s*"));
            }
            if (widener != null) {
                NYX_WIDENERS.put(id, widener);
            }
            String name = value(properties, "name");
            String version = value(properties, "version");
            loader.addURL(file.toURI().toURL());
            ModContainer mod = new ModContainer(id, name != null ? name : id, version != null ? version : "unknown",
                    entrypoint, value(properties, "description"), value(properties, "authors"),
                    value(properties, "license"), value(properties, "website"), value(properties, "issues"),
                    value(properties, "icon"), file, false, "nyx");
            MODS.add(mod);
            return mod;
        } catch (IOException error) {
            log("Skipping " + file.getName() + ": " + error);
            return null;
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
