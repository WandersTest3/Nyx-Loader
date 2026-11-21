package dev.nyx.api;

import dev.nyx.loader.NyxRuntime;
import java.io.File;

public final class Nyx {

    public static final String VERSION = "0.0.4";
    public static final String MINECRAFT_VERSION = "1.7.10";

    private Nyx() {
    }

    public static File getGameDirectory() {
        return NyxRuntime.getGameDirectory();
    }

    public static File getModsDirectory() {
        return NyxRuntime.getModsDirectory();
    }

    public static File getConfigDirectory() {
        return new File(NyxRuntime.getGameDirectory(), "config" + File.separator + "nyx");
    }

    public static boolean isModLoaded(String id) {
        return NyxRuntime.isModLoaded(id);
    }

    public static void onClientTick(Runnable listener) {
        NyxRuntime.registerTickListener(listener);
    }

    public static void onHud(HudRenderer renderer) {
        NyxRuntime.registerHudRenderer(renderer);
    }

    public static void registerKey(String modId, String name, int defaultKey, Runnable action) {
        NyxRuntime.registerKey(modId, name, defaultKey, action);
    }

    public static NyxConfig getConfig(String modId) {
        return NyxConfig.of(modId);
    }

    public static int getFps() {
        return NyxRuntime.getFps();
    }

    public static void log(String message) {
        NyxRuntime.log(message);
    }
}
