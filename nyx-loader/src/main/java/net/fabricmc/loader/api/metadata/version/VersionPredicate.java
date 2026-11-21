package net.fabricmc.loader.api.metadata.version;

import dev.nyx.loader.fabric.version.Predicates;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public interface VersionPredicate extends Predicate<Version> {

    Collection<? extends PredicateTerm> getTerms();

    VersionInterval getInterval();

    interface PredicateTerm {

        VersionComparisonOperator getOperator();

        Version getReferenceVersion();
    }

    static VersionPredicate parse(String predicate) throws VersionParsingException {
        return Predicates.parse(predicate);
    }

    static Collection<VersionPredicate> parse(Collection<String> predicates) throws VersionParsingException {
        List<VersionPredicate> result = new ArrayList<>(predicates.size());
        for (String predicate : predicates) {
            result.add(parse(predicate));
        }
        return result;
    }
}
