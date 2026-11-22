package net.minecraft.launchwrapper;

import dev.nyx.loader.launch.GameLoader;
import dev.nyx.loader.launch.NyxLaunch;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class LaunchClassLoader extends URLClassLoader implements GameLoader {

    public static final int BUFFER_SIZE = 1 << 12;
    private static final boolean TRACE_EARLY = Boolean.getBoolean("nyx.traceEarly");

    static {
        registerAsParallelCapable();
    }

    private final List<URL> sources;
    private final ClassLoader parent = getClass().getClassLoader();
    private final List<IClassTransformer> transformers = new ArrayList<>(2);
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private final Set<String> invalidClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> classLoaderExceptions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> transformerExceptions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>(1000);
    private final Set<String> negativeResourceCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Package, Manifest> packageManifests = new ConcurrentHashMap<>();
    private IClassNameTransformer renameTransformer;

    public LaunchClassLoader(URL[] sources) {
        super(sources, ClassLoader.getPlatformClassLoader());
        this.sources = new ArrayList<>(java.util.Arrays.asList(sources));
        addClassLoaderExclusion("java.");
        addClassLoaderExclusion("sun.");
        addClassLoaderExclusion("org.lwjgl.");
        addClassLoaderExclusion("org.apache.logging.");
        addClassLoaderExclusion("net.minecraft.launchwrapper.");
        addClassLoaderExclusion("dev.nyx.loader.");
        addClassLoaderExclusion("dev.nyx.api.");
        addClassLoaderExclusion("net.fabricmc.api.");
        addClassLoaderExclusion("net.fabricmc.loader.");
        addClassLoaderExclusion("org.spongepowered.asm.");
        addClassLoaderExclusion("org.objectweb.asm.");
        addTransformerExclusion("javax.");
        addTransformerExclusion("argo.");
        addTransformerExclusion("com.google.common.");
        addTransformerExclusion("org.bouncycastle.");
        addTransformerExclusion("net.minecraft.launchwrapper.injector.");
        addTransformerExclusion("com.llamalad7.mixinextras.");
    }

    public void registerTransformer(String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName)
                    .getDeclaredConstructor().newInstance();
            transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && renameTransformer == null) {
                renameTransformer = (IClassNameTransformer) transformer;
            }
        } catch (Exception error) {
            LogWrapper.log(org.apache.logging.log4j.Level.ERROR, error, "A critical problem occurred registering the ASM transformer class %s", transformerClassName);
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        if (invalidClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }
        for (String exception : classLoaderExceptions) {
            if (name.startsWith(exception)) {
                return parent.loadClass(name);
            }
        }
        Class<?> cached = cachedClasses.get(name);
        if (cached != null) {
            return cached;
        }
        synchronized (getClassLoadingLock(name)) {
            cached = cachedClasses.get(name);
            if (cached != null) {
                return cached;
            }
            for (String exception : transformerExceptions) {
                if (name.startsWith(exception)) {
                    try {
                        byte[] raw = getClassBytes(name);
                        if (raw == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] fixed = NyxLaunch.fixExcluded(name, raw);
                        if (fixed == raw) {
                            Class<?> clazz = super.findClass(name);
                            cachedClasses.put(name, clazz);
                            return clazz;
                        }
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) {
                            definePackageIfMissing(name.substring(0, dot));
                        }
                        URLConnection connection = findCodeSourceConnectionFor(name.replace('.', '/') + ".class");
                        CodeSource source = connection instanceof JarURLConnection
                                ? new CodeSource(((JarURLConnection) connection).getJarFileURL(), (CodeSigner[]) null) : null;
                        Class<?> clazz = defineClass(name, fixed, 0, fixed.length, source);
                        cachedClasses.put(name, clazz);
                        return clazz;
                    } catch (ClassNotFoundException error) {
                        invalidClasses.add(name);
                        throw error;
                    } catch (IOException error) {
                        invalidClasses.add(name);
                        throw new ClassNotFoundException(name, error);
                    }
                }
            }
            try {
                String transformedName = transformName(name);
                cached = cachedClasses.get(transformedName);
                if (cached != null) {
                    return cached;
                }
                String untransformedName = untransformName(name);
                int lastDot = untransformedName.lastIndexOf('.');
                String packageName = lastDot == -1 ? "" : untransformedName.substring(0, lastDot);
                String fileName = untransformedName.replace('.', '/') + ".class";
                URLConnection connection = findCodeSourceConnectionFor(fileName);
                CodeSigner[] signers = null;
                if (lastDot > -1 && !untransformedName.startsWith("net.minecraft.")) {
                    if (connection instanceof JarURLConnection) {
                        JarURLConnection jarConnection = (JarURLConnection) connection;
                        JarFile jarFile = jarConnection.getJarFile();
                        if (jarFile != null && jarFile.getManifest() != null) {
                            Manifest manifest = jarFile.getManifest();
                            java.util.jar.JarEntry entry = jarFile.getJarEntry(fileName);
                            Package pkg = getDefinedPackage(packageName);
                            getClassBytes(untransformedName);
                            signers = entry != null ? entry.getCodeSigners() : null;
                            if (pkg == null) {
                                try {
                                    pkg = definePackage(packageName, manifest, jarConnection.getJarFileURL());
                                } catch (IllegalArgumentException ignored) {
                                    pkg = getDefinedPackage(packageName);
                                }
                                if (pkg != null) {
                                    packageManifests.put(pkg, manifest);
                                }
                            }
                        }
                    } else {
                        definePackageIfMissing(packageName);
                    }
                } else if (lastDot > -1) {
                    definePackageIfMissing(packageName);
                }
                byte[] transformedClass = runTransformers(untransformedName, transformedName,
                        getClassBytes(untransformedName), true);
                if (transformedClass == null) {
                    throw new ClassNotFoundException(name);
                }
                CodeSource codeSource = connection == null ? null
                        : new CodeSource(connection instanceof JarURLConnection
                                ? ((JarURLConnection) connection).getJarFileURL() : connection.getURL(), signers);
                if (TRACE_EARLY && !NyxLaunch.isMixinReady() && transformedName.startsWith("net.minecraft.")) {
                    new Throwable("[Nyx] early load of " + transformedName).printStackTrace(System.out);
                }
                Class<?> clazz = defineClass(transformedName, transformedClass, 0, transformedClass.length, codeSource);
                cachedClasses.put(transformedName, clazz);
                return clazz;
            } catch (ClassNotFoundException error) {
                invalidClasses.add(name);
                throw error;
            } catch (Throwable error) {
                invalidClasses.add(name);
                if (error instanceof LinkageError || error instanceof RuntimeException) {
                    LogWrapper.log(org.apache.logging.log4j.Level.TRACE, error, "Exception encountered attempting classloading of %s", name);
                }
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    private void definePackageIfMissing(String packageName) {
        if (packageName.isEmpty() || getDefinedPackage(packageName) != null) {
            return;
        }
        try {
            definePackage(packageName, null, null, null, null, null, null, null);
        } catch (IllegalArgumentException ignored) {
            getDefinedPackage(packageName);
        }
    }

    private URLConnection findCodeSourceConnectionFor(String name) {
        URL resource = findResource(name);
        if (resource == null) {
            return null;
        }
        try {
            return resource.openConnection();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private byte[] runTransformers(String name, String transformedName, byte[] basicClass, boolean withMixin) {
        byte[] bytes = NyxLaunch.preTransform(name, transformedName, basicClass);
        boolean forMixinOnly = !withMixin && !transformedName.startsWith("net.minecraft.") && name.indexOf('.') >= 0;
        if (!forMixinOnly) {
            for (IClassTransformer transformer : transformers) {
                bytes = transformer.transform(name, transformedName, bytes);
            }
        }
        return NyxLaunch.postTransform(transformedName, bytes, withMixin);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        sources.add(url);
    }

    public List<URL> getSources() {
        return sources;
    }

    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(transformers);
    }

    public void addClassLoaderExclusion(String toExclude) {
        classLoaderExceptions.add(toExclude);
    }

    public void addTransformerExclusion(String toExclude) {
        transformerExceptions.add(toExclude);
    }

    public byte[] getClassBytes(String name) throws IOException {
        if (negativeResourceCache.contains(name)) {
            return null;
        }
        byte[] cached = resourceCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (name.indexOf('.') == -1) {
            for (String reserved : new String[] {"CON", "PRN", "AUX", "NUL", "COM1", "LPT1"}) {
                if (name.toUpperCase(java.util.Locale.ROOT).startsWith(reserved)) {
                    byte[] data = getClassBytes("_" + name);
                    if (data != null) {
                        resourceCache.put(name, data);
                        return data;
                    }
                }
            }
        }
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            negativeResourceCache.add(name);
            return null;
        }
        try (InputStream input = resource.openStream()) {
            byte[] data = input.readAllBytes();
            resourceCache.put(name, data);
            return data;
        }
    }

    private String untransformName(String name) {
        return renameTransformer != null ? renameTransformer.unmapClassName(name) : name;
    }

    private String transformName(String name) {
        return renameTransformer != null ? renameTransformer.remapClassName(name) : name;
    }

    public void clearNegativeEntries(Set<String> entriesToClear) {
        negativeResourceCache.removeAll(entriesToClear);
    }

    @Override
    public ClassLoader asClassLoader() {
        return this;
    }

    @Override
    public boolean isClassLoadedHere(String name) {
        return cachedClasses.containsKey(name);
    }

    @Override
    public byte[] getClassBytesForMixin(String name, boolean runTransformers) throws IOException {
        String transformedName = transformName(name);
        String untransformedName = untransformName(name);
        byte[] bytes = getClassBytes(untransformedName);
        if (bytes == null) {
            URL url = parent.getResource(name.replace('.', '/') + ".class");
            if (url == null) {
                return null;
            }
            try (InputStream input = url.openStream()) {
                return input.readAllBytes();
            }
        }
        if (!runTransformers) {
            return bytes;
        }
        for (String exception : transformerExceptions) {
            if (name.startsWith(exception)) {
                return bytes;
            }
        }
        return runTransformers(untransformedName, transformedName, bytes, false);
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        return url != null ? url : parent.getResource(name);
    }

    @Override
    public java.util.Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>(Collections.list(findResources(name)));
        for (URL url : Collections.list(parent.getResources(name))) {
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return Collections.enumeration(urls);
    }
}
