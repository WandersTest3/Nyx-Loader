package net.fabricmc.loader.impl.util.log;

public final class Log {

    private Log() {
    }

    private static void emit(String level, LogCategory category, String message, Throwable error) {
        System.out.println("[" + level + "] [" + category + "] " + message);
        if (error != null) {
            error.printStackTrace(System.out);
        }
    }

    private static String format(String message, Object... args) {
        try {
            return args == null || args.length == 0 ? message : String.format(message, args);
        } catch (RuntimeException error) {
            return message;
        }
    }

    public static void error(LogCategory category, String message, Object... args) {
        emit("ERROR", category, format(message, args), null);
    }

    public static void error(LogCategory category, String message, Throwable error) {
        emit("ERROR", category, message, error);
    }

    public static void error(LogCategory category, String message) {
        emit("ERROR", category, message, null);
    }

    public static void warn(LogCategory category, String message, Object... args) {
        emit("WARN", category, format(message, args), null);
    }

    public static void warn(LogCategory category, String message, Throwable error) {
        emit("WARN", category, message, error);
    }

    public static void warn(LogCategory category, String message) {
        emit("WARN", category, message, null);
    }

    public static void info(LogCategory category, String message, Object... args) {
        emit("INFO", category, format(message, args), null);
    }

    public static void info(LogCategory category, String message, Throwable error) {
        emit("INFO", category, message, error);
    }

    public static void info(LogCategory category, String message) {
        emit("INFO", category, message, null);
    }

    public static void debug(LogCategory category, String message, Object... args) {
    }

    public static void debug(LogCategory category, String message, Throwable error) {
    }

    public static void debug(LogCategory category, String message) {
    }

    public static void trace(LogCategory category, String message, Object... args) {
    }

    public static void trace(LogCategory category, String message, Throwable error) {
    }

    public static void trace(LogCategory category, String message) {
    }

    public static boolean shouldLog(LogLevel level, LogCategory category) {
        return level != LogLevel.DEBUG && level != LogLevel.TRACE;
    }
}
