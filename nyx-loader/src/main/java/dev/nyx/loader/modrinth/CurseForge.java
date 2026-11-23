package dev.nyx.loader.modrinth;

import dev.nyx.api.Nyx;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.Json;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CurseForge {

    private static final String API = "https://api.curseforge.com/v1";
    private static final String USER_AGENT = "WandersTest3/Nyx-Loader/" + Nyx.VERSION
            + " (https://github.com/WandersTest3/Nyx-Loader)";
    private static final int GAME = 432;
    private static final int MODS = 6;
    private static final int PACKS = 12;
    private static final int TIMEOUT = 15000;

    private static volatile String key;

    private CurseForge() {
    }

    public static File keyFile() {
        return new File(new File(NyxRuntime.getGameDirectory(), "nyx"), "curseforge.key");
    }

    public static String key() {
        String current = key;
        if (current != null) {
            return current.isEmpty() ? null : current;
        }
        String value = System.getProperty("nyx.curseforge.key");
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv("NYX_CURSEFORGE_KEY");
        }
        File file = keyFile();
        if ((value == null || value.trim().isEmpty()) && file.isFile()) {
            try {
                value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException error) {
                value = null;
            }
        }
        key = value == null ? "" : value.trim();
        return key.isEmpty() ? null : key;
    }

    public static boolean hasKey() {
        return key() != null;
    }

    public static void setKey(String value) throws IOException {
        String clean = value == null ? "" : value.trim();
        File file = keyFile();
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), clean.getBytes(StandardCharsets.UTF_8));
        key = clean;
    }

    private static String call(String path, String body) throws IOException {
        String apiKey = key();
        if (apiKey == null) {
            throw new IOException("CurseForge API key missing");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("x-api-key", apiKey);
        if (body != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " from api.curseforge.com");
        }
        try (InputStream input = connection.getInputStream()) {
            return new String(input.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static String loaderFilter(List<String> loaders) {
        boolean forge = loaders.contains("forge");
        boolean fabric = loaders.contains("fabric") || loaders.contains("legacy-fabric");
        if (forge && !fabric) {
            return "&modLoaderType=1";
        }
        if (fabric && !forge) {
            return "&modLoaderType=4";
        }
        return "";
    }

    public static Modrinth.Page search(String query, List<String> loaders, int offset, int limit, boolean packs)
            throws IOException {
        String text = query == null ? "" : query.trim();
        String url = "/mods/search?gameId=" + GAME + "&classId=" + (packs ? PACKS : MODS)
                + "&gameVersion=1.7.10&sortField=" + (text.isEmpty() ? 2 : 1) + "&sortOrder=desc&index=" + offset
                + "&pageSize=" + limit + (packs ? "" : loaderFilter(loaders))
                + (text.isEmpty() ? "" : "&searchFilter=" + Modrinth.encode(text));
        Map<?, ?> root = (Map<?, ?>) Json.parse(call(url, null));
        List<Modrinth.Project> projects = new ArrayList<Modrinth.Project>();
        for (Object raw : Modrinth.list(root.get("data"))) {
            Map<?, ?> map = (Map<?, ?>) raw;
            String author = null;
            List<?> authors = Modrinth.list(map.get("authors"));
            if (!authors.isEmpty() && authors.get(0) instanceof Map) {
                author = Modrinth.string(((Map<?, ?>) authors.get(0)).get("name"));
            }
            String icon = null;
            if (map.get("logo") instanceof Map) {
                Map<?, ?> logo = (Map<?, ?>) map.get("logo");
                icon = Modrinth.string(logo.get("thumbnailUrl") != null ? logo.get("thumbnailUrl") : logo.get("url"));
            }
            List<String> found = new ArrayList<String>();
            for (Object index : Modrinth.list(map.get("latestFilesIndexes"))) {
                Map<?, ?> entry = (Map<?, ?>) index;
                if (!"1.7.10".equals(Modrinth.string(entry.get("gameVersion")))) {
                    continue;
                }
                long type = Modrinth.number(entry.get("modLoader"));
                String name = type == 1 ? "forge" : type == 4 ? "fabric" : null;
                if (name != null && !found.contains(name)) {
                    found.add(name);
                }
            }
            projects.add(new Modrinth.Project(Modrinth.string(map.get("id")), Modrinth.string(map.get("slug")),
                    Modrinth.string(map.get("name")), author, Modrinth.string(map.get("summary")),
                    Modrinth.number(map.get("downloadCount")), icon, found, "curseforge"));
        }
        int total = 0;
        if (root.get("pagination") instanceof Map) {
            total = (int) Modrinth.number(((Map<?, ?>) root.get("pagination")).get("totalCount"));
        }
        return new Modrinth.Page(projects, total);
    }

    private static Modrinth.Version version(Map<?, ?> map, String projectId) {
        String name = Modrinth.string(map.get("fileName"));
        if (name == null) {
            return null;
        }
        long id = Modrinth.number(map.get("id"));
        String url = Modrinth.string(map.get("downloadUrl"));
        if (url == null || url.isEmpty()) {
            url = "https://edge.forgecdn.net/files/" + id / 1000L + "/" + id % 1000L + "/" + Modrinth.encode(name);
        }
        String sha1 = null;
        for (Object raw : Modrinth.list(map.get("hashes"))) {
            Map<?, ?> hash = (Map<?, ?>) raw;
            if (Modrinth.number(hash.get("algo")) == 1L) {
                sha1 = Modrinth.string(hash.get("value"));
            }
        }
        List<String[]> dependencies = new ArrayList<String[]>();
        for (Object raw : Modrinth.list(map.get("dependencies"))) {
            Map<?, ?> dependency = (Map<?, ?>) raw;
            if (Modrinth.number(dependency.get("relationType")) == 3L) {
                dependencies.add(new String[] {Modrinth.string(dependency.get("modId")), null});
            }
        }
        return new Modrinth.Version(String.valueOf(id), projectId, name, name, Collections.<String>emptyList(),
                new Modrinth.FileRef(url, name, sha1, Modrinth.number(map.get("fileLength"))), dependencies);
    }

    public static Modrinth.Version bestVersion(String modId, List<String> loaders, boolean packs) throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(call("/mods/" + Modrinth.encode(modId)
                + "/files?gameVersion=1.7.10&pageSize=50" + (packs ? "" : loaderFilter(loaders)), null));
        Map<?, ?> best = null;
        Map<?, ?> fallback = null;
        for (Object raw : Modrinth.list(root.get("data"))) {
            Map<?, ?> file = (Map<?, ?>) raw;
            String name = Modrinth.string(file.get("fileName"));
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".jar") && !(packs && lower.endsWith(".zip"))) {
                continue;
            }
            if (fallback == null || newer(file, fallback)) {
                fallback = file;
            }
            if (Modrinth.number(file.get("releaseType")) == 1L && (best == null || newer(file, best))) {
                best = file;
            }
        }
        Map<?, ?> chosen = best != null ? best : fallback;
        return chosen == null ? null : version(chosen, modId);
    }

    private static boolean newer(Map<?, ?> a, Map<?, ?> b) {
        String left = Modrinth.string(a.get("fileDate"));
        String right = Modrinth.string(b.get("fileDate"));
        return left != null && right != null ? left.compareTo(right) > 0
                : Modrinth.number(a.get("id")) > Modrinth.number(b.get("id"));
    }

    public static Modrinth.Details details(String modId) throws IOException {
        Map<?, ?> mod = (Map<?, ?>) Json.parse(call("/mods/" + Modrinth.encode(modId), null));
        List<String[]> gallery = new ArrayList<String[]>();
        if (mod.get("data") instanceof Map) {
            for (Object raw : Modrinth.list(((Map<?, ?>) mod.get("data")).get("screenshots"))) {
                Map<?, ?> item = (Map<?, ?>) raw;
                String url = Modrinth.string(item.get("url"));
                if (url != null) {
                    gallery.add(new String[] {url, Modrinth.string(item.get("title"))});
                }
            }
        }
        String body = null;
        try {
            Map<?, ?> description = (Map<?, ?>) Json.parse(call("/mods/" + Modrinth.encode(modId) + "/description", null));
            body = Modrinth.string(description.get("data"));
        } catch (IOException error) {
            body = null;
        }
        return new Modrinth.Details(body, gallery);
    }

    public static long fingerprint(File file) throws IOException {
        byte[] raw = Files.readAllBytes(file.toPath());
        byte[] data = new byte[raw.length];
        int length = 0;
        for (byte b : raw) {
            if (b != 9 && b != 10 && b != 13 && b != 32) {
                data[length++] = b;
            }
        }
        final int m = 0x5bd1e995;
        int h = 1 ^ length;
        int i = 0;
        int rest = length;
        while (rest >= 4) {
            int k = data[i] & 0xFF | (data[i + 1] & 0xFF) << 8 | (data[i + 2] & 0xFF) << 16 | (data[i + 3] & 0xFF) << 24;
            k *= m;
            k ^= k >>> 24;
            k *= m;
            h *= m;
            h ^= k;
            i += 4;
            rest -= 4;
        }
        if (rest == 3) {
            h ^= (data[i + 2] & 0xFF) << 16;
        }
        if (rest >= 2) {
            h ^= (data[i + 1] & 0xFF) << 8;
        }
        if (rest >= 1) {
            h ^= data[i] & 0xFF;
            h *= m;
        }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h & 0xFFFFFFFFL;
    }

    public static String[] match(File jar) throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(call("/fingerprints/" + GAME,
                "{\"fingerprints\":[" + fingerprint(jar) + "]}"));
        if (!(root.get("data") instanceof Map)) {
            return null;
        }
        List<?> matches = Modrinth.list(((Map<?, ?>) root.get("data")).get("exactMatches"));
        if (matches.isEmpty() || !(matches.get(0) instanceof Map)) {
            return null;
        }
        Object file = ((Map<?, ?>) matches.get(0)).get("file");
        if (!(file instanceof Map)) {
            return null;
        }
        Map<?, ?> info = (Map<?, ?>) file;
        return new String[] {Modrinth.string(info.get("modId")), Modrinth.string(info.get("id"))};
    }
}
