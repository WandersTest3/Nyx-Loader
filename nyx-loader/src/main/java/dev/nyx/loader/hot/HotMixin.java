package dev.nyx.loader.hot;

import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.launch.ClassCache;
import dev.nyx.loader.launch.GameLoader;
import dev.nyx.loader.launch.NyxLaunch;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class HotMixin {

    public static final class Spec {
        public final String config;
        public final String modId;
        public final int compatibility;

        public Spec(String config, String modId, int compatibility) {
            this.config = config;
            this.modId = modId;
            this.compatibility = compatibility;
        }
    }

    private static volatile boolean applying;

    private HotMixin() {
    }

    public static boolean isApplying() {
        return applying;
    }

    public static synchronized String apply(List<Spec> specs) {
        if (specs.isEmpty()) {
            return null;
        }
        applying = true;
        try {
            return applyConfigs(specs);
        } finally {
            applying = false;
        }
    }

    private static String applyConfigs(List<Spec> specs) {
        IMixinTransformer transformer = NyxLaunch.mixinTransformer();
        if (!NyxLaunch.isMixinReady() || transformer == null) {
            return "Mixin is not running";
        }
        Object processor;
        Method select;
        MixinEnvironment environment;
        List<?> configs;
        try {
            Field field = transformer.getClass().getDeclaredField("processor");
            field.setAccessible(true);
            processor = field.get(transformer);
            Field environmentField = processor.getClass().getDeclaredField("currentEnvironment");
            environmentField.setAccessible(true);
            environment = (MixinEnvironment) environmentField.get(processor);
            select = processor.getClass().getDeclaredMethod("select", MixinEnvironment.class);
            select.setAccessible(true);
            Field configsField = processor.getClass().getDeclaredField("configs");
            configsField.setAccessible(true);
            configs = (List<?>) configsField.get(processor);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return "this Mixin version cannot be extended at runtime (" + error + ")";
        }
        List<String> names = new ArrayList<String>();
        List<Config> handles = new ArrayList<Config>();
        try {
            for (Spec spec : specs) {
                Mixins.addConfiguration(spec.config);
                names.add(spec.config);
            }
            for (Config raw : new ArrayList<Config>(Mixins.getConfigs())) {
                for (Spec spec : specs) {
                    if (raw.getName().equals(spec.config)) {
                        IMixinConfig config = raw.getConfig();
                        config.decorate(FabricUtil.KEY_MOD_ID, spec.modId);
                        config.decorate(FabricUtil.KEY_COMPATIBILITY, spec.compatibility);
                        handles.add(raw);
                    }
                }
            }
            synchronized (processor) {
                select.invoke(processor, environment);
            }
        } catch (Throwable error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null
                    ? error.getCause() : error;
            rollback(configs, processor, names);
            return "invalid Mixin config (" + cause + ")";
        }
        Set<String> targets = new LinkedHashSet<String>();
        for (Config handle : handles) {
            Set<String> found = handle.getConfig().getTargets();
            if (found != null) {
                for (String target : found) {
                    targets.add(target.replace('/', '.'));
                }
            }
        }
        GameLoader loader = NyxLaunch.gameLoader();
        List<HotRedefiner.Plan> plans = new ArrayList<HotRedefiner.Plan>();
        try {
            for (String target : targets) {
                ClassCache.bypass(target);
                if (!loader.isClassLoadedHere(target)) {
                    continue;
                }
                Class<?> type = Class.forName(target, false, loader.asClassLoader());
                byte[] original = NyxLaunch.getClassBytes(target, true);
                byte[] current = HotState.loaded(target);
                if (current == null) {
                    current = original;
                }
                byte[] next = NyxLaunch.applyMixin(target, original, false);
                HotRedefiner.Plan plan = HotRedefiner.plan(type, current, next);
                if (plan.blocker() != null) {
                    rollback(configs, processor, names);
                    return plan.blocker();
                }
                plans.add(plan);
            }
        } catch (Throwable error) {
            rollback(configs, processor, names);
            return "could not prepare the target class (" + error + ")";
        }
        if (plans.isEmpty()) {
            NyxRuntime.log("Mixin config(s) " + names + " were added; " + targets.size() + " target(s) will be patched when they load");
            return null;
        }
        Instrumentation instrumentation = HotAgent.get();
        if (instrumentation == null) {
            rollback(configs, processor, names);
            return "this Java runtime cannot reload classes (" + HotAgent.failure() + ")";
        }
        try {
            HotRedefiner.apply(plans, instrumentation);
        } catch (Throwable error) {
            NyxRuntime.log("Could not reload classes for " + names + ": " + error);
            error.printStackTrace();
            rollback(configs, processor, names);
            return "the JVM refused the change (" + error + ")";
        }
        for (HotRedefiner.Plan plan : plans) {
            HotState.record(plan.name, plan.redefinedBytes());
        }
        NyxRuntime.log("Applied Mixin config(s) " + names + " to " + plans.size() + " loaded class(es) and "
                + (targets.size() - plans.size()) + " more will be patched when they load");
        return null;
    }

    private static void rollback(List<?> configs, Object processor, List<String> names) {
        synchronized (processor) {
            for (java.util.Iterator<?> iterator = configs.iterator(); iterator.hasNext();) {
                Object config = iterator.next();
                if (config instanceof IMixinConfig && names.contains(((IMixinConfig) config).getName())) {
                    iterator.remove();
                }
            }
        }
        try {
            for (Config pending : new ArrayList<Config>(Mixins.getConfigs())) {
                if (names.contains(pending.getName())) {
                    Mixins.getConfigs().remove(pending);
                }
            }
        } catch (UnsupportedOperationException ignored) {
            return;
        }
    }
}
