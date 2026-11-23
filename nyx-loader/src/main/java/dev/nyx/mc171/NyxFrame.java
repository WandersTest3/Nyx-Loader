package dev.nyx.mc171;

import net.minecraft.class_1600;
import net.minecraft.class_624;
import dev.nyx.loader.KeyBinding;
import dev.nyx.loader.NyxRuntime;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class NyxFrame {

    private static final int MAX_ERRORS = 5;

    private static long lastFrame;
    private static long windowStart;
    private static int frames;
    private static int errors;
    private static boolean menuKeyDown;
    private static boolean debugOpened;
    private static boolean bridged;

    private NyxFrame() {
    }

    public static void onFrame() {
        dev.nyx.loader.Watchdog.beat();
        if (errors >= MAX_ERRORS) {
            return;
        }
        try {
            long now = System.nanoTime();
            if (lastFrame != 0L) {
                NyxRuntime.updateFrameStats(currentFps(), (now - lastFrame) / 1000000L);
            }
            lastFrame = now;
            frames++;
            if (windowStart == 0L) {
                windowStart = now;
            }
            class_1600 minecraft = class_1600.method_2965();
            if (minecraft == null || minecraft.field_3814 == null || !NyxRuntime.isStarted()) {
                return;
            }
            if (!bridged) {
                bridged = true;
                NyxRuntime.setBridge(new McBridge());
            }
            NyxRuntime.frame(minecraft.field_3803 != null, minecraft.field_3816);
            NyxCrash.watchShaders();
            NyxCrash.deliverPendingChat(minecraft);
            pollKeys(minecraft);
            debugOpen(minecraft);
            if (NyxRuntime.hasHudRenderers()) {
                renderHud(minecraft);
            }
        } catch (Throwable error) {
            errors++;
            NyxRuntime.log("NyxFrame hook error (" + errors + "/" + MAX_ERRORS + "): " + error);
            error.printStackTrace();
        }
    }

    private static int lastFps;

    private static int currentFps() {
        long now = System.nanoTime();
        long elapsed = now - windowStart;
        if (elapsed >= 1000000000L) {
            lastFps = (int) Math.round(frames * 1000000000.0D / elapsed);
            frames = 0;
            windowStart = now;
        }
        return lastFps;
    }

    private static void pollKeys(class_1600 minecraft) {
        boolean menuDown = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (menuDown && !menuKeyDown && minecraft.field_3816 == null) {
            minecraft.method_2928(new NyxModMenuScreen(null));
        }
        menuKeyDown = menuDown;
        for (KeyBinding binding : NyxRuntime.getKeyBindings()) {
            boolean down = Keyboard.isKeyDown(binding.key);
            if (down && !binding.down && minecraft.field_3816 == null) {
                try {
                    binding.action.run();
                } catch (Throwable error) {
                    NyxRuntime.log("Key action " + binding.modId + "." + binding.name + " failed: " + error);
                }
            }
            binding.down = down;
        }
    }

    private static void debugOpen(class_1600 minecraft) {
        if (!debugOpened && minecraft.field_3816 instanceof class_624 && Boolean.getBoolean("nyx.debug.openmodmenu")) {
            debugOpened = true;
            minecraft.method_2928(new NyxModMenuScreen(minecraft.field_3816));
        }
    }

    private static void renderHud(class_1600 minecraft) {
        int displayWidth = Display.getWidth();
        int displayHeight = Display.getHeight();
        int scale = Gfx.guiScale(displayWidth, displayHeight);
        int width = (int) Math.ceil(displayWidth / (double) scale);
        int height = (int) Math.ceil(displayHeight / (double) scale);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            NyxRuntime.renderHud(new NyxHudGraphics(minecraft, width, height));
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
