package dev.nyx.loader;

import dev.nyx.api.Nyx;
import java.io.File;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.VanillaTweaker;

public class NyxTweaker implements ITweaker {

    private final VanillaTweaker vanilla = new VanillaTweaker();

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        vanilla.acceptOptions(args, gameDir, assetsDir, profile);
        NyxRuntime.setGameDirectory(gameDir);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        NyxRuntime.log("Nyx Loader " + Nyx.VERSION + " for Minecraft " + Nyx.MINECRAFT_VERSION);
        classLoader.addClassLoaderExclusion("dev.nyx.api.");
        vanilla.injectIntoClassLoader(classLoader);
        classLoader.registerTransformer("dev.nyx.loader.NyxClassTransformer");
        NyxRuntime.discoverMods(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return vanilla.getLaunchTarget();
    }

    @Override
    public String[] getLaunchArguments() {
        return vanilla.getLaunchArguments();
    }
}
