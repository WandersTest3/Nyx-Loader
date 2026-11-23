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
        put("on", "Ligado", "On");
        put("off", "Desligado", "Off");
        put("config", "Configura\u00e7\u00f5es", "Settings");
        put("config_none", "Este mod n\u00e3o tem tela de configura\u00e7\u00e3o", "This mod has no settings screen");
        put("config_failed", "N\u00e3o foi poss\u00edvel abrir as configura\u00e7\u00f5es: ", "Could not open the settings: ");
        put("dl_open", "Baixar mods", "Get mods");
        put("dl_folder", "Pasta", "Folder");
        put("dl_title", "Baixar mods", "Get mods");
        put("dl_search", "Pesquisar...", "Search...");
        put("dl_searching", "Pesquisando...", "Searching...");
        put("dl_count", "%d de %d mods para 1.7.10", "%d of %d mods for 1.7.10");
        put("dl_error", "Erro: ", "Error: ");
        put("dl_more", "Mais resultados", "More results");
        put("dl_back", "Voltar", "Back");
        put("dl_hint", "Escolha um item na lista. S\u00f3 aparecem itens com vers\u00e3o para 1.7.10. O bot\u00e3o MR/CF troca o site.",
                "Pick an item from the list. Only items with a 1.7.10 version are shown. The MR/CF button switches the site.");
        put("dl_install", "Baixar", "Download");
        put("dl_installed", "Instalado", "Installed");
        put("dl_loaders", "Loaders:", "Loaders:");
        put("dl_filter_all", "Filtro: todos", "Filter: all");
        put("dl_filter_forge", "Filtro: Forge", "Filter: Forge");
        put("dl_filter_fabric", "Filtro: Fabric", "Filter: Fabric");
        put("dl_resolving", "Procurando vers\u00e3o...", "Finding a version...");
        put("dl_no_version", "Nenhuma vers\u00e3o para 1.7.10 com Forge ou Fabric.", "No 1.7.10 version for Forge or Fabric.");
        put("dl_dependency", "Baixando depend\u00eancia", "Downloading dependency");
        put("dl_downloading", "Baixando", "Downloading");
        put("dl_loading", "Carregando...", "Loading...");
        put("dl_already", "J\u00e1 est\u00e1 na pasta de mods.", "Already in the mods folder.");
        put("dl_loaded", "Instalado e carregado sem reiniciar.", "Installed and loaded without restarting.");
        put("dl_restart", "Instalado. Reinicie o Minecraft para carregar:", "Installed. Restart Minecraft to load:");
        put("dl_reason_forge", "mod Forge", "Forge mod");
        put("dl_reason_mixins", "usa mixins", "uses mixins");
        put("dl_reason_content", "registra conte\u00fado na inicializa\u00e7\u00e3o", "registers content at startup");
        put("dl_reason_missing", "precisa de", "needs");
        put("dl_reason_loaded", "j\u00e1 carregado", "already loaded");
        put("dl_title_packs", "Baixar texturas", "Get resource packs");
        put("dl_search_packs", "Pesquisar texturas...", "Search resource packs...");
        put("dl_count_packs", "%d de %d texturas para 1.7.10", "%d of %d resource packs for 1.7.10");
        put("dl_site_modrinth", "Site: Modrinth", "Site: Modrinth");
        put("dl_site_curseforge", "Site: CurseForge", "Site: CurseForge");
        put("dl_cf_nokey", "CurseForge precisa de uma chave de API gratuita.", "CurseForge needs a free API key.");
        put("dl_cf_hint", "Crie uma chave em console.curseforge.com, copie e toque em Colar chave. Ela fica salva em nyx/curseforge.key.",
                "Create a key at console.curseforge.com, copy it and tap Paste key. It is saved in nyx/curseforge.key.");
        put("dl_cf_paste", "Colar chave CurseForge", "Paste CurseForge key");
        put("dl_cf_empty", "A \u00e1rea de transfer\u00eancia est\u00e1 vazia.", "The clipboard is empty.");
        put("dl_cf_saved", "Chave salva.", "Key saved.");
        put("dl_pack_done", "Textura instalada. Ative na lista de resource packs.", "Resource pack installed. Enable it in the pack list.");
        put("dl_gallery", "Galeria", "Gallery");
        put("dl_image_loading", "Carregando imagem...", "Loading image...");
        put("dl_details_loading", "Carregando descri\u00e7\u00e3o...", "Loading description...");
        put("dl_reason_mixin", "mixin n\u00e3o aplic\u00e1vel ao vivo:", "mixin cannot be applied live:");
        put("update", "Atualizar", "Update");
        put("update_checking", "Procurando atualiza\u00e7\u00e3o...", "Checking for updates...");
        put("update_latest", "J\u00e1 est\u00e1 na vers\u00e3o mais recente.", "Already up to date.");
        put("update_unknown", "Este mod n\u00e3o foi encontrado no Modrinth nem no CurseForge.", "This mod was not found on Modrinth or CurseForge.");
        put("update_done", "Atualizado para %s. Reinicie o Minecraft para usar a nova vers\u00e3o.", "Updated to %s. Restart Minecraft to use the new version.");
        put("update_failed", "Falha ao atualizar: ", "Update failed: ");
        put("dl_packs", "Baixar", "Download");
        put("crash_title", "O Minecraft crashou", "Minecraft crashed");
        put("crash_saved", "O Nyx evitou que o jogo fechasse.", "Nyx kept the game from closing.");
        put("crash_chat_stay", "O jogo crashou e foi recuperado.", "The game crashed and was recovered.");
        put("crash_chat_left", "O jogo crashou e voc\u00ea saiu do mundo para evitar o fechamento.",
                "The game crashed and you left the world to keep it running.");
        put("crash_suspect", "Prov\u00e1vel culpado:", "Likely culprit:");
        put("crash_unknown", "n\u00e3o identificado", "not identified");
        put("crash_shaders_off", "Shaders desativados:", "Shaders disabled:");
        put("crash_report", "Relat\u00f3rio:", "Report:");
        put("crash_error", "Erro:", "Error:");
        put("crash_menu", "Menu principal", "Main menu");
        put("crash_copy", "Copiar relat\u00f3rio", "Copy report");
        put("crash_copied", "Relat\u00f3rio copiado", "Report copied");
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
