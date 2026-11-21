package dev.nyx.mc171;

import net.minecraft.class_1600;
import dev.nyx.api.NyxGraphics;
import dev.nyx.loader.NyxRuntime;

final class NyxHudGraphics implements NyxGraphics {

    private final class_1600 minecraft;
    private final int width;
    private final int height;

    NyxHudGraphics(class_1600 minecraft, int width, int height) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int fontHeight() {
        return 9;
    }

    public int stringWidth(String text) {
        return minecraft.field_3814.method_954(text);
    }

    public int drawString(String text, int x, int y, int argb) {
        int end = minecraft.field_3814.method_956(text, x, y, argb);
        org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        return end;
    }

    public void fillRect(int x, int y, int rectWidth, int rectHeight, int argb) {
        Gfx.rect(x, y, rectWidth, rectHeight, argb);
    }

    public int fps() {
        return NyxRuntime.getFps();
    }

    public long frameTimeMillis() {
        return NyxRuntime.getFrameTimeMillis();
    }

    public boolean inGame() {
        return minecraft.field_3803 != null;
    }

    public boolean screenOpen() {
        return minecraft.field_3816 != null;
    }
}
