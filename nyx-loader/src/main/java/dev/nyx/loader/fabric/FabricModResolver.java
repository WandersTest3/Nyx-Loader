package dev.nyx.loader.fabric;

import dev.nyx.loader.NyxRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModDependency;

public final class FabricModResolver {

    public static final class Candidate {

        final FabricModMetadata metadata;
        final Path path;
        final Candidate parent;
        final String location;
        final boolean builtin;
        final List<Candidate> nested = new ArrayList<>();
        final List<Path> libraries = new ArrayList<>();

        Candidate(FabricModMetadata metadata, Path path, Candidate parent, String location, boolean builtin) {
            this.metadata = metadata;
            this.path = path;
            this.parent = parent;
            this.location = location;
            this.builtin = builtin;
        }

        String describe() {
            String where = path != null ? path.getFileName().toString() : "built-in";
            if (parent != null) {
                where = parent.describe() + " > " + where;
            }
            return metadata.getId() + " " + metadata.getVersion().getFriendlyString() + " (" + where + ")";
        }
    }

    public static final class Result {

        public final List<FabricModContainer> mods = new ArrayList<>();
        public final List<Path> libraries = new ArrayList<>();
    }

    private final Path cacheDirectory;
    private final EnvType envType;
    private final List<Candidate> builtins = new ArrayList<>();
    private final List<Candidate> roots = new ArrayList<>();
    private final Set<String> forbidden = new HashSet<>();
    private String forbiddenReason = "";

    public void forbid(java.util.Collection<String> ids, String reason) {
        forbidden.addAll(ids);
        forbiddenReason = reason;
    }

    public FabricModResolver(Path cacheDirectory, EnvType envType) {
        this.cacheDirectory = cacheDirectory;
        this.envType = envType;
    }

    public Candidate addBuiltin(FabricModMetadata metadata, Path path) {
        Candidate candidate = new Candidate(metadata, path, null, null, true);
        builtins.add(candidate);
        return candidate;
    }

    public void addBuiltinNested(Candidate parent, String resource, InputStream input) throws IOException {
        byte[] bytes = readAll(input);
        Candidate child = readNested(parent, resource, bytes);
        if (child != null) {
            parent.nested.add(child);
        }
    }

    public boolean addRoot(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return false;
            }
            FabricModMetadata metadata = parse(zip, entry, jar.getFileName().toString());
            Candidate candidate = new Candidate(metadata, jar, null, null, false);
            readNestedJars(candidate, zip);
            roots.add(candidate);
            return true;
        }
    }

    private FabricModMetadata parse(ZipFile zip, ZipEntry entry, String where) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return FabricModMetadata.parse(new String(readAll(input), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid fabric.mod.json in " + where + ": " + error.getMessage(), error);
        }
    }

    private void readNestedJars(Candidate candidate, ZipFile zip) throws IOException {
        for (String location : candidate.metadata.getJars()) {
            ZipEntry entry = zip.getEntry(location);
            if (entry == null) {
                NyxRuntime.log("Nested jar " + location + " missing in " + candidate.describe());
                continue;
            }
            byte[] bytes;
            try (InputStream input = zip.getInputStream(entry)) {
                bytes = readAll(input);
            }
            Candidate child = readNested(candidate, location, bytes);
            if (child != null) {
                candidate.nested.add(child);
            }
        }
    }

    private Candidate readNested(Candidate parent, String location, byte[] bytes) throws IOException {
        String name = location.substring(location.lastIndexOf('/') + 1);
        Path file = cacheDirectory.resolve(sha1(bytes).substring(0, 12) + "-" + name);
        if (!Files.isRegularFile(file) || Files.size(file) != bytes.length) {
            Files.createDirectories(cacheDirectory);
            Path temp = Files.createTempFile(cacheDirectory, "nested", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                parent.libraries.add(file);
                return null;
            }
            FabricModMetadata metadata = parse(zip, entry, parent.describe() + " > " + name);
            Candidate child = new Candidate(metadata, file, parent, location, false);
            readNestedJars(child, zip);
            return child;
        }
    }

    public Result resolve() {
        Map<String, Candidate> fixed = new HashMap<>();
        for (Candidate builtin : builtins) {
            fixed.put(builtin.metadata.getId(), builtin);
            for (String provided : builtin.metadata.getProvides()) {
                fixed.put(provided, builtin);
            }
        }
        Set<Candidate> excluded = new HashSet<>();
        markForbidden(roots, excluded);
        for (Candidate builtin : builtins) {
            markForbidden(builtin.nested, excluded);
        }
        Map<String, Candidate> selected;
        while (true) {
            selected = select(fixed, excluded);
            Candidate failing = null;
            for (Candidate candidate : selected.values()) {
                if (candidate.builtin || candidate.parent == null) {
                    continue;
                }
                if (!problems(candidate, selected).isEmpty()) {
                    failing = candidate;
                    break;
                }
            }
            if (failing == null) {
                break;
            }
            excluded.add(failing);
        }
        List<String> errors = new ArrayList<>();
        Set<Candidate> unique = new HashSet<>(selected.values());
        for (Candidate candidate : unique) {
            if (candidate.builtin) {
                continue;
            }
            for (String problem : problems(candidate, selected)) {
                errors.add(problem);
            }
            for (ModDependency dependency : candidate.metadata.getDependencies()) {
                if (!dependency.getKind().isSoft()) {
                    continue;
                }
                Candidate target = selected.get(dependency.getModId());
                boolean positive = dependency.getKind().isPositive();
                if (positive && (target == null || !dependency.matches(target.metadata.getVersion()))) {
                    NyxRuntime.log("Mod " + candidate.describe() + " " + dependency.getKind().getKey() + " "
                            + dependency.getModId() + " " + dependency.getVersionRequirements());
                } else if (!positive && target != null && dependency.matches(target.metadata.getVersion())) {
                    NyxRuntime.log("Mod " + candidate.describe() + " conflicts with " + target.describe());
                }
            }
        }
        for (Candidate root : roots) {
            if (!unique.contains(root) && !root.metadata.getEnvironment().matches(envType)) {
                NyxRuntime.log("Skipping " + root.describe() + ": not a client mod");
            } else if (!unique.contains(root)) {
                Candidate chosen = selected.get(root.metadata.getId());
                NyxRuntime.log("Skipping " + root.describe() + ": replaced by "
                        + (chosen == null ? "nothing" : chosen.describe()));
            }
        }
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Incompatible mod set:");
            for (String error : errors) {
                message.append("\n - ").append(error);
            }
            throw new IllegalStateException(message.toString());
        }
        Result result = new Result();
        Map<Candidate, FabricModContainer> containers = new LinkedHashMap<>();
        List<Candidate> ordered = new ArrayList<>();
        for (Candidate builtin : builtins) {
            ordered.add(builtin);
        }
        collect(roots, unique, ordered);
        for (Candidate builtin : builtins) {
            collect(builtin.nested, unique, ordered);
        }
        for (Candidate candidate : ordered) {
            if (containers.containsKey(candidate)) {
                continue;
            }
            FabricModContainer container = new FabricModContainer(candidate.metadata, candidate.path,
                    candidate.builtin, candidate.location);
            containers.put(candidate, container);
            if (candidate.parent != null && containers.containsKey(candidate.parent)) {
                container.setParent(containers.get(candidate.parent));
            }
            result.mods.add(container);
            result.libraries.addAll(candidate.libraries);
        }
        return result;
    }

    private void markForbidden(List<Candidate> list, Set<Candidate> excluded) {
        for (Candidate candidate : list) {
            if (forbidden.contains(candidate.metadata.getId())) {
                excluded.add(candidate);
                NyxRuntime.log("Not loading " + candidate.describe() + ": " + forbiddenReason);
            }
            markForbidden(candidate.nested, excluded);
        }
    }

    private void collect(List<Candidate> list, Set<Candidate> unique, List<Candidate> out) {
        for (Candidate candidate : list) {
            if (unique.contains(candidate) && !out.contains(candidate)) {
                out.add(candidate);
            }
            if (unique.contains(candidate)) {
                collect(candidate.nested, unique, out);
            }
        }
    }

    private Map<String, Candidate> select(Map<String, Candidate> fixed, Set<Candidate> excluded) {
        Map<String, Candidate> selected = new LinkedHashMap<>(fixed);
        List<Candidate> available = new ArrayList<>();
        for (Candidate root : roots) {
            if (root.metadata.getEnvironment().matches(envType)) {
                available.add(root);
            }
        }
        for (Candidate builtin : builtins) {
            addNested(builtin, available, excluded);
        }
        Set<Candidate> chosen = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, List<Candidate>> groups = new LinkedHashMap<>();
            for (Candidate candidate : available) {
                if (excluded.contains(candidate) || fixed.containsKey(candidate.metadata.getId())
                        || !parentSelected(candidate, selected)) {
                    continue;
                }
                groups.computeIfAbsent(candidate.metadata.getId(), k -> new ArrayList<>()).add(candidate);
            }
            for (Map.Entry<String, List<Candidate>> group : groups.entrySet()) {
                Candidate best = best(group.getValue(), fixed);
                if (best == null) {
                    continue;
                }
                Candidate previous = selected.get(group.getKey());
                if (previous != best) {
                    selected.put(group.getKey(), best);
                    for (String provided : best.metadata.getProvides()) {
                        if (!fixed.containsKey(provided)) {
                            selected.put(provided, best);
                        }
                    }
                    changed = true;
                }
                if (chosen.add(best)) {
                    int before = available.size();
                    addNested(best, available, excluded);
                    changed |= available.size() != before;
                }
            }
            final Map<String, Candidate> current = selected;
            if (selected.values().removeIf(candidate -> !parentSelected(candidate, current))) {
                changed = true;
            }
        }
        return selected;
    }

    private static boolean parentSelected(Candidate candidate, Map<String, Candidate> selected) {
        Candidate parent = candidate.parent;
        return parent == null || parent.builtin || selected.get(parent.metadata.getId()) == parent;
    }

    private void addNested(Candidate parent, List<Candidate> available, Set<Candidate> excluded) {
        for (Candidate child : parent.nested) {
            if (!available.contains(child) && !excluded.contains(child)
                    && child.metadata.getEnvironment().matches(envType)) {
                available.add(child);
            }
        }
    }

    private Candidate best(List<Candidate> group, Map<String, Candidate> fixed) {
        Candidate best = null;
        for (Candidate candidate : group) {
            if (!fixedSatisfied(candidate, fixed)) {
                continue;
            }
            if (best == null) {
                best = candidate;
                continue;
            }
            int order = candidate.metadata.getVersion().compareTo(best.metadata.getVersion());
            if (order > 0 || order == 0 && candidate.parent == null && best.parent != null) {
                best = candidate;
            }
        }
        if (best == null) {
            for (Candidate candidate : group) {
                if (candidate.parent == null) {
                    return candidate;
                }
            }
        }
        return best;
    }

    private boolean fixedSatisfied(Candidate candidate, Map<String, Candidate> fixed) {
        for (ModDependency dependency : candidate.metadata.getDependencies()) {
            Candidate target = fixed.get(dependency.getModId());
            if (target == null) {
                continue;
            }
            boolean matches = dependency.matches(target.metadata.getVersion());
            if (dependency.getKind() == ModDependency.Kind.DEPENDS && !matches) {
                return false;
            }
            if (dependency.getKind() == ModDependency.Kind.BREAKS && matches) {
                return false;
            }
        }
        return true;
    }

    private List<String> problems(Candidate candidate, Map<String, Candidate> selected) {
        List<String> problems = new ArrayList<>();
        for (ModDependency dependency : candidate.metadata.getDependencies()) {
            Candidate target = selected.get(dependency.getModId());
            if (dependency.getKind() == ModDependency.Kind.DEPENDS) {
                if (target == null) {
                    problems.add(candidate.describe() + " requires " + dependency.getModId() + " "
                            + describe(dependency.getVersionRequirements()) + ", which is missing");
                } else if (!dependency.matches(target.metadata.getVersion())) {
                    problems.add(candidate.describe() + " requires " + dependency.getModId() + " "
                            + describe(dependency.getVersionRequirements()) + ", but found "
                            + target.metadata.getVersion().getFriendlyString());
                }
            } else if (dependency.getKind() == ModDependency.Kind.BREAKS && target != null
                    && dependency.matches(target.metadata.getVersion())) {
                problems.add(candidate.describe() + " is incompatible with " + target.describe());
            }
        }
        return problems;
    }

    private static String describe(Collection<?> predicates) {
        List<String> parts = new ArrayList<>();
        for (Object predicate : predicates) {
            parts.add(String.valueOf(predicate));
        }
        return String.join(" or ", parts);
    }

    static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static String sha1(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
