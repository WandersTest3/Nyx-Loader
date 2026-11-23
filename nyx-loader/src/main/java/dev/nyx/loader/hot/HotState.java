package dev.nyx.loader.hot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HotState {

    private static final Map<String, byte[]> LOADED = new ConcurrentHashMap<String, byte[]>();

    private HotState() {
    }

    public static void record(String name, byte[] bytes) {
        LOADED.put(name, bytes);
    }

    public static byte[] loaded(String name) {
        return LOADED.get(name);
    }
}
