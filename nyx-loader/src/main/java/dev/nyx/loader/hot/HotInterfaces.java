package dev.nyx.loader.hot;

import dev.nyx.loader.launch.NyxLaunch;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class HotInterfaces {

    private static final Map<String, Map<String, String[]>> INTERFACES = new ConcurrentHashMap<String, Map<String, String[]>>();
    private static final Map<String, Class<?>> TARGETS = new ConcurrentHashMap<String, Class<?>>();

    private HotInterfaces() {
    }

    static void register(String iface, String name, String desc, String bridgeClass, String bridgeName, String bridgeDesc,
            Class<?> target) {
        Map<String, String[]> methods = INTERFACES.computeIfAbsent(iface, key -> new ConcurrentHashMap<String, String[]>());
        methods.put(name + desc, new String[] {bridgeClass, bridgeName, bridgeDesc});
        TARGETS.put(iface, target);
    }

    public static boolean isInstance(Object value, String iface) {
        Class<?> target = TARGETS.get(iface);
        return target != null && target.isInstance(value);
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

    public static boolean active() {
        return !INTERFACES.isEmpty();
    }

    public static byte[] transform(byte[] bytes) {
        if (bytes == null || INTERFACES.isEmpty()) {
            return bytes;
        }
        boolean found = false;
        for (String iface : INTERFACES.keySet()) {
            if (contains(bytes, iface.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                found = true;
                break;
            }
        }
        if (!found) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            InsnList list = method.instructions;
            for (AbstractInsnNode insn = list.getFirst(); insn != null;) {
                AbstractInsnNode following = insn.getNext();
                if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    Map<String, String[]> methods = INTERFACES.get(call.owner);
                    String[] bridge = methods == null ? null : methods.get(call.name + call.desc);
                    if (bridge != null) {
                        list.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, bridge[0], bridge[1], bridge[2], false));
                        changed = true;
                    }
                } else if (insn instanceof TypeInsnNode && INTERFACES.containsKey(((TypeInsnNode) insn).desc)) {
                    if (insn.getOpcode() == Opcodes.CHECKCAST) {
                        list.remove(insn);
                        changed = true;
                    } else if (insn.getOpcode() == Opcodes.INSTANCEOF) {
                        String iface = ((TypeInsnNode) insn).desc;
                        InsnList replacement = new InsnList();
                        replacement.add(new LdcInsnNode(iface));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/nyx/loader/hot/HotInterfaces",
                                "isInstance", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                        list.insert(insn, replacement);
                        list.remove(insn);
                        changed = true;
                    }
                }
                insn = following;
            }
        }
        if (!changed) {
            return bytes;
        }
        final java.util.Set<String> hidden = new java.util.HashSet<String>(INTERFACES.keySet());
        ClassWriter writer = new ClassWriter(node.version >= Opcodes.V1_7 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String first, String second) {
                if (first.equals(second)) {
                    return first;
                }
                if (hidden.contains(first) || hidden.contains(second)) {
                    return "java/lang/Object";
                }
                try {
                    ClassLoader loader = NyxLaunch.gameClassLoader();
                    Class<?> a = Class.forName(first.replace('/', '.'), false, loader);
                    Class<?> b = Class.forName(second.replace('/', '.'), false, loader);
                    if (a.isAssignableFrom(b)) {
                        return first;
                    }
                    if (b.isAssignableFrom(a)) {
                        return second;
                    }
                    if (a.isInterface() || b.isInterface()) {
                        return "java/lang/Object";
                    }
                    do {
                        a = a.getSuperclass();
                    } while (!a.isAssignableFrom(b));
                    return a.getName().replace('.', '/');
                } catch (ClassNotFoundException | LinkageError error) {
                    return "java/lang/Object";
                }
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }
}
