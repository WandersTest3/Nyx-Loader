package dev.nyx.loader.fabric;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.metadata.CustomValue;

abstract class CustomValueImpl implements CustomValue {

    static CustomValue of(Object raw) {
        if (raw instanceof Map) {
            Map<String, CustomValue> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                values.put(String.valueOf(entry.getKey()), of(entry.getValue()));
            }
            return new ObjectImpl(values);
        }
        if (raw instanceof List) {
            List<CustomValue> values = new ArrayList<>();
            for (Object item : (List<?>) raw) {
                values.add(of(item));
            }
            return new ArrayImpl(values);
        }
        if (raw instanceof String) {
            return new Simple(CvType.STRING, raw);
        }
        if (raw instanceof Number) {
            return new Simple(CvType.NUMBER, raw);
        }
        if (raw instanceof Boolean) {
            return new Simple(CvType.BOOLEAN, raw);
        }
        return new Simple(CvType.NULL, null);
    }

    @Override
    public CvObject getAsObject() {
        throw new ClassCastException("Not an object: " + getType());
    }

    @Override
    public CvArray getAsArray() {
        throw new ClassCastException("Not an array: " + getType());
    }

    @Override
    public String getAsString() {
        throw new ClassCastException("Not a string: " + getType());
    }

    @Override
    public Number getAsNumber() {
        throw new ClassCastException("Not a number: " + getType());
    }

    @Override
    public boolean getAsBoolean() {
        throw new ClassCastException("Not a boolean: " + getType());
    }

    static final class Simple extends CustomValueImpl {

        private final CvType type;
        private final Object value;

        Simple(CvType type, Object value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public CvType getType() {
            return type;
        }

        @Override
        public String getAsString() {
            if (type != CvType.STRING) {
                return super.getAsString();
            }
            return (String) value;
        }

        @Override
        public Number getAsNumber() {
            if (type != CvType.NUMBER) {
                return super.getAsNumber();
            }
            return (Number) value;
        }

        @Override
        public boolean getAsBoolean() {
            if (type != CvType.BOOLEAN) {
                return super.getAsBoolean();
            }
            return (Boolean) value;
        }
    }

    static final class ObjectImpl extends CustomValueImpl implements CvObject {

        private final Map<String, CustomValue> values;

        ObjectImpl(Map<String, CustomValue> values) {
            this.values = values;
        }

        @Override
        public CvType getType() {
            return CvType.OBJECT;
        }

        @Override
        public CvObject getAsObject() {
            return this;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean containsKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public CustomValue get(String key) {
            return values.get(key);
        }

        @Override
        public Iterator<Map.Entry<String, CustomValue>> iterator() {
            List<Map.Entry<String, CustomValue>> entries = new ArrayList<>();
            for (Map.Entry<String, CustomValue> entry : values.entrySet()) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
            }
            return entries.iterator();
        }
    }

    static final class ArrayImpl extends CustomValueImpl implements CvArray {

        private final List<CustomValue> values;

        ArrayImpl(List<CustomValue> values) {
            this.values = values;
        }

        @Override
        public CvType getType() {
            return CvType.ARRAY;
        }

        @Override
        public CvArray getAsArray() {
            return this;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public CustomValue get(int index) {
            return values.get(index);
        }

        @Override
        public Iterator<CustomValue> iterator() {
            return values.iterator();
        }
    }
}
