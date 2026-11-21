package dev.nyx.loader.mixin;

import dev.nyx.loader.launch.NyxLaunch;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

public class MixinServiceNyx implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider,
        IClassTracker {

    private final ReEntranceLock lock = new ReEntranceLock(1);

    @Override
    public String getName() {
        return "Nyx";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory) {
            IMixinTransformer transformer = ((IMixinTransformerFactory) internal).createTransformer();
            NyxLaunch.setMixinTransformer(transformer);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void beginPhase() {
    }

    @Override
    public void checkEnv(Object bootSource) {
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return (required, text) -> "This mod needs a newer Mixin (compatibility " + text + ") than Nyx provides";
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual("nyx");
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return Collections.emptyList();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return NyxLaunch.gameClassLoader().getResourceAsStream(name);
    }

    @Override
    public String getSideName() {
        return "CLIENT";
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_22;
    }

    @Override
    public ILogger getLogger(String name) {
        return new LoggerAdapterConsole(name);
    }

    @Override
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return NyxLaunch.gameClassLoader().loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, NyxLaunch.gameClassLoader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, MixinServiceNyx.class.getClassLoader());
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        byte[] bytes = NyxLaunch.getClassBytes(name.replace('/', '.'), runTransformers);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, readerFlags);
        return node;
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return Collections.emptyList();
    }

    @Override
    public void addTransformerExclusion(String name) {
    }

    @Override
    public void registerInvalidClass(String className) {
    }

    @Override
    public boolean isClassLoaded(String className) {
        return NyxLaunch.nyxClassLoader().isClassLoadedHere(className);
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }
}
