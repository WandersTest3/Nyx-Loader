package dev.nyx.loader;

import dev.nyx.api.Nyx;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class NyxTweaker implements ITweaker {

    private static final String LAUNCH_TARGET = "net.minecraft.client.main.Main";

    private List<String> args = new ArrayList<String>();
    private File gameDir;
    private File assetsDir;
    private String profile;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.args = new ArrayList<String>(args);
        this.gameDir = gameDir;
        this.assetsDir = assetsDir;
        this.profile = profile;
        NyxRuntime.setGameDirectory(gameDir);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        NyxRuntime.log("Nyx Loader " + Nyx.VERSION + " for Minecraft " + Nyx.MINECRAFT_VERSION);
        classLoader.addClassLoaderExclusion("dev.nyx.api.");
        classLoader.registerTransformer("dev.nyx.loader.NyxClassTransformer");
        NyxRuntime.discoverMods(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return LAUNCH_TARGET;
    }

    @Override
    public String[] getLaunchArguments() {
        List<String> result = new ArrayList<String>(args);
        addOption(result, "--version", profile);
        addOption(result, "--gameDir", gameDir != null ? gameDir.getAbsolutePath() : null);
        addOption(result, "--assetsDir", assetsDir != null ? assetsDir.getAbsolutePath() : null);
        return result.toArray(new String[result.size()]);
    }

    private static void addOption(List<String> list, String name, String value) {
        if (value != null && !list.contains(name)) {
            list.add(name);
            list.add(value);
        }
    }
}
