package dev.nyx.loader;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CrashAnalyzer {

    private static final String[] IGNORED = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "cpw.mods.fml.", "net.minecraftforge.",
        "org.lwjgl.", "org.spongepowered.", "com.llamalad7.", "org.objectweb.asm.", "com.google.", "org.apache.",
        "io.netty.", "paulscode.", "com.mojang.", "LZMA.", "scala.", "dev.nyx.mc171.NyxCrash",
        "dev.nyx.mc171.NyxFrame", "dev.nyx.loader.launch.", "dev.nyx.loader.Hooks", "dev.nyx.loader.NyxRuntime", "dev.nyx.loader.fabric.NyxFabricLoader"
    };
    private static final String[] HANDLER_PREFIXES = {
        "handler$", "redirect$", "modify$", "localvar$", "constant$", "wrapOperation$", "wrapWithCondition$",
        "modifyExpressionValue$", "modifyReturnValue$", "modifyReceiver$"
    };
    private static final ModContainer NYX = new ModContainer("nyx", "Nyx Loader", dev.nyx.api.Nyx.VERSION, null,
            null, null, null, null, null, null, null, false, "nyx");
    private static final int EXCERPT = 8;

    private CrashAnalyzer() {
    }

    public static final class Result {
        public final Throwable root;
        public final List<ModContainer> suspects;
        public final List<String> excerpt;
        public final boolean touchesShaders;

        Result(Throwable root, List<ModContainer> suspects, List<String> excerpt, boolean touchesShaders) {
            this.root = root;
            this.suspects = Collections.unmodifiableList(suspects);
            this.excerpt = Collections.unmodifiableList(excerpt);
            this.touchesShaders = touchesShaders;
        }

        public String suspectNames() {
            if (suspects.isEmpty()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (ModContainer mod : suspects) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(mod.name);
            }
            return text.toString();
        }

        public boolean blames(String id) {
            for (ModContainer mod : suspects) {
                if (id.equalsIgnoreCase(mod.id)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Result analyze(Throwable error) {
        List<Throwable> chain = chain(error);
        Throwable root = chain.get(chain.size() - 1);
        Map<File, ModContainer> files = InstalledMods.byFile();
        Map<String, ModContainer> byId = new HashMap<String, ModContainer>();
        for (ModContainer mod : files.values()) {
            byId.put(mod.id.toLowerCase(java.util.Locale.ROOT), mod);
        }
        Map<String, ModContainer> classCache = new HashMap<String, ModContainer>();
        LinkedHashMap<String, ModContainer> suspects = new LinkedHashMap<String, ModContainer>();
        boolean shaders = false;
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (StackTraceElement frame : chain.get(i).getStackTrace()) {
                String className = frame.getClassName();
                if (className.startsWith("shadersmod.")) {
                    shaders = true;
                }
                ModContainer mixinOwner = mixinOwner(frame.getMethodName(), byId);
                if (mixinOwner != null) {
                    suspects.putIfAbsent(mixinOwner.id, mixinOwner);
                }
                ModContainer owner = owner(className, files, classCache);
                if (owner != null) {
                    suspects.putIfAbsent(owner.id, owner);
                }
            }
        }
        return new Result(root, new ArrayList<ModContainer>(suspects.values()), excerpt(error, root), shaders);
    }

    private static List<Throwable> chain(Throwable error) {
        List<Throwable> chain = new ArrayList<Throwable>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable current = error;
        while (current != null && seen.add(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }

    private static List<String> excerpt(Throwable top, Throwable root) {
        List<String> lines = new ArrayList<String>();
        if (root != top) {
            lines.add(top.toString());
        }
        lines.add((root != top ? "Caused by: " : "") + root.toString());
        StackTraceElement[] trace = root.getStackTrace();
        for (int i = 0; i < trace.length && i < EXCERPT; i++) {
            lines.add("  at " + trace[i]);
        }
        if (trace.length > EXCERPT) {
            lines.add("  ... " + (trace.length - EXCERPT) + " more");
        }
        return lines;
    }

    private static ModContainer mixinOwner(String method, Map<String, ModContainer> byId) {
        for (String prefix : HANDLER_PREFIXES) {
            if (method.startsWith(prefix)) {
                String[] parts = method.split("\\$");
                if (parts.length >= 4) {
                    return byId.get(parts[2].toLowerCase(java.util.Locale.ROOT));
                }
                return null;
            }
        }
        return null;
    }

    private static ModContainer owner(String className, Map<File, ModContainer> files, Map<String, ModContainer> cache) {
        for (String prefix : IGNORED) {
            if (className.startsWith(prefix)) {
                return null;
            }
        }
        if (className.startsWith("dev.nyx.mc171.") || className.startsWith("dev.nyx.loader.")
                || className.startsWith("dev.nyx.api.")) {
            return NYX;
        }
        if (cache.containsKey(className)) {
            return cache.get(className);
        }
        String resource = className;
        int lambda = resource.indexOf("$$");
        if (lambda > 0) {
            resource = resource.substring(0, lambda);
        }
        ModContainer owner = null;
        try {
            ClassLoader loader = dev.nyx.loader.launch.NyxLaunch.gameClassLoader();
            URL url = loader.getResource(resource.replace('.', '/') + ".class");
            File jar = jarOf(url);
            if (jar != null) {
                owner = files.get(InstalledMods.canonical(jar));
            }
        } catch (Throwable ignored) {
            owner = null;
        }
        cache.put(className, owner);
        return owner;
    }

    private static File jarOf(URL url) {
        if (url == null) {
            return null;
        }
        try {
            if ("jar".equals(url.getProtocol())) {
                return new File(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
