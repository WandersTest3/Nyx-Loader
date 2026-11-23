package dev.nyx.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class InstalledMods {

    private static final List<ModContainer> EXTRA = new ArrayList<ModContainer>();

    private InstalledMods() {
    }

    public static synchronized void addRuntime(ModContainer mod) {
        EXTRA.add(mod);
    }

    public static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException error) {
            return file.getAbsoluteFile();
        }
    }

    public static List<ModContainer> all() {
        List<ModContainer> result = new ArrayList<ModContainer>(NyxRuntime.getMods());
        result.addAll(NyxRuntime.getFabricEntries());
        result.addAll(NyxRuntime.getForgeEntries());
        synchronized (InstalledMods.class) {
            result.addAll(EXTRA);
        }
        return result;
    }

    public static List<ModContainer> list() {
        File modsDir = canonical(NyxRuntime.getModsDirectory());
        Set<File> shown = new HashSet<File>();
        List<ModContainer> result = new ArrayList<ModContainer>();
        for (ModContainer mod : all()) {
            File file = mod.file == null ? null : canonical(mod.file);
            if (file != null && modsDir.equals(file.getParentFile())) {
                shown.add(file);
                result.add(mod);
            }
        }
        result.addAll(tweakerJars(modsDir, shown));
        Collections.sort(result, (a, b) -> {
            if (a.library != b.library) {
                return a.library ? 1 : -1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    public static Map<File, ModContainer> byFile() {
        Map<File, ModContainer> result = new HashMap<File, ModContainer>();
        for (ModContainer mod : all()) {
            if (mod.file != null) {
                File file = canonical(mod.file);
                ModContainer previous = result.get(file);
                if (previous == null || previous.library && !mod.library) {
                    result.put(file, mod);
                }
            }
        }
        for (ModContainer mod : tweakerJars(canonical(NyxRuntime.getModsDirectory()), result.keySet())) {
            result.put(canonical(mod.file), mod);
        }
        return result;
    }

    private static List<ModContainer> tweakerJars(File modsDir, Set<File> known) {
        List<ModContainer> result = new ArrayList<ModContainer>();
        File[] files = modsDir.listFiles();
        if (files == null) {
            return result;
        }
        Arrays.sort(files);
        for (File file : files) {
            String fileName = file.getName();
            if (!file.isFile() || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar") || known.contains(canonical(file))) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                Manifest manifest = jar.getManifest();
                Attributes attributes = manifest == null ? null : manifest.getMainAttributes();
                if (attributes == null || attributes.getValue("TweakClass") == null
                        && attributes.getValue("FMLCorePlugin") == null) {
                    continue;
                }
                String base = fileName.substring(0, fileName.length() - 4);
                boolean optifine = jar.getEntry("optifine/OptiFineForgeTweaker.class") != null;
                String name = optifine ? "OptiFine" : base;
                String version = attributes.getValue("Implementation-Version");
                if (optifine && base.startsWith("OptiFine_")) {
                    version = base.substring("OptiFine_".length());
                }
                result.add(new ModContainer(optifine ? "optifine" : base.toLowerCase(Locale.ROOT), name,
                        version == null ? "?" : version, null, optifine ? "OptiFine" : null,
                        optifine ? "sp614x" : null, null, optifine ? "https://optifine.net" : null, null, null, file,
                        false, "forge"));
            } catch (IOException ignored) {
                continue;
            }
        }
        return result;
    }
}
