package dev.nyx.loader.fabric;

import dev.nyx.loader.launch.TinyMappings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.fabricmc.loader.api.MappingResolver;

public final class MappingResolverImpl implements MappingResolver {

    private static final String OFFICIAL = "official";
    private static final String RUNTIME = "intermediary";

    private final TinyMappings mappings;
    private final dev.nyx.loader.launch.RuntimeNames names;

    public MappingResolverImpl(TinyMappings mappings, dev.nyx.loader.launch.RuntimeNames names) {
        this.mappings = mappings;
        this.names = names;
    }

    @Override
    public Collection<String> getNamespaces() {
        return names.isIdentity() ? Collections.unmodifiableList(Arrays.asList(OFFICIAL, RUNTIME))
                : Collections.unmodifiableList(Arrays.asList(OFFICIAL, RUNTIME, "srg"));
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return names.namespace();
    }

    private void check(String namespace) {
        if (!OFFICIAL.equals(namespace) && !RUNTIME.equals(namespace) && !names.namespace().equals(namespace)) {
            throw new IllegalArgumentException("Unknown namespace " + namespace);
        }
    }

    private String toIntermediaryClass(String namespace, String className) {
        String internal = className.replace('.', '/');
        if (OFFICIAL.equals(namespace)) {
            return mappings.mapClass(internal);
        }
        if (RUNTIME.equals(namespace)) {
            return internal;
        }
        return names.unmap(internal);
    }

    private String fromIntermediaryClass(String targetNamespace, String internal) {
        if (OFFICIAL.equals(targetNamespace)) {
            return mappings.unmapClass(internal);
        }
        if (RUNTIME.equals(targetNamespace)) {
            return internal;
        }
        return names.map(internal);
    }

    @Override
    public String mapClassName(String namespace, String className) {
        check(namespace);
        return fromIntermediaryClass(names.namespace(), toIntermediaryClass(namespace, className)).replace('/', '.');
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        check(targetNamespace);
        String intermediary = names.unmap(className.replace('.', '/'));
        return fromIntermediaryClass(targetNamespace, intermediary).replace('/', '.');
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        check(namespace);
        String intermediary = name;
        if (OFFICIAL.equals(namespace)) {
            String mapped = mappings.fieldName(owner.replace('.', '/'), name);
            intermediary = mapped == null ? name : mapped;
        } else if (!RUNTIME.equals(namespace)) {
            return name;
        }
        return names.field(intermediary);
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        check(namespace);
        String intermediary = name;
        if (OFFICIAL.equals(namespace)) {
            String mapped = mappings.methodName(owner.replace('.', '/'), name, descriptor);
            intermediary = mapped == null ? name : mapped;
        } else if (!RUNTIME.equals(namespace)) {
            return name;
        }
        return names.method(intermediary);
    }
}
