package dev.nyx.loader.mixin;

import dev.nyx.loader.launch.NyxLaunch;
import java.util.Objects;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

public class NyxGlobalPropertyService implements IGlobalPropertyService {

    private static final class Key implements IPropertyKey {

        final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && Objects.equals(name, ((Key) other).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) NyxLaunch.properties().get(((Key) key).name);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        NyxLaunch.properties().put(((Key) key).name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) NyxLaunch.properties().getOrDefault(((Key) key).name, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = NyxLaunch.properties().get(((Key) key).name);
        return value != null ? value.toString() : defaultValue;
    }
}
