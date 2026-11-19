package dev.nyx.example.hello;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxMod;
import org.lwjgl.opengl.Display;

public class HelloNyx implements NyxMod {

    @Override
    public void onInitialize() {
        Display.setTitle("Minecraft " + Nyx.MINECRAFT_VERSION + " | Nyx " + Nyx.VERSION);
        Nyx.log("Hello from Hello Nyx");
    }
}
