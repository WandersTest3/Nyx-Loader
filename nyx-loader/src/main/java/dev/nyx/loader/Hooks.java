package dev.nyx.loader;

import dev.nyx.loader.fabric.NyxFabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public final class Hooks {

    private static boolean fabricStarted;
    private static volatile Thread renderThread;
    private static volatile Object pendingRefresh;

    private Hooks() {
    }

    public static void startFabricClient(Object game) {
        if (fabricStarted || !NyxFabricLoader.isReady()) {
            return;
        }
        fabricStarted = true;
        renderThread = Thread.currentThread();
        NyxFabricLoader loader = NyxFabricLoader.get();
        loader.setGameInstance(game);
        loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
        loader.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
        NyxRuntime.log("Fabric entrypoints done");
    }

    public static String insertBranding(String brand) {
        if (brand == null || brand.isEmpty() || "vanilla".equals(brand)) {
            return "nyx";
        }
        return brand + ",nyx";
    }

    public static boolean offRenderThread() {
        Thread render = renderThread;
        return render != null && Thread.currentThread() != render;
    }

    public static void deferRefresh(Object game) {
        NyxRuntime.log("Resource reload requested from thread " + Thread.currentThread().getName() + "; running it on the render thread");
        pendingRefresh = game;
    }

    private static void runPendingRefresh() {
        Object game = pendingRefresh;
        if (game == null) {
            return;
        }
        pendingRefresh = null;
        try {
            java.lang.reflect.Method method = game.getClass().getDeclaredMethod(dev.nyx.loader.launch.NyxLaunch.names().method("method_5576"));
            method.setAccessible(true);
            method.invoke(game);
        } catch (Throwable error) {
            NyxRuntime.log("Deferred resource reload failed: " + error);
        }
    }

    public static void onGameStart() {
        renderThread = Thread.currentThread();
        try {
            NyxRuntime.start();
        } catch (Throwable error) {
            NyxRuntime.log("Game start hook failed: " + error);
            error.printStackTrace();
        }
    }

    public static void onClientTick() {
        runPendingRefresh();
        try {
            NyxRuntime.tick();
        } catch (Throwable error) {
            NyxRuntime.log("Client tick hook failed: " + error);
        }
    }
}
