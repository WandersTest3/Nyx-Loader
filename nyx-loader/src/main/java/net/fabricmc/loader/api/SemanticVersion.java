package net.fabricmc.loader.api;

import dev.nyx.loader.fabric.version.Versions;
import java.util.Optional;

public interface SemanticVersion extends Version {

    int COMPONENT_WILDCARD = Integer.MIN_VALUE;

    int getVersionComponentCount();

    int getVersionComponent(int pos);

    Optional<String> getPrereleaseKey();

    Optional<String> getBuildKey();

    boolean hasWildcard();

    @Deprecated
    default int compareTo(SemanticVersion o) {
        return compareTo((Version) o);
    }

    static SemanticVersion parse(String s) throws VersionParsingException {
        return Versions.parseSemantic(s, false);
    }
}
