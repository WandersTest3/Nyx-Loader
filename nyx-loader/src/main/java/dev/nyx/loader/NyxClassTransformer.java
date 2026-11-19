package dev.nyx.loader;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class NyxClassTransformer implements IClassTransformer {

    private static final String MINECRAFT_CLASS = "bao";
    private static final String MAIN_MENU_CLASS = "bee";
    private static final String HOOKS = "dev/nyx/loader/Hooks";
    private static final String FRAME = "NyxFrame";
    private static final String MENU_HOOK = "NyxMenuHook";

    private static final String INIT_METHOD = "ag";
    private static final String INIT_MARKER = "Pre startup";
    private static final String TICK_METHOD = "p";
    private static final String TICK_MARKER = "Ticking screen";
    private static final String FRAME_METHOD = "g";
    private static final String DISPLAY = "org/lwjgl/opengl/Display";
    private static final String DISPLAY_UPDATE = "update";

    private static final String DRAW_METHOD = "a";
    private static final String DRAW_DESC = "(IIF)V";
    private static final String CLICK_METHOD = "a";
    private static final String CLICK_DESC = "(III)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return bytes;
        }
        boolean minecraft = MINECRAFT_CLASS.equals(transformedName) || MINECRAFT_CLASS.equals(name);
        boolean mainMenu = MAIN_MENU_CLASS.equals(transformedName) || MAIN_MENU_CLASS.equals(name);
        if (!minecraft && !mainMenu) {
            return bytes;
        }
        try {
            return minecraft ? patchMinecraft(bytes) : patchMainMenu(bytes);
        } catch (Throwable error) {
            NyxRuntime.log("Failed to patch " + (minecraft ? "Minecraft" : "the main menu") + ": " + error);
            return bytes;
        }
    }

    private byte[] patchMinecraft(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean init = false;
        boolean tick = false;
        boolean frame = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!"()V".equals(method.desc)) {
                continue;
            }
            if (INIT_METHOD.equals(method.name) && hasConstant(method, INIT_MARKER)) {
                insertBeforeReturns(method, HOOKS, "onGameStart");
                init = true;
            } else if (TICK_METHOD.equals(method.name) && hasConstant(method, TICK_MARKER)) {
                method.instructions.insert(hookCall(HOOKS, "onClientTick"));
                tick = true;
            } else if (FRAME_METHOD.equals(method.name) && startsWithDisplayUpdate(method)) {
                method.instructions.insert(hookCall(FRAME, "onFrame"));
                frame = true;
            }
        }
        if (!init || !tick) {
            NyxRuntime.log("Unsupported Minecraft build: hooks found init=" + init + " tick=" + tick);
        }
        if (!frame) {
            NyxRuntime.log("Frame hook not found: HUD renderers and key bindings are disabled");
        }
        if (!init && !tick && !frame) {
            return bytes;
        }
        return write(node);
    }

    private byte[] patchMainMenu(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean draw = false;
        boolean click = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (DRAW_METHOD.equals(method.name) && DRAW_DESC.equals(method.desc)) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ILOAD, 1));
                call.add(new VarInsnNode(Opcodes.ILOAD, 2));
                call.add(new VarInsnNode(Opcodes.FLOAD, 3));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MENU_HOOK, "draw", "(Lbdw;IIF)V"));
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(insn, cloneCall(call));
                    }
                }
                draw = true;
            } else if (CLICK_METHOD.equals(method.name) && CLICK_DESC.equals(method.desc)) {
                InsnList call = new InsnList();
                LabelNode proceed = new LabelNode();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ILOAD, 1));
                call.add(new VarInsnNode(Opcodes.ILOAD, 2));
                call.add(new VarInsnNode(Opcodes.ILOAD, 3));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MENU_HOOK, "click", "(Lbdw;III)Z"));
                call.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                call.add(new InsnNode(Opcodes.RETURN));
                call.add(proceed);
                method.instructions.insert(call);
                click = true;
            }
        }
        if (!draw || !click) {
            NyxRuntime.log("Main menu button not installed: draw=" + draw + " click=" + click);
        }
        if (!draw && !click) {
            return bytes;
        }
        return write(node);
    }

    private static InsnList cloneCall(InsnList source) {
        InsnList copy = new InsnList();
        for (AbstractInsnNode insn : source.toArray()) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode var = (VarInsnNode) insn;
                copy.add(new VarInsnNode(var.getOpcode(), var.var));
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode method = (MethodInsnNode) insn;
                copy.add(new MethodInsnNode(method.getOpcode(), method.owner, method.name, method.desc));
            }
        }
        return copy;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean startsWithDisplayUpdate(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() < 0) {
                continue;
            }
            return insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC
                    && DISPLAY.equals(((MethodInsnNode) insn).owner)
                    && DISPLAY_UPDATE.equals(((MethodInsnNode) insn).name);
        }
        return false;
    }

    private static boolean hasConstant(MethodNode method, String constant) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && constant.equals(((LdcInsnNode) insn).cst)) {
                return true;
            }
        }
        return false;
    }

    private static void insertBeforeReturns(MethodNode method, String owner, String hook) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(insn, hookCall(owner, hook));
            }
        }
    }

    private static InsnList hookCall(String owner, String hook) {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, hook, "()V"));
        return list;
    }
}
