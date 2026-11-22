package dev.nyx.loader.mixin;

import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.fabric.FabricModContainer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

public final class NyxMixinBootstrap {

    private static final String[][] LOADER_MIXIN = {
        {"0.20.0", "COMPATIBILITY_0_17_5"},
        {"0.19.4", "COMPATIBILITY_0_17_4"},
        {"0.19.0", "COMPATIBILITY_0_17_1"},
        {"0.18.4", "COMPATIBILITY_0_17_0"},
        {"0.17.3", "COMPATIBILITY_0_16_5"},
        {"0.16.0", "COMPATIBILITY_0_14_0"},
        {"0.12.0-", "COMPATIBILITY_0_10_0"},
    };

    private NyxMixinBootstrap() {
    }

    public static void init(List<FabricModContainer> mods) {
        System.setProperty("mixin.bootstrapService", MixinServiceNyxBootstrap.class.getName());
        System.setProperty("mixin.service", MixinServiceNyx.class.getName());
        if (dev.nyx.loader.launch.NyxLaunch.isForge() && System.getProperty("mixin.ignoreRequired") == null) {
            System.setProperty("mixin.ignoreRequired", "true");
            NyxRuntime.log("Forge is present: Fabric mixins that conflict with Forge are skipped instead of crashing");
        }
        MixinBootstrap.init();
        Map<String, FabricModContainer> configToMod = new HashMap<>();
        int count = 0;
        for (FabricModContainer mod : mods) {
            for (String config : mod.getFabricMetadata().getMixinConfigs(EnvType.CLIENT)) {
                FabricModContainer previous = configToMod.putIfAbsent(config, mod);
                if (previous != null) {
                    throw new RuntimeException("Mixin config " + config + " is used by both "
                            + previous.getMetadata().getId() + " and " + mod.getMetadata().getId());
                }
                try {
                    Mixins.addConfiguration(config);
                    count++;
                } catch (Throwable error) {
                    throw new RuntimeException("Error parsing Mixin config " + config + " of "
                            + mod.getMetadata().getId(), error);
                }
            }
        }
        for (Config raw : Mixins.getConfigs()) {
            FabricModContainer mod = configToMod.get(raw.getName());
            if (mod == null) {
                continue;
            }
            IMixinConfig config = raw.getConfig();
            config.decorate(FabricUtil.KEY_MOD_ID, mod.getMetadata().getId());
            config.decorate(FabricUtil.KEY_COMPATIBILITY, compatibility(mod));
        }
        try {
            Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            gotoPhase.setAccessible(true);
            gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
            gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Could not start Mixin", error);
        }
        NyxRuntime.log("Mixin ready with " + count + " config(s)");
    }

    private static int compatibility(FabricModContainer mod) {
        Version min = null;
        for (ModDependency dependency : mod.getMetadata().getDependencies()) {
            String id = dependency.getModId();
            if (dependency.getKind() != ModDependency.Kind.DEPENDS
                    || !("fabricloader".equals(id) || "fabric-loader".equals(id))) {
                continue;
            }
            List<VersionInterval> intervals = dependency.getVersionIntervals();
            if (intervals.isEmpty()) {
                continue;
            }
            Version lower = intervals.get(0).getMin();
            if (lower == null) {
                return FabricUtil.COMPATIBILITY_0_9_2;
            }
            if (min == null || lower.compareTo(min) < 0) {
                min = lower;
            }
        }
        if (min == null) {
            return FabricUtil.COMPATIBILITY_0_9_2;
        }
        for (String[] entry : LOADER_MIXIN) {
            try {
                if (min.compareTo(Version.parse(entry[0])) >= 0) {
                    return FabricUtil.class.getField(entry[1]).getInt(null);
                }
            } catch (ReflectiveOperationException | net.fabricmc.loader.api.VersionParsingException error) {
                throw new IllegalStateException(error);
            }
        }
        return FabricUtil.COMPATIBILITY_0_9_2;
    }
}
