package dev.nyx.loader.fabric;

import dev.nyx.loader.fabric.version.Predicates;
import dev.nyx.loader.fabric.version.Versions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

public final class FabricModMetadata implements ModMetadata {

    public static final class EntrypointDef {

        public final String adapter;
        public final String value;

        EntrypointDef(String adapter, String value) {
            this.adapter = adapter;
            this.value = value;
        }
    }

    public static final class MixinDef {

        public final String config;
        public final ModEnvironment environment;

        MixinDef(String config, ModEnvironment environment) {
            this.config = config;
            this.environment = environment;
        }
    }

    String type = "fabric";
    String id;
    Version version;
    final List<String> provides = new ArrayList<>();
    ModEnvironment environment = ModEnvironment.UNIVERSAL;
    final Map<String, List<EntrypointDef>> entrypoints = new LinkedHashMap<>();
    final List<String> jars = new ArrayList<>();
    final List<MixinDef> mixins = new ArrayList<>();
    String accessWidener;
    final List<ModDependency> dependencies = new ArrayList<>();
    String name;
    String description = "";
    final List<Person> authors = new ArrayList<>();
    final List<Person> contributors = new ArrayList<>();
    ContactInformation contact = ContactInformation.EMPTY;
    final List<String> license = new ArrayList<>();
    final TreeMap<Integer, String> icons = new TreeMap<>();
    final Map<String, String> languageAdapters = new LinkedHashMap<>();
    final Map<String, CustomValue> custom = new LinkedHashMap<>();

    public static FabricModMetadata builtin(String id, String name, String version, String description,
            String author, String homepage, String issues, String license, String icon, String... provides) {
        FabricModMetadata metadata = new FabricModMetadata();
        metadata.type = "builtin";
        metadata.id = id;
        metadata.name = name;
        metadata.version = Versions.parseLenient(version);
        metadata.description = description;
        if (author != null) {
            metadata.authors.add(new PersonImpl(author, ContactInformation.EMPTY));
        }
        Map<String, String> contact = new LinkedHashMap<>();
        if (homepage != null) {
            contact.put("homepage", homepage);
        }
        if (issues != null) {
            contact.put("issues", issues);
        }
        metadata.contact = new ContactImpl(contact);
        if (license != null) {
            metadata.license.add(license);
        }
        if (icon != null) {
            metadata.icons.put(0, icon);
        }
        Collections.addAll(metadata.provides, provides);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public static FabricModMetadata parse(String json) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("fabric.mod.json must be an object");
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Object schema = map.get("schemaVersion");
        long schemaVersion = schema instanceof Number ? ((Number) schema).longValue() : 0L;
        if (schemaVersion != 1L) {
            throw new IllegalArgumentException("Unsupported fabric.mod.json schemaVersion " + schemaVersion
                    + " (only 1 is supported)");
        }
        FabricModMetadata metadata = new FabricModMetadata();
        metadata.id = requireString(map, "id");
        if (!metadata.id.matches("[a-z][a-z0-9-_]{1,63}")) {
            throw new IllegalArgumentException("Invalid mod id " + metadata.id);
        }
        String versionText = requireString(map, "version");
        metadata.version = Versions.parseLenient(versionText);
        for (Object item : list(map.get("provides"))) {
            metadata.provides.add(String.valueOf(item));
        }
        metadata.environment = environment(map.get("environment"));
        Object entrypoints = map.get("entrypoints");
        if (entrypoints instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) entrypoints).entrySet()) {
                List<EntrypointDef> defs = new ArrayList<>();
                for (Object item : list(entry.getValue())) {
                    if (item instanceof String) {
                        defs.add(new EntrypointDef("default", (String) item));
                    } else if (item instanceof Map) {
                        Map<String, Object> def = (Map<String, Object>) item;
                        Object adapter = def.get("adapter");
                        defs.add(new EntrypointDef(adapter == null ? "default" : String.valueOf(adapter),
                                String.valueOf(def.get("value"))));
                    }
                }
                metadata.entrypoints.put(entry.getKey(), defs);
            }
        }
        for (Object item : list(map.get("jars"))) {
            if (item instanceof Map && ((Map<String, Object>) item).get("file") instanceof String) {
                metadata.jars.add((String) ((Map<String, Object>) item).get("file"));
            }
        }
        for (Object item : list(map.get("mixins"))) {
            if (item instanceof String) {
                metadata.mixins.add(new MixinDef((String) item, ModEnvironment.UNIVERSAL));
            } else if (item instanceof Map) {
                Map<String, Object> def = (Map<String, Object>) item;
                metadata.mixins.add(new MixinDef(String.valueOf(def.get("config")), environment(def.get("environment"))));
            }
        }
        Object accessWidener = map.get("accessWidener");
        if (accessWidener == null) {
            accessWidener = map.get("classTweaker");
        }
        if (accessWidener instanceof String) {
            metadata.accessWidener = (String) accessWidener;
        }
        for (ModDependency.Kind kind : ModDependency.Kind.values()) {
            Object deps = map.get(kind.getKey());
            if (deps instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) deps).entrySet()) {
                    List<String> predicates = new ArrayList<>();
                    for (Object predicate : list(entry.getValue())) {
                        predicates.add(String.valueOf(predicate));
                    }
                    metadata.dependencies.add(new Dependency(kind, entry.getKey(), predicates));
                }
            }
        }
        Object name = map.get("name");
        metadata.name = name instanceof String ? (String) name : metadata.id;
        Object description = map.get("description");
        metadata.description = description instanceof String ? (String) description : "";
        people(map.get("authors"), metadata.authors);
        people(map.get("contributors"), metadata.contributors);
        metadata.contact = contact(map.get("contact"));
        for (Object item : list(map.get("license"))) {
            metadata.license.add(String.valueOf(item));
        }
        Object icon = map.get("icon");
        if (icon instanceof String) {
            metadata.icons.put(0, (String) icon);
        } else if (icon instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) icon).entrySet()) {
                try {
                    metadata.icons.put(Integer.parseInt(entry.getKey()), String.valueOf(entry.getValue()));
                } catch (NumberFormatException ignored) {
                    metadata.icons.put(0, String.valueOf(entry.getValue()));
                }
            }
        }
        Object adapters = map.get("languageAdapters");
        if (adapters instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) adapters).entrySet()) {
                metadata.languageAdapters.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        Object custom = map.get("custom");
        if (custom instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) custom).entrySet()) {
                metadata.custom.put(entry.getKey(), CustomValueImpl.of(entry.getValue()));
            }
        }
        return metadata;
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("Missing '" + key + "'");
        }
        return (String) value;
    }

    private static List<?> list(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return (List<?>) value;
        }
        return Collections.singletonList(value);
    }

    private static ModEnvironment environment(Object value) {
        if ("client".equals(value)) {
            return ModEnvironment.CLIENT;
        }
        if ("server".equals(value)) {
            return ModEnvironment.SERVER;
        }
        return ModEnvironment.UNIVERSAL;
    }

    @SuppressWarnings("unchecked")
    private static void people(Object value, List<Person> out) {
        for (Object item : list(value)) {
            if (item instanceof String) {
                out.add(new PersonImpl((String) item, ContactInformation.EMPTY));
            } else if (item instanceof Map) {
                Map<String, Object> person = (Map<String, Object>) item;
                out.add(new PersonImpl(String.valueOf(person.get("name")), contact(person.get("contact"))));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ContactInformation contact(Object value) {
        if (!(value instanceof Map)) {
            return ContactInformation.EMPTY;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
            map.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new ContactImpl(map);
    }

    public Map<String, List<EntrypointDef>> getEntrypointDefs() {
        return entrypoints;
    }

    public List<String> getJars() {
        return jars;
    }

    public List<String> getMixinConfigs(EnvType env) {
        List<String> result = new ArrayList<>();
        for (MixinDef def : mixins) {
            if (def.environment.matches(env)) {
                result.add(def.config);
            }
        }
        return result;
    }

    public String getAccessWidener() {
        return accessWidener;
    }

    public Map<String, String> getLanguageAdapterDefinitions() {
        return languageAdapters;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getProvides() {
        return Collections.unmodifiableList(provides);
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public String getName() {
        return name == null || name.isEmpty() ? id : name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Collection<Person> getAuthors() {
        return Collections.unmodifiableList(authors);
    }

    @Override
    public Collection<Person> getContributors() {
        return Collections.unmodifiableList(contributors);
    }

    @Override
    public ContactInformation getContact() {
        return contact;
    }

    @Override
    public Collection<String> getLicense() {
        return Collections.unmodifiableList(license);
    }

    @Override
    public Optional<String> getIconPath(int size) {
        if (icons.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Integer, String> entry = icons.ceilingEntry(size);
        if (entry == null) {
            entry = icons.lastEntry();
        }
        return Optional.of(entry.getValue());
    }

    @Override
    public boolean containsCustomValue(String key) {
        return custom.containsKey(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return custom.get(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return Collections.unmodifiableMap(custom);
    }

    @Override
    public boolean containsCustomElement(String key) {
        return containsCustomValue(key);
    }

    static final class PersonImpl implements Person {

        private final String name;
        private final ContactInformation contact;

        PersonImpl(String name, ContactInformation contact) {
            this.name = name;
            this.contact = contact;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ContactInformation getContact() {
            return contact;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final class ContactImpl implements ContactInformation {

        private final Map<String, String> values;

        ContactImpl(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Map<String, String> asMap() {
            return Collections.unmodifiableMap(values);
        }
    }

    public static final class Dependency implements ModDependency {

        private final Kind kind;
        private final String modId;
        private final List<String> raw;
        private final List<VersionPredicate> predicates = new ArrayList<>();

        Dependency(Kind kind, String modId, List<String> raw) {
            this.kind = kind;
            this.modId = modId;
            this.raw = raw;
            for (String text : raw) {
                predicates.add(Predicates.parseLenient(text));
            }
            if (predicates.isEmpty()) {
                predicates.add(Predicates.ANY);
            }
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public String getModId() {
            return modId;
        }

        @Override
        public boolean matches(Version version) {
            for (VersionPredicate predicate : predicates) {
                if (predicate.test(version)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Collection<VersionPredicate> getVersionRequirements() {
            return Collections.unmodifiableList(predicates);
        }

        @Override
        public List<VersionInterval> getVersionIntervals() {
            List<VersionInterval> result = new ArrayList<>();
            for (VersionPredicate predicate : predicates) {
                VersionInterval interval = predicate.getInterval();
                if (interval != null) {
                    result = VersionInterval.or(result, interval);
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return kind.getKey() + " " + modId + " " + String.join(" || ", raw);
        }
    }

    static Version parseVersionOrNull(String text) {
        try {
            return Version.parse(text);
        } catch (VersionParsingException error) {
            return null;
        }
    }
}
