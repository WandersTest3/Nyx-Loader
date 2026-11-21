package dev.nyx.loader.fabric;

import java.util.Iterator;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class EnvironmentStripper {

    private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
    private static final String INTERFACE = "Lnet/fabricmc/api/EnvironmentInterface;";
    private static final String INTERFACES = "Lnet/fabricmc/api/EnvironmentInterfaces;";
    private static final byte[] MARKER = "net/fabricmc/api/Environment".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private EnvironmentStripper() {
    }

    public static byte[] strip(String name, byte[] bytes) {
        if (bytes == null || !contains(bytes, MARKER)) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (isServerOnly(node.invisibleAnnotations) || isServerOnly(node.visibleAnnotations)) {
            throw new IllegalStateException("Cannot load class " + name.replace('/', '.')
                    + " in environment type CLIENT");
        }
        boolean changed = false;
        changed |= stripInterfaces(node, node.invisibleAnnotations);
        changed |= stripInterfaces(node, node.visibleAnnotations);
        for (Iterator<FieldNode> it = node.fields.iterator(); it.hasNext();) {
            FieldNode field = it.next();
            if (isServerOnly(field.invisibleAnnotations) || isServerOnly(field.visibleAnnotations)) {
                it.remove();
                changed = true;
            }
        }
        for (Iterator<MethodNode> it = node.methods.iterator(); it.hasNext();) {
            MethodNode method = it.next();
            if (isServerOnly(method.invisibleAnnotations) || isServerOnly(method.visibleAnnotations)) {
                it.remove();
                changed = true;
            }
        }
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isServerOnly(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (ENVIRONMENT.equals(annotation.desc) && "SERVER".equals(enumValue(annotation, "value"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean stripInterfaces(ClassNode node, List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        boolean changed = false;
        for (AnnotationNode annotation : annotations) {
            if (INTERFACE.equals(annotation.desc)) {
                changed |= stripInterface(node, annotation);
            } else if (INTERFACES.equals(annotation.desc) && annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if ("value".equals(annotation.values.get(i))) {
                        for (Object item : (List<?>) annotation.values.get(i + 1)) {
                            changed |= stripInterface(node, (AnnotationNode) item);
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean stripInterface(ClassNode node, AnnotationNode annotation) {
        if (!"SERVER".equals(enumValue(annotation, "value"))) {
            return false;
        }
        Object itf = value(annotation, "itf");
        if (itf instanceof Type) {
            return node.interfaces.remove(((Type) itf).getInternalName());
        }
        return false;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static String enumValue(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value instanceof String[] && ((String[]) value).length == 2) {
            return ((String[]) value)[1];
        }
        return null;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
