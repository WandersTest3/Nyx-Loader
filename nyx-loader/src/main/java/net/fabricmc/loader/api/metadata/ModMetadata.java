package net.fabricmc.loader.api.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.Version;

public interface ModMetadata {

    String getType();

    String getId();

    Collection<String> getProvides();

    Version getVersion();

    ModEnvironment getEnvironment();

    Collection<ModDependency> getDependencies();

    @Deprecated
    default Collection<ModDependency> getDepends() {
        return ofKind(ModDependency.Kind.DEPENDS);
    }

    @Deprecated
    default Collection<ModDependency> getRecommends() {
        return ofKind(ModDependency.Kind.RECOMMENDS);
    }

    @Deprecated
    default Collection<ModDependency> getSuggests() {
        return ofKind(ModDependency.Kind.SUGGESTS);
    }

    @Deprecated
    default Collection<ModDependency> getConflicts() {
        return ofKind(ModDependency.Kind.CONFLICTS);
    }

    @Deprecated
    default Collection<ModDependency> getBreaks() {
        return ofKind(ModDependency.Kind.BREAKS);
    }

    private Collection<ModDependency> ofKind(ModDependency.Kind kind) {
        List<ModDependency> result = new ArrayList<>();
        for (ModDependency dependency : getDependencies()) {
            if (dependency.getKind() == kind) {
                result.add(dependency);
            }
        }
        return result;
    }

    String getName();

    String getDescription();

    Collection<Person> getAuthors();

    Collection<Person> getContributors();

    ContactInformation getContact();

    Collection<String> getLicense();

    Optional<String> getIconPath(int size);

    boolean containsCustomValue(String key);

    CustomValue getCustomValue(String key);

    Map<String, CustomValue> getCustomValues();

    @Deprecated
    boolean containsCustomElement(String key);
}
