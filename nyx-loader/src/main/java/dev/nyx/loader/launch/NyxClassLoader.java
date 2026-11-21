package dev.nyx.loader.launch;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NyxClassLoader extends URLClassLoader {

    private static final String[] PARENT_ONLY = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.", "org.xml.", "org.w3c.",
        "dev.nyx.loader.", "dev.nyx.api.", "net.fabricmc.api.", "net.fabricmc.loader.",
        "org.spongepowered.asm.", "org.objectweb.asm.", "org.lwjgl.", "org.apache.logging.log4j."
    };

    private static final String[] GAME_SIDE_FROM_PARENT = {
        "dev.nyx.mc171."
    };

    static {
        registerAsParallelCapable();
    }

    private final ClassLoader parent;
    private final Map<String, CodeSource> codeSources = new ConcurrentHashMap<>();

    public NyxClassLoader(ClassLoader parent) {
        super("nyx", new URL[0], parent);
        this.parent = parent;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    public boolean isClassLoadedHere(String name) {
        return findLoadedClass(name) != null;
    }

    static boolean isParentOnly(String name) {
        for (String prefix : PARENT_ONLY) {
            if (name.startsWith(prefix)) {
                for (String gameSide : GAME_SIDE_FROM_PARENT) {
                    if (name.startsWith(gameSide)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isGameSideFromParent(String name) {
        for (String prefix : GAME_SIDE_FROM_PARENT) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    URL findOwnClass(String name) {
        String path = name.replace('.', '/') + ".class";
        URL url = findResource(path);
        if (url == null && isGameSideFromParent(name)) {
            url = parent.getResource(path);
        }
        return url;
    }

    public byte[] getRawClassBytes(String name) throws IOException {
        String path = name.replace('.', '/') + ".class";
        URL url = findOwnClass(name);
        if (url == null) {
            url = parent.getResource(path);
        }
        if (url == null) {
            return null;
        }
        try (InputStream input = url.openStream()) {
            return input.readAllBytes();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (!isParentOnly(name)) {
                    URL url = findOwnClass(name);
                    if (url != null) {
                        type = define(name, url);
                    }
                }
                if (type == null) {
                    type = parent.loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    private Class<?> define(String name, URL url) throws ClassNotFoundException {
        byte[] bytes;
        try (InputStream input = url.openStream()) {
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw new ClassNotFoundException(name, error);
        }
        byte[] transformed = NyxLaunch.transformClass(name, bytes);
        if (transformed == null) {
            throw new ClassNotFoundException(name + " (rejected by transformer)");
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String pkg = name.substring(0, dot);
            if (getDefinedPackage(pkg) == null) {
                try {
                    definePackage(pkg, null, null, null, null, null, null, null);
                } catch (IllegalArgumentException ignored) {
                    getDefinedPackage(pkg);
                }
            }
        }
        return defineClass(name, transformed, 0, transformed.length, codeSource(url));
    }

    private CodeSource codeSource(URL url) {
        String key;
        URL location = url;
        try {
            if ("jar".equals(url.getProtocol())) {
                location = ((JarURLConnection) url.openConnection()).getJarFileURL();
            }
        } catch (IOException ignored) {
            location = url;
        }
        key = location.toString();
        final URL finalLocation = location;
        return codeSources.computeIfAbsent(key, k -> new CodeSource(finalLocation, (Certificate[]) null));
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        return url != null ? url : parent.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>(Collections.list(findResources(name)));
        for (URL url : Collections.list(parent.getResources(name))) {
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return Collections.enumeration(urls);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url == null ? null : url.openStream();
        } catch (IOException error) {
            return null;
        }
    }
}
