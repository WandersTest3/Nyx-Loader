package dev.nyx.loader.fabric.version;

import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;

public final class SemanticVersionImpl implements SemanticVersion {

    private final int[] components;
    private final String prerelease;
    private final String build;
    private final String friendly;

    SemanticVersionImpl(int[] components, String prerelease, String build, String friendly) {
        this.components = components;
        this.prerelease = prerelease;
        this.build = build;
        this.friendly = friendly;
    }

    public static SemanticVersion lowestOf(int... components) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                text.append('.');
            }
            text.append(components[i]);
        }
        text.append('-');
        return new SemanticVersionImpl(components.clone(), "", null, text.toString());
    }

    @Override
    public int getVersionComponentCount() {
        return components.length;
    }

    @Override
    public int getVersionComponent(int pos) {
        if (pos < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(pos));
        }
        if (pos >= components.length) {
            return components.length > 0 && components[components.length - 1] == COMPONENT_WILDCARD
                    ? COMPONENT_WILDCARD : 0;
        }
        return components[pos];
    }

    int[] components() {
        return components;
    }

    public int wildcardPosition() {
        for (int i = 0; i < components.length; i++) {
            if (components[i] == COMPONENT_WILDCARD) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Optional<String> getPrereleaseKey() {
        return Optional.ofNullable(prerelease);
    }

    @Override
    public Optional<String> getBuildKey() {
        return Optional.ofNullable(build);
    }

    @Override
    public boolean hasWildcard() {
        return wildcardPosition() >= 0;
    }

    @Override
    public String getFriendlyString() {
        return friendly;
    }

    @Override
    public int compareTo(Version other) {
        if (!(other instanceof SemanticVersion)) {
            return getFriendlyString().compareTo(other.getFriendlyString());
        }
        SemanticVersion o = (SemanticVersion) other;
        int count = Math.max(getVersionComponentCount(), o.getVersionComponentCount());
        for (int i = 0; i < count; i++) {
            int a = getVersionComponent(i);
            int b = o.getVersionComponent(i);
            if (a == COMPONENT_WILDCARD || b == COMPONENT_WILDCARD) {
                continue;
            }
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return comparePrerelease(prerelease, o.getPrereleaseKey().orElse(null));
    }

    private static int comparePrerelease(String a, String b) {
        if (a == null) {
            return b == null ? 0 : 1;
        }
        if (b == null) {
            return -1;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() ? (b.isEmpty() ? 0 : -1) : 1;
        }
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            String x = left[i];
            String y = right[i];
            boolean xNumeric = x.matches("\\d+");
            boolean yNumeric = y.matches("\\d+");
            int result;
            if (xNumeric && yNumeric) {
                result = new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
            } else if (xNumeric) {
                result = -1;
            } else if (yNumeric) {
                result = 1;
            } else {
                result = x.compareTo(y);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SemanticVersionImpl)) {
            return false;
        }
        SemanticVersionImpl o = (SemanticVersionImpl) other;
        return Arrays.equals(components, o.components) && java.util.Objects.equals(prerelease, o.prerelease)
                && java.util.Objects.equals(build, o.build);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(components) * 31 + java.util.Objects.hashCode(prerelease);
    }

    @Override
    public String toString() {
        return friendly;
    }
}
