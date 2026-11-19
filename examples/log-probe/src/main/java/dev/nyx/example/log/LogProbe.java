package dev.nyx.example.log;

import dev.nyx.api.Nyx;
import dev.nyx.api.NyxMod;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogProbe implements NyxMod {

    private static final int TICKS_PER_LOG = 200;

    private File logFile;
    private int ticks;

    @Override
    public void onInitialize() {
        File logsDirectory = new File(Nyx.getGameDirectory(), "logs");
        if (!logsDirectory.isDirectory() && !logsDirectory.mkdirs()) {
            Nyx.log("Could not create " + logsDirectory);
            return;
        }
        logFile = new File(logsDirectory, "nyx-probe.log");
        write("Nyx Loader " + Nyx.VERSION + " funcionando no Minecraft " + Nyx.MINECRAFT_VERSION);
        write("Java " + System.getProperty("java.version") + " | " + System.getProperty("os.name"));
        write("Pasta do jogo: " + Nyx.getGameDirectory().getAbsolutePath());
        write("Mod carregado: " + Nyx.isModLoaded("log-probe"));
        Nyx.onClientTick(this::tick);
    }

    private void tick() {
        ticks++;
        if (ticks % TICKS_PER_LOG == 0) {
            write("tick " + ticks);
        }
    }

    private void write(String line) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println("[" + stamp + "] " + line);
        } catch (IOException error) {
            Nyx.log("Could not write probe log: " + error);
        }
    }
}
