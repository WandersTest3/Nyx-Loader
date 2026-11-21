package dev.nyx.loader.fabric.version;

import net.fabricmc.loader.api.Version;

public final class StringVersion implements Version {

    private final String version;

    public StringVersion(String version) {
        this.version = version;
    }

    @Override
    public String getFriendlyString() {
        return version;
    }

    @Override
    public int compareTo(Version other) {
        return version.compareTo(other.getFriendlyString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StringVersion && version.equals(((StringVersion) other).version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }
}
