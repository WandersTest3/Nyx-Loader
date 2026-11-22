package dev.nyx.loader.forge;

import dev.nyx.loader.NyxRuntime;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import sun.misc.Unsafe;

public final class ForgeCompat {

    private static Unsafe unsafe;
    private static MethodHandles.Lookup trusted;

    private ForgeCompat() {
    }

    public static synchronized Unsafe unsafe() {
        if (unsafe == null) {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                unsafe = (Unsafe) field.get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsafe is not available", error);
            }
        }
        return unsafe;
    }

    @SuppressWarnings("deprecation")
    public static synchronized MethodHandles.Lookup trustedLookup() {
        if (trusted == null) {
            try {
                Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                Unsafe u = unsafe();
                trusted = (MethodHandles.Lookup) u.getObject(u.staticFieldBase(field), u.staticFieldOffset(field));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Trusted lookup is not available", error);
            }
        }
        return trusted;
    }

    public static void prepareJava() {
        try {
            MethodHandle opens = trustedLookup().findVirtual(Module.class, "implAddOpens",
                    MethodType.methodType(void.class, String.class));
            int count = 0;
            for (Module module : ModuleLayer.boot().modules()) {
                for (String pkg : module.getPackages()) {
                    opens.invoke(module, pkg);
                    count++;
                }
            }
            NyxRuntime.log("Opened " + count + " JDK packages for legacy mods");
        } catch (Throwable error) {
            NyxRuntime.log("Could not open JDK packages: " + error);
        }
        definePatchedTweaker();
        definePatchedInApp("cpw.mods.fml.relauncher.CoreModManager");
    }

    private static void definePatchedInApp(String name) {
        ClassLoader app = ForgeCompat.class.getClassLoader();
        try (java.io.InputStream input = app.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) {
                return;
            }
            byte[] bytes = ForgeFixes.transform(name.replace('.', '/'), input.readAllBytes());
            java.lang.reflect.Method define = ClassLoader.class.getDeclaredMethod("defineClass", String.class,
                    byte[].class, int.class, int.class, java.security.ProtectionDomain.class);
            define.setAccessible(true);
            java.net.URL resource = app.getResource(name.replace('.', '/') + ".class");
            java.net.URL location = ((java.net.JarURLConnection) resource.openConnection()).getJarFileURL();
            java.security.ProtectionDomain domain = new java.security.ProtectionDomain(
                    new java.security.CodeSource(location, (java.security.cert.Certificate[]) null), null, app, null);
            define.invoke(app, name, bytes, 0, bytes.length, domain);
        } catch (Throwable error) {
            NyxRuntime.log("Could not adapt " + name + ": " + error);
        }
    }

    private static void definePatchedTweaker() {
        String name = "cpw.mods.fml.common.launcher.FMLTweaker";
        ClassLoader app = ForgeCompat.class.getClassLoader();
        try (java.io.InputStream input = app.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) {
                return;
            }
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(node, 0);
            int removed = 0;
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode
                            && "java/lang/System".equals(((org.objectweb.asm.tree.MethodInsnNode) insn).owner)
                            && "setSecurityManager".equals(((org.objectweb.asm.tree.MethodInsnNode) insn).name)) {
                        method.instructions.set(insn, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.POP));
                        removed++;
                    }
                }
            }
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] bytes = writer.toByteArray();
            java.lang.reflect.Method define = ClassLoader.class.getDeclaredMethod("defineClass", String.class,
                    byte[].class, int.class, int.class, java.security.ProtectionDomain.class);
            define.setAccessible(true);
            java.net.URL resource = app.getResource(name.replace('.', '/') + ".class");
            java.net.URL location = ((java.net.JarURLConnection) resource.openConnection()).getJarFileURL();
            java.security.ProtectionDomain domain = new java.security.ProtectionDomain(
                    new java.security.CodeSource(location, (java.security.cert.Certificate[]) null), null, app, null);
            define.invoke(app, name, bytes, 0, bytes.length, domain);
            NyxRuntime.log("FMLTweaker adapted for Java 21 (" + removed + " security manager call removed)");
        } catch (Throwable error) {
            NyxRuntime.log("Could not adapt FMLTweaker: " + error);
        }
    }

    public static void unpack200(Object unpacker, java.io.InputStream input, java.util.jar.JarOutputStream output)
            throws Exception {
        byte[] data = input.readAllBytes();
        java.lang.reflect.Method unpack = unpacker.getClass().getMethod("unpack", java.io.InputStream.class,
                java.util.jar.JarOutputStream.class);
        unpack.setAccessible(true);
        unpack.invoke(unpacker, new java.io.ByteArrayInputStream(data), output);
    }

    private static final class LegacyModifiers {

        @SuppressWarnings("unused")
        static int modifiers;
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        if (owner == Field.class && "modifiers".equals(name)) {
            return LegacyModifiers.class.getDeclaredField("modifiers");
        }
        return owner.getDeclaredField(name);
    }

    public static boolean skipCascadingTweak(java.io.File file, String tweakClass) {
        boolean fabric = false;
        if (file != null) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                fabric = zip.getEntry("fabric.mod.json") != null;
            } catch (java.io.IOException ignored) {
                fabric = false;
            }
        }
        java.io.File jar = file;
        boolean mixin = tweakClass != null && tweakClass.startsWith("org.spongepowered.asm.launch.");
        if (fabric || mixin) {
            NyxRuntime.log("Ignoring tweaker " + tweakClass + " from " + (jar == null ? "?" : jar.getName())
                    + (fabric ? " (loaded as a Fabric mod by Nyx)" : " (Mixin is provided by Nyx)"));
            return true;
        }
        return false;
    }

    public static Object holderInvoke(java.lang.reflect.Method method, Object target, Object[] args)
            throws Exception {
        if (method != null) {
            return method.invoke(target, args);
        }
        if (target == null && args != null && args.length >= 1 && args[0] instanceof Field) {
            return args[0];
        }
        if (target instanceof Field && args != null && args.length == 2) {
            Field field = (Field) target;
            setFailsafeFieldValue(field, args[0], args[1]);
            return null;
        }
        throw new IllegalStateException("Unexpected reflective call in Forge object holder");
    }

    public static Object makeEnum(Class<?> enumClass, String name, int ordinal, Class<?>[] paramTypes,
            Object[] paramValues) throws Throwable {
        int extra = paramTypes == null ? 0 : paramTypes.length;
        Class<?>[] types = new Class<?>[extra + 2];
        types[0] = String.class;
        types[1] = int.class;
        if (extra > 0) {
            System.arraycopy(paramTypes, 0, types, 2, extra);
        }
        MethodHandle constructor = trustedLookup().findConstructor(enumClass,
                MethodType.methodType(void.class, types));
        List<Object> args = new ArrayList<>(extra + 2);
        args.add(name);
        args.add(ordinal);
        if (extra > 0) {
            for (Object value : paramValues) {
                args.add(value);
            }
        }
        return constructor.invokeWithArguments(args);
    }

    @SuppressWarnings("deprecation")
    public static void setFailsafeFieldValue(Field field, Object target, Object value) {
        Unsafe u = unsafe();
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base = isStatic ? u.staticFieldBase(field) : target;
        long offset = isStatic ? u.staticFieldOffset(field) : u.objectFieldOffset(field);
        Class<?> type = field.getType();
        if (!type.isPrimitive()) {
            u.putObjectVolatile(base, offset, value);
        } else if (type == int.class) {
            u.putIntVolatile(base, offset, (Integer) value);
        } else if (type == long.class) {
            u.putLongVolatile(base, offset, (Long) value);
        } else if (type == boolean.class) {
            u.putBooleanVolatile(base, offset, (Boolean) value);
        } else if (type == float.class) {
            u.putFloatVolatile(base, offset, (Float) value);
        } else if (type == double.class) {
            u.putDoubleVolatile(base, offset, (Double) value);
        } else if (type == byte.class) {
            u.putByteVolatile(base, offset, (Byte) value);
        } else if (type == short.class) {
            u.putShortVolatile(base, offset, (Short) value);
        } else if (type == char.class) {
            u.putCharVolatile(base, offset, (Character) value);
        }
    }
}
