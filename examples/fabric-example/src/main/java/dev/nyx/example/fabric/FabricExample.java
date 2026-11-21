package dev.nyx.example.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class FabricExample implements ClientModInitializer {

    public static final String ID = "nyx-fabric-example";

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        String version = loader.getModContainer(ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        MappingResolver mappings = loader.getMappingResolver();
        String minecraftClass = mappings.mapClassName("official", "bao");
        System.out.println("[Nyx Fabric Example] " + version + " ready; Minecraft class = " + minecraftClass
                + "; " + loader.getAllMods().size() + " mods");
    }
}
