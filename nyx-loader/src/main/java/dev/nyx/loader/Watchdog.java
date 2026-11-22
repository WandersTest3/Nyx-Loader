package dev.nyx.loader;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;

public final class Watchdog {

    private static final long STALL_NANOS = 8_000_000_000L;
    private static final int MAX_DUMPS = 5;

    private static volatile long beat = System.nanoTime();
    private static boolean started;
    private static int dumps;

    private Watchdog() {
    }

    public static void beat() {
        beat = System.nanoTime();
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        beat = System.nanoTime();
        Thread thread = new Thread(Watchdog::run, "Nyx-Watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        boolean armed = true;
        while (true) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException error) {
                return;
            }
            boolean stalled = System.nanoTime() - beat > STALL_NANOS;
            if (stalled && armed && dumps < MAX_DUMPS) {
                armed = false;
                dump();
            } else if (!stalled) {
                armed = true;
            }
        }
    }

    private static void dump() {
        dumps++;
        try {
            File dir = new File(NyxRuntime.getGameDirectory(), "logs");
            dir.mkdirs();
            File file = new File(dir, "nyx-freeze-" + dumps + ".txt");
            try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
                out.println("Nyx watchdog: no frame for " + (System.nanoTime() - beat) / 1000000L + " ms");
                for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                    Thread thread = entry.getKey();
                    out.println();
                    out.println("\"" + thread.getName() + "\" state=" + thread.getState() + " daemon=" + thread.isDaemon());
                    for (StackTraceElement element : entry.getValue()) {
                        out.println("    at " + element);
                    }
                }
            }
            NyxRuntime.log("Game stalled; thread dump written to " + file.getPath());
        } catch (Throwable error) {
            NyxRuntime.log("Watchdog dump failed: " + error);
        }
    }
}
