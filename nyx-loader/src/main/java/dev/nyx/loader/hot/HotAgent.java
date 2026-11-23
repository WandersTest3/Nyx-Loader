package dev.nyx.loader.hot;

import dev.nyx.loader.NyxRuntime;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;

public final class HotAgent {

    private static volatile Instrumentation instrumentation;
    private static volatile String failure;
    private static boolean attempted;

    private HotAgent() {
    }

    public static java.lang.invoke.MethodHandles.Lookup lookupIn(Class<?> type) throws ReflectiveOperationException {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field holder = unsafeType.getDeclaredField("theUnsafe");
        holder.setAccessible(true);
        Object unsafe = holder.get(null);
        java.lang.reflect.Field impl = java.lang.invoke.MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Object base = unsafeType.getMethod("staticFieldBase", java.lang.reflect.Field.class).invoke(unsafe, impl);
        long offset = (Long) unsafeType.getMethod("staticFieldOffset", java.lang.reflect.Field.class).invoke(unsafe, impl);
        java.lang.invoke.MethodHandles.Lookup trusted = (java.lang.invoke.MethodHandles.Lookup) unsafeType
                .getMethod("getObject", Object.class, long.class).invoke(unsafe, base, offset);
        return trusted.in(type);
    }

    public static void premain(String arguments, Instrumentation value) {
        instrumentation = value;
    }

    public static void agentmain(String arguments, Instrumentation value) {
        instrumentation = value;
    }

    public static void prepare() {
        if (System.getProperty("jdk.attach.allowAttachSelf") == null) {
            System.setProperty("jdk.attach.allowAttachSelf", "true");
        }
    }

    public static synchronized Instrumentation get() {
        if (instrumentation == null && !attempted) {
            attempted = true;
            attach();
        }
        return instrumentation;
    }

    public static String failure() {
        return failure == null ? "unavailable" : failure;
    }

    private static void allowSelfAttach() {
        try {
            Class<?> machine = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field holder = unsafeType.getDeclaredField("theUnsafe");
            holder.setAccessible(true);
            Object unsafe = holder.get(null);
            java.lang.reflect.Field flag = machine.getDeclaredField("ALLOW_ATTACH_SELF");
            Object base = unsafeType.getMethod("staticFieldBase", java.lang.reflect.Field.class).invoke(unsafe, flag);
            long offset = (Long) unsafeType.getMethod("staticFieldOffset", java.lang.reflect.Field.class).invoke(unsafe, flag);
            unsafeType.getMethod("putBoolean", Object.class, long.class, boolean.class).invoke(unsafe, base, offset, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return;
        }
    }

    private static void attach() {
        allowSelfAttach();
        try {
            File jar = new File(HotAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.isFile()) {
                failure = "Nyx is not running from a jar";
                return;
            }
            Class<?> machine = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = machine.getMethod("attach", String.class).invoke(null, String.valueOf(ProcessHandle.current().pid()));
            try {
                machine.getMethod("loadAgent", String.class).invoke(vm, jar.getAbsolutePath());
            } finally {
                machine.getMethod("detach").invoke(vm);
            }
            if (instrumentation == null) {
                failure = "the agent did not start";
            }
        } catch (ClassNotFoundException error) {
            failure = "this Java runtime has no attach support";
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            failure = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        } catch (URISyntaxException | ReflectiveOperationException | RuntimeException error) {
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        if (instrumentation == null) {
            NyxRuntime.log("Instrumentation is not available (" + failure + "); mixins of new mods will need a restart");
        } else {
            NyxRuntime.log("Instrumentation attached");
        }
    }
}
