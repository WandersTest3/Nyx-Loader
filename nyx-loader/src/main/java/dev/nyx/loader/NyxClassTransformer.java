package dev.nyx.loader;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class NyxClassTransformer implements IClassTransformer {

    private static final String MINECRAFT_CLASS = "net.minecraft.client.Minecraft";
    private static final String HOOKS = "dev/nyx/loader/Hooks";

    private static final String INIT_METHOD = "a";
    private static final String INIT_MARKER = "/font/default.png";
    private static final String TICK_METHOD = "k";
    private static final String TICK_MARKER = "centerChunkSource";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return bytes;
        }
        if (!MINECRAFT_CLASS.equals(transformedName) && !MINECRAFT_CLASS.equals(name)) {
            return bytes;
        }
        try {
            return patch(bytes);
        } catch (Throwable error) {
            NyxRuntime.log("Failed to patch Minecraft, mods will not receive events: " + error);
            return bytes;
        }
    }

    private byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean init = false;
        boolean tick = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!"()V".equals(method.desc)) {
                continue;
            }
            if (INIT_METHOD.equals(method.name) && hasConstant(method, INIT_MARKER)) {
                insertBeforeReturns(method, "onGameStart");
                init = true;
            } else if (TICK_METHOD.equals(method.name) && hasConstant(method, TICK_MARKER)) {
                method.instructions.insert(hookCall("onClientTick"));
                tick = true;
            }
        }
        if (!init || !tick) {
            NyxRuntime.log("Unsupported Minecraft build: hooks found init=" + init + " tick=" + tick);
        }
        if (!init && !tick) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean hasConstant(MethodNode method, String constant) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && constant.equals(((LdcInsnNode) insn).cst)) {
                return true;
            }
        }
        return false;
    }

    private static void insertBeforeReturns(MethodNode method, String hook) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(insn, hookCall(hook));
            }
        }
    }

    private static InsnList hookCall(String hook) {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, "()V"));
        return list;
    }
}
