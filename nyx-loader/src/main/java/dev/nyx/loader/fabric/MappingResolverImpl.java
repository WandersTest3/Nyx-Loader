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

    public MappingResolverImpl(TinyMappings mappings) {
        this.mappings = mappings;
    }

    @Override
    public Collection<String> getNamespaces() {
        return Collections.unmodifiableList(Arrays.asList(OFFICIAL, RUNTIME));
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return RUNTIME;
    }

    private void check(String namespace) {
        if (!OFFICIAL.equals(namespace) && !RUNTIME.equals(namespace)) {
            throw new IllegalArgumentException("Unknown namespace " + namespace);
        }
    }

    @Override
    public String mapClassName(String namespace, String className) {
        check(namespace);
        if (RUNTIME.equals(namespace) || mappings == null) {
            return className;
        }
        return mappings.mapClass(className.replace('.', '/')).replace('/', '.');
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        check(targetNamespace);
        if (RUNTIME.equals(targetNamespace) || mappings == null) {
            return className;
        }
        return mappings.unmapClass(className.replace('.', '/')).replace('/', '.');
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        check(namespace);
        if (RUNTIME.equals(namespace) || mappings == null) {
            return name;
        }
        String mapped = mappings.fieldName(owner.replace('.', '/'), name);
        return mapped == null ? name : mapped;
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        check(namespace);
        if (RUNTIME.equals(namespace) || mappings == null) {
            return name;
        }
        String mapped = mappings.methodName(owner.replace('.', '/'), name, descriptor);
        return mapped == null ? name : mapped;
    }
}
