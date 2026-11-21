package dev.nyx.loader.launch;

import dev.nyx.api.Nyx;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.FabricModContainer;
import dev.nyx.loader.fabric.FabricModMetadata;
import dev.nyx.loader.fabric.FabricModResolver;
import dev.nyx.loader.fabric.MappingResolverImpl;
import dev.nyx.loader.fabric.NyxFabricLoader;
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
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class NyxMain {

    private static final String MAIN_CLASS = "net.minecraft.client.main.Main";
    private static final String MAPPINGS = "mappings/mappings.tiny";
    private static final String REALMS = "com/mojang/realmsclient/RealmsMainScreen.class";
    private static final String MIXINEXTRAS = "META-INF/jars/mixinextras-fabric-0.5.5.jar";
    private static final int REMAP_REVISION = 1;

    private NyxMain() {
    }

    public static void main(String[] args) throws Throwable {
        File gameDir = new File(option(args, "--gameDir", "."));
        NyxRuntime.setGameDirectory(gameDir);
        NyxRuntime.log("Nyx Loader " + Nyx.VERSION + " for Minecraft " + Nyx.MINECRAFT_VERSION + " on Java "
                + System.getProperty("java.version"));
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
        NyxLaunch.setClassLoader(loader);
        NyxRuntime.setClassLoader(loader);
        Thread.currentThread().setContextClassLoader(loader);

        FabricModResolver resolver = new FabricModResolver(cache.resolve("nested"), EnvType.CLIENT);
        resolver.addBuiltin(FabricModMetadata.builtin("minecraft", "Minecraft", Nyx.MINECRAFT_VERSION,
                "Minecraft Java Edition", "Mojang Studios", null, null, null, null), remapped);
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
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar")) {
                    continue;
                }
                try {
                    if (!resolver.addRoot(file.toPath())) {
                        NyxRuntime.readMod(file, loader);
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
        NyxRuntime.log(NyxRuntime.getMods().size() + " Nyx mod(s), " + fabricMods + " Fabric mod(s)");
        for (FabricModContainer mod : result.mods) {
            if (!mod.isBuiltin()) {
                NyxRuntime.log("  - " + mod.getMetadata().getId() + " "
                        + mod.getMetadata().getVersion().getFriendlyString()
                        + (mod.isNested() ? " (via " + mod.getParent().getMetadata().getId() + ")" : ""));
            }
        }
        NyxRuntime.setFabricMods(result.mods);

        NyxFabricLoader fabricLoader = NyxFabricLoader.create(result.mods, new MappingResolverImpl(mappings), args);
        NyxMixinBootstrap.init(result.mods);
        NyxLaunch.setMixinReady();
        fabricLoader.setupEntrypoints();
        fabricLoader.invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);

        Class<?> main = Class.forName(MAIN_CLASS, true, loader);
        MethodHandle handle = MethodHandles.publicLookup().findStatic(main, "main",
                MethodType.methodType(void.class, String[].class));
        handle.invokeExact(args);
    }

    private static void readAccessWidener(FabricModContainer mod, String path) throws IOException {
        try (ZipFile zip = new ZipFile(mod.getJarPath().toFile())) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                throw new IOException("Access widener " + path + " missing in " + mod.getMetadata().getId());
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                NyxLaunch.accessWidener().read(reader, mod.getMetadata().getId() + "/" + path);
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
