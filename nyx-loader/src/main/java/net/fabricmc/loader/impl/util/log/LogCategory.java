package net.fabricmc.loader.impl.util.log;

public final class LogCategory {

    public static final LogCategory GENERAL = new LogCategory("General");

    public final String context;
    public final String name;

    private LogCategory(String name) {
        this.context = "Nyx";
        this.name = name;
    }

    public static LogCategory create(String... names) {
        return new LogCategory(String.join("/", names));
    }

    public static LogCategory createCustom(String context, String... names) {
        return new LogCategory(context + "/" + String.join("/", names));
    }

    @Override
    public String toString() {
        return name;
    }
}
