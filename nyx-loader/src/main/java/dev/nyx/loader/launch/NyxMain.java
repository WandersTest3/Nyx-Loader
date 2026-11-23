package dev.nyx.loader.launch;

import dev.nyx.api.Nyx;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.FabricModContainer;
import dev.nyx.loader.fabric.FabricModMetadata;
import dev.nyx.loader.fabric.FabricModResolver;
import dev.nyx.loader.fabric.MappingResolverImpl;
import dev.nyx.loader.fabric.NyxFabricLoader;
import dev.nyx.loader.forge.ForgeCompat;
import dev.nyx.loader.mixin.NyxMixinBootstrap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public final class NyxMain {

    private static final String MAIN_CLASS = "net.minecraft.client.main.Main";
    private static final String MAPPINGS = "mappings/mappings.tiny";
    private static final String REALMS = "com/mojang/realmsclient/RealmsMainScreen.class";
    private static final String MIXINEXTRAS = "META-INF/jars/mixinextras-fabric-0.5.5.jar";
    private static final int REMAP_REVISION = 1;
    private static final String[] FORGE_INCOMPATIBLE_MODULES = {
        "legacy-fabric-registry-sync-api-v1", "legacy-fabric-registry-sync-api-v2", "legacy-fabric-biome-api-v1",
        "legacy-fabric-block-api-v1", "legacy-fabric-block-entity-api-v1", "legacy-fabric-command-api-v1",
        "legacy-fabric-command-api-v2", "legacy-fabric-effect-api-v1", "legacy-fabric-enchantment-api-v1",
        "legacy-fabric-entity-api-v1", "legacy-fabric-item-api-v1", "legacy-fabric-networking-api-v1"
    };
    private static final String FML_TWEAKER = "cpw.mods.fml.common.launcher.FMLTweaker";
    private static final String SRG_DATA = "deobfuscation_data-1.7.10.lzma";

    private NyxMain() {
    }

    private static final java.io.PrintStream ORIGINAL_ERR = System.err;

    public static void main(String[] args) throws Throwable {
        try {
            run(args);
        } catch (Throwable error) {
            reportCrash(args, error);
            throw error;
        }
    }

    private static void reportCrash(String[] args, Throwable error) {
        error.printStackTrace(ORIGINAL_ERR);
        ClassCache.invalidate(new File(option(args, "--gameDir", ".")));
        try {
            File logs = new File(option(args, "--gameDir", "."), "logs");
            logs.mkdirs();
            try (java.io.PrintStream out = new java.io.PrintStream(new File(logs, "nyx-crash.txt"), "UTF-8")) {
                out.println("Nyx Loader " + Nyx.VERSION + " crashed while starting Minecraft " + Nyx.MINECRAFT_VERSION);
                out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
                out.println();
                error.printStackTrace(out);
            }
        } catch (Throwable ignored) {
            ORIGINAL_ERR.println("Could not write nyx-crash.txt: " + ignored);
        }
    }

    private static void run(String[] args) throws Throwable {
        File gameDir = new File(option(args, "--gameDir", "."));
        NyxRuntime.setGameDirectory(gameDir);
        boolean forge = ClassLoader.getSystemResource(FML_TWEAKER.replace('.', '/') + ".class") != null
                && !Boolean.getBoolean("nyx.disableForge");
        NyxRuntime.log("Nyx Loader " + Nyx.VERSION + " for Minecraft " + Nyx.MINECRAFT_VERSION + " on Java "
                + System.getProperty("java.version") + (forge ? " (Forge + Fabric)" : " (Fabric)"));
        Path cache = gameDir.toPath().resolve(".nyx");
        Files.createDirectories(cache);

        Path gameJar = locate(System.getProperty("nyx.gameJar"), MAIN_CLASS.replace('.', '/') + ".class");
        if (gameJar == null) {
            throw new IllegalStateException("Minecraft jar not found on the classpath");
        }
        TinyMappings mappings;
        String mappingsHash;
        try (InputStream input = open(System.getProperty("nyx.mappings"), MAPPINGS)) {
            byte[] bytes = input.readAllBytes();
            mappingsHash = sha1(bytes);
            try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(bytes),
                    StandardCharsets.UTF_8)) {
                mappings = TinyMappings.read(reader);
            }
        }
        NyxLaunch.setMappings(mappings);
        if (forge) {
            launchForge(args, gameDir, cache, gameJar, mappings);
        } else {
            launchFabric(args, cache, gameJar, mappings, mappingsHash);
        }
    }

    private static void launchFabric(String[] args, Path cache, Path gameJar, TinyMappings mappings,
            String mappingsHash) throws Throwable {
        Path remapped = cache.resolve("remapped").resolve("minecraft-" + Nyx.MINECRAFT_VERSION + "-intermediary-"
                + sha1(Files.readAllBytes(gameJar)).substring(0, 10) + "-" + mappingsHash.substring(0, 8) + "-r"
                + REMAP_REVISION + ".jar");
        if (!Files.isRegularFile(remapped)) {
            Files.createDirectories(remapped.getParent());
            long start = System.currentTimeMillis();
            NyxRuntime.log("Remapping " + gameJar.getFileName() + " to intermediary (first launch only)...");
            new JarRemapper(mappings).remap(gameJar, remapped);
            NyxRuntime.log("Remapped in " + (System.currentTimeMillis() - start) + " ms");
        }
        NyxClassLoader loader = new NyxClassLoader(NyxMain.class.getClassLoader());
        loader.addURL(remapped.toUri().toURL());
        Path realms = locate(null, REALMS);
        if (realms != null) {
            loader.addURL(realms.toUri().toURL());
        }
        NyxLaunch.setGameLoader(loader);
        NyxRuntime.setClassLoader(loader);
        Thread.currentThread().setContextClassLoader(loader);

        NyxFabricLoader fabricLoader = loadMods(args, cache, remapped, loader, mappings);
        startMixinAndPreLaunch(fabricLoader);

        ClassCache.activate(NyxRuntime.getGameDirectory(), "fabric", new ArrayList<>());
        Class<?> main = Class.forName(MAIN_CLASS, true, loader);
        MethodHandle handle = MethodHandles.publicLookup().findStatic(main, "main",
                MethodType.methodType(void.class, String[].class));
        handle.invokeExact(args);
    }

    private static void launchForge(String[] args, File gameDir, Path cache, Path gameJar, TinyMappings mappings)
            throws Throwable {
        ForgeCompat.prepareJava();
        try (InputStream raw = ClassLoader.getSystemResourceAsStream(SRG_DATA)) {
            if (raw == null) {
                throw new IOException(SRG_DATA + " not found: is Forge on the classpath?");
            }
            Class<?> lzma = Class.forName("LZMA.LzmaInputStream");
            InputStream input = (InputStream) lzma.getConstructor(InputStream.class).newInstance(raw);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                NyxLaunch.setNames(RuntimeNames.srg(mappings, reader));
            }
        }
        NyxLaunch.setForge(true);

        String profile = null;
        File assetsDir = null;
        List<String> tweakClassNames = new ArrayList<>();
        List<String> nonOptions = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String key = arg;
            String value = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                key = arg.substring(0, eq);
                value = arg.substring(eq + 1);
            }
            boolean known = key.equals("--version") || key.equals("--gameDir") || key.equals("--assetsDir")
                    || key.equals("--tweakClass");
            if (!known) {
                nonOptions.add(arg);
                continue;
            }
            if (value == null) {
                value = i + 1 < args.length ? args[++i] : "";
            }
            switch (key) {
                case "--version":
                    profile = value;
                    break;
                case "--assetsDir":
                    assetsDir = new File(value);
                    break;
                case "--tweakClass":
                    tweakClassNames.add(value);
                    break;
                default:
                    break;
            }
        }
        if (!tweakClassNames.contains(FML_TWEAKER)) {
            tweakClassNames.add(0, FML_TWEAKER);
        }

        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                urls.add(new File(entry).toURI().toURL());
            }
        }
        LaunchClassLoader loader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.classLoader = loader;
        Launch.minecraftHome = gameDir;
        Launch.assetsDir = assetsDir;
        Launch.blackboard = new HashMap<>();
        NyxLaunch.setGameLoader(loader);
        NyxRuntime.setClassLoader(loader);
        Thread.currentThread().setContextClassLoader(loader);
        disableForgeSplashOnFirstRun(gameDir);

        NyxFabricLoader fabricLoader = loadMods(args, cache, gameJar, loader, mappings);

        List<String> argumentList = new ArrayList<>();
        Launch.blackboard.put("TweakClasses", tweakClassNames);
        Launch.blackboard.put("ArgumentList", argumentList);
        Set<String> allTweakerNames = new HashSet<>();
        List<ITweaker> allTweakers = new ArrayList<>();
        List<ITweaker> tweakers = new ArrayList<>();
        Launch.blackboard.put("Tweaks", tweakers);
        ITweaker primary = null;
        do {
            for (Iterator<String> it = tweakClassNames.iterator(); it.hasNext();) {
                String tweakName = it.next();
                it.remove();
                if (!allTweakerNames.add(tweakName)) {
                    continue;
                }
                NyxRuntime.log("Loading tweak class " + tweakName);
                loader.addClassLoaderExclusion(tweakName.substring(0, tweakName.lastIndexOf('.')));
                ITweaker tweaker = (ITweaker) Class.forName(tweakName, true, loader).getDeclaredConstructor().newInstance();
                tweakers.add(tweaker);
                if (primary == null) {
                    primary = tweaker;
                }
            }
            for (Iterator<ITweaker> it = tweakers.iterator(); it.hasNext();) {
                ITweaker tweaker = it.next();
                tweaker.acceptOptions(new ArrayList<>(nonOptions), gameDir, assetsDir, profile);
                tweaker.injectIntoClassLoader(loader);
                allTweakers.add(tweaker);
                it.remove();
            }
        } while (!tweakClassNames.isEmpty());
        for (ITweaker tweaker : allTweakers) {
            argumentList.addAll(Arrays.asList(tweaker.getLaunchArguments()));
        }

        startMixinAndPreLaunch(fabricLoader);

        List<String> transformerNames = new ArrayList<>();
        for (Object transformer : loader.getTransformers()) {
            transformerNames.add(transformer.getClass().getName());
        }
        ClassCache.activate(gameDir, "forge", transformerNames);
        String target = primary.getLaunchTarget();
        NyxRuntime.log("Launching " + target);
        Class<?> main = Class.forName(target, false, loader);
        MethodHandle handle = MethodHandles.publicLookup().findStatic(main, "main",
                MethodType.methodType(void.class, String[].class));
        handle.invokeExact(argumentList.toArray(new String[0]));
    }

    private static void disableForgeSplashOnFirstRun(File gameDir) {
        File splash = new File(gameDir, "config" + File.separator + "splash.properties");
        if (splash.exists() || Boolean.getBoolean("nyx.keepForgeSplash")) {
            return;
        }
        try {
            Files.createDirectories(splash.getParentFile().toPath());
            Files.write(splash.toPath(), "enabled=false\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            NyxRuntime.log("Could not write splash.properties: " + error);
        }
    }

    private static NyxFabricLoader loadMods(String[] args, Path cache, Path minecraftRoot, GameLoader loader,
            TinyMappings mappings) throws Throwable {
        FabricModResolver resolver = new FabricModResolver(cache.resolve("nested"), EnvType.CLIENT);
        resolver.addBuiltin(FabricModMetadata.builtin("minecraft", "Minecraft", Nyx.MINECRAFT_VERSION,
                "Minecraft Java Edition", "Mojang Studios", null, null, null, null), minecraftRoot);
        resolver.addBuiltin(FabricModMetadata.builtin("java", "Java", javaVersion(),
                System.getProperty("java.vm.name"), null, null, null, null, null), null);
        Path self = codeSource();
        FabricModResolver.Candidate fabric = resolver.addBuiltin(FabricModMetadata.builtin("fabricloader",
                "Fabric Loader (Nyx)", NyxFabricLoader.FABRIC_LOADER_VERSION, "Fabric compatibility layer of Nyx",
                "Wanders_Test3", "https://github.com/WandersTest3/Nyx-Loader",
                "https://github.com/WandersTest3/Nyx-Loader/issues", "Mod Test3 License V1.0", "assets/nyx/icon.png",
                "fabric-loader"), self);
        resolver.addBuiltin(FabricModMetadata.builtin("nyx", "Nyx Loader", Nyx.VERSION, "Nyx Loader",
                "Wanders_Test3", "https://github.com/WandersTest3/Nyx-Loader",
                "https://github.com/WandersTest3/Nyx-Loader/issues", "Mod Test3 License V1.0", "assets/nyx/icon.png"),
                self);
        if (NyxLaunch.isForge()) {
            List<String> forbidden = new ArrayList<>();
            for (String module : FORGE_INCOMPATIBLE_MODULES) {
                forbidden.add(module);
                forbidden.add(module + "-common");
                forbidden.add(module + "-common-versioned");
            }
            resolver.forbid(forbidden, "it replaces registries that Forge (FML) already manages");
            resolver.addBuiltin(FabricModMetadata.builtin("forge", "Minecraft Forge", forgeVersion(),
                    "Minecraft Forge", "Forge team", null, null, "LGPL-2.1", null, "fml"), null);
        }
        try (InputStream input = NyxMain.class.getClassLoader().getResourceAsStream(MIXINEXTRAS)) {
            if (input != null) {
                resolver.addBuiltinNested(fabric, MIXINEXTRAS, input);
            } else {
                NyxRuntime.log("MixinExtras is missing from the Nyx jar");
            }
        }

        File modsDir = NyxRuntime.getModsDirectory();
        if (!modsDir.isDirectory() && !modsDir.mkdirs()) {
            NyxRuntime.log("Could not create mods directory: " + modsDir);
        }
        File[] files = modsDir.listFiles();
        int forgeMods = 0;
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar")) {
                    continue;
                }
                try {
                    if (resolver.addRoot(file.toPath())) {
                        continue;
                    }
                    if (NyxRuntime.isNyxModJar(file)) {
                        NyxRuntime.readMod(file, loader);
                    } else if (isForgeMod(file)) {
                        forgeMods++;
                        if (!NyxLaunch.isForge()) {
                            NyxRuntime.log("Skipping " + file.getName() + ": Forge mod, but Forge is not installed");
                        }
                    }
                } catch (IOException error) {
                    NyxRuntime.log("Skipping " + file.getName() + ": " + error.getMessage());
                }
            }
        }

        FabricModResolver.Result result;
        try {
            result = resolver.resolve();
        } catch (IllegalStateException error) {
            NyxRuntime.log(error.getMessage());
            throw error;
        }
        int fabricMods = 0;
        for (FabricModContainer mod : result.mods) {
            if (mod.isBuiltin()) {
                continue;
            }
            fabricMods++;
            loader.addURL(mod.getJarPath().toUri().toURL());
            String widener = mod.getFabricMetadata().getAccessWidener();
            if (widener != null) {
                readAccessWidener(mod, widener);
            }
        }
        for (Path library : result.libraries) {
            loader.addURL(library.toUri().toURL());
        }
        NyxRuntime.log(NyxRuntime.getMods().size() + " Nyx mod(s), " + fabricMods + " Fabric mod(s)"
                + (NyxLaunch.isForge() ? ", " + forgeMods + " Forge mod(s) (loaded by FML)" : ""));
        for (FabricModContainer mod : result.mods) {
            if (!mod.isBuiltin()) {
                NyxRuntime.log("  - " + mod.getMetadata().getId() + " "
                        + mod.getMetadata().getVersion().getFriendlyString()
                        + (mod.isNested() ? " (via " + mod.getParent().getMetadata().getId() + ")" : ""));
            }
        }
        readNyxAccessWideners();
        NyxRuntime.setFabricMods(result.mods);
        return NyxFabricLoader.create(result.mods, new MappingResolverImpl(mappings, NyxLaunch.names()), args);
    }

    private static void startMixinAndPreLaunch(NyxFabricLoader fabricLoader) {
        NyxMixinBootstrap.init(fabricLoader.getModsInternal());
        NyxLaunch.setMixinReady();
        fabricLoader.setupEntrypoints();
        fabricLoader.invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
    }

    private static boolean isForgeMod(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("mcmod.info") != null) {
                return true;
            }
            java.util.jar.Manifest manifest = new java.util.jar.JarFile(file).getManifest();
            return manifest != null && (manifest.getMainAttributes().getValue("FMLCorePlugin") != null
                    || manifest.getMainAttributes().getValue("FMLAT") != null);
        } catch (IOException error) {
            return false;
        }
    }

    private static String forgeVersion() {
        try (InputStream input = ClassLoader.getSystemResourceAsStream("fmlversion.properties")) {
            if (input == null) {
                return "10.13.4";
            }
            java.util.Properties properties = new java.util.Properties();
            properties.load(input);
            return properties.getProperty("fmlbuild.major.number", "7") + "."
                    + properties.getProperty("fmlbuild.minor.number", "10") + "."
                    + properties.getProperty("fmlbuild.revision.number", "0");
        } catch (IOException error) {
            return "10.13.4";
        }
    }

    private static void readNyxAccessWideners() throws IOException {
        for (dev.nyx.loader.ModContainer mod : NyxRuntime.getMods()) {
            String path = NyxRuntime.getNyxAccessWidener(mod.id);
            if (path == null) {
                continue;
            }
            try (ZipFile zip = new ZipFile(mod.file)) {
                ZipEntry entry = zip.getEntry(path);
                if (entry == null) {
                    throw new IOException("Access widener " + path + " missing in " + mod.id);
                }
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    NyxLaunch.accessWidener().read(reader, mod.id + "/" + path, NyxLaunch.names());
                }
            }
        }
    }

    private static void readAccessWidener(FabricModContainer mod, String path) throws IOException {
        try (ZipFile zip = new ZipFile(mod.getJarPath().toFile())) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                throw new IOException("Access widener " + path + " missing in " + mod.getMetadata().getId());
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                NyxLaunch.accessWidener().read(reader, mod.getMetadata().getId() + "/" + path, NyxLaunch.names());
            }
        }
    }

    private static String javaVersion() {
        String spec = System.getProperty("java.specification.version", "21");
        return spec.startsWith("1.") ? spec.substring(2) : spec;
    }

    private static Path codeSource() throws URISyntaxException {
        URL location = NyxMain.class.getProtectionDomain().getCodeSource().getLocation();
        return Paths.get(location.toURI());
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static Path locate(String override, String resource) throws IOException, URISyntaxException {
        if (override != null) {
            return Paths.get(override);
        }
        URL url = ClassLoader.getSystemResource(resource);
        if (url == null) {
            return null;
        }
        if ("jar".equals(url.getProtocol())) {
            return Paths.get(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
        }
        return null;
    }

    private static InputStream open(String override, String resource) throws IOException {
        if (override != null) {
            return Files.newInputStream(Paths.get(override));
        }
        InputStream input = ClassLoader.getSystemResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Intermediary mappings (" + resource + ") not found on the classpath");
        }
        return input;
    }

    private static String sha1(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
