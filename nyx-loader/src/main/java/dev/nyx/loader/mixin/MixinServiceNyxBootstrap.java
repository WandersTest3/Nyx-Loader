package dev.nyx.loader.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public class MixinServiceNyxBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "Nyx";
    }

    @Override
    public String getServiceClassName() {
        return MixinServiceNyx.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
