package dev.nyx.mc171;

import java.util.List;

final class Chat {

    private Chat() {
    }

    static boolean print(Object game, List<String> lines) throws ReflectiveOperationException {
        if (game == null) {
            return false;
        }
        Object gui = Mc.get(game, "field_3820");
        if (gui == null) {
            return false;
        }
        Object chat = Mc.call(gui, "method_983");
        for (String line : lines) {
            Object component = Mc.construct("class_1989", new Class<?>[] {String.class}, line);
            Mc.method(chat.getClass(), "method_6690", 1).invoke(chat, component);
        }
        return true;
    }
}
