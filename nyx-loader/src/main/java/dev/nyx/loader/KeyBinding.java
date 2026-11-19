package dev.nyx.loader;

public final class KeyBinding {

    public final String modId;
    public final String name;
    public final Runnable action;
    public final int key;
    public boolean down;

    KeyBinding(String modId, String name, int key, Runnable action) {
        this.modId = modId;
        this.name = name;
        this.key = key;
        this.action = action;
    }
}
