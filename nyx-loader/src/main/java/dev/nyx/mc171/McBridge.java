package dev.nyx.mc171;

import dev.nyx.loader.ClientBridge;
import dev.nyx.loader.NyxRuntime;
import java.util.Collections;
import net.minecraft.class_1600;

final class McBridge implements ClientBridge {

    public Object game() {
        return class_1600.method_2965();
    }

    public void chat(String message) {
        try {
            Chat.print(class_1600.method_2965(), Collections.singletonList(message));
        } catch (Throwable error) {
            NyxRuntime.log("Chat message failed: " + error);
        }
    }
}
