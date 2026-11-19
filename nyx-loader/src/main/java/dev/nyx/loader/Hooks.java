package dev.nyx.loader;

public final class Hooks {

    private Hooks() {
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
