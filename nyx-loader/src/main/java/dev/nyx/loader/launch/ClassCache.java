package dev.nyx.loader.launch;

import dev.nyx.api.Nyx;
import dev.nyx.loader.NyxRuntime;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassCache {

    private static final int FORMAT = 1;
    private static final int MAGIC = 0x4E595843;
    private static final int END = 0x4E595845;
    private static final int MAX_SEGMENTS = 12;
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static final long FLUSH_DELAY = 5000L;

    private static final ThreadLocal<int[]> TAINTS = ThreadLocal.withInitial(() -> new int[1]);
    private static final Map<String, Entry> INDEX = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PENDING = new ConcurrentHashMap<>();
    private static final List<MappedByteBuffer> SEGMENTS = new ArrayList<>();

    private static volatile boolean active;
    private static File directory;
    private static String fingerprint;
    private static volatile long lastPut;
    private static long storedBytes;
    private static int hits;
    private static int misses;
    private static int nextSegment;
    private static final java.util.concurrent.atomic.AtomicLong transformNanos = new java.util.concurrent.atomic.AtomicLong();

    private ClassCache() {
    }

    private static final class Entry {
        final MappedByteBuffer segment;
        final int offset;
        final int length;

        Entry(MappedByteBuffer segment, int offset, int length) {
            this.segment = segment;
            this.offset = offset;
            this.length = length;
        }
    }

    public static File directory(File gameDir) {
        return new File(gameDir, ".nyx" + File.separator + "class-cache");
    }

    public static boolean enabled(File gameDir) {
        String property = System.getProperty("nyx.classCache");
        if (property != null) {
            return Boolean.parseBoolean(property);
        }
        File config = new File(gameDir, "config" + File.separator + "nyx" + File.separator + "loader.properties");
        if (config.isFile()) {
            java.util.Properties properties = new java.util.Properties();
            try (java.io.InputStream input = new java.io.FileInputStream(config)) {
                properties.load(input);
            } catch (IOException ignored) {
                return true;
            }
            return !"false".equalsIgnoreCase(properties.getProperty("class-cache", "true").trim());
        }
        return true;
    }

    public static synchronized void activate(File gameDir, String mode, List<String> transformers) {
        if (active || !enabled(gameDir)) {
            return;
        }
        long start = System.nanoTime();
        directory = directory(gameDir);
        try {
            fingerprint = fingerprint(gameDir, mode, transformers);
            File state = new File(directory, "state");
            boolean unclean = state.isFile() && "loading".equals(read(state));
            File stamp = new File(directory, "fingerprint");
            boolean same = stamp.isFile() && fingerprint.equals(read(stamp));
            if (unclean || !same) {
                clear(directory);
                if (unclean) {
                    NyxRuntime.log("Class cache discarded: the previous launch did not reach the main menu");
                }
            }
            Files.createDirectories(directory.toPath());
            write(stamp, fingerprint);
            write(state, "loading");
            loadSegments();
            active = true;
            NyxRuntime.log("Class cache: " + INDEX.size() + " classes ready ("
                    + (System.nanoTime() - start) / 1000000L + " ms)");
            Thread flusher = new Thread(ClassCache::flushLoop, "Nyx-ClassCache");
            flusher.setDaemon(true);
            flusher.start();
            Runtime.getRuntime().addShutdownHook(new Thread(ClassCache::flush, "Nyx-ClassCache-Exit"));
        } catch (Throwable error) {
            active = false;
            NyxRuntime.log("Class cache disabled: " + error);
        }
    }

    public static void markReady() {
        if (!active) {
            return;
        }
        try {
            write(new File(directory, "state"), "ok");
        } catch (IOException ignored) {
            return;
        }
        NyxRuntime.log("Class cache: " + hits + " hits, " + misses + " transformed in "
                + transformNanos.get() / 1000000L + " ms");
    }

    public static void invalidate(File gameDir) {
        active = false;
        PENDING.clear();
        try {
            clear(directory(gameDir));
        } catch (IOException ignored) {
            return;
        }
    }

    public static void taint() {
        TAINTS.get()[0]++;
    }

    public static int taints() {
        return TAINTS.get()[0];
    }

    public static byte[] get(String name) {
        if (!active) {
            return null;
        }
        byte[] pending = PENDING.get(name);
        if (pending != null) {
            return pending;
        }
        Entry entry = INDEX.get(name);
        if (entry == null) {
            misses++;
            return null;
        }
        byte[] bytes = new byte[entry.length];
        entry.segment.get(entry.offset, bytes);
        hits++;
        return bytes;
    }

    public static void put(String name, byte[] bytes, int taintsBefore, long started) {
        if (active) {
            transformNanos.addAndGet(System.nanoTime() - started);
        }
        if (!active || bytes == null || TAINTS.get()[0] != taintsBefore || INDEX.containsKey(name)) {
            return;
        }
        synchronized (ClassCache.class) {
            if (storedBytes + bytes.length > MAX_BYTES) {
                return;
            }
            storedBytes += bytes.length;
        }
        PENDING.put(name, bytes);
        lastPut = System.currentTimeMillis();
    }

    private static void flushLoop() {
        while (true) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException error) {
                return;
            }
            if (!PENDING.isEmpty() && System.currentTimeMillis() - lastPut > FLUSH_DELAY) {
                flush();
            }
        }
    }

    public static synchronized void flush() {
        if (!active || PENDING.isEmpty()) {
            return;
        }
        Map<String, byte[]> batch = new HashMap<>(PENDING);
        try {
            int number;
            synchronized (SEGMENTS) {
                number = nextSegment++;
            }
            File target = new File(directory, String.format("segment-%04d.bin", number));
            writeSegment(target, batch);
            MappedByteBuffer mapped = map(target);
            synchronized (SEGMENTS) {
                SEGMENTS.add(mapped);
            }
            indexSegment(mapped);
            for (String name : batch.keySet()) {
                PENDING.remove(name);
            }
            if (SEGMENTS.size() > MAX_SEGMENTS) {
                compact();
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not write class cache: " + error);
        }
    }

    private static void compact() throws IOException {
        Map<String, byte[]> all = new HashMap<>();
        for (Map.Entry<String, Entry> entry : INDEX.entrySet()) {
            Entry value = entry.getValue();
            byte[] bytes = new byte[value.length];
            value.segment.get(value.offset, bytes);
            all.put(entry.getKey(), bytes);
        }
        int number;
        synchronized (SEGMENTS) {
            number = nextSegment++;
        }
        File merged = new File(directory, String.format("segment-%04d.bin", number));
        writeSegment(merged, all);
        File[] files = directory.listFiles((dir, name) -> name.startsWith("segment-") && name.endsWith(".bin"));
        if (files != null) {
            for (File file : files) {
                if (!file.equals(merged)) {
                    file.delete();
                }
            }
        }
        MappedByteBuffer mapped = map(merged);
        synchronized (SEGMENTS) {
            SEGMENTS.clear();
            SEGMENTS.add(mapped);
        }
        synchronized (ClassCache.class) {
            storedBytes = 0L;
        }
        indexSegment(mapped);
    }

    private static void writeSegment(File target, Map<String, byte[]> entries) throws IOException {
        File temp = new File(target.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp), 1 << 16))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            byte[] stamp = fingerprint.getBytes(StandardCharsets.UTF_8);
            out.writeShort(stamp.length);
            out.write(stamp);
            out.writeInt(entries.size());
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeShort(name.length);
                out.write(name);
                out.writeInt(entry.getValue().length);
                out.write(entry.getValue());
            }
            out.writeInt(END);
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void loadSegments() throws IOException {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("segment-") && name.endsWith(".bin"));
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            String digits = file.getName().substring(8, file.getName().length() - 4);
            try {
                nextSegment = Math.max(nextSegment, Integer.parseInt(digits) + 1);
            } catch (NumberFormatException ignored) {
                continue;
            }
            try {
                MappedByteBuffer mapped = map(file);
                if (indexSegment(mapped)) {
                    SEGMENTS.add(mapped);
                } else {
                    file.delete();
                }
            } catch (IOException | RuntimeException error) {
                file.delete();
            }
        }
    }

    private static MappedByteBuffer map(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel channel = raf.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    private static boolean indexSegment(MappedByteBuffer mapped) {
        ByteBuffer buffer = mapped.duplicate();
        if (buffer.remaining() < 14 || buffer.getInt() != MAGIC || buffer.getInt() != FORMAT) {
            return false;
        }
        byte[] stamp = new byte[buffer.getShort() & 0xFFFF];
        buffer.get(stamp);
        if (!fingerprint.equals(new String(stamp, StandardCharsets.UTF_8))) {
            return false;
        }
        if (buffer.getInt(buffer.limit() - 4) != END) {
            return false;
        }
        int count = buffer.getInt();
        Map<String, Entry> found = new HashMap<>();
        long bytes = 0L;
        for (int i = 0; i < count; i++) {
            byte[] name = new byte[buffer.getShort() & 0xFFFF];
            buffer.get(name);
            int length = buffer.getInt();
            if (length < 0 || length > buffer.remaining() - 4) {
                return false;
            }
            found.put(new String(name, StandardCharsets.UTF_8), new Entry(mapped, buffer.position(), length));
            buffer.position(buffer.position() + length);
            bytes += length;
        }
        INDEX.putAll(found);
        synchronized (ClassCache.class) {
            storedBytes += bytes;
        }
        return true;
    }

    private static String fingerprint(File gameDir, String mode, List<String> transformers) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        update(digest, "format=" + FORMAT + ";nyx=" + Nyx.VERSION + ";mode=" + mode + ";java="
                + System.getProperty("java.specification.version"));
        for (String transformer : transformers) {
            update(digest, "t:" + transformer);
        }
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                stat(digest, new File(entry));
            }
        }
        List<File> mods = new ArrayList<>();
        collect(new File(gameDir, "mods"), mods, 3);
        for (File file : mods) {
            stat(digest, file);
        }
        List<File> configs = new ArrayList<>();
        collect(new File(gameDir, "config"), configs, 4);
        for (File file : configs) {
            if (file.length() <= 512L * 1024L) {
                update(digest, "c:" + file.getPath());
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#")) {
                        update(digest, trimmed);
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (byte b : digest.digest()) {
            text.append(String.format("%02x", b));
        }
        return text.toString();
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            if (file.isDirectory()) {
                if (depth > 0) {
                    collect(file, out, depth - 1);
                }
            } else {
                out.add(file);
            }
        }
    }

    private static void stat(MessageDigest digest, File file) {
        update(digest, "f:" + file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified());
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void clear(File dir) throws IOException {
        synchronized (SEGMENTS) {
            SEGMENTS.clear();
        }
        INDEX.clear();
        storedBytes = 0L;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }
}
