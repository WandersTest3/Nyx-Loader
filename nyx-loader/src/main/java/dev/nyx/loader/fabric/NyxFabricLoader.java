package dev.nyx.loader.fabric;

import dev.nyx.api.Nyx;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.launch.NyxLaunch;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

public final class NyxFabricLoader implements FabricLoader {

    public static final String FABRIC_LOADER_VERSION = System.getProperty("nyx.fabricLoaderVersion", "0.20.1");

    private static NyxFabricLoader instance;

    private final List<FabricModContainer> mods = new ArrayList<>();
    private final Map<String, FabricModContainer> modMap = new HashMap<>();
    private final Map<String, List<Entry>> entrypoints = new LinkedHashMap<>();
    private final Map<String, LanguageAdapter> adapters = new HashMap<>();
    private final ObjectShare objectShare = new ObjectShareImpl();
    private final MappingResolver mappingResolver;
    private final String[] launchArguments;
    private Object gameInstance;

    private NyxFabricLoader(List<FabricModContainer> mods, MappingResolver mappingResolver, String[] launchArguments) {
        this.mappingResolver = mappingResolver;
        this.launchArguments = launchArguments.clone();
        this.mods.addAll(mods);
        for (FabricModContainer mod : mods) {
            modMap.putIfAbsent(mod.getMetadata().getId(), mod);
        }
        for (FabricModContainer mod : mods) {
            for (String provided : mod.getMetadata().getProvides()) {
                modMap.putIfAbsent(provided, mod);
            }
        }
        adapters.put("default", DefaultLanguageAdapter.INSTANCE);
    }

    public static NyxFabricLoader create(List<FabricModContainer> mods, MappingResolver resolver, String[] args) {
        if (instance != null) {
            throw new IllegalStateException("Fabric loader already created");
        }
        instance = new NyxFabricLoader(mods, resolver, args);
        return instance;
    }

    public static NyxFabricLoader get() {
        if (instance == null) {
            throw new IllegalStateException("Nyx Fabric compatibility is not initialized yet");
        }
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    public List<FabricModContainer> getModsInternal() {
        return Collections.unmodifiableList(mods);
    }

    public void setupEntrypoints() {
        for (FabricModContainer mod : mods) {
            for (Map.Entry<String, String> adapter : mod.getFabricMetadata().getLanguageAdapterDefinitions().entrySet()) {
                try {
                    Class<?> type = Class.forName(adapter.getValue(), true, NyxLaunch.gameClassLoader());
                    adapters.put(adapter.getKey(), (LanguageAdapter) type.getDeclaredConstructor().newInstance());
                } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
                    throw new RuntimeException("Could not create language adapter " + adapter.getKey() + " of "
                            + mod.getMetadata().getId(), error);
                }
            }
        }
        for (FabricModContainer mod : mods) {
            for (Map.Entry<String, List<FabricModMetadata.EntrypointDef>> key
                    : mod.getFabricMetadata().getEntrypointDefs().entrySet()) {
                for (FabricModMetadata.EntrypointDef def : key.getValue()) {
                    entrypoints.computeIfAbsent(key.getKey(), k -> new ArrayList<>()).add(new Entry(mod, def));
                }
            }
        }
    }

    public void setGameInstance(Object game) {
        this.gameInstance = game;
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (EntrypointContainer<T> container : getEntrypointContainers(key, type)) {
            result.add(container.getEntrypoint());
        }
        return result;
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<Entry> list = entrypoints.get(key);
        if (list == null) {
            return Collections.emptyList();
        }
        List<EntrypointContainer<T>> result = new ArrayList<>();
        EntrypointException failure = null;
        for (Entry entry : list) {
            try {
                T value = entry.get(type);
                if (value != null) {
                    result.add(new ContainerImpl<>(entry, value));
                }
            } catch (Throwable error) {
                if (failure == null) {
                    failure = new EntrypointException(key, entry.mod.getMetadata().getId(), error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        RuntimeException failure = null;
        List<Entry> list = entrypoints.get(key);
        if (list == null) {
            return;
        }
        for (Entry entry : list) {
            try {
                T value = entry.get(type);
                if (value != null) {
                    invoker.accept(value);
                }
            } catch (Throwable error) {
                RuntimeException wrapped = new RuntimeException("Could not execute entrypoint stage '" + key
                        + "' due to errors, provided by '" + entry.mod.getMetadata().getId() + "' at '"
                        + entry.def.value + "'!", error);
                if (failure == null) {
                    failure = wrapped;
                } else {
                    failure.addSuppressed(wrapped);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    @Override
    public MappingResolver getMappingResolver() {
        return mappingResolver;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(modMap.get(id));
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return Collections.unmodifiableList(new ArrayList<ModContainer>(mods));
    }

    @Override
    public boolean isModLoaded(String id) {
        return modMap.containsKey(id) || NyxRuntime.isNyxModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return Boolean.getBoolean("fabric.development");
    }

    @Override
    public EnvType getEnvironmentType() {
        return EnvType.CLIENT;
    }

    @Override
    public String getRawGameVersion() {
        return Nyx.MINECRAFT_VERSION;
    }

    @Override
    public Object getGameInstance() {
        return gameInstance;
    }

    @Override
    public Path getGameDir() {
        return NyxRuntime.getGameDirectory().toPath().toAbsolutePath().normalize();
    }

    @Override
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    @Override
    public Path getConfigDir() {
        Path config = getGameDir().resolve("config");
        try {
            Files.createDirectories(config);
        } catch (java.io.IOException error) {
            NyxRuntime.log("Could not create config directory: " + error);
        }
        return config;
    }

    @Override
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (!sanitize) {
            return launchArguments.clone();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < launchArguments.length; i++) {
            String arg = launchArguments[i];
            if ("--accessToken".equals(arg) || "--session".equals(arg) || "--uuid".equals(arg)
                    || "--userProperties".equals(arg) || "--profileProperties".equals(arg)) {
                i++;
                continue;
            }
            result.add(arg);
        }
        return result.toArray(new String[0]);
    }

    private final class Entry {

        final FabricModContainer mod;
        final FabricModMetadata.EntrypointDef def;
        final Map<Class<?>, Object> instances = new HashMap<>();

        Entry(FabricModContainer mod, FabricModMetadata.EntrypointDef def) {
            this.mod = mod;
            this.def = def;
        }

        synchronized <T> T get(Class<T> type) throws LanguageAdapterException {
            Object cached = instances.get(type);
            if (cached != null) {
                return type.cast(cached);
            }
            LanguageAdapter adapter = adapters.get(def.adapter);
            if (adapter == null) {
                throw new LanguageAdapterException("Unknown language adapter " + def.adapter);
            }
            T value = adapter.create(mod, def.value, type);
            instances.put(type, value);
            return value;
        }
    }

    private static final class ContainerImpl<T> implements EntrypointContainer<T> {

        private final Entry entry;
        private final T value;

        ContainerImpl(Entry entry, T value) {
            this.entry = entry;
            this.value = value;
        }

        @Override
        public T getEntrypoint() {
            return value;
        }

        @Override
        public ModContainer getProvider() {
            return entry.mod;
        }

        @Override
        public String getDefinition() {
            return entry.def.value;
        }
    }
}
