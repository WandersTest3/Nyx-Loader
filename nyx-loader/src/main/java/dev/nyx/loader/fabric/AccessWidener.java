package dev.nyx.loader.fabric;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class AccessWidener {

    private static final int ACCESSIBLE = 1;
    private static final int EXTENDABLE = 2;
    private static final int MUTABLE = 4;

    private final Map<String, Integer> classes = new HashMap<>();
    private final Map<String, Integer> methods = new HashMap<>();
    private final Map<String, Integer> fields = new HashMap<>();
    private final Set<String> targets = new HashSet<>();

    public void read(Reader source, String where, dev.nyx.loader.launch.RuntimeNames names) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String header = reader.readLine();
        while (header != null && header.trim().isEmpty()) {
            header = reader.readLine();
        }
        if (header == null) {
            return;
        }
        String[] head = header.trim().split("\\s+");
        if (head.length < 3 || !(head[0].equals("accessWidener") || head[0].equals("classTweaker"))) {
            throw new IOException("Invalid access widener header in " + where + ": " + header);
        }
        if (!head[2].equals("intermediary")) {
            throw new IOException("Access widener " + where + " uses namespace " + head[2]
                    + ", but the runtime namespace is intermediary");
        }
        String line;
        int number = 1;
        while ((line = reader.readLine()) != null) {
            number++;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            String access = parts[0];
            if (access.startsWith("transitive-")) {
                access = access.substring("transitive-".length());
            }
            int flag;
            switch (access) {
                case "accessible":
                    flag = ACCESSIBLE;
                    break;
                case "extendable":
                    flag = EXTENDABLE;
                    break;
                case "mutable":
                    flag = MUTABLE;
                    break;
                case "inject-interface":
                    continue;
                default:
                    throw new IOException("Unknown access " + parts[0] + " in " + where + ":" + number);
            }
            if (parts.length < 3) {
                throw new IOException("Invalid line in " + where + ":" + number);
            }
            String kind = parts[1];
            String owner = names.map(parts[2]);
            if (parts.length >= 5) {
                parts[3] = kind.equals("method") ? names.method(parts[3]) : names.field(parts[3]);
                parts[4] = kind.equals("method") ? names.mapMethodDesc(parts[4]) : names.mapDesc(parts[4]);
            }
            switch (kind) {
                case "class":
                    if (flag == MUTABLE) {
                        throw new IOException("Classes can't be mutable in " + where + ":" + number);
                    }
                    classes.merge(owner, flag, (a, b) -> a | b);
                    targets.add(owner);
                    break;
                case "method":
                    if (parts.length < 5 || flag == MUTABLE) {
                        throw new IOException("Invalid method entry in " + where + ":" + number);
                    }
                    methods.merge(owner + "/" + parts[3] + parts[4], flag, (a, b) -> a | b);
                    classes.merge(owner, flag == EXTENDABLE ? EXTENDABLE : ACCESSIBLE, (a, b) -> a | b);
                    targets.add(owner);
                    break;
                case "field":
                    if (parts.length < 5 || flag == EXTENDABLE) {
                        throw new IOException("Invalid field entry in " + where + ":" + number);
                    }
                    fields.merge(owner + "/" + parts[3], flag, (a, b) -> a | b);
                    if (flag == ACCESSIBLE) {
                        classes.merge(owner, ACCESSIBLE, (a, b) -> a | b);
                    }
                    targets.add(owner);
                    break;
                default:
                    throw new IOException("Unknown kind " + kind + " in " + where + ":" + number);
            }
        }
    }

    public boolean isEmpty() {
        return targets.isEmpty();
    }

    public byte[] apply(String name, byte[] bytes) {
        if (bytes == null || targets.isEmpty()) {
            return bytes;
        }
        boolean outer = targets.contains(name);
        boolean inner = false;
        if (!outer) {
            for (String target : targets) {
                if (target.startsWith(name + "$")) {
                    inner = true;
                    break;
                }
            }
            if (!inner) {
                return bytes;
            }
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new Visitor(writer, name), 0);
        return writer.toByteArray();
    }

    private static int makePublic(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    private static int makeProtected(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return access;
        }
        return (access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
    }

    private int classAccess(String name, int access) {
        Integer flags = classes.get(name);
        if (flags == null) {
            return access;
        }
        if ((flags & (ACCESSIBLE | EXTENDABLE)) != 0) {
            access = makePublic(access);
        }
        if ((flags & EXTENDABLE) != 0) {
            access &= ~Opcodes.ACC_FINAL;
        }
        return access;
    }

    private final class Visitor extends ClassVisitor {

        private final String name;
        private final Set<String> widenedPrivate = new HashSet<>();

        Visitor(ClassVisitor next, String name) {
            super(Opcodes.ASM9, next);
            this.name = name;
        }

        @Override
        public void visit(int version, int access, String className, String signature, String superName,
                String[] interfaces) {
            super.visit(version, classAccess(className, access), className, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String innerName, String outerName, String simpleName, int access) {
            super.visitInnerClass(innerName, outerName, simpleName, classAccess(innerName, access));
        }

        @Override
        public FieldVisitor visitField(int access, String fieldName, String descriptor, String signature,
                Object value) {
            Integer flags = fields.get(name + "/" + fieldName);
            if (flags != null) {
                if ((flags & ACCESSIBLE) != 0) {
                    access = makePublic(access);
                }
                if ((flags & MUTABLE) != 0) {
                    access &= ~Opcodes.ACC_FINAL;
                }
            }
            return super.visitField(access, fieldName, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                String[] exceptions) {
            Integer flags = methods.get(name + "/" + methodName + descriptor);
            if (flags != null) {
                boolean wasPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
                boolean special = methodName.startsWith("<") || (access & Opcodes.ACC_STATIC) != 0;
                if ((flags & ACCESSIBLE) != 0) {
                    access = makePublic(access);
                    if (wasPrivate && !special && (flags & EXTENDABLE) == 0) {
                        access |= Opcodes.ACC_FINAL;
                    }
                }
                if ((flags & EXTENDABLE) != 0) {
                    access = makeProtected(access) & ~Opcodes.ACC_FINAL;
                }
                if (wasPrivate && !special) {
                    widenedPrivate.add(methodName + descriptor);
                }
            }
            MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, visitor) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String target, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESPECIAL && owner.equals(name) && !target.startsWith("<")
                            && methods.containsKey(name + "/" + target + desc)) {
                        opcode = itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                    }
                    super.visitMethodInsn(opcode, owner, target, desc, itf);
                }
            };
        }
    }
}
