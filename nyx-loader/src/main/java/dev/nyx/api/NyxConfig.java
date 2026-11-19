package dev.nyx.api;

import dev.nyx.loader.NyxRuntime;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class NyxConfig {

    private static final Map<String, NyxConfig> CONFIGS = new HashMap<String, NyxConfig>();

    private final File file;
    private final Properties properties = new Properties();

    private NyxConfig(File file) {
        this.file = file;
        if (file.isFile()) {
            try (InputStream input = new FileInputStream(file)) {
                properties.load(input);
            } catch (IOException error) {
                NyxRuntime.log("Could not read config " + file.getName() + ": " + error);
            }
        }
    }

    static synchronized NyxConfig of(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("id");
        }
        NyxConfig config = CONFIGS.get(id);
        if (config == null) {
            File directory = new File(NyxRuntime.getGameDirectory(), "config" + File.separator + "nyx");
            config = new NyxConfig(new File(directory, id + ".properties"));
            CONFIGS.put(id, config);
        }
        return config;
    }

    public File getFile() {
        return file;
    }

    public String get(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = get(key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    public NyxConfig set(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public NyxConfig set(String key, int value) {
        return set(key, String.valueOf(value));
    }

    public NyxConfig set(String key, boolean value) {
        return set(key, String.valueOf(value));
    }

    public NyxConfig setDefault(String key, String value) {
        if (!properties.containsKey(key)) {
            properties.setProperty(key, value);
        }
        return this;
    }

    public void save() {
        File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            NyxRuntime.log("Could not create config directory: " + directory);
            return;
        }
        try (OutputStream output = new FileOutputStream(file)) {
            properties.store(output, "Nyx config");
        } catch (IOException error) {
            NyxRuntime.log("Could not save config " + file.getName() + ": " + error);
        }
    }
}
