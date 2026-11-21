package dev.nyx.loader.fabric.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

public final class Predicates {

    private static final VersionComparisonOperator[] OPERATORS = {
        VersionComparisonOperator.GREATER_EQUAL, VersionComparisonOperator.LESS_EQUAL,
        VersionComparisonOperator.GREATER, VersionComparisonOperator.LESS, VersionComparisonOperator.EQUAL,
        VersionComparisonOperator.SAME_TO_NEXT_MINOR, VersionComparisonOperator.SAME_TO_NEXT_MAJOR
    };

    public static final VersionPredicate ANY = new PredicateImpl("*", Collections.emptyList());

    private Predicates() {
    }

    public static VersionPredicate parse(String text) throws VersionParsingException {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return ANY;
        }
        List<Term> terms = new ArrayList<>();
        for (String part : trimmed.split("\\s+")) {
            VersionComparisonOperator operator = VersionComparisonOperator.EQUAL;
            String rest = part;
            for (VersionComparisonOperator candidate : OPERATORS) {
                if (part.startsWith(candidate.getSerialized())) {
                    operator = candidate;
                    rest = part.substring(candidate.getSerialized().length());
                    break;
                }
            }
            if ("*".equals(rest)) {
                continue;
            }
            Version version = Versions.parse(rest, true);
            if (version instanceof SemanticVersion && ((SemanticVersion) version).hasWildcard()
                    && operator != VersionComparisonOperator.EQUAL) {
                throw new VersionParsingException("Wildcards are only allowed with '=': " + part);
            }
            if (!(version instanceof SemanticVersion) && operator != VersionComparisonOperator.EQUAL) {
                throw new VersionParsingException("Non-semantic version with operator: " + part);
            }
            terms.add(new Term(operator, version));
        }
        return new PredicateImpl(trimmed, terms);
    }

    public static VersionPredicate parseLenient(String text) {
        try {
            return parse(text);
        } catch (VersionParsingException error) {
            return new PredicateImpl(text, Collections.singletonList(
                    new Term(VersionComparisonOperator.EQUAL, new StringVersion(text))));
        }
    }

    static final class Term implements VersionPredicate.PredicateTerm {

        private final VersionComparisonOperator operator;
        private final Version reference;

        Term(VersionComparisonOperator operator, Version reference) {
            this.operator = operator;
            this.reference = reference;
        }

        @Override
        public VersionComparisonOperator getOperator() {
            return operator;
        }

        @Override
        public Version getReferenceVersion() {
            return reference;
        }

        boolean test(Version version) {
            if (reference instanceof SemanticVersionImpl && ((SemanticVersionImpl) reference).hasWildcard()) {
                if (!(version instanceof SemanticVersion)) {
                    return false;
                }
                SemanticVersionImpl ref = (SemanticVersionImpl) reference;
                int wildcard = ref.wildcardPosition();
                SemanticVersion candidate = (SemanticVersion) version;
                for (int i = 0; i < wildcard; i++) {
                    if (candidate.getVersionComponent(i) != ref.getVersionComponent(i)) {
                        return false;
                    }
                }
                return true;
            }
            return operator.test(version, reference);
        }

        VersionInterval interval() {
            if (!(reference instanceof SemanticVersion)) {
                return Intervals.of(reference, true, reference, true);
            }
            SemanticVersionImpl ref = (SemanticVersionImpl) reference;
            if (ref.hasWildcard()) {
                int wildcard = ref.wildcardPosition();
                if (wildcard == 0) {
                    return Intervals.INFINITE;
                }
                int[] low = new int[wildcard];
                int[] high = new int[wildcard];
                for (int i = 0; i < wildcard; i++) {
                    low[i] = ref.getVersionComponent(i);
                    high[i] = low[i];
                }
                high[wildcard - 1]++;
                return Intervals.of(SemanticVersionImpl.lowestOf(low), true, SemanticVersionImpl.lowestOf(high), false);
            }
            SemanticVersion min = operator.minVersion(ref);
            SemanticVersion max = operator.maxVersion(ref);
            return Intervals.of(min, operator.isMinInclusive(), max, operator.isMaxInclusive());
        }

        @Override
        public String toString() {
            return operator.getSerialized() + reference.getFriendlyString();
        }
    }

    static final class PredicateImpl implements VersionPredicate {

        private final String text;
        private final List<Term> terms;

        PredicateImpl(String text, List<Term> terms) {
            this.text = text;
            this.terms = terms;
        }

        @Override
        public Collection<? extends PredicateTerm> getTerms() {
            return Collections.unmodifiableList(terms);
        }

        @Override
        public VersionInterval getInterval() {
            VersionInterval result = Intervals.INFINITE;
            for (Term term : terms) {
                result = Intervals.and(result, term.interval());
                if (result == null) {
                    return null;
                }
            }
            return result;
        }

        @Override
        public boolean test(Version version) {
            for (Term term : terms) {
                if (!term.test(version)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
