package dev.nyx.mc171;

import dev.nyx.loader.NyxRuntime;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public final class Lang {

    private static final Map<String, String[]> TEXTS = new HashMap<String, String[]>();

    private static boolean loaded;
    private static boolean portuguese;
    private static int guiScale;

    static {
        put("mods", "Mods", "Mods");
        put("search", "Pesquisar...", "Search...");
        put("showing", "Exibindo %d mods e %d bibliotecas", "Showing %d mods and %d libraries");
        put("folder", "Abrir pasta de mods", "Open mods folder");
        put("done", "Conclu\u00eddo", "Done");
        put("site", "Site", "Website");
        put("issues", "Issues", "Issues");
        put("license", "Licen\u00e7a:", "License:");
        put("credits", "Cr\u00e9ditos:", "Credits:");
        put("authors", "Autores:", "Authors:");
        put("client", "Nyx", "Nyx");
        put("fabric", "Fabric", "Fabric");
        put("forge", "Forge", "Forge");
        put("library", "Biblioteca", "Library");
        put("empty", "Nenhum mod encontrado", "No mods found");
        put("hint", "Coloque arquivos .jar na pasta mods", "Put .jar files in the mods folder");
        put("copied", "Link copiado: ", "Link copied: ");
        put("folder_path", "Pasta de mods: ", "Mods folder: ");
        put("filter_all", "Filtro: tudo", "Filter: all");
        put("filter_mods", "Filtro: mods", "Filter: mods");
        put("filter_libs", "Filtro: bibliotecas", "Filter: libraries");
        put("by", "Por ", "By ");
        put("file", "Arquivo:", "File:");
        put("no_description", "Sem descri\u00e7\u00e3o.", "No description.");
    }

    private Lang() {
    }

    private static void put(String key, String pt, String en) {
        TEXTS.put(key, new String[] {pt, en});
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File options = new File(NyxRuntime.getGameDirectory(), "options.txt");
        if (!options.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(options), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("lang:")) {
                    portuguese = line.substring(5).trim().toLowerCase().startsWith("pt");
                } else if (line.startsWith("guiScale:")) {
                    try {
                        guiScale = Integer.parseInt(line.substring(9).trim());
                    } catch (NumberFormatException ignored) {
                        guiScale = 0;
                    }
                }
            }
        } catch (IOException error) {
            NyxRuntime.log("Could not read options.txt: " + error);
        }
    }

    public static int guiScale() {
        load();
        return guiScale;
    }

    public static String t(String key) {
        load();
        String[] pair = TEXTS.get(key);
        if (pair == null) {
            return key;
        }
        return portuguese ? pair[0] : pair[1];
    }
}
