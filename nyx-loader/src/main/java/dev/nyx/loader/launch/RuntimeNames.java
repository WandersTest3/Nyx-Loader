package dev.nyx.loader.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class RuntimeNames extends Remapper {

    public static final RuntimeNames IDENTITY = new RuntimeNames();

    private static final Pattern CLASS_REF = Pattern.compile("net/minecraft/class_\\d+(\\$[\\w$]+)?");
    private static final Pattern MEMBER_REF = Pattern.compile("\\b(method_\\d+|field_\\d+)\\b");

    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> classesReverse = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final String namespace;

    private RuntimeNames() {
        this.namespace = "intermediary";
    }

    private RuntimeNames(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    public boolean isIdentity() {
        return this == IDENTITY;
    }

    public static RuntimeNames srg(TinyMappings tiny, BufferedReader srg) throws IOException {
        Map<String, String> srgClasses = new HashMap<>();
        Map<String, String> srgFields = new HashMap<>();
        Map<String, String> srgMethods = new HashMap<>();
        String line;
        while ((line = srg.readLine()) != null) {
            String[] parts = line.trim().split(" ");
            if (parts.length < 3) {
                continue;
            }
            switch (parts[0]) {
                case "CL:":
                    srgClasses.put(parts[1], parts[2]);
                    break;
                case "FD:":
                    srgFields.put(parts[1], parts[2].substring(parts[2].lastIndexOf('/') + 1));
                    break;
                case "MD:":
                    if (parts.length >= 5) {
                        srgMethods.put(parts[1] + parts[2], parts[3].substring(parts[3].lastIndexOf('/') + 1));
                    }
                    break;
                default:
                    break;
            }
        }
        RuntimeNames names = new RuntimeNames("srg");
        for (Map.Entry<String, String> entry : tiny.classEntries().entrySet()) {
            String official = entry.getKey();
            String mapped = srgClasses.getOrDefault(official, official);
            names.classes.put(entry.getValue(), mapped);
            names.classesReverse.put(mapped, entry.getValue());
        }
        for (String[] method : tiny.methodEntries()) {
            String mapped = srgMethods.get(method[0] + "/" + method[1] + method[2]);
            if (mapped != null) {
                names.methods.put(method[3], mapped);
            }
        }
        for (String[] field : tiny.fieldEntries()) {
            String mapped = srgFields.get(field[0] + "/" + field[1]);
            if (mapped != null) {
                names.fields.put(field[3], mapped);
            }
        }
        return names;
    }

    @Override
    public String map(String internalName) {
        if (this == IDENTITY || internalName == null) {
            return internalName;
        }
        String mapped = classes.get(internalName);
        if (mapped != null) {
            return mapped;
        }
        int inner = internalName.indexOf('$');
        if (inner > 0) {
            String outer = classes.get(internalName.substring(0, inner));
            if (outer != null) {
                return outer + internalName.substring(inner);
            }
        }
        return internalName;
    }

    public String unmap(String internalName) {
        if (this == IDENTITY || internalName == null) {
            return internalName;
        }
        String mapped = classesReverse.get(internalName);
        return mapped != null ? mapped : internalName;
    }

    public String method(String name) {
        return this == IDENTITY ? name : methods.getOrDefault(name, name);
    }

    public String field(String name) {
        return this == IDENTITY ? name : fields.getOrDefault(name, name);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return method(name);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return field(name);
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return name;
    }

    public String remapString(String value) {
        if (this == IDENTITY || value == null) {
            return value;
        }
        if (value.indexOf("class_") < 0 && value.indexOf("method_") < 0 && value.indexOf("field_") < 0) {
            return value;
        }
        boolean dotted = value.startsWith("net.minecraft.class_");
        String text = dotted ? value.replace('.', '/') : value;
        Matcher classes = CLASS_REF.matcher(text);
        StringBuffer out = new StringBuffer();
        while (classes.find()) {
            classes.appendReplacement(out, Matcher.quoteReplacement(map(classes.group())));
        }
        classes.appendTail(out);
        Matcher members = MEMBER_REF.matcher(out.toString());
        StringBuffer result = new StringBuffer();
        while (members.find()) {
            String name = members.group(1);
            String mapped = name.startsWith("method_") ? method(name) : field(name);
            members.appendReplacement(result, Matcher.quoteReplacement(mapped));
        }
        members.appendTail(result);
        String mapped = result.toString();
        return dotted ? mapped.replace('/', '.') : mapped;
    }

    public byte[] remapClass(byte[] bytes) {
        if (this == IDENTITY) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(Opcodes.ASM9, writer, this) {
            @Override
            protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
                return new StringAnnotationRemapper(descriptor, annotationVisitor, RuntimeNames.this);
            }

            @Override
            protected org.objectweb.asm.MethodVisitor createMethodRemapper(org.objectweb.asm.MethodVisitor methodVisitor) {
                return new org.objectweb.asm.commons.MethodRemapper(Opcodes.ASM9, methodVisitor, RuntimeNames.this) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            value = remapString((String) value);
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    protected AnnotationVisitor createAnnotationRemapper(String descriptor,
                            AnnotationVisitor annotationVisitor) {
                        return new StringAnnotationRemapper(descriptor, annotationVisitor, RuntimeNames.this);
                    }
                };
            }

            @Override
            protected org.objectweb.asm.FieldVisitor createFieldRemapper(org.objectweb.asm.FieldVisitor fieldVisitor) {
                return new org.objectweb.asm.commons.FieldRemapper(Opcodes.ASM9, fieldVisitor, RuntimeNames.this) {
                    @Override
                    protected AnnotationVisitor createAnnotationRemapper(String descriptor,
                            AnnotationVisitor annotationVisitor) {
                        return new StringAnnotationRemapper(descriptor, annotationVisitor, RuntimeNames.this);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static final class StringAnnotationRemapper extends AnnotationRemapper {

        private final RuntimeNames names;

        StringAnnotationRemapper(String descriptor, AnnotationVisitor visitor, RuntimeNames names) {
            super(Opcodes.ASM9, descriptor, visitor, names);
            this.names = names;
        }

        @Override
        public void visit(String name, Object value) {
            if ("require".equals(name) && value instanceof Integer && NyxLaunch.isForge()) {
                value = 0;
            }
            if (value instanceof String) {
                value = names.remapString((String) value);
            }
            super.visit(name, value);
        }

        @Override
        protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
            return new StringAnnotationRemapper(descriptor, annotationVisitor, names);
        }
    }

    public String mapFieldNameFor(String name) {
        return field(name);
    }

    public static List<String> unused() {
        return null;
    }
}
