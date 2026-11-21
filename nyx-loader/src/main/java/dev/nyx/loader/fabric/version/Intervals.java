package dev.nyx.loader.fabric.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

public final class Intervals {

    public static final VersionInterval INFINITE = new Interval(null, false, null, false);

    private Intervals() {
    }

    public static VersionInterval of(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
        return new Interval(min, minInclusive, max, maxInclusive);
    }

    public static VersionInterval and(VersionInterval a, VersionInterval b) {
        if (a == null || b == null) {
            return null;
        }
        Version min;
        boolean minInclusive;
        int compareMin = compare(a.getMin(), b.getMin(), true);
        if (compareMin > 0) {
            min = a.getMin();
            minInclusive = a.isMinInclusive();
        } else if (compareMin < 0) {
            min = b.getMin();
            minInclusive = b.isMinInclusive();
        } else {
            min = a.getMin();
            minInclusive = a.isMinInclusive() && b.isMinInclusive();
        }
        Version max;
        boolean maxInclusive;
        int compareMax = compare(a.getMax(), b.getMax(), false);
        if (compareMax < 0) {
            max = a.getMax();
            maxInclusive = a.isMaxInclusive();
        } else if (compareMax > 0) {
            max = b.getMax();
            maxInclusive = b.isMaxInclusive();
        } else {
            max = a.getMax();
            maxInclusive = a.isMaxInclusive() && b.isMaxInclusive();
        }
        if (min != null && max != null) {
            int order = min.compareTo(max);
            if (order > 0 || order == 0 && !(minInclusive && maxInclusive)) {
                return null;
            }
        }
        return new Interval(min, minInclusive, max, maxInclusive);
    }

    public static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
        List<VersionInterval> result = new ArrayList<>();
        for (VersionInterval x : a) {
            for (VersionInterval y : b) {
                VersionInterval merged = and(x, y);
                if (merged != null) {
                    result.add(merged);
                }
            }
        }
        return normalize(result);
    }

    public static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
        List<VersionInterval> result = new ArrayList<>(a);
        if (b != null) {
            result.add(b);
        }
        return normalize(result);
    }

    public static List<VersionInterval> not(VersionInterval interval) {
        if (interval == null) {
            return Collections.singletonList(INFINITE);
        }
        List<VersionInterval> result = new ArrayList<>();
        if (interval.getMin() != null) {
            result.add(new Interval(null, false, interval.getMin(), !interval.isMinInclusive()));
        }
        if (interval.getMax() != null) {
            result.add(new Interval(interval.getMax(), !interval.isMaxInclusive(), null, false));
        }
        return result;
    }

    public static List<VersionInterval> not(Collection<VersionInterval> intervals) {
        List<VersionInterval> result = Collections.singletonList(INFINITE);
        for (VersionInterval interval : intervals) {
            result = and(result, not(interval));
        }
        return result;
    }

    private static int compare(Version a, Version b, boolean nullIsLow) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return nullIsLow ? -1 : 1;
        }
        if (b == null) {
            return nullIsLow ? 1 : -1;
        }
        return a.compareTo(b);
    }

    private static List<VersionInterval> normalize(List<VersionInterval> intervals) {
        List<VersionInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(VersionInterval::getMin, (x, y) -> compare(x, y, true)));
        return sorted;
    }

    public static boolean contains(VersionInterval interval, Version version) {
        if (interval.getMin() != null) {
            int order = version.compareTo(interval.getMin());
            if (order < 0 || order == 0 && !interval.isMinInclusive()) {
                return false;
            }
        }
        if (interval.getMax() != null) {
            int order = version.compareTo(interval.getMax());
            if (order > 0 || order == 0 && !interval.isMaxInclusive()) {
                return false;
            }
        }
        return true;
    }

    static final class Interval implements VersionInterval {

        private final Version min;
        private final boolean minInclusive;
        private final Version max;
        private final boolean maxInclusive;

        Interval(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
            this.min = min;
            this.minInclusive = min != null && minInclusive;
            this.max = max;
            this.maxInclusive = max != null && maxInclusive;
        }

        @Override
        public boolean isSemantic() {
            return (min == null || min instanceof net.fabricmc.loader.api.SemanticVersion)
                    && (max == null || max instanceof net.fabricmc.loader.api.SemanticVersion);
        }

        @Override
        public Version getMin() {
            return min;
        }

        @Override
        public boolean isMinInclusive() {
            return minInclusive;
        }

        @Override
        public Version getMax() {
            return max;
        }

        @Override
        public boolean isMaxInclusive() {
            return maxInclusive;
        }

        @Override
        public String toString() {
            return (min == null ? "(-∞" : (minInclusive ? "[" : "(") + min.getFriendlyString()) + ","
                    + (max == null ? "∞)" : max.getFriendlyString() + (maxInclusive ? "]" : ")"));
        }
    }
}
