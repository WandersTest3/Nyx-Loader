package dev.nyx.loader;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxMod;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import net.minecraft.launchwrapper.LaunchClassLoader;

public final class NyxRuntime {

    private static final String METADATA_FILE = "nyx.mod.properties";

    private static final List<ModContainer> MODS = new ArrayList<ModContainer>();
    private static final List<Runnable> TICK_LISTENERS = new CopyOnWriteArrayList<Runnable>();

    private static File gameDirectory = new File(".");
    private static LaunchClassLoader classLoader;
    private static boolean started;

    private NyxRuntime() {
    }

    public static File getGameDirectory() {
        return gameDirectory;
    }

    public static File getModsDirectory() {
        return new File(gameDirectory, "mods");
    }

    public static boolean isModLoaded(String id) {
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

    static void setGameDirectory(File directory) {
        if (directory != null) {
            gameDirectory = directory;
        }
    }

    static void discoverMods(LaunchClassLoader loader) {
        classLoader = loader;
        File directory = getModsDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            log("Could not create mods directory: " + directory);
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                readMod(file, loader);
            }
        }
        log(MODS.size() + " mod(s) found in " + directory);
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

    private static void readMod(File file, LaunchClassLoader loader) {
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
            if (isModLoaded(id)) {
                log("Skipping " + file.getName() + ": duplicate mod id " + id);
                return;
            }
            String name = value(properties, "name");
            String version = value(properties, "version");
            loader.addURL(file.toURI().toURL());
            MODS.add(new ModContainer(id, name != null ? name : id, version != null ? version : "unknown", entrypoint, file));
        } catch (IOException error) {
            log("Skipping " + file.getName() + ": " + error);
        }
    }

    private static void initialize(ModContainer mod) {
        try {
            Object instance = Class.forName(mod.entrypoint, true, classLoader).newInstance();
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
