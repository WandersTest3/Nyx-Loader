package dev.nyx.loader.forge;

import dev.nyx.loader.NyxRuntime;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ForgeFixes {

    private static final String ENUM_HELPER = "net/minecraftforge/common/util/EnumHelper";
    private static final String COMPAT = "dev/nyx/loader/forge/ForgeCompat";

    private static final String PATCH_MANAGER = "cpw/mods/fml/common/patcher/ClassPatchManager";

    private static final String EVENT_TRANSFORMER = "cpw/mods/fml/common/asm/transformers/EventSubscriptionTransformer";
    private static final String OBJECT_HOLDER = "cpw/mods/fml/common/registry/ObjectHolderRef";
    private static final String ITEMSTACK_HOLDER = "cpw/mods/fml/common/registry/ItemStackHolderRef";

    private ForgeFixes() {
    }

    private static final byte[] DECLARED_FIELD = "getDeclaredField".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    private static final byte[] ATTRIB_POINTER = "glVertexAttribPointerARB".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    private static final byte[] ASM_MARKER = "org/objectweb/asm/".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

    private static byte[] redirectAttribPointer(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC && "org/lwjgl/opengl/ARBVertexShader".equals(call.owner)
                            && "glVertexAttribPointerARB".equals(call.name) && call.desc.endsWith("Buffer;)V")) {
                        call.owner = "dev/nyx/loader/GlCompat";
                        call.name = "attribPointer";
                        changed = true;
                    }
                }
            }
        }
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static byte[] transform(String name, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (name.startsWith("net/minecraft/") && !name.startsWith("net/minecraft/launchwrapper/")) {
            bytes = unfinalStatics(bytes);
        } else if (!name.startsWith("org/objectweb/asm/") && contains(bytes, ASM_MARKER)) {
            bytes = upgradeAsmApi(name, bytes);
        }
        if (!name.startsWith("dev/nyx/") && contains(bytes, DECLARED_FIELD)) {
            bytes = redirectDeclaredField(bytes);
        }
        if (!name.startsWith("dev/nyx/") && contains(bytes, ATTRIB_POINTER)) {
            bytes = redirectAttribPointer(bytes);
        }
        if (ENUM_HELPER.equals(name)) {
            return patchEnumHelper(bytes);
        }
        if (PATCH_MANAGER.equals(name)) {
            return replacePack200(bytes);
        }
        if ("cpw/mods/fml/relauncher/CoreModManager".equals(name)) {
            return guardCascadingTweak(bytes);
        }
        if ("cpw/mods/fml/common/discovery/asm/ASMModParser".equals(name)) {
            return fixSuperTypeNull(bytes);
        }
        if (EVENT_TRANSFORMER.equals(name)) {
            return fixSuperNameType(bytes);
        }
        if (OBJECT_HOLDER.equals(name) || ITEMSTACK_HOLDER.equals(name)) {
            return fixHolder(bytes);
        }
        return bytes;
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

    private static byte[] redirectDeclaredField(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && "java/lang/Class".equals(((MethodInsnNode) insn).owner)
                        && "getDeclaredField".equals(((MethodInsnNode) insn).name)) {
                    method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getDeclaredField",
                            "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", false));
                    changed = true;
                }
            }
        }
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] unfinalStatics(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            return bytes;
        }
        boolean[] changed = {false};
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String fieldName, String descriptor,
                    String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0 && value == null
                        && (descriptor.startsWith("L") || descriptor.startsWith("["))) {
                    access &= ~Opcodes.ACC_FINAL;
                    changed[0] = true;
                }
                return super.visitField(access, fieldName, descriptor, signature, value);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : bytes;
    }

    private static final int ASM5 = 5 << 16;
    private static final int ASM9 = 9 << 16;

    private static byte[] upgradeAsmApi(String name, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.LdcInsnNode)
                        || !Integer.valueOf(ASM5).equals(((org.objectweb.asm.tree.LdcInsnNode) insn).cst)) {
                    continue;
                }
                org.objectweb.asm.tree.AbstractInsnNode next = insn.getNext();
                int steps = 0;
                while (next != null && !(next instanceof MethodInsnNode) && steps < 8) {
                    next = next.getNext();
                    steps++;
                }
                if (next instanceof MethodInsnNode && "<init>".equals(((MethodInsnNode) next).name)
                        && ((MethodInsnNode) next).owner.startsWith("org/objectweb/asm/")) {
                    ((org.objectweb.asm.tree.LdcInsnNode) insn).cst = ASM9;
                    changed = true;
                }
            }
        }
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] guardCascadingTweak(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!"handleCascadingTweak".equals(method.name)
                    || !method.desc.startsWith("(Ljava/io/File;Ljava/util/jar/JarFile;Ljava/lang/String;")) {
                continue;
            }
            InsnList list = new InsnList();
            org.objectweb.asm.tree.LabelNode proceed = new org.objectweb.asm.tree.LabelNode();
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
            list.add(new VarInsnNode(Opcodes.ALOAD, 2));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "skipCascadingTweak",
                    "(Ljava/io/File;Ljava/lang/String;)Z", false));
            list.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, proceed));
            list.add(new InsnNode(Opcodes.RETURN));
            list.add(proceed);
            method.instructions.insert(list);
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && "java/lang/reflect/Method".equals(((MethodInsnNode) insn).owner)
                        && "invoke".equals(((MethodInsnNode) insn).name)) {
                    method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "cascadeAddUrl",
                            "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static byte[] removeUnusedMixinHandlers(String name, byte[] before, byte[] after) {
        if (after == null || after == before) {
            return after;
        }
        ClassNode node = new ClassNode();
        new ClassReader(after).accept(node, 0);
        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals(node.name)) {
                        referenced.add(call.name + call.desc);
                    }
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode) {
                    org.objectweb.asm.tree.InvokeDynamicInsnNode indy = (org.objectweb.asm.tree.InvokeDynamicInsnNode) insn;
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof org.objectweb.asm.Handle && ((org.objectweb.asm.Handle) arg).getOwner().equals(node.name)) {
                            referenced.add(((org.objectweb.asm.Handle) arg).getName() + ((org.objectweb.asm.Handle) arg).getDesc());
                        }
                    }
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode
                        && ((org.objectweb.asm.tree.LdcInsnNode) insn).cst instanceof org.objectweb.asm.Handle) {
                    org.objectweb.asm.Handle handle = (org.objectweb.asm.Handle) ((org.objectweb.asm.tree.LdcInsnNode) insn).cst;
                    referenced.add(handle.getName() + handle.getDesc());
                }
            }
        }
        int removed = 0;
        for (java.util.Iterator<MethodNode> it = node.methods.iterator(); it.hasNext();) {
            MethodNode method = it.next();
            if (!isInjectorHandler(method) || referenced.contains(method.name + method.desc)) {
                continue;
            }
            it.remove();
            removed++;
            NyxRuntime.log("Removed unused Fabric mixin handler " + method.name + " from " + name
                    + " (its injection did not match the Forge version of this class)");
        }
        if (removed == 0) {
            return after;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isInjectorHandler(MethodNode method) {
        int dollar = method.name.indexOf('$');
        if (dollar <= 0) {
            return false;
        }
        String prefix = method.name.substring(0, dollar);
        if (!(prefix.equals("handler") || prefix.equals("redirect") || prefix.equals("modify")
                || prefix.equals("localvar") || prefix.equals("constant") || prefix.equals("wrapOperation")
                || prefix.equals("wrapWithCondition") || prefix.equals("modifyExpressionValue")
                || prefix.equals("modifyReturnValue") || prefix.equals("modifyReceiver"))) {
            return false;
        }
        if (method.invisibleAnnotations == null) {
            return false;
        }
        for (org.objectweb.asm.tree.AnnotationNode annotation : method.invisibleAnnotations) {
            if (annotation.desc.endsWith("/MixinMerged;")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] fixSuperTypeNull(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!"getASMSuperType".equals(method.name)) {
                continue;
            }
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.ARETURN) {
                    InsnList list = new InsnList();
                    org.objectweb.asm.tree.LabelNode ok = new org.objectweb.asm.tree.LabelNode();
                    list.add(new InsnNode(Opcodes.DUP));
                    list.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNONNULL, ok));
                    list.add(new InsnNode(Opcodes.POP));
                    list.add(new org.objectweb.asm.tree.LdcInsnNode("java/lang/Object"));
                    list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/objectweb/asm/Type", "getObjectType",
                            "(Ljava/lang/String;)Lorg/objectweb/asm/Type;", false));
                    list.add(ok);
                    method.instructions.insertBefore(insn, list);
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] fixSuperNameType(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && "org/objectweb/asm/Type".equals(((MethodInsnNode) insn).owner)
                        && "getType".equals(((MethodInsnNode) insn).name)
                        && "(Ljava/lang/String;)Lorg/objectweb/asm/Type;".equals(((MethodInsnNode) insn).desc)
                        && insn.getPrevious() instanceof FieldInsnNode
                        && "superName".equals(((FieldInsnNode) insn.getPrevious()).name)) {
                    ((MethodInsnNode) insn).name = "getObjectType";
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] fixHolder(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if ("makeWritable".equals(method.name)) {
                method.instructions = new InsnList();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                continue;
            }
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && "java/lang/reflect/Method".equals(((MethodInsnNode) insn).owner)
                        && "invoke".equals(((MethodInsnNode) insn).name)) {
                    method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "holderInvoke",
                            "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] replacePack200(byte[] bytes) {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        names.put("java/util/jar/Pack200", "org/apache/commons/compress/java/util/jar/Pack200");
        names.put("java/util/jar/Pack200$Unpacker", "org/apache/commons/compress/java/util/jar/Pack200$Unpacker");
        names.put("java/util/jar/Pack200$Packer", "org/apache/commons/compress/java/util/jar/Pack200$Packer");
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(new org.objectweb.asm.commons.ClassRemapper(node,
                new org.objectweb.asm.commons.SimpleRemapper(names)), 0);
        for (MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.endsWith("Pack200$Unpacker")
                        && "unpack".equals(((MethodInsnNode) insn).name)
                        && ((MethodInsnNode) insn).desc.startsWith("(Ljava/io/InputStream;")) {
                    method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "unpack200",
                            "(Ljava/lang/Object;Ljava/io/InputStream;Ljava/util/jar/JarOutputStream;)V", false));
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        NyxRuntime.log("Forge binary patches will use the Commons Compress Pack200 unpacker");
        return writer.toByteArray();
    }

    private static byte[] patchEnumHelper(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode method : node.methods) {
            InsnList body = null;
            if ("setup".equals(method.name) && "()V".equals(method.desc)) {
                body = new InsnList();
                body.add(new InsnNode(Opcodes.ICONST_1));
                body.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENUM_HELPER, "isSetup", "Z"));
                body.add(new InsnNode(Opcodes.RETURN));
            } else if ("makeEnum".equals(method.name)) {
                body = new InsnList();
                body.add(new VarInsnNode(Opcodes.ALOAD, 0));
                body.add(new VarInsnNode(Opcodes.ALOAD, 1));
                body.add(new VarInsnNode(Opcodes.ILOAD, 2));
                body.add(new VarInsnNode(Opcodes.ALOAD, 3));
                body.add(new VarInsnNode(Opcodes.ALOAD, 4));
                body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "makeEnum",
                        "(Ljava/lang/Class;Ljava/lang/String;I[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;", false));
                body.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Enum"));
                body.add(new InsnNode(Opcodes.ARETURN));
            } else if ("setFailsafeFieldValue".equals(method.name)) {
                body = new InsnList();
                body.add(new VarInsnNode(Opcodes.ALOAD, 0));
                body.add(new VarInsnNode(Opcodes.ALOAD, 1));
                body.add(new VarInsnNode(Opcodes.ALOAD, 2));
                body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "setFailsafeFieldValue",
                        "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                body.add(new InsnNode(Opcodes.RETURN));
            }
            if (body != null) {
                method.instructions = body;
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                patched++;
            }
        }
        NyxRuntime.log("EnumHelper adapted for Java 21 (" + patched + " methods)");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
