package dev.nyx.mc171;

import dev.nyx.loader.NyxRuntime;
import net.minecraft.class_1600;
import net.minecraft.class_388;
import net.minecraft.class_617;

public final class NyxPackHook {

    private static final int WIDTH = 74;
    private static final int HEIGHT = 20;
    private static final int MARGIN = 4;

    private static boolean failed;

    private NyxPackHook() {
    }

    private static boolean inside(class_388 screen, int mouseX, int mouseY) {
        int x = screen.field_1230 - WIDTH - MARGIN;
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= MARGIN && mouseY < MARGIN + HEIGHT;
    }

    public static void draw(class_388 screen, int mouseX, int mouseY, float partialTicks) {
        if (failed) {
            return;
        }
        try {
            class_1600 minecraft = class_1600.method_2965();
            int x = screen.field_1230 - WIDTH - MARGIN;
            Gfx.button(x, MARGIN, WIDTH, HEIGHT, inside(screen, mouseX, mouseY), true);
            String label = Lang.t("dl_packs");
            minecraft.field_3814.method_956(label, x + (WIDTH - minecraft.field_3814.method_954(label)) / 2, MARGIN + 6,
                    0xFFFFFFFF);
            org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable error) {
            failed = true;
            NyxRuntime.log("Pack download button disabled after error: " + error);
        }
    }

    public static boolean click(class_388 screen, int mouseX, int mouseY, int button) {
        if (failed || button != 0 || !inside(screen, mouseX, mouseY)) {
            return false;
        }
        try {
            class_1600.method_2965().method_2928(new NyxDownloadScreen(screen, true));
            return true;
        } catch (Throwable error) {
            failed = true;
            NyxRuntime.log("Pack download screen could not be opened: " + error);
            return false;
        }
    }

    public static void devOpen(String what) {
        class_1600 minecraft = class_1600.method_2965();
        class_388 current = minecraft.field_3816;
        if (what.equals("vanilla")) {
            minecraft.method_2928(new class_617(current));
        } else if (what.equals("mods")) {
            minecraft.method_2928(new NyxModMenuScreen(current));
        } else {
            minecraft.method_2928(new NyxDownloadScreen(current, what.equals("packs")));
        }
    }

    static class_388 refreshed(class_388 screen) {
        try {
            class_1600 minecraft = class_1600.method_2965();
            minecraft.method_5572().method_5902();
            if (screen instanceof class_617) {
                return new class_617(((class_617) screen).field_2248);
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not refresh the resource pack list: " + error);
        }
        return screen;
    }
}
