package dev.nyx.loader.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class TinyMappings {

    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> classesReverse = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();
    private final java.util.List<String[]> methodEntries = new java.util.ArrayList<>();
    private final java.util.List<String[]> fieldEntries = new java.util.ArrayList<>();

    private TinyMappings() {
    }

    public static TinyMappings read(Reader source) throws IOException {
        TinyMappings mappings = new TinyMappings();
        BufferedReader reader = new BufferedReader(source);
        String header = reader.readLine();
        if (header == null) {
            throw new IOException("Empty mappings");
        }
        String[] head = header.split("\t");
        if (head[0].equals("v1")) {
            int from = indexOf(head, "official", 1);
            int to = indexOf(head, "intermediary", 1);
            mappings.readV1(reader, from, to);
        } else if (head[0].equals("tiny") && head.length > 1 && head[1].equals("2")) {
            int from = indexOf(head, "official", 3);
            int to = indexOf(head, "intermediary", 3);
            mappings.readV2(reader, from, to);
        } else {
            throw new IOException("Unsupported mappings header: " + header);
        }
        return mappings;
    }

    private static int indexOf(String[] header, String namespace, int offset) throws IOException {
        for (int i = offset; i < header.length; i++) {
            if (header[i].equals(namespace)) {
                return i - offset;
            }
        }
        throw new IOException("Namespace " + namespace + " missing in mappings");
    }

    private void readV1(BufferedReader reader, int from, int to) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\t");
            if (parts.length < 2) {
                continue;
            }
            switch (parts[0]) {
                case "CLASS":
                    putClass(parts[1 + from], parts[1 + to]);
                    break;
                case "FIELD":
                    fields.put(parts[1] + "/" + parts[3 + from], parts[3 + to]);
                    fieldEntries.add(new String[] {parts[1], parts[3 + from], parts[2], parts[3 + to]});
                    break;
                case "METHOD":
                    methods.put(parts[1] + "/" + parts[3 + from] + parts[2], parts[3 + to]);
                    methodEntries.add(new String[] {parts[1], parts[3 + from], parts[2], parts[3 + to]});
                    break;
                default:
                    break;
            }
        }
    }

    private void readV2(BufferedReader reader, int from, int to) throws IOException {
        String line;
        String owner = null;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '\t') {
                depth++;
            }
            String[] parts = line.substring(depth).split("\t", -1);
            if (depth == 0 && parts[0].equals("c")) {
                owner = parts[1 + from];
                putClass(owner, parts[1 + to]);
            } else if (depth == 1 && owner != null && parts[0].equals("f")) {
                fields.put(owner + "/" + parts[2 + from], parts[2 + to]);
                fieldEntries.add(new String[] {owner, parts[2 + from], parts[1], parts[2 + to]});
            } else if (depth == 1 && owner != null && parts[0].equals("m")) {
                methods.put(owner + "/" + parts[2 + from] + parts[1], parts[2 + to]);
                methodEntries.add(new String[] {owner, parts[2 + from], parts[1], parts[2 + to]});
            }
        }
    }

    private void putClass(String from, String to) {
        if (to == null || to.isEmpty()) {
            to = from;
        }
        classes.put(from, to);
        classesReverse.put(to, from);
    }

    public java.util.List<String[]> methodEntries() {
        return methodEntries;
    }

    public java.util.List<String[]> fieldEntries() {
        return fieldEntries;
    }

    public Map<String, String> classEntries() {
        return classes;
    }

    public boolean hasClass(String name) {
        return classes.containsKey(name);
    }

    public String mapClass(String name) {
        String mapped = classes.get(name);
        if (mapped != null) {
            return mapped;
        }
        int inner = name.lastIndexOf('$');
        if (inner > 0) {
            String outer = classes.get(name.substring(0, inner));
            if (outer != null) {
                return outer + name.substring(inner);
            }
        }
        return name;
    }

    public String unmapClass(String name) {
        String mapped = classesReverse.get(name);
        if (mapped != null) {
            return mapped;
        }
        int inner = name.lastIndexOf('$');
        if (inner > 0) {
            String outer = classesReverse.get(name.substring(0, inner));
            if (outer != null) {
                return outer + name.substring(inner);
            }
        }
        return name;
    }

    public String fieldName(String owner, String name) {
        return fields.get(owner + "/" + name);
    }

    public String methodName(String owner, String name, String descriptor) {
        return methods.get(owner + "/" + name + descriptor);
    }
}
