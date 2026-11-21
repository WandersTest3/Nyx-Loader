package dev.nyx.loader.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.ObjectShare;

final class ObjectShareImpl implements ObjectShare {

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, List<BiConsumer<String, Object>>> waiting = new HashMap<>();

    private static void validate(String key) {
        if (key == null || key.indexOf(':') <= 0) {
            throw new IllegalArgumentException("Invalid key, must be modid:subkey: " + key);
        }
    }

    @Override
    public synchronized Object get(String key) {
        validate(key);
        return values.get(key);
    }

    @Override
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        validate(key);
        Object value;
        synchronized (this) {
            value = values.get(key);
            if (value == null) {
                waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer);
                return;
            }
        }
        consumer.accept(key, value);
    }

    @Override
    public Object put(String key, Object value) {
        validate(key);
        if (value == null) {
            throw new NullPointerException("value");
        }
        Object previous;
        List<BiConsumer<String, Object>> consumers;
        synchronized (this) {
            previous = values.put(key, value);
            consumers = waiting.remove(key);
        }
        if (consumers != null) {
            for (BiConsumer<String, Object> consumer : consumers) {
                consumer.accept(key, value);
            }
        }
        return previous;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        validate(key);
        synchronized (this) {
            Object previous = values.get(key);
            if (previous != null) {
                return previous;
            }
        }
        put(key, value);
        return null;
    }

    @Override
    public synchronized Object remove(String key) {
        validate(key);
        return values.remove(key);
    }
}
