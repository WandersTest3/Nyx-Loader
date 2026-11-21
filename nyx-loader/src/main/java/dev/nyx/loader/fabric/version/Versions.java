package dev.nyx.loader.fabric.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public final class Versions {

    private Versions() {
    }

    public static Version parse(String text, boolean wildcards) throws VersionParsingException {
        if (text == null || text.isEmpty()) {
            throw new VersionParsingException("Version must be a non-empty string!");
        }
        try {
            return parseSemantic(text, wildcards);
        } catch (VersionParsingException error) {
            return new StringVersion(text);
        }
    }

    public static Version parseLenient(String text) {
        try {
            return parse(text, false);
        } catch (VersionParsingException error) {
            return new StringVersion(text == null ? "" : text);
        }
    }

    public static SemanticVersion parseSemantic(String text, boolean wildcards) throws VersionParsingException {
        if (text == null || text.isEmpty()) {
            throw new VersionParsingException("Version must be a non-empty string!");
        }
        String rest = text;
        String build = null;
        int plus = rest.indexOf('+');
        if (plus >= 0) {
            build = rest.substring(plus + 1);
            rest = rest.substring(0, plus);
            if (!build.matches("[0-9A-Za-z.+-]*")) {
                throw new VersionParsingException("Invalid build metadata: " + build);
            }
        }
        String prerelease = null;
        int dash = rest.indexOf('-');
        if (dash >= 0) {
            prerelease = rest.substring(dash + 1);
            rest = rest.substring(0, dash);
            if (!prerelease.isEmpty() && !prerelease.matches("[0-9A-Za-z.-]+")) {
                throw new VersionParsingException("Invalid prerelease: " + prerelease);
            }
        }
        if (rest.isEmpty() || rest.endsWith(".") || rest.startsWith(".")) {
            throw new VersionParsingException("Invalid version: " + text);
        }
        String[] parts = rest.split("\\.");
        int[] components = new int[parts.length];
        boolean wildcard = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (wildcards && ("x".equals(part) || "X".equals(part) || "*".equals(part))) {
                if (prerelease != null) {
                    throw new VersionParsingException("Prerelease after wildcard: " + text);
                }
                components[i] = SemanticVersion.COMPONENT_WILDCARD;
                wildcard = true;
                continue;
            }
            if (wildcard) {
                throw new VersionParsingException("Component after wildcard: " + text);
            }
            if (!part.matches("\\d+")) {
                throw new VersionParsingException("Non-numeric component: " + text);
            }
            try {
                components[i] = Integer.parseInt(part);
            } catch (NumberFormatException error) {
                throw new VersionParsingException("Component too large: " + text);
            }
        }
        if (components[0] == SemanticVersion.COMPONENT_WILDCARD && components.length == 1 && !"*".equals(parts[0])) {
            throw new VersionParsingException("Invalid version: " + text);
        }
        return new SemanticVersionImpl(components, prerelease, build, text);
    }
}
