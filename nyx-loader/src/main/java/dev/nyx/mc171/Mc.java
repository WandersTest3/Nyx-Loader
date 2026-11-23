package dev.nyx.mc171;

import dev.nyx.loader.launch.NyxLaunch;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

final class Mc {

    private static final Map<String, Field> FIELDS = new HashMap<String, Field>();
    private static final Map<String, Method> METHODS = new HashMap<String, Method>();

    private Mc() {
    }

    static Class<?> cls(String intermediary) throws ClassNotFoundException {
        String name = NyxLaunch.names().map("net/minecraft/" + intermediary).replace('/', '.');
        return Class.forName(name, false, NyxLaunch.gameClassLoader());
    }

    static synchronized Field field(Class<?> type, String intermediary) throws NoSuchFieldException {
        String key = type.getName() + "#" + intermediary;
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        String name = NyxLaunch.names().field(intermediary);
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                FIELDS.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                continue;
            }
        }
        throw new NoSuchFieldException(intermediary + " (" + name + ") in " + type.getName());
    }

    static synchronized Method method(Class<?> type, String intermediary, int parameters) throws NoSuchMethodException {
        String key = type.getName() + "#" + intermediary + "/" + parameters;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        String name = NyxLaunch.names().method(intermediary);
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                    method.setAccessible(true);
                    METHODS.put(key, method);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(intermediary + " (" + name + ") in " + type.getName());
    }

    static Object get(Object target, String intermediary) throws ReflectiveOperationException {
        return field(target.getClass(), intermediary).get(target);
    }

    static Object getStatic(Class<?> type, String intermediary) throws ReflectiveOperationException {
        return field(type, intermediary).get(null);
    }

    static void set(Object target, String intermediary, Object value) throws ReflectiveOperationException {
        field(target.getClass(), intermediary).set(target, value);
    }

    static Object call(Object target, String intermediary, Object... args) throws ReflectiveOperationException {
        return method(target.getClass(), intermediary, args.length).invoke(target, args);
    }

    static Object construct(String intermediary, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Constructor<?> constructor = cls(intermediary).getConstructor(types);
        return constructor.newInstance(args);
    }
}
