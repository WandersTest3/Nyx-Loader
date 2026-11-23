package dev.nyx.mc171;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxConfig;
import dev.nyx.loader.CrashAnalyzer;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.launch.NyxLaunch;
import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import net.minecraft.class_1600;
import net.minecraft.class_388;
import org.lwjgl.opengl.GL11;

public final class NyxCrash {

    private static final long WINDOW = 30000L;
    private static final int MAX_IN_WORLD = 3;
    private static final int MAX_OUTSIDE = 3;
    private static final int CHAT_FRAMES = 3;
    private static final String SHADERS = "shadersmod.client.Shaders";

    private static final Deque<Long> IN_WORLD = new ArrayDeque<Long>();
    private static final Deque<Long> OUTSIDE = new ArrayDeque<Long>();
    private static final List<String> PENDING_CHAT = new ArrayList<String>();

    private static MethodHandle loop;
    private static Boolean enabled;
    private static int shaderCheck;
    private static Field shaderNameField;
    private static String lastShader;
    private static long shaderChangedAt;
    private static boolean optifineChecked;
    private static boolean optifine;
    private static String lastReportText;

    private NyxCrash() {
    }

    private static boolean enabled() {
        if (enabled == null) {
            NyxConfig config = Nyx.getConfig("loader");
            config.setDefault("crash-recovery", "true");
            config.setDefault("class-cache", "true");
            config.save();
            enabled = config.getBoolean("crash-recovery", true) && !Boolean.getBoolean("nyx.disableCrashRecovery");
        }
        return enabled.booleanValue();
    }

    public static void runLoop(Object game) throws Throwable {
        MethodHandle handle = loop;
        if (handle == null) {
            Method method = Mc.method(game.getClass(), "method_2916", 0);
            handle = MethodHandles.lookup().unreflect(method);
            loop = handle;
        }
        try {
            handle.invoke(game);
        } catch (Throwable error) {
            if (!recover(game, error, false)) {
                throw error;
            }
        }
    }

    public static boolean hasCrashed(Object game) {
        try {
            Field crashed = Mc.field(game.getClass(), "field_3776");
            if (!crashed.getBoolean(game)) {
                return false;
            }
            Object report = Mc.get(game, "field_3777");
            if (report == null) {
                return true;
            }
            Throwable cause = (Throwable) Mc.call(report, "method_7");
            if (cause == null || !recover(game, report, cause, true)) {
                return true;
            }
            crashed.setBoolean(game, false);
            Mc.set(game, "field_3777", null);
            return false;
        } catch (Throwable error) {
            NyxRuntime.log("Crash check failed: " + error);
            return true;
        }
    }

    private static boolean recover(Object game, Throwable error, boolean server) {
        Throwable cause = error;
        Object report = null;
        try {
            if (Mc.cls("class_875").isInstance(error)) {
                report = Mc.call(error, "method_2682");
                Throwable inner = report == null ? null : (Throwable) Mc.call(report, "method_7");
                if (inner != null) {
                    cause = inner;
                }
            }
        } catch (Throwable ignored) {
            report = null;
        }
        return recover(game, report, cause, server);
    }

    private static boolean recover(Object game, Object report, Throwable cause, boolean server) {
        if (!enabled() || cause instanceof OutOfMemoryError || cause instanceof ThreadDeath) {
            return false;
        }
        try {
            if (Mc.cls("class_353").isInstance(cause)) {
                return false;
            }
            long now = System.currentTimeMillis();
            prune(IN_WORLD, now);
            prune(OUTSIDE, now);
            Object world = Mc.get(game, "field_3803");
            boolean stay = world != null && !server && IN_WORLD.size() < MAX_IN_WORLD;
            if (!stay && OUTSIDE.size() >= MAX_OUTSIDE) {
                NyxRuntime.log("Crash recovery gave up after " + MAX_OUTSIDE + " crashes in " + WINDOW / 1000L + " s");
                return false;
            }
            (stay ? IN_WORLD : OUTSIDE).addLast(now);
            CrashAnalyzer.Result result = CrashAnalyzer.analyze(cause);
            String file = saveReport(game, report, cause);
            boolean shaders = shadersActive() && (result.touchesShaders || result.blames("optifine")
                    || shaderChangedAt > 0 && now - shaderChangedAt < 60000L);
            String shaderPack = shaders ? disableShaders() : null;
            resetState(game);
            List<String> lines = chatLines(result, file, shaderPack, stay);
            NyxRuntime.log("Recovered from a crash" + (server ? " of the integrated server" : "") + "; suspects: "
                    + (result.suspectNames() == null ? "unknown" : result.suspectNames()) + "; report: " + file);
            if (stay) {
                ((class_1600) game).method_2928((class_388) null);
                chat(game, lines);
            } else {
                if (world != null) {
                    Mc.call(game, "method_2929", (Object) null);
                }
                synchronized (PENDING_CHAT) {
                    PENDING_CHAT.clear();
                    PENDING_CHAT.addAll(lines);
                }
                ((class_1600) game).method_2928(new NyxCrashScreen(result, file, shaderPack, lastReportText));
            }
            return true;
        } catch (Throwable failure) {
            NyxRuntime.log("Crash recovery failed: " + unwrap(failure));
            unwrap(failure).printStackTrace();
            return false;
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
    }

    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > WINDOW) {
            times.removeFirst();
        }
    }

    private static String saveReport(Object game, Object report, Throwable cause) {
        lastReportText = null;
        try {
            if (report == null) {
                report = Mc.construct("class_1", new Class<?>[] {String.class, Throwable.class}, "Unexpected error",
                        cause);
            }
            try {
                Mc.call(game, "method_2945", report);
            } catch (Throwable ignored) {
                NyxRuntime.log("Could not add game details to the crash report: " + unwrap(ignored));
            }
            File dir = new File((File) Mc.get(game, "field_3762"), "crash-reports");
            File file = new File(dir, "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date())
                    + "-client.txt");
            String text = String.valueOf(Mc.call(report, "method_10"));
            lastReportText = text;
            System.out.println(text);
            if (Boolean.TRUE.equals(Mc.call(report, "method_5008", file))) {
                return "crash-reports/" + file.getName();
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not save the crash report: " + unwrap(error));
            cause.printStackTrace();
        }
        return null;
    }

    private static List<String> chatLines(CrashAnalyzer.Result result, String file, String shaderPack, boolean stay) {
        List<String> lines = new ArrayList<String>();
        lines.add("§c§l[Nyx] " + Lang.t(stay ? "crash_chat_stay" : "crash_chat_left"));
        String suspects = result.suspectNames();
        lines.add("§c" + Lang.t("crash_suspect") + " §f" + (suspects == null ? Lang.t("crash_unknown") : suspects));
        if (shaderPack != null) {
            lines.add("§e" + Lang.t("crash_shaders_off") + " §f" + shaderPack);
        }
        lines.add("\u00a76" + result.root.toString());
        int frames = 0;
        for (StackTraceElement frame : result.root.getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.startsWith("java.") || owner.startsWith("jdk.") || owner.startsWith("sun.")
                    || owner.startsWith("dev.nyx.mc171.NyxCrash")) {
                continue;
            }
            if (frames++ >= CHAT_FRAMES) {
                break;
            }
            String simple = owner.substring(owner.lastIndexOf('.') + 1);
            lines.add("\u00a77  " + simple + "." + frame.getMethodName()
                    + (frame.getLineNumber() > 0 ? ":" + frame.getLineNumber() : ""));
        }
        if (file != null) {
            lines.add("§7" + Lang.t("crash_report") + " " + file);
        }
        return lines;
    }

    private static void chat(Object game, List<String> lines) throws ReflectiveOperationException {
        Chat.print(game, lines);
    }

    static void deliverPendingChat(Object game) {
        List<String> lines;
        synchronized (PENDING_CHAT) {
            if (PENDING_CHAT.isEmpty()) {
                return;
            }
            lines = new ArrayList<String>(PENDING_CHAT);
        }
        try {
            if (Mc.get(game, "field_3803") == null || Mc.get(game, "field_3805") == null
                    || ((class_1600) game).field_3816 != null) {
                return;
            }
            synchronized (PENDING_CHAT) {
                PENDING_CHAT.clear();
            }
            chat(game, lines);
        } catch (Throwable error) {
            NyxRuntime.log("Could not show the crash message in chat: " + unwrap(error));
        }
    }

    private static void resetState(Object game) {
        try {
            Class<?> tessellator = Mc.cls("class_533");
            Object instance = Mc.getStatic(tessellator, "field_1945");
            Field drawing = Mc.field(tessellator, "field_1970");
            if (instance != null && drawing.getBoolean(instance)) {
                try {
                    Mc.method(tessellator, "method_1412", 0).invoke(instance);
                } catch (Throwable ignored) {
                    NyxRuntime.log("Tessellator reset failed: " + unwrap(ignored));
                }
                drawing.setBoolean(instance, false);
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not reset the tessellator: " + unwrap(error));
        }
        try {
            Object profiler = Mc.get(game, "field_3767");
            if (profiler != null) {
                Mc.call(profiler, "method_2355");
            }
        } catch (Throwable ignored) {
            NyxRuntime.log("Could not reset the profiler: " + unwrap(ignored));
        }
        resetGl();
    }

    private static void resetGl() {
        quiet(() -> org.lwjgl.opengl.ARBShaderObjects.glUseProgramObjectARB(0));
        quiet(() -> org.lwjgl.opengl.EXTFramebufferObject.glBindFramebufferEXT(0x8D40, 0));
        quiet(() -> org.lwjgl.opengl.GL13.glActiveTexture(0x84C0));
        quiet(() -> {
            for (int i = 0; i < 16 && GL11.glGetInteger(0x0BB0) > 0; i++) {
                GL11.glPopAttrib();
            }
        });
        quiet(() -> {
            for (int i = 0; i < 16 && GL11.glGetInteger(0x0BB1) > 0; i++) {
                GL11.glPopClientAttrib();
            }
        });
        popMatrices(GL11.GL_TEXTURE, 0x0BA5);
        popMatrices(GL11.GL_PROJECTION, 0x0BA4);
        popMatrices(GL11.GL_MODELVIEW, 0x0BA3);
        quiet(() -> {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(true);
            GL11.glColorMask(true, true, true, true);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                continue;
            }
        });
    }

    private static void popMatrices(int mode, int depthQuery) {
        quiet(() -> {
            GL11.glMatrixMode(mode);
            for (int i = 0; i < 64 && GL11.glGetInteger(depthQuery) > 1; i++) {
                GL11.glPopMatrix();
            }
        });
    }

    private interface GlAction {
        void run();
    }

    private static void quiet(GlAction action) {
        try {
            action.run();
        } catch (Throwable ignored) {
            return;
        }
    }

    private static boolean optifinePresent() {
        if (!optifineChecked) {
            optifineChecked = true;
            optifine = NyxLaunch.gameClassLoader().getResource("shadersmod/client/Shaders.class") != null;
        }
        return optifine;
    }

    private static Class<?> shaders() throws ClassNotFoundException {
        return Class.forName(SHADERS, false, NyxLaunch.gameClassLoader());
    }

    private static boolean shadersActive() {
        if (!optifinePresent()) {
            return false;
        }
        try {
            return shaders().getField("shaderPackLoaded").getBoolean(null);
        } catch (Throwable error) {
            return false;
        }
    }

    private static String disableShaders() {
        String pack = null;
        try {
            Class<?> type = shaders();
            pack = currentShader(type);
            invokeQuiet(type, "setShaderPack", new Class<?>[] {String.class}, "OFF");
            invokeQuiet(type, "uninit", new Class<?>[0]);
            invokeQuiet(type, "storeConfig", new Class<?>[0]);
            lastShader = "OFF";
            shaderChangedAt = 0L;
            NyxRuntime.log("Shader pack disabled after a crash: " + pack);
        } catch (Throwable error) {
            NyxRuntime.log("Could not disable the shader pack: " + unwrap(error));
        }
        return pack == null ? "?" : clean(pack);
    }

    private static String clean(String text) {
        return text.replaceAll("§.", "");
    }

    private static void invokeQuiet(Class<?> type, String name, Class<?>[] types, Object... args) {
        try {
            Method method = type.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (Throwable error) {
            NyxRuntime.log("Shaders." + name + " failed: " + unwrap(error));
        }
    }

    private static String currentShader(Class<?> type) throws ReflectiveOperationException {
        if (shaderNameField == null) {
            shaderNameField = type.getDeclaredField("currentshadername");
            shaderNameField.setAccessible(true);
        }
        Object value = shaderNameField.get(null);
        return value == null ? null : String.valueOf(value);
    }

    static void watchShaders() {
        if (!optifinePresent() || ++shaderCheck % 20 != 0) {
            return;
        }
        try {
            String current = currentShader(shaders());
            if (lastShader != null && current != null && !current.equals(lastShader)) {
                shaderChangedAt = System.currentTimeMillis();
            }
            lastShader = current;
        } catch (Throwable error) {
            optifine = false;
        }
    }
}
