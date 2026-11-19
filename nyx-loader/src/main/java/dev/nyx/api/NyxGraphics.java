package dev.nyx.api;

public interface NyxGraphics {

    int width();

    int height();

    int fontHeight();

    int stringWidth(String text);

    int drawString(String text, int x, int y, int argb);

    void fillRect(int x, int y, int width, int height, int argb);

    int fps();

    long frameTimeMillis();

    boolean inGame();

    boolean screenOpen();
}
