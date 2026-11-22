package dev.nyx.loader.launch;

import java.io.IOException;
import java.net.URL;

public interface GameLoader {

    ClassLoader asClassLoader();

    void addURL(URL url);

    boolean isClassLoadedHere(String name);

    byte[] getClassBytesForMixin(String name, boolean runTransformers) throws IOException;
}
