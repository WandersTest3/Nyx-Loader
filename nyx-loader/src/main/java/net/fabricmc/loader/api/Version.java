package net.fabricmc.loader.api;

import dev.nyx.loader.fabric.version.Versions;

public interface Version extends Comparable<Version> {

    String getFriendlyString();

    static Version parse(String string) throws VersionParsingException {
        return Versions.parse(string, false);
    }
}
