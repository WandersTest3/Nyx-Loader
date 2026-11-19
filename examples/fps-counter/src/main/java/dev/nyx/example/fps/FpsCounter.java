package dev.nyx.example.fps;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxConfig;
import dev.nyx.api.NyxGraphics;
import dev.nyx.api.NyxMod;
import org.lwjgl.input.Keyboard;

public class FpsCounter implements NyxMod {

    private static final String ID = "fps-counter";
    private static final int PADDING = 3;
    private static final int MARGIN = 4;

    private NyxConfig config;
    private boolean enabled;
    private boolean showFrameTime;
    private boolean background;
    private String corner;

    @Override
    public void onInitialize() {
        config = Nyx.getConfig(ID);
        config.setDefault("enabled", "true");
        config.setDefault("corner", "top-left");
        config.setDefault("show-frame-time", "true");
        config.setDefault("background", "true");
        config.save();
        enabled = config.getBoolean("enabled", true);
        corner = config.get("corner", "top-left");
        showFrameTime = config.getBoolean("show-frame-time", true);
        background = config.getBoolean("background", true);
        Nyx.registerKey(ID, "toggle", Keyboard.KEY_F6, new Runnable() {
            @Override
            public void run() {
                enabled = !enabled;
                config.set("enabled", enabled).save();
            }
        });
        Nyx.onHud(this::render);
    }

    private void render(NyxGraphics graphics) {
        if (!enabled) {
            return;
        }
        int fps = graphics.fps();
        String text = "FPS: " + fps;
        if (showFrameTime) {
            text += " (" + graphics.frameTimeMillis() + " ms)";
        }
        int width = graphics.stringWidth(text);
        int height = graphics.fontHeight();
        int x = corner.endsWith("right") ? graphics.width() - width - MARGIN - PADDING : MARGIN + PADDING;
        int y = corner.startsWith("bottom") ? graphics.height() - height - MARGIN - PADDING : MARGIN + PADDING;
        if (background) {
            graphics.fillRect(x - PADDING, y - PADDING, width + PADDING * 2, height + PADDING * 2, 0x90000000);
        }
        graphics.drawString(text, x, y, color(fps));
    }

    private static int color(int fps) {
        if (fps >= 50) {
            return 0xFF55FF55;
        }
        if (fps >= 25) {
            return 0xFFFFFF55;
        }
        return 0xFFFF5555;
    }
}
