package dev.nyx.loader.fabric;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

public final class FabricModContainer implements ModContainer {

    private final FabricModMetadata metadata;
    private final Path path;
    private final boolean builtin;
    private final String nestedLocation;
    private FabricModContainer parent;
    private final List<ModContainer> children = new ArrayList<>();
    private List<Path> roots;

    public FabricModContainer(FabricModMetadata metadata, Path path, boolean builtin, String nestedLocation) {
        this.metadata = metadata;
        this.path = path;
        this.builtin = builtin;
        this.nestedLocation = nestedLocation;
    }

    void setParent(FabricModContainer parent) {
        this.parent = parent;
        parent.children.add(this);
    }

    public FabricModMetadata getFabricMetadata() {
        return metadata;
    }

    public Path getJarPath() {
        return path;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isNested() {
        return parent != null;
    }

    public FabricModContainer getParent() {
        return parent;
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public synchronized List<Path> getRootPaths() {
        if (roots == null) {
            if (path == null) {
                roots = Collections.emptyList();
            } else if (java.nio.file.Files.isDirectory(path)) {
                roots = Collections.singletonList(path);
            } else {
                try {
                    FileSystem fs;
                    try {
                        fs = FileSystems.newFileSystem(path, (ClassLoader) null);
                    } catch (java.nio.file.FileSystemAlreadyExistsException error) {
                        fs = FileSystems.getFileSystem(java.net.URI.create("jar:" + path.toUri()));
                    }
                    roots = Collections.singletonList(fs.getRootDirectories().iterator().next());
                } catch (IOException error) {
                    throw new RuntimeException("Could not open mod " + metadata.getId() + " at " + path, error);
                }
            }
        }
        return roots;
    }

    @Override
    public ModOrigin getOrigin() {
        return new ModOrigin() {
            @Override
            public Kind getKind() {
                if (parent != null) {
                    return Kind.NESTED;
                }
                return path == null ? Kind.UNKNOWN : Kind.PATH;
            }

            @Override
            public List<Path> getPaths() {
                if (getKind() != Kind.PATH) {
                    throw new UnsupportedOperationException("kind " + getKind() + " doesn't have paths");
                }
                return Collections.singletonList(path);
            }

            @Override
            public String getParentModId() {
                if (parent == null) {
                    throw new UnsupportedOperationException("not nested");
                }
                return parent.getMetadata().getId();
            }

            @Override
            public String getParentSubLocation() {
                if (parent == null) {
                    throw new UnsupportedOperationException("not nested");
                }
                return nestedLocation;
            }

            @Override
            public String toString() {
                return parent != null ? parent.getMetadata().getId() + "/" + nestedLocation : String.valueOf(path);
            }
        };
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Collection<ModContainer> getContainedMods() {
        return Collections.unmodifiableList(children);
    }

    @Override
    @Deprecated
    public Path getRootPath() {
        List<Path> paths = getRootPaths();
        if (paths.isEmpty()) {
            throw new UnsupportedOperationException("Mod " + metadata.getId() + " has no root path");
        }
        return paths.get(0);
    }

    @Override
    public String toString() {
        return metadata.getId() + " " + metadata.getVersion().getFriendlyString();
    }
}
