package dev.nyx.loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class GlCompat {

    private static final int GL_BYTE = 5120;
    private static final int GL_UNSIGNED_BYTE = 5121;
    private static final int GL_SHORT = 5122;
    private static final int GL_UNSIGNED_SHORT = 5123;
    private static final int GL_FLOAT = 5126;

    private static final Class<?> ARB = classOrNull("org.lwjgl.opengl.ARBVertexShader");
    private static final Class<?> GL20 = classOrNull("org.lwjgl.opengl.GL20");
    private static final Object UNSAFE;
    private static final Method GET_OBJECT;
    private static final Method GET_LONG;
    private static final Method FIELD_OFFSET;
    private static final long BUFFER_ADDRESS;

    private static Method arbFloat;
    private static Method arbShort;
    private static Method arbByte;
    private static Method gl20Pointer;
    private static boolean resolved;
    private static boolean warned;

    static {
        Object unsafe = null;
        Method getObject = null;
        Method getLong = null;
        Method fieldOffset = null;
        long address = -1L;
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field field = type.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);
            getObject = type.getMethod("getObject", Object.class, long.class);
            getLong = type.getMethod("getLong", Object.class, long.class);
            fieldOffset = type.getMethod("objectFieldOffset", Field.class);
            address = (Long) fieldOffset.invoke(unsafe, Buffer.class.getDeclaredField("address"));
        } catch (Throwable error) {
            unsafe = null;
        }
        UNSAFE = unsafe;
        GET_OBJECT = getObject;
        GET_LONG = getLong;
        FIELD_OFFSET = fieldOffset;
        BUFFER_ADDRESS = address;
    }

    private GlCompat() {
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable error) {
            return null;
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... types) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, types);
        } catch (Throwable error) {
            return null;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        arbFloat = find(ARB, "glVertexAttribPointerARB", int.class, int.class, boolean.class, int.class, FloatBuffer.class);
        arbShort = find(ARB, "glVertexAttribPointerARB", int.class, int.class, boolean.class, boolean.class, int.class,
                ShortBuffer.class);
        arbByte = find(ARB, "glVertexAttribPointerARB", int.class, int.class, boolean.class, boolean.class, int.class,
                ByteBuffer.class);
        gl20Pointer = find(GL20, "glVertexAttribPointer", int.class, int.class, int.class, boolean.class, int.class,
                ByteBuffer.class);
    }

    public static void attribPointer(int index, int size, boolean normalized, int stride, FloatBuffer buffer) {
        resolve();
        if (arbFloat != null && invoke(arbFloat, index, size, normalized, stride, buffer)) {
            return;
        }
        fallback(index, size, GL_FLOAT, normalized, stride, buffer, 4);
    }

    public static void attribPointer(int index, int size, boolean unsigned, boolean normalized, int stride,
            ShortBuffer buffer) {
        resolve();
        if (arbShort != null && invoke(arbShort, index, size, unsigned, normalized, stride, buffer)) {
            return;
        }
        fallback(index, size, unsigned ? GL_UNSIGNED_SHORT : GL_SHORT, normalized, stride, buffer, 2);
    }

    public static void attribPointer(int index, int size, boolean unsigned, boolean normalized, int stride,
            ByteBuffer buffer) {
        resolve();
        if (arbByte != null && invoke(arbByte, index, size, unsigned, normalized, stride, buffer)) {
            return;
        }
        fallback(index, size, unsigned ? GL_UNSIGNED_BYTE : GL_BYTE, normalized, stride, buffer, 1);
    }

    private static boolean invoke(Method method, Object... args) {
        try {
            method.invoke(null, args);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void fallback(int index, int size, int type, boolean normalized, int stride, Buffer buffer,
            int elementSize) {
        resolve();
        ByteBuffer bytes = asBytes(buffer, elementSize);
        if (gl20Pointer != null && bytes != null && invoke(gl20Pointer, index, size, type, normalized, stride, bytes)) {
            return;
        }
        if (!warned) {
            warned = true;
            NyxRuntime.log("glVertexAttribPointer is not available in this LWJGL; attribute " + index + " is skipped");
        }
    }

    private static ByteBuffer asBytes(Buffer buffer, int elementSize) {
        if (buffer instanceof ByteBuffer) {
            return (ByteBuffer) buffer;
        }
        if (UNSAFE == null || !buffer.isDirect()) {
            return null;
        }
        try {
            ByteBuffer parent = null;
            for (String fieldName : new String[] {"att", "bb"}) {
                try {
                    Field backing = buffer.getClass().getDeclaredField(fieldName);
                    Object value = GET_OBJECT.invoke(UNSAFE, buffer, (Long) FIELD_OFFSET.invoke(UNSAFE, backing));
                    if (value instanceof ByteBuffer && ((ByteBuffer) value).isDirect()) {
                        parent = (ByteBuffer) value;
                        break;
                    }
                } catch (NoSuchFieldException ignored) {
                    continue;
                }
            }
            if (parent == null) {
                return null;
            }
            long start = (Long) GET_LONG.invoke(UNSAFE, buffer, BUFFER_ADDRESS)
                    - (Long) GET_LONG.invoke(UNSAFE, parent, BUFFER_ADDRESS);
            ByteBuffer view = parent.duplicate();
            view.clear();
            view.position((int) start + buffer.position() * elementSize);
            return view;
        } catch (Throwable error) {
            return null;
        }
    }
}
