package dev.nyx.loader;

import dev.nyx.loader.fabric.FabricModContainer;
import dev.nyx.loader.fabric.FabricModMetadata;
import dev.nyx.loader.fabric.NyxFabricLoader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;

public final class HotLoader {

    private static final Set<String> RUNTIME_ENTRYPOINTS = new HashSet<String>(Arrays.asList("client", "modmenu"));
    private static final Set<String> PROVIDED = new HashSet<String>(Arrays.asList("minecraft", "java", "fabricloader",
            "fabric-loader", "nyx"));

    private HotLoader() {
    }

    public static final class Result {
        public final boolean loaded;
        public final String reason;
        public final ModContainer mod;

        Result(boolean loaded, String reason, ModContainer mod) {
            this.loaded = loaded;
            this.reason = reason;
            this.mod = mod;
        }
    }

    public static Result load(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("nyx.mod.properties") != null) {
                String blocker = NyxRuntime.runtimeBlocker(file);
                if (blocker != null) {
                    return new Result(false, blocker, null);
                }
                ModContainer mod = NyxRuntime.loadAtRuntime(file);
                return new Result(true, null, mod);
            }
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                return loadFabric(file, zip, fabric);
            }
            if (zip.getEntry("mcmod.info") != null) {
                return new Result(false, "forge", null);
            }
            return new Result(false, "unknown", null);
        } catch (Throwable error) {
            NyxRuntime.log("Could not load " + file.getName() + " at runtime: " + error);
            error.printStackTrace();
            return new Result(false, error.getClass().getSimpleName() + ": " + error.getMessage(), null);
        }
    }

    private static Result loadFabric(File file, ZipFile zip, ZipEntry entry) throws IOException {
        if (!NyxFabricLoader.isReady()) {
            return new Result(false, "fabric", null);
        }
        FabricModMetadata metadata;
        try (InputStream input = zip.getInputStream(entry)) {
            metadata = FabricModMetadata.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        String blocker = fabricBlocker(metadata);
        if (blocker != null) {
            return new Result(false, blocker, null);
        }
        NyxRuntime.getClassLoader().addURL(file.toURI().toURL());
        FabricModContainer container = new FabricModContainer(metadata, file.toPath(), false, null);
        NyxFabricLoader loader = NyxFabricLoader.get();
        loader.addRuntimeMod(container);
        ModContainer listed = NyxRuntime.toEntry(container);
        NyxRuntime.addFabricEntry(listed);
        loader.invokeModEntrypoints("client", metadata.getId(), ClientModInitializer.class,
                ClientModInitializer::onInitializeClient);
        NyxRuntime.log("Loaded " + metadata.getName() + " " + metadata.getVersion().getFriendlyString()
                + " without restarting");
        return new Result(true, null, listed);
    }

    private static String fabricBlocker(FabricModMetadata metadata) {
        if (NyxRuntime.isModLoaded(metadata.getId())) {
            return "already loaded";
        }
        if (metadata.getEnvironment() == ModEnvironment.SERVER) {
            return "server only";
        }
        if (!metadata.getMixinConfigs(EnvType.CLIENT).isEmpty()) {
            return "mixins";
        }
        if (metadata.getAccessWidener() != null) {
            return "access widener";
        }
        if (!metadata.getJars().isEmpty()) {
            return "nested jars";
        }
        if (!metadata.getLanguageAdapterDefinitions().isEmpty()) {
            return "language adapter";
        }
        for (Map.Entry<String, List<FabricModMetadata.EntrypointDef>> key : metadata.getEntrypointDefs().entrySet()) {
            if (!RUNTIME_ENTRYPOINTS.contains(key.getKey()) && !key.getValue().isEmpty()) {
                return "entrypoint " + key.getKey();
            }
            for (FabricModMetadata.EntrypointDef def : key.getValue()) {
                if (!"default".equals(def.adapter)) {
                    return "language adapter";
                }
            }
        }
        for (ModDependency dependency : metadata.getDependencies()) {
            if (dependency.getKind() != ModDependency.Kind.DEPENDS) {
                continue;
            }
            String id = dependency.getModId();
            if (!PROVIDED.contains(id) && !NyxRuntime.isModLoaded(id)) {
                return "missing " + id;
            }
        }
        return null;
    }
}
