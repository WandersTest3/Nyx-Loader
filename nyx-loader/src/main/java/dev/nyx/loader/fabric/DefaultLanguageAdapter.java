package dev.nyx.loader.fabric;

import dev.nyx.loader.launch.NyxLaunch;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

public final class DefaultLanguageAdapter implements LanguageAdapter {

    public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

    private DefaultLanguageAdapter() {
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        String[] parts = value.split("::");
        if (parts.length > 2) {
            throw new LanguageAdapterException("Invalid entrypoint definition: " + value);
        }
        Class<?> owner;
        try {
            owner = Class.forName(parts[0], true, NyxLaunch.gameClassLoader());
        } catch (ClassNotFoundException | LinkageError error) {
            throw new LanguageAdapterException("Could not load class " + parts[0], error);
        }
        if (parts.length == 1) {
            if (!type.isAssignableFrom(owner)) {
                throw new LanguageAdapterException("Class " + owner.getName() + " does not implement " + type.getName());
            }
            try {
                Constructor<?> constructor = owner.getDeclaredConstructor();
                constructor.setAccessible(true);
                return type.cast(constructor.newInstance());
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new LanguageAdapterException("Could not instantiate " + owner.getName(), error);
            }
        }
        String member = parts[1];
        List<Method> methods = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(member)) {
                methods.add(method);
            }
        }
        Field field = null;
        try {
            field = owner.getDeclaredField(member);
        } catch (NoSuchFieldException ignored) {
            field = null;
        }
        if (field != null && !methods.isEmpty()) {
            throw new LanguageAdapterException("Ambiguous member " + value);
        }
        if (field != null) {
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new LanguageAdapterException("Field " + value + " must be static");
            }
            if (!type.isAssignableFrom(field.getType())) {
                throw new LanguageAdapterException("Field " + value + " is not a " + type.getName());
            }
            try {
                field.setAccessible(true);
                return type.cast(field.get(null));
            } catch (ReflectiveOperationException error) {
                throw new LanguageAdapterException("Could not read " + value, error);
            }
        }
        if (methods.size() != 1) {
            throw new LanguageAdapterException(methods.isEmpty() ? "Member not found: " + value
                    : "Ambiguous method " + value);
        }
        if (!type.isInterface()) {
            throw new LanguageAdapterException("Method entrypoints need an interface type, got " + type.getName());
        }
        Method method = methods.get(0);
        try {
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            if (!Modifier.isStatic(method.getModifiers())) {
                Constructor<?> constructor = owner.getDeclaredConstructor();
                constructor.setAccessible(true);
                handle = handle.bindTo(constructor.newInstance());
            }
            return MethodHandleProxies.asInterfaceInstance(type, handle);
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new LanguageAdapterException("Could not bind " + value + " to " + type.getName(), error);
        }
    }
}
