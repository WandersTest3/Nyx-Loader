package dev.nyx.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NyxOptions {

    public enum Type {
        TOGGLE, CYCLE, SLIDER
    }

    public static final class Option {
        public final Type type;
        public final String key;
        public final String label;
        public final String description;
        public final String defaultValue;
        public final List<String> values;
        public final int min;
        public final int max;

        Option(Type type, String key, String label, String description, String defaultValue, List<String> values,
                int min, int max) {
            this.type = type;
            this.key = key;
            this.label = label;
            this.description = description;
            this.defaultValue = defaultValue;
            this.values = values;
            this.min = min;
            this.max = max;
        }
    }

    private final String modId;
    private final NyxConfig config;
    private final List<Option> options = new ArrayList<Option>();
    private final List<Runnable> saveListeners = new ArrayList<Runnable>();
    private String title;

    private NyxOptions(String modId) {
        this.modId = modId;
        this.config = Nyx.getConfig(modId);
        this.title = modId;
    }

    public static NyxOptions of(String modId) {
        return new NyxOptions(modId);
    }

    public NyxOptions title(String value) {
        title = value;
        return this;
    }

    public NyxOptions toggle(String key, String label, boolean defaultValue) {
        return toggle(key, label, null, defaultValue);
    }

    public NyxOptions toggle(String key, String label, String description, boolean defaultValue) {
        return add(new Option(Type.TOGGLE, key, label, description, String.valueOf(defaultValue),
                Collections.<String>emptyList(), 0, 0));
    }

    public NyxOptions cycle(String key, String label, String defaultValue, String... values) {
        return cycleDescribed(key, label, null, defaultValue, values);
    }

    public NyxOptions cycleDescribed(String key, String label, String description, String defaultValue,
            String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values");
        }
        List<String> list = new ArrayList<String>();
        Collections.addAll(list, values);
        return add(new Option(Type.CYCLE, key, label, description, defaultValue, Collections.unmodifiableList(list),
                0, 0));
    }

    public NyxOptions slider(String key, String label, int defaultValue, int min, int max) {
        return slider(key, label, null, defaultValue, min, max);
    }

    public NyxOptions slider(String key, String label, String description, int defaultValue, int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        return add(new Option(Type.SLIDER, key, label, description, String.valueOf(defaultValue),
                Collections.<String>emptyList(), min, max));
    }

    public NyxOptions onSave(Runnable listener) {
        if (listener != null) {
            saveListeners.add(listener);
        }
        return this;
    }

    private NyxOptions add(Option option) {
        if (option.key == null || option.label == null) {
            throw new IllegalArgumentException("key and label are required");
        }
        options.add(option);
        config.setDefault(option.key, option.defaultValue);
        return this;
    }

    public void register() {
        config.save();
        dev.nyx.loader.NyxRuntime.registerOptions(modId, this);
    }

    public String getModId() {
        return modId;
    }

    public String getTitle() {
        return title;
    }

    public NyxConfig getConfig() {
        return config;
    }

    public List<Option> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public void save() {
        config.save();
        for (Runnable listener : saveListeners) {
            try {
                listener.run();
            } catch (Throwable error) {
                Nyx.log("Options save listener of " + modId + " failed: " + error);
            }
        }
    }
}
