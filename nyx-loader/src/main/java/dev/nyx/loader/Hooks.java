package dev.nyx.loader;

import dev.nyx.loader.fabric.NyxFabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public final class Hooks {

    private static boolean fabricStarted;

    private Hooks() {
    }

    public static void startFabricClient(Object game) {
        if (fabricStarted || !NyxFabricLoader.isReady()) {
            return;
        }
        fabricStarted = true;
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

    public static void onGameStart() {
        try {
            NyxRuntime.start();
        } catch (Throwable error) {
            NyxRuntime.log("Game start hook failed: " + error);
            error.printStackTrace();
        }
    }

    public static void onClientTick() {
        try {
            NyxRuntime.tick();
        } catch (Throwable error) {
            NyxRuntime.log("Client tick hook failed: " + error);
        }
    }
}
