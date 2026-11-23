package dev.nyx.loader.modrinth;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class Updater {

    private Updater() {
    }

    public static final class Result {
        public final boolean known;
        public final Modrinth.Version newer;
        public final String site;

        Result(boolean known, Modrinth.Version newer, String site) {
            this.known = known;
            this.newer = newer;
            this.site = site;
        }
    }

    public static Result check(File jar, List<String> loaders) throws IOException {
        String sha1 = Modrinth.sha1(jar);
        Modrinth.Version latest = Modrinth.latestFor(sha1, loaders);
        if (latest != null) {
            boolean same = latest.file.sha1 != null && latest.file.sha1.equalsIgnoreCase(sha1);
            return new Result(true, same ? null : latest, "modrinth");
        }
        if (!CurseForge.hasKey()) {
            return new Result(false, null, null);
        }
        String[] match = CurseForge.match(jar);
        if (match == null || match[0] == null) {
            return new Result(false, null, null);
        }
        Modrinth.Version best = CurseForge.bestVersion(match[0], loaders, false);
        if (best == null || best.id.equals(match[1])) {
            return new Result(true, null, "curseforge");
        }
        return new Result(true, best, "curseforge");
    }

    public static File apply(Result result, File oldJar, Modrinth.Progress progress) throws IOException {
        File directory = oldJar.getParentFile();
        File downloaded = Modrinth.download(result.newer.file, directory, progress);
        if (!downloaded.equals(oldJar) && !oldJar.delete()) {
            oldJar.deleteOnExit();
        }
        return downloaded;
    }
}
