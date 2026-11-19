package dev.nyx.example.timer;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxMod;
import org.lwjgl.opengl.Display;

public class SessionTimer implements NyxMod {

    private static final int TICKS_PER_SECOND = 20;

    private int ticks;

    @Override
    public void onInitialize() {
        Nyx.onClientTick(this::tick);
    }

    private void tick() {
        ticks++;
        if (ticks % TICKS_PER_SECOND != 0) {
            return;
        }
        int seconds = ticks / TICKS_PER_SECOND;
        Display.setTitle(String.format("Minecraft %s | Session %02d:%02d:%02d",
                Nyx.MINECRAFT_VERSION, seconds / 3600, seconds / 60 % 60, seconds % 60));
    }
}
