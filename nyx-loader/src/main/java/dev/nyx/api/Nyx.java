package dev.nyx.api;

import dev.nyx.loader.NyxRuntime;
import java.io.File;

public final class Nyx {

    public static final String VERSION = "0.0.1";
    public static final String MINECRAFT_VERSION = "1.0";

    private Nyx() {
    }

    public static File getGameDirectory() {
        return NyxRuntime.getGameDirectory();
    }

    public static File getModsDirectory() {
        return NyxRuntime.getModsDirectory();
    }

    public static boolean isModLoaded(String id) {
        return NyxRuntime.isModLoaded(id);
    }

    public static void onClientTick(Runnable listener) {
        NyxRuntime.registerTickListener(listener);
    }

    public static void log(String message) {
        NyxRuntime.log(message);
    }
}
