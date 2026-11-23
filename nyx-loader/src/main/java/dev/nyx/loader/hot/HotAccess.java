package dev.nyx.loader.hot;

import dev.nyx.loader.fabric.AccessWidener;
import dev.nyx.loader.launch.ClassCache;
import dev.nyx.loader.launch.NyxLaunch;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class HotAccess {

    private HotAccess() {
    }

    public static String apply(File jar, String path, String modId) {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return "access widener " + path + " is missing";
            }
            AccessWidener probe = new AccessWidener();
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                probe.read(reader, modId + "/" + path, NyxLaunch.names());
            }
            for (String target : probe.targets()) {
                String name = target.replace('/', '.');
                if (NyxLaunch.gameLoader().isClassLoadedHere(name)) {
                    return "access widener for " + name + ", which is already loaded";
                }
            }
            for (String target : probe.targets()) {
                ClassCache.bypass(target.replace('/', '.'));
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                NyxLaunch.accessWidener().read(reader, modId + "/" + path, NyxLaunch.names());
            }
            return null;
        } catch (IOException error) {
            return "access widener: " + error.getMessage();
        }
    }
}
