package dev.nyx.loader.launch;

import dev.nyx.loader.NyxRuntime;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class GamePatcher {

    private static final String MINECRAFT = "net/minecraft/class_1600";
    private static final String MAIN_MENU = "net/minecraft/class_624";
    private static final String SCREEN = "net/minecraft/class_388";
    private static final String BRAND = "net/minecraft/client/ClientBrandRetriever";
    private static final String HOOKS = "dev/nyx/loader/Hooks";
    private static final String FRAME = "dev/nyx/mc171/NyxFrame";
    private static final String MENU = "dev/nyx/mc171/NyxMenuHook";

    private static final String INIT = "method_2921";
    private static final String INIT_MARKER = "Pre startup";
    private static final String LWJGL_MARKER = "LWJGL Version: ";
    private static final String TICK = "method_2954";
    private static final String TICK_MARKER = "Ticking screen";
    private static final String FRAME_METHOD = "method_6648";
    private static final String REFRESH = "method_5576";
    private static final String RUN = "method_5580";
    private static final String GAME_LOOP = "method_2916";
    private static final String HAS_CRASHED = "field_3776";
    private static final String CRASH = "dev/nyx/mc171/NyxCrash";
    private static final String DRAW = "method_1025";
    private static final String CLICK = "method_1026";

    private GamePatcher() {
    }

    static byte[] transform(String name, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            RuntimeNames names = NyxLaunch.names();
            if (name.equals(names.map(MINECRAFT))) {
                return patchMinecraft(bytes);
            }
            if (name.equals(names.map(MAIN_MENU))) {
                return patchMainMenu(bytes);
            }
            if (name.equals(BRAND)) {
                return patchBrand(bytes);
            }
            return bytes;
        } catch (RuntimeException error) {
            NyxRuntime.log("Failed to patch " + name + ": " + error);
            return bytes;
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchMinecraft(byte[] bytes) {
        ClassNode node = read(bytes);
        RuntimeNames names = NyxLaunch.names();
        String initName = names.method(INIT);
        String tickName = names.method(TICK);
        String frameName = names.method(FRAME_METHOD);
        String refreshName = names.method(REFRESH);
        String runName = names.method(RUN);
        String loopName = names.method(GAME_LOOP);
        String crashedName = names.field(HAS_CRASHED);
        String self = node.name;
        boolean loopPatched = false;
        boolean crashedPatched = false;
        boolean init = false;
        boolean fabric = false;
        boolean tick = false;
        boolean frame = false;
        for (MethodNode method : node.methods) {
            if (!"()V".equals(method.desc)) {
                continue;
            }
            if (initName.equals(method.name) && constant(method, INIT_MARKER) != null) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(insn, call(HOOKS, "onGameStart", "()V"));
                    }
                }
                init = true;
                AbstractInsnNode lwjgl = constant(method, LWJGL_MARKER);
                if (lwjgl != null) {
                    AbstractInsnNode target = lwjgl;
                    while (target != null && !(target instanceof FieldInsnNode && target.getOpcode() == Opcodes.GETSTATIC)) {
                        target = target.getPrevious();
                    }
                    if (target == null) {
                        target = lwjgl;
                    }
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "startFabricClient", "(Ljava/lang/Object;)V", false));
                    method.instructions.insertBefore(target, list);
                    fabric = true;
                }
            } else if (tickName.equals(method.name) && constant(method, TICK_MARKER) != null) {
                method.instructions.insert(call(HOOKS, "onClientTick", "()V"));
                tick = true;
            } else if (refreshName.equals(method.name) && (method.access & Opcodes.ACC_STATIC) == 0) {
                LabelNode run = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(call(HOOKS, "offRenderThread", "()Z"));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, run));
                guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "deferRefresh", "(Ljava/lang/Object;)V", false));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(run);
                method.instructions.insert(guard);
            } else if (runName.equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if (self.equals(call.owner) && loopName.equals(call.name) && "()V".equals(call.desc)
                                && call.getOpcode() != Opcodes.INVOKESTATIC) {
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, CRASH, "runLoop",
                                    "(Ljava/lang/Object;)V", false));
                            loopPatched = true;
                        }
                    } else if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETFIELD) {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        if (self.equals(field.owner) && crashedName.equals(field.name) && "Z".equals(field.desc)) {
                            method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, CRASH, "hasCrashed",
                                    "(Ljava/lang/Object;)Z", false));
                            crashedPatched = true;
                        }
                    }
                }
            } else if (frameName.equals(method.name) && startsWithDisplayUpdate(method)) {
                method.instructions.insert(call(FRAME, "onFrame", "()V"));
                frame = true;
            }
        }
        if (!loopPatched || !crashedPatched) {
            NyxRuntime.log("Crash recovery hooks: loop=" + loopPatched + " crashed=" + crashedPatched);
        }
        if (!init || !fabric || !tick || !frame) {
            NyxRuntime.log("Minecraft hooks: init=" + init + " fabric=" + fabric + " tick=" + tick + " frame=" + frame);
        }
        return write(node);
    }

    private static byte[] patchMainMenu(byte[] bytes) {
        ClassNode node = read(bytes);
        RuntimeNames names = NyxLaunch.names();
        String drawName = names.method(DRAW);
        String clickName = names.method(CLICK);
        String screen = names.map(SCREEN);
        boolean draw = false;
        boolean click = false;
        for (MethodNode method : node.methods) {
            if (drawName.equals(method.name) && "(IIF)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        list.add(new VarInsnNode(Opcodes.ILOAD, 1));
                        list.add(new VarInsnNode(Opcodes.ILOAD, 2));
                        list.add(new VarInsnNode(Opcodes.FLOAD, 3));
                        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MENU, "draw", "(L" + screen + ";IIF)V", false));
                        method.instructions.insertBefore(insn, list);
                    }
                }
                draw = true;
            } else if (clickName.equals(method.name) && "(III)V".equals(method.desc)) {
                InsnList list = new InsnList();
                LabelNode proceed = new LabelNode();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new VarInsnNode(Opcodes.ILOAD, 1));
                list.add(new VarInsnNode(Opcodes.ILOAD, 2));
                list.add(new VarInsnNode(Opcodes.ILOAD, 3));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MENU, "click", "(L" + screen + ";III)Z", false));
                list.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                list.add(new InsnNode(Opcodes.RETURN));
                list.add(proceed);
                method.instructions.insert(list);
                click = true;
            }
        }
        if (!draw || !click) {
            NyxRuntime.log("Mod menu button not installed: draw=" + draw + " click=" + click);
        }
        return write(node);
    }

    private static byte[] patchBrand(byte[] bytes) {
        ClassNode node = read(bytes);
        for (MethodNode method : node.methods) {
            if ("()Ljava/lang/String;".equals(method.desc) && (method.access & Opcodes.ACC_STATIC) != 0) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.ARETURN) {
                        method.instructions.insertBefore(insn,
                                call(HOOKS, "insertBranding", "(Ljava/lang/String;)Ljava/lang/String;"));
                    }
                }
            }
        }
        return write(node);
    }

    private static InsnList call(String owner, String name, String desc) {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
        return list;
    }

    private static AbstractInsnNode constant(MethodNode method, String value) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && value.equals(((LdcInsnNode) insn).cst)) {
                return insn;
            }
        }
        return null;
    }

    private static boolean startsWithDisplayUpdate(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() < 0) {
                continue;
            }
            return insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC
                    && "org/lwjgl/opengl/Display".equals(((MethodInsnNode) insn).owner)
                    && "update".equals(((MethodInsnNode) insn).name);
        }
        return false;
    }
}
