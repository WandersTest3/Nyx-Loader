package dev.nyx.api;

import dev.nyx.loader.NyxRuntime;
import java.io.File;

public final class Nyx {

    public static final String VERSION = "0.0.12";
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

    public static Object getMinecraft() {
        return NyxRuntime.getGame();
    }

    public static boolean isRenderThread() {
        return NyxRuntime.isRenderThread();
    }

    public static void runOnRenderThread(Runnable task) {
        NyxRuntime.runOnRenderThread(task);
    }

    public static void onFrame(Runnable listener) {
        NyxRuntime.registerFrameListener(listener);
    }

    public static void onWorldJoin(Runnable listener) {
        NyxRuntime.registerWorldListener(listener, true);
    }

    public static void onWorldLeave(Runnable listener) {
        NyxRuntime.registerWorldListener(listener, false);
    }

    public static void onScreenChange(ScreenListener listener) {
        NyxRuntime.registerScreenListener(listener);
    }

    public static void sendClientMessage(String message) {
        NyxRuntime.sendClientMessage(message);
    }

    public static void registerConfigScreen(String modId, ConfigScreenFactory factory) {
        NyxRuntime.registerConfigScreen(modId, factory);
    }

    public static long getFrameTimeMillis() {
        return NyxRuntime.getFrameTimeMillis();
    }
}
