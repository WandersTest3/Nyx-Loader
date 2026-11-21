package dev.nyx.mc171;

import net.minecraft.class_1600;
import net.minecraft.class_388;
import dev.nyx.loader.ModContainer;
import dev.nyx.loader.NyxRuntime;

public final class NyxMenuHook {

    private static final int SIZE = 20;
    private static final int MARGIN = 4;

    private static boolean failed;

    private NyxMenuHook() {
    }

    private static boolean inside(class_388 screen, int mouseX, int mouseY) {
        int x = screen.field_1230 - SIZE - MARGIN;
        return mouseX >= x && mouseX < x + SIZE && mouseY >= MARGIN && mouseY < MARGIN + SIZE;
    }

    public static void draw(class_388 screen, int mouseX, int mouseY, float partialTicks) {
        if (failed) {
            return;
        }
        try {
            class_1600 minecraft = class_1600.method_2965();
            boolean hover = inside(screen, mouseX, mouseY);
            int x = screen.field_1230 - SIZE - MARGIN;
            Gfx.button(x, MARGIN, SIZE, SIZE, hover, true);
            ModContainer nyx = NyxRuntime.getBuiltinEntries().get(0);
            Gfx.texture(Gfx.icon(nyx), x + 2, MARGIN + 2, 16);
            if (hover && minecraft != null && minecraft.field_3814 != null) {
                String label = Lang.t("mods");
                int width = minecraft.field_3814.method_954(label);
                int labelX = x + SIZE - width - 6;
                Gfx.rect(labelX - 3, MARGIN + SIZE + 3, width + 6, 12, 0xF0100010);
                Gfx.border(labelX - 3, MARGIN + SIZE + 3, width + 6, 12, 0xFF57197F);
                minecraft.field_3814.method_956(label, labelX, MARGIN + SIZE + 5, 0xFFFFFFFF);
                org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } catch (Throwable error) {
            failed = true;
            NyxRuntime.log("Mod menu button disabled after error: " + error);
            error.printStackTrace();
        }
    }

    public static boolean click(class_388 screen, int mouseX, int mouseY, int button) {
        if (failed || button != 0 || !inside(screen, mouseX, mouseY)) {
            return false;
        }
        try {
            class_1600.method_2965().method_2928(new NyxModMenuScreen(screen));
            return true;
        } catch (Throwable error) {
            failed = true;
            NyxRuntime.log("Mod menu could not be opened: " + error);
            error.printStackTrace();
            return false;
        }
    }
}
