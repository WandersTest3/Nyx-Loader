package dev.nyx.loader.hot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HotRedefiner {

    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final String FIELDS = "dev/nyx/loader/hot/HotFields";

    private HotRedefiner() {
    }

    public static final class Plan {
        public final Class<?> target;
        public final String name;
        String blocker;
        byte[] redefined;
        byte[] hidden;
        byte[] trampoline;
        String trampolineName;
        final List<String[]> moved = new ArrayList<String[]>();
        final List<String[]> bridges = new ArrayList<String[]>();
        final Map<String, Object> staticInitials = new HashMap<String, Object>();

        Plan(Class<?> target, String name) {
            this.target = target;
            this.name = name;
        }

        public String blocker() {
            return blocker;
        }

        public String trampolineClass() {
            return trampolineName == null ? null : trampolineName;
        }

        public byte[] redefinedBytes() {
            return redefined;
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static String key(String name, String desc) {
        return name + desc;
    }

    private static String describe(AbstractInsnNode node, Map<LabelNode, Integer> labels) {
        StringBuilder text = new StringBuilder().append(node.getOpcode()).append(':');
        if (node instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) node;
            text.append(m.owner).append('.').append(m.name).append(m.desc);
        } else if (node instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) node;
            text.append(f.owner).append('.').append(f.name).append(f.desc);
        } else if (node instanceof VarInsnNode) {
            text.append(((VarInsnNode) node).var);
        } else if (node instanceof IntInsnNode) {
            text.append(((IntInsnNode) node).operand);
        } else if (node instanceof LdcInsnNode) {
            text.append(((LdcInsnNode) node).cst);
        } else if (node instanceof TypeInsnNode) {
            text.append(((TypeInsnNode) node).desc);
        } else if (node instanceof JumpInsnNode) {
            text.append(labels.get(((JumpInsnNode) node).label));
        } else if (node instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode d = (InvokeDynamicInsnNode) node;
            text.append(d.name).append(d.desc).append(d.bsm);
            for (Object arg : d.bsmArgs) {
                text.append(',').append(arg);
            }
        } else if (node instanceof MultiANewArrayInsnNode) {
            text.append(((MultiANewArrayInsnNode) node).desc).append(((MultiANewArrayInsnNode) node).dims);
        } else if (node instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode t = (TableSwitchInsnNode) node;
            text.append(t.min).append(t.max).append(labels.get(t.dflt));
            for (LabelNode label : t.labels) {
                text.append(',').append(labels.get(label));
            }
        } else if (node instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode t = (LookupSwitchInsnNode) node;
            text.append(t.keys).append(labels.get(t.dflt));
            for (LabelNode label : t.labels) {
                text.append(',').append(labels.get(label));
            }
        }
        return text.toString();
    }

    private static List<String> code(MethodNode method) {
        Map<LabelNode, Integer> labels = new HashMap<LabelNode, Integer>();
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof LabelNode) {
                labels.put((LabelNode) node, labels.size());
            }
        }
        List<String> lines = new ArrayList<String>();
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof LabelNode) {
                lines.add("L" + labels.get(node));
            } else if (!(node instanceof LineNumberNode) && !(node instanceof FrameNode)) {
                lines.add(describe(node, labels));
            }
        }
        return lines;
    }

    private static String staticDesc(String owner, String desc, boolean isStatic) {
        return isStatic ? desc : "(L" + owner + ";" + desc.substring(1);
    }

    public static Plan plan(Class<?> target, byte[] current, byte[] next) {
        Plan plan = new Plan(target, target.getName());
        ClassNode c = read(current);
        ClassNode n = read(next);
        String owner = n.name;
        if (!c.name.equals(n.name)) {
            plan.blocker = "class name";
            return plan;
        }
        if (!java.util.Objects.equals(c.superName, n.superName)) {
            plan.blocker = "superclass of " + owner;
            return plan;
        }
        Set<String> oldInterfaces = new TreeSet<String>(c.interfaces);
        Set<String> newInterfaces = new TreeSet<String>(n.interfaces);
        Set<String> addedInterfaces = new TreeSet<String>(newInterfaces);
        addedInterfaces.removeAll(oldInterfaces);
        if (!newInterfaces.containsAll(oldInterfaces)) {
            plan.blocker = "interface removed from " + owner.replace('/', '.');
            return plan;
        }
        if (c.access != n.access) {
            plan.blocker = "class modifiers of " + owner;
            return plan;
        }
        Map<String, FieldNode> oldFields = new HashMap<String, FieldNode>();
        for (FieldNode field : c.fields) {
            oldFields.put(key(field.name, field.desc), field);
        }
        Map<String, MethodNode> oldMethods = new HashMap<String, MethodNode>();
        for (MethodNode method : c.methods) {
            oldMethods.put(key(method.name, method.desc), method);
        }
        List<FieldNode> addedFields = new ArrayList<FieldNode>();
        Set<String> addedFieldKeys = new HashSet<String>();
        Set<String> seenFields = new HashSet<String>();
        for (FieldNode field : n.fields) {
            String id = key(field.name, field.desc);
            seenFields.add(id);
            FieldNode old = oldFields.get(id);
            if (old == null) {
                addedFields.add(field);
                addedFieldKeys.add(id);
            } else {
                field.access = old.access;
            }
        }
        List<MethodNode> added = new ArrayList<MethodNode>();
        Set<String> addedKeys = new HashSet<String>();
        Set<String> seenMethods = new HashSet<String>();
        Set<String> privateMethods = new HashSet<String>();
        for (MethodNode method : n.methods) {
            String id = key(method.name, method.desc);
            seenMethods.add(id);
            MethodNode old = oldMethods.get(id);
            if (old == null) {
                added.add(method);
                addedKeys.add(id);
            } else {
                method.access = old.access;
            }
            if ((method.access & Opcodes.ACC_PRIVATE) != 0) {
                privateMethods.add(id);
            }
        }
        MethodNode oldInit = oldMethods.get("<clinit>()V");
        MethodNode newInit = null;
        for (MethodNode method : n.methods) {
            if (method.name.equals("<clinit>")) {
                newInit = method;
            }
        }
        if (newInit != null && (oldInit == null || !code(oldInit).equals(code(newInit)))) {
            plan.blocker = "static initializer of " + owner.replace('/', '.');
            return plan;
        }
        for (MethodNode method : added) {
            if (method.name.equals("<init>") || (method.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                plan.blocker = "method " + method.name;
                return plan;
            }
        }
        int bridgeIndex = 0;
        for (String iface : addedInterfaces) {
            byte[] raw;
            try {
                raw = dev.nyx.loader.launch.NyxLaunch.getClassBytes(iface.replace('/', '.'), false);
            } catch (java.io.IOException error) {
                raw = null;
            }
            if (raw == null) {
                plan.blocker = "interface " + iface.replace('/', '.');
                return plan;
            }
            ClassNode contract = read(raw);
            for (MethodNode abstractMethod : contract.methods) {
                if ((abstractMethod.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) != Opcodes.ACC_ABSTRACT) {
                    continue;
                }
                String id = key(abstractMethod.name, abstractMethod.desc);
                if (!seenMethods.contains(id)) {
                    continue;
                }
                String bridgeDesc = "(Ljava/lang/Object;" + abstractMethod.desc.substring(1);
                plan.bridges.add(new String[] {iface, abstractMethod.name, abstractMethod.desc, "ib" + bridgeIndex++,
                        bridgeDesc, addedKeys.contains(id) ? "added" : "existing"});
            }
        }
        int serial = SERIAL.incrementAndGet();
        String trampoline = owner + "$NyxHot" + serial;
        String hidden = owner + "$NyxHotImpl" + serial;
        plan.trampolineName = trampoline;
        for (FieldNode field : addedFields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 && field.value != null) {
                plan.staticInitials.put(owner + "." + field.name, field.value);
            }
        }
        Rewriter rewriter = new Rewriter(owner, trampoline, addedKeys, addedFieldKeys, privateMethods);
        for (MethodNode method : n.methods) {
            if (!addedKeys.contains(key(method.name, method.desc))) {
                rewriter.rewrite(method, false);
            }
        }
        for (MethodNode method : added) {
            rewriter.rewrite(method, true);
        }
        if (rewriter.blocker != null) {
            plan.blocker = rewriter.blocker;
            return plan;
        }
        for (FieldNode field : addedFields) {
            n.fields.remove(field);
        }
        for (FieldNode old : c.fields) {
            if (!seenFields.contains(key(old.name, old.desc))) {
                n.fields.add(old);
            }
        }
        for (MethodNode old : c.methods) {
            if (!seenMethods.contains(key(old.name, old.desc))) {
                n.methods.add(old);
            }
        }
        n.methods.removeAll(added);
        n.interfaces = new ArrayList<String>(c.interfaces);
        n.nestHostClass = c.nestHostClass;
        n.nestMembers = c.nestMembers;
        n.signature = c.signature;
        ClassWriter redefined = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        n.accept(redefined);
        plan.redefined = redefined.toByteArray();
        if (!added.isEmpty() || !plan.bridges.isEmpty()) {
            ClassNode h = new ClassNode();
            h.version = n.version;
            h.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_SUPER;
            h.name = hidden;
            h.superName = "java/lang/Object";
            for (MethodNode method : added) {
                boolean wasStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                String desc = staticDesc(owner, method.desc, wasStatic);
                plan.moved.add(new String[] {method.name, desc, method.desc, wasStatic ? "1" : "0"});
                method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | (method.access & Opcodes.ACC_SYNTHETIC);
                method.desc = desc;
                method.signature = null;
                method.parameters = null;
                method.visibleAnnotations = null;
                method.invisibleAnnotations = null;
                method.visibleParameterAnnotations = null;
                method.invisibleParameterAnnotations = null;
                method.visibleTypeAnnotations = null;
                method.invisibleTypeAnnotations = null;
                h.methods.add(method);
            }
            ClassWriter hiddenWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            h.accept(hiddenWriter);
            plan.hidden = hiddenWriter.toByteArray();
            plan.trampoline = trampoline(trampoline, owner, plan.moved, plan.bridges);
        }
        return plan;
    }

    private static byte[] trampoline(String name, String owner, List<String[]> moved, List<String[]> bridges) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_SUPER, name,
                null, "java/lang/Object", null);
        for (int i = 0; i < moved.size(); i++) {
            String[] entry = moved.get(i);
            w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "h" + i, "Ljava/lang/invoke/MethodHandle;", null, null).visitEnd();
            org.objectweb.asm.MethodVisitor mv = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, entry[0], entry[1], null, null);
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, name, "h" + i, "Ljava/lang/invoke/MethodHandle;");
            int slot = 0;
            for (Type type : Type.getArgumentTypes(entry[1])) {
                mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
                slot += type.getSize();
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", entry[1], false);
            mv.visitInsn(Type.getReturnType(entry[1]).getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        for (String[] bridge : bridges) {
            org.objectweb.asm.MethodVisitor mv = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, bridge[3], bridge[4], null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitTypeInsn(Opcodes.CHECKCAST, owner);
            int slot = 1;
            for (Type type : Type.getArgumentTypes(bridge[2])) {
                mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
                slot += type.getSize();
            }
            if (bridge[5].equals("added")) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, name, bridge[1], staticDesc(owner, bridge[2], false), false);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, bridge[1], bridge[2], false);
            }
            mv.visitInsn(Type.getReturnType(bridge[2]).getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    private static final class Rewriter {
        final String owner;
        final String trampoline;
        final Set<String> addedMethods;
        final Set<String> addedFields;
        final Set<String> privateMethods;
        String blocker;

        Rewriter(String owner, String trampoline, Set<String> addedMethods, Set<String> addedFields, Set<String> privateMethods) {
            this.owner = owner;
            this.trampoline = trampoline;
            this.addedMethods = addedMethods;
            this.addedFields = addedFields;
            this.privateMethods = privateMethods;
        }

        void rewrite(MethodNode method, boolean moved) {
            InsnList list = method.instructions;
            for (AbstractInsnNode node = list.getFirst(); node != null;) {
                AbstractInsnNode following = node.getNext();
                if (node instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) node;
                    if (call.owner.equals(owner) && addedMethods.contains(key(call.name, call.desc))) {
                        boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
                        list.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, trampoline, call.name,
                                staticDesc(owner, call.desc, isStatic), false));
                    } else if (moved && call.getOpcode() == Opcodes.INVOKESPECIAL && !call.name.equals("<init>")) {
                        if (call.owner.equals(owner) && privateMethods.contains(key(call.name, call.desc))) {
                            call.setOpcode(Opcodes.INVOKEVIRTUAL);
                        } else if (blocker == null) {
                            blocker = "super call in " + method.name;
                        }
                    }
                } else if (node instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) node;
                    if (field.owner.equals(owner) && addedFields.contains(key(field.name, field.desc))) {
                        replaceField(list, field);
                    }
                } else if (node instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) node;
                    for (int i = 0; i < dynamic.bsmArgs.length; i++) {
                        if (dynamic.bsmArgs[i] instanceof Handle) {
                            dynamic.bsmArgs[i] = handle((Handle) dynamic.bsmArgs[i]);
                        }
                    }
                } else if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Handle) {
                    ((LdcInsnNode) node).cst = handle((Handle) ((LdcInsnNode) node).cst);
                }
                node = following;
            }
        }

        Handle handle(Handle handle) {
            if (!handle.getOwner().equals(owner) || !addedMethods.contains(key(handle.getName(), handle.getDesc()))) {
                return handle;
            }
            boolean isStatic = handle.getTag() == Opcodes.H_INVOKESTATIC;
            return new Handle(Opcodes.H_INVOKESTATIC, trampoline, handle.getName(),
                    staticDesc(owner, handle.getDesc(), isStatic), false);
        }

        void replaceField(InsnList list, FieldInsnNode field) {
            char kind = field.desc.charAt(0);
            String suffix = kind == '[' || kind == 'L' ? "L" : String.valueOf(kind);
            String primitive = suffix.equals("L") ? "Ljava/lang/Object;" : field.desc;
            String id = owner + "." + field.name;
            InsnList code = new InsnList();
            switch (field.getOpcode()) {
                case Opcodes.GETFIELD:
                    code.add(new LdcInsnNode(id));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIELDS, "get" + suffix,
                            "(Ljava/lang/Object;Ljava/lang/String;)" + primitive, false));
                    break;
                case Opcodes.PUTFIELD:
                    code.add(new LdcInsnNode(id));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIELDS, "put" + suffix,
                            "(Ljava/lang/Object;" + primitive + "Ljava/lang/String;)V", false));
                    break;
                case Opcodes.GETSTATIC:
                    code.add(new LdcInsnNode(id));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIELDS, "sget" + suffix,
                            "(Ljava/lang/String;)" + primitive, false));
                    break;
                default:
                    code.add(new LdcInsnNode(id));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIELDS, "sput" + suffix,
                            "(" + primitive + "Ljava/lang/String;)V", false));
                    break;
            }
            if (suffix.equals("L") && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC)
                    && !field.desc.equals("Ljava/lang/Object;")) {
                code.add(new TypeInsnNode(Opcodes.CHECKCAST, field.desc.charAt(0) == '[' ? field.desc
                        : field.desc.substring(1, field.desc.length() - 1)));
            }
            list.insert(field, code);
            list.remove(field);
        }
    }

    public static void apply(List<Plan> plans, Instrumentation instrumentation) throws Throwable {
        List<ClassDefinition> definitions = new ArrayList<ClassDefinition>();
        for (Plan plan : plans) {
            if (plan.hidden != null) {
                MethodHandles.Lookup lookup = HotAgent.lookupIn(plan.target);
                MethodHandles.Lookup impl = lookup.defineHiddenClass(plan.hidden, true, MethodHandles.Lookup.ClassOption.NESTMATE);
                Class<?> tramp = lookup.defineClass(plan.trampoline);
                for (int i = 0; i < plan.moved.size(); i++) {
                    String[] entry = plan.moved.get(i);
                    MethodType type = MethodType.fromMethodDescriptorString(entry[1], plan.target.getClassLoader());
                    MethodHandle handle = impl.findStatic(impl.lookupClass(), entry[0], type);
                    tramp.getField("h" + i).set(null, handle);
                }
            }
            for (String[] bridge : plan.bridges) {
                HotInterfaces.register(bridge[0], bridge[1], bridge[2], plan.trampolineName, bridge[3], bridge[4], plan.target);
            }
            for (Map.Entry<String, Object> initial : plan.staticInitials.entrySet()) {
                HotFields.initStatic(initial.getKey(), initial.getValue());
            }
            definitions.add(new ClassDefinition(plan.target, plan.redefined));
        }
        instrumentation.redefineClasses(definitions.toArray(new ClassDefinition[0]));
    }
}
