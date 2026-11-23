package dev.nyx.loader.modrinth;

import dev.nyx.api.Nyx;
import dev.nyx.loader.fabric.Json;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Modrinth {

    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "WandersTest3/Nyx-Loader/" + Nyx.VERSION
            + " (https://github.com/WandersTest3/Nyx-Loader)";
    private static final int TIMEOUT = 15000;
    private static final long MAX_DOWNLOAD = 128L * 1024L * 1024L;

    private Modrinth() {
    }

    public interface Progress {
        void update(long done, long total);
    }

    public static final class Project {
        public final String id;
        public final String slug;
        public final String title;
        public final String author;
        public final String description;
        public final long downloads;
        public final String iconUrl;
        public final List<String> loaders;
        public final String site;

        public Project(String id, String slug, String title, String author, String description, long downloads,
                String iconUrl, List<String> loaders, String site) {
            this.site = site;
            this.id = id;
            this.slug = slug;
            this.title = title;
            this.author = author;
            this.description = description;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.loaders = loaders;
        }
    }

    public static final class FileRef {
        public final String url;
        public final String filename;
        public final String sha1;
        public final long size;

        public FileRef(String url, String filename, String sha1, long size) {
            this.url = url;
            this.filename = filename;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    public static final class Version {
        public final String id;
        public final String projectId;
        public final String name;
        public final String number;
        public final List<String> loaders;
        public final FileRef file;
        public final List<String[]> requiredDependencies;

        public Version(String id, String projectId, String name, String number, List<String> loaders, FileRef file,
                List<String[]> requiredDependencies) {
            this.id = id;
            this.projectId = projectId;
            this.name = name;
            this.number = number;
            this.loaders = loaders;
            this.file = file;
            this.requiredDependencies = requiredDependencies;
        }
    }

    public static final class Page {
        public final List<Project> projects;
        public final int total;

        public Page(List<Project> projects, int total) {
            this.projects = projects;
            this.total = total;
        }
    }

    public static Page search(String query, List<String> loaders, int offset, int limit, boolean packs)
            throws IOException {
        StringBuilder facets = new StringBuilder("[[\"versions:1.7.10\"],[\"project_type:"
                + (packs ? "resourcepack" : "mod") + "\"]");
        if (!packs) {
            facets.append(",[");
            for (int i = 0; i < loaders.size(); i++) {
                facets.append(i == 0 ? "" : ",").append("\"categories:").append(loaders.get(i)).append('"');
            }
            facets.append(']');
        }
        facets.append(']');
        String trimmed = query == null ? "" : query.trim();
        String url = API + "/search?limit=" + limit + "&offset=" + offset + "&index="
                + (trimmed.isEmpty() ? "downloads" : "relevance") + "&query=" + encode(trimmed) + "&facets="
                + encode(facets.toString());
        Map<?, ?> root = (Map<?, ?>) Json.parse(get(url));
        List<Project> projects = new ArrayList<Project>();
        for (Object hit : list(root.get("hits"))) {
            Map<?, ?> map = (Map<?, ?>) hit;
            List<String> found = new ArrayList<String>();
            for (Object category : list(map.get("categories"))) {
                String value = String.valueOf(category);
                if (value.equals("forge") || value.equals("fabric") || value.equals("legacy-fabric")) {
                    found.add(value);
                }
            }
            projects.add(new Project(string(map.get("project_id")), string(map.get("slug")),
                    string(map.get("title")), string(map.get("author")), string(map.get("description")),
                    number(map.get("downloads")), string(map.get("icon_url")), found, "modrinth"));
        }
        return new Page(projects, (int) number(root.get("total_hits")));
    }

    public static Version bestVersion(String projectId, List<String> loaders) throws IOException {
        return bestVersion(projectId, loaders, false);
    }

    public static Version bestVersion(String projectId, List<String> loaders, boolean packs) throws IOException {
        StringBuilder loaderList = new StringBuilder("[");
        for (int i = 0; i < loaders.size(); i++) {
            loaderList.append(i == 0 ? "" : ",").append('"').append(loaders.get(i)).append('"');
        }
        loaderList.append(']');
        String url = API + "/project/" + encode(projectId) + "/version?game_versions=" + encode("[\"1.7.10\"]")
                + (packs ? "" : "&loaders=" + encode(loaderList.toString()));
        List<?> versions = list(Json.parse(get(url)));
        Version fallback = null;
        for (Object raw : versions) {
            Map<?, ?> map = (Map<?, ?>) raw;
            Version version = version(map, packs);
            if (version == null) {
                continue;
            }
            if ("release".equals(string(map.get("version_type")))) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }

    private static Version version(Map<?, ?> map, boolean packs) {
        List<String> versionLoaders = new ArrayList<String>();
        for (Object loader : list(map.get("loaders"))) {
            versionLoaders.add(String.valueOf(loader));
        }
        FileRef chosen = null;
        for (Object raw : list(map.get("files"))) {
            Map<?, ?> file = (Map<?, ?>) raw;
            String name = string(file.get("filename"));
            if (name == null || !acceptable(name, packs)) {
                continue;
            }
            Map<?, ?> hashes = file.get("hashes") instanceof Map ? (Map<?, ?>) file.get("hashes")
                    : Collections.emptyMap();
            FileRef ref = new FileRef(string(file.get("url")), name, string(hashes.get("sha1")),
                    number(file.get("size")));
            if (chosen == null || Boolean.TRUE.equals(file.get("primary"))) {
                chosen = ref;
            }
        }
        if (chosen == null || chosen.url == null) {
            return null;
        }
        List<String[]> dependencies = new ArrayList<String[]>();
        for (Object raw : list(map.get("dependencies"))) {
            Map<?, ?> dependency = (Map<?, ?>) raw;
            if ("required".equals(string(dependency.get("dependency_type")))) {
                dependencies.add(new String[] {string(dependency.get("project_id")),
                        string(dependency.get("version_id"))});
            }
        }
        return new Version(string(map.get("id")), string(map.get("project_id")), string(map.get("name")),
                string(map.get("version_number")), versionLoaders, chosen, dependencies);
    }

    public static Version versionById(String versionId) throws IOException {
        Object raw = Json.parse(get(API + "/version/" + encode(versionId)));
        return raw instanceof Map ? version((Map<?, ?>) raw, false) : null;
    }

    public static File download(FileRef ref, File directory, Progress progress) throws IOException {
        String name = new File(ref.filename).getName();
        if (name.isEmpty() || name.startsWith(".") || !acceptable(name, true)) {
            throw new IOException("Invalid file name: " + ref.filename);
        }
        if (!ref.url.startsWith("https://")) {
            throw new IOException("Refusing a non-HTTPS download");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        File target = new File(directory, name);
        File temp = new File(directory, name + ".nyxdownload");
        HttpURLConnection connection = open(ref.url);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (Exception error) {
            throw new IOException(error);
        }
        long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : ref.size;
        long done = 0L;
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[1 << 15];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                if (done > MAX_DOWNLOAD) {
                    throw new IOException("Download too large");
                }
                if (progress != null) {
                    progress.update(done, total);
                }
            }
        } catch (IOException error) {
            temp.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
        if (ref.sha1 != null && !ref.sha1.equalsIgnoreCase(hex(digest.digest()))) {
            temp.delete();
            throw new IOException("SHA-1 mismatch for " + name);
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static boolean acceptable(String name, boolean packs) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jar") || packs && lower.endsWith(".zip");
    }

    public static final class Details {
        public final String body;
        public final List<String[]> gallery;

        public Details(String body, List<String[]> gallery) {
            this.body = body;
            this.gallery = gallery;
        }
    }

    public static Details details(String projectId) throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(get(API + "/project/" + encode(projectId)));
        List<String[]> gallery = new ArrayList<String[]>();
        for (Object raw : list(root.get("gallery"))) {
            Map<?, ?> item = (Map<?, ?>) raw;
            String url = string(item.get("url"));
            if (url != null) {
                gallery.add(new String[] {url, string(item.get("title"))});
            }
        }
        return new Details(string(root.get("body")), gallery);
    }

    public static String sha1(File file) throws IOException {
        try (InputStream input = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1 << 15];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    public static Version latestFor(String sha1, List<String> loaders) throws IOException {
        StringBuilder body = new StringBuilder("{\"loaders\":[");
        for (int i = 0; i < loaders.size(); i++) {
            body.append(i == 0 ? "" : ",").append('"').append(loaders.get(i)).append('"');
        }
        body.append("],\"game_versions\":[\"1.7.10\"]}");
        HttpURLConnection connection = (HttpURLConnection) new URL(API + "/version_file/" + sha1
                + "/update?algorithm=sha1").openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        if (code == 404) {
            connection.disconnect();
            return null;
        }
        if (code != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " from api.modrinth.com");
        }
        try (InputStream input = connection.getInputStream()) {
            Object raw = Json.parse(new String(input.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8));
            return raw instanceof Map ? version((Map<?, ?>) raw, false) : null;
        } finally {
            connection.disconnect();
        }
    }

    public static byte[] fetchBytes(String url, int maxBytes) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream()) {
            byte[] data = input.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IOException("Response too large");
            }
            return data;
        } finally {
            connection.disconnect();
        }
    }

    static String get(String url) throws IOException {
        return new String(fetchBytes(url, 8 * 1024 * 1024), StandardCharsets.UTF_8);
    }

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " from " + new URL(url).getHost());
        }
        return connection;
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException error) {
            throw new IllegalStateException(error);
        }
    }

    static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            text.append(String.format("%02x", b));
        }
        return text.toString();
    }
}
