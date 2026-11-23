package dev.nyx.loader.hot;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HotFields {

    private static final Object NULL = new Object();
    private static final Map<String, Object> STATICS = new ConcurrentHashMap<String, Object>();
    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<Object>();
    private static final Map<Ref, Map<String, Object>> INSTANCES = new HashMap<Ref, Map<String, Object>>();

    private HotFields() {
    }

    private static final class Ref extends WeakReference<Object> {
        private final int hash;

        Ref(Object value, boolean queued) {
            super(value, queued ? QUEUE : null);
            hash = System.identityHashCode(value);
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Ref)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == ((Ref) other).get();
        }
    }

    public static void initStatic(String key, Object value) {
        STATICS.put(key, value == null ? NULL : value);
    }

    private static Object read(Object self, String key) {
        synchronized (INSTANCES) {
            purge();
            Map<String, Object> values = INSTANCES.get(new Ref(self, false));
            return values == null ? null : values.get(key);
        }
    }

    private static void write(Object self, String key, Object value) {
        synchronized (INSTANCES) {
            purge();
            Ref ref = new Ref(self, true);
            Map<String, Object> values = INSTANCES.get(ref);
            if (values == null) {
                values = new HashMap<String, Object>();
                INSTANCES.put(ref, values);
            }
            values.put(key, value == null ? NULL : value);
        }
    }

    private static void purge() {
        Object stale;
        while ((stale = QUEUE.poll()) != null) {
            INSTANCES.remove(stale);
        }
    }

    private static Object unwrap(Object value) {
        return value == NULL ? null : value;
    }

    public static Object getL(Object self, String key) {
        return unwrap(read(self, key));
    }

    public static boolean getZ(Object self, String key) {
        Object value = read(self, key);
        return value != null && (Boolean) value;
    }

    public static byte getB(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0 : (Byte) value;
    }

    public static char getC(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0 : (Character) value;
    }

    public static short getS(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0 : (Short) value;
    }

    public static int getI(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0 : (Integer) value;
    }

    public static long getJ(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0L : (Long) value;
    }

    public static float getF(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0.0F : (Float) value;
    }

    public static double getD(Object self, String key) {
        Object value = read(self, key);
        return value == null ? 0.0D : (Double) value;
    }

    public static void putL(Object self, Object value, String key) {
        write(self, key, value);
    }

    public static void putZ(Object self, boolean value, String key) {
        write(self, key, value);
    }

    public static void putB(Object self, byte value, String key) {
        write(self, key, value);
    }

    public static void putC(Object self, char value, String key) {
        write(self, key, value);
    }

    public static void putS(Object self, short value, String key) {
        write(self, key, value);
    }

    public static void putI(Object self, int value, String key) {
        write(self, key, value);
    }

    public static void putJ(Object self, long value, String key) {
        write(self, key, value);
    }

    public static void putF(Object self, float value, String key) {
        write(self, key, value);
    }

    public static void putD(Object self, double value, String key) {
        write(self, key, value);
    }

    public static Object sgetL(String key) {
        return unwrap(STATICS.get(key));
    }

    public static boolean sgetZ(String key) {
        Object value = STATICS.get(key);
        return value != null && (value instanceof Boolean ? (Boolean) value : ((Number) value).intValue() != 0);
    }

    public static byte sgetB(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0 : ((Number) value).byteValue();
    }

    public static char sgetC(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0 : value instanceof Character ? (Character) value : (char) ((Number) value).intValue();
    }

    public static short sgetS(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0 : ((Number) value).shortValue();
    }

    public static int sgetI(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static long sgetJ(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static float sgetF(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0.0F : ((Number) value).floatValue();
    }

    public static double sgetD(String key) {
        Object value = STATICS.get(key);
        return value == null ? 0.0D : ((Number) value).doubleValue();
    }

    public static void sputL(Object value, String key) {
        STATICS.put(key, value == null ? NULL : value);
    }

    public static void sputZ(boolean value, String key) {
        STATICS.put(key, value);
    }

    public static void sputB(byte value, String key) {
        STATICS.put(key, value);
    }

    public static void sputC(char value, String key) {
        STATICS.put(key, value);
    }

    public static void sputS(short value, String key) {
        STATICS.put(key, value);
    }

    public static void sputI(int value, String key) {
        STATICS.put(key, value);
    }

    public static void sputJ(long value, String key) {
        STATICS.put(key, value);
    }

    public static void sputF(float value, String key) {
        STATICS.put(key, value);
    }

    public static void sputD(double value, String key) {
        STATICS.put(key, value);
    }
}
