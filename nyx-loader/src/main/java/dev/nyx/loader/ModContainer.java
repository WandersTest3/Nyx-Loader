package dev.nyx.loader;

import java.io.File;

public final class ModContainer {

    public final String id;
    public final String name;
    public final String version;
    public final String entrypoint;
    public final String description;
    public final String authors;
    public final String license;
    public final String website;
    public final String issues;
    public final String iconPath;
    public final File file;
    public final boolean library;
    public final String kind;

    public ModContainer(String id, String name, String version, String entrypoint, String description, String authors,
            String license, String website, String issues, String iconPath, File file, boolean library, String kind) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.entrypoint = entrypoint;
        this.description = description;
        this.authors = authors;
        this.license = license;
        this.website = website;
        this.issues = issues;
        this.iconPath = iconPath;
        this.file = file;
        this.library = library;
        this.kind = kind;
    }
}
