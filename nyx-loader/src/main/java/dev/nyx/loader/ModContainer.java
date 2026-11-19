package dev.nyx.loader;

import java.io.File;

final class ModContainer {

    final String id;
    final String name;
    final String version;
    final String entrypoint;
    final File file;

    ModContainer(String id, String name, String version, String entrypoint, File file) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.entrypoint = entrypoint;
        this.file = file;
    }
}
