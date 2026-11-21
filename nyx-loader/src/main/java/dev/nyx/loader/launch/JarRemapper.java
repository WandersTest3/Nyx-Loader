package dev.nyx.loader.launch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class JarRemapper {

    private static final class Info {

        String superName;
        String[] interfaces = new String[0];
        final Set<String> privateMembers = new HashSet<>();
    }

    private final TinyMappings mappings;
    private final Map<String, Info> hierarchy = new HashMap<>();

    public JarRemapper(TinyMappings mappings) {
        this.mappings = mappings;
    }

    public void remap(Path input, Path output) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(input.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String upper = name.toUpperCase(java.util.Locale.ROOT);
                if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                        || upper.endsWith(".DSA") || upper.endsWith(".EC"))) {
                    continue;
                }
                byte[] bytes;
                try (InputStream stream = zip.getInputStream(entry)) {
                    bytes = readAll(stream);
                }
                if (name.endsWith(".class")) {
                    classes.put(name.substring(0, name.length() - 6), bytes);
                } else {
                    resources.put(name, bytes);
                }
            }
        }
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            ClassReader reader = new ClassReader(entry.getValue());
            Info info = new Info();
            info.superName = reader.getSuperName();
            info.interfaces = reader.getInterfaces();
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                        String signature, Object value) {
                    if ((access & Opcodes.ACC_PRIVATE) != 0) {
                        info.privateMembers.add(name);
                    }
                    return null;
                }

                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_PRIVATE) != 0) {
                        info.privateMembers.add(name + descriptor);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            hierarchy.put(entry.getKey(), info);
        }
        Remapper remapper = new HierarchyRemapper();
        Path temp = Files.createTempFile(output.getParent(), "remap", ".tmp");
        try (OutputStream file = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(file)) {
            Set<String> written = new HashSet<>();
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                ClassReader reader = new ClassReader(entry.getValue());
                ClassWriter writer = new ClassWriter(0);
                ClassVisitor visitor = new ClassRemapper(writer, remapper);
                reader.accept(visitor, 0);
                String name = remapper.map(entry.getKey()) + ".class";
                if (written.add(name)) {
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(writer.toByteArray());
                    zip.closeEntry();
                }
            }
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                if (written.add(entry.getKey())) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
        }
        Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean isPrivate(String owner, String member) {
        Info info = hierarchy.get(owner);
        return info != null && info.privateMembers.contains(member);
    }

    private List<String> parents(String owner) {
        List<String> result = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            result.add(current);
            Info info = hierarchy.get(current);
            if (info == null) {
                continue;
            }
            if (info.superName != null) {
                queue.add(info.superName);
            }
            for (String itf : info.interfaces) {
                queue.add(itf);
            }
        }
        return result;
    }

    private final class HierarchyRemapper extends Remapper {

        private final Map<String, String> methodCache = new HashMap<>();
        private final Map<String, String> fieldCache = new HashMap<>();

        HierarchyRemapper() {
            super();
        }

        @Override
        public String map(String internalName) {
            return mappings.mapClass(internalName);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.startsWith("<") || owner.startsWith("[")) {
                return name;
            }
            String key = owner + "." + name + descriptor;
            String cached = methodCache.get(key);
            if (cached != null) {
                return cached;
            }
            String result = name;
            for (String type : parents(owner)) {
                if (!type.equals(owner) && isPrivate(type, name + descriptor)) {
                    continue;
                }
                String mapped = mappings.methodName(type, name, descriptor);
                if (mapped != null) {
                    result = mapped;
                    break;
                }
            }
            methodCache.put(key, result);
            return result;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String key = owner + "." + name;
            String cached = fieldCache.get(key);
            if (cached != null) {
                return cached;
            }
            String result = name;
            for (String type : parents(owner)) {
                if (!type.equals(owner) && isPrivate(type, name)) {
                    continue;
                }
                String mapped = mappings.fieldName(type, name);
                if (mapped != null) {
                    result = mapped;
                    break;
                }
            }
            fieldCache.put(key, result);
            return result;
        }
    }

    static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
