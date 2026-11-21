package net.fabricmc.loader.api.metadata.version;

import dev.nyx.loader.fabric.version.Intervals;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.Version;

public interface VersionInterval {

    VersionInterval INFINITE = Intervals.INFINITE;

    boolean isSemantic();

    Version getMin();

    boolean isMinInclusive();

    Version getMax();

    boolean isMaxInclusive();

    default VersionInterval and(VersionInterval o) {
        return and(this, o);
    }

    default List<VersionInterval> or(Collection<VersionInterval> o) {
        return or(o, this);
    }

    default List<VersionInterval> not() {
        return not(this);
    }

    static VersionInterval and(VersionInterval a, VersionInterval b) {
        return Intervals.and(a, b);
    }

    static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
        return Intervals.and(a, b);
    }

    static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
        return Intervals.or(a, b);
    }

    static List<VersionInterval> not(VersionInterval interval) {
        return Intervals.not(interval);
    }

    static List<VersionInterval> not(Collection<VersionInterval> intervals) {
        return Intervals.not(intervals);
    }
}
