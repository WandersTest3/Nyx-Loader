package net.fabricmc.loader.api;

import dev.nyx.loader.fabric.DefaultLanguageAdapter;

public interface LanguageAdapter {

    static LanguageAdapter getDefault() {
        return DefaultLanguageAdapter.INSTANCE;
    }

    <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException;
}
