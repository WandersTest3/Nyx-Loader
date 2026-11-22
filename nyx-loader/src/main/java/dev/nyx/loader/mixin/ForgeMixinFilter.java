package dev.nyx.loader.mixin;

import dev.nyx.loader.NyxRuntime;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class ForgeMixinFilter {

    private static final String[] BUILTIN = {
        "legacy-fabric-networking-api-v1.mixins.json:MinecraftServerMixin"
    };

    private static List<String> entries;

    private ForgeMixinFilter() {
    }

    private static synchronized List<String> entries() {
        if (entries != null) {
            return entries;
        }
        entries = new ArrayList<>();
        for (String entry : BUILTIN) {
            entries.add(entry);
        }
        File file = new File(NyxRuntime.getGameDirectory(), "config" + File.separator + "nyx" + File.separator
                + "forge-disabled-mixins.txt");
        try {
            if (!file.isFile()) {
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), ("# Fabric mixins disabled when Forge is installed, one per line: config.json:MixinClass\n"
                        + String.join("\n", BUILTIN) + "\n").getBytes(StandardCharsets.UTF_8));
            } else {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && !entries.contains(line)) {
                        entries.add(line);
                    }
                }
            }
        } catch (IOException error) {
            NyxRuntime.log("Could not read " + file + ": " + error);
        }
        return entries;
    }

    static String filter(String configName, String text) {
        for (String entry : entries()) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0 || !entry.substring(0, colon).equals(configName)) {
                continue;
            }
            String mixin = entry.substring(colon + 1);
            if (mixin.equals("*")) {
                String updated = text.replaceAll("(\"(?:mixins|client|server)\"\\s*:\\s*)\\[[^\\]]*\\]", "$1[]");
                if (!updated.equals(text)) {
                    NyxRuntime.log("Disabled Fabric mixin config " + configName + " because it conflicts with Forge");
                    text = updated;
                }
                continue;
            }
            String quoted = java.util.regex.Pattern.quote("\"" + mixin + "\"");
            String updated = text.replaceAll(",\\s*" + quoted, "").replaceAll(quoted + "\\s*,?", "");
            if (!updated.equals(text)) {
                NyxRuntime.log("Disabled Fabric mixin " + entry + " because it conflicts with Forge");
                text = updated;
            }
        }
        return text;
    }
}
