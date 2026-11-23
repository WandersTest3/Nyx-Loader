package dev.nyx.mc171;

import dev.nyx.loader.HotLoader;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.launch.NyxLaunch;
import dev.nyx.loader.modrinth.CurseForge;
import dev.nyx.loader.modrinth.Modrinth;
import dev.nyx.loader.modrinth.RichText;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_370;
import net.minecraft.class_388;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class NyxDownloadScreen extends class_388 {

    private static final int ROW = 36;
    private static final int MARGIN = 6;
    private static final int SEARCH_Y = 18;
    private static final int SEARCH_H = 18;
    private static final int COUNT_Y = 42;
    private static final int LIST_TOP = 54;
    private static final int BUTTON_H = 20;
    private static final int BAR_W = 6;
    private static final int BAR_HIT = 14;
    private static final int PAGE = 20;
    private static final long SEARCH_DELAY = 600L;
    private static final int KEY_ESCAPE = 1;
    private static final int KEY_BACKSPACE = 14;
    private static final Map<String, String> STATES = new java.util.concurrent.ConcurrentHashMap<String, String>();

    private static final class Detail {
        volatile List<RichText.Block> blocks;
        volatile List<String[]> gallery;
        volatile String error;
    }

    private final class_388 parent;
    private final boolean packs;
    private final Map<String, Detail> details = new java.util.concurrent.ConcurrentHashMap<String, Detail>();
    private boolean installedPack;
    private int site;
    private String tipLabel;
    private int tipX;
    private String keyMessage;
    private final StringBuilder query = new StringBuilder();
    private final List<Modrinth.Project> results = Collections.synchronizedList(new ArrayList<Modrinth.Project>());

    private volatile String listStatus;
    private volatile int total;
    private volatile boolean searching;
    private volatile int generation;
    private long searchAt = 1L;
    private int filter;
    private Modrinth.Project selected;
    private int listScroll;
    private int detailScroll;
    private boolean barDragging;
    private int barGrab;
    private int pressedRow = -1;

    private int listX;
    private int listW;
    private int listBottom;
    private int rightX;
    private int rightW;
    private int buttonY;

    public NyxDownloadScreen(class_388 parent) {
        this(parent, false);
    }

    public NyxDownloadScreen(class_388 parent, boolean packs) {
        this.parent = parent;
        this.packs = packs;
    }

    private static String key(Modrinth.Project project) {
        return project.site + ":" + project.id;
    }

    private boolean needsKey() {
        return site == 1 && !CurseForge.hasKey();
    }

    private void leave() {
        Gfx.releaseImages();
        class_388 target = parent;
        if (packs && installedPack) {
            target = NyxPackHook.refreshed(parent);
        }
        field_1229.method_2928(target);
    }

    private void layout() {
        listX = MARGIN;
        listW = (int) (field_1230 * 0.47D);
        rightX = listX + listW + 10;
        rightW = field_1230 - rightX - MARGIN;
        listBottom = field_1231 - 34;
        buttonY = field_1231 - 26;
    }

    private List<String> loaders() {
        boolean forge = NyxLaunch.isForge();
        if (filter == 1 && forge) {
            return Collections.singletonList("forge");
        }
        if (filter == 2 || filter == 1) {
            return Arrays.asList("legacy-fabric", "fabric");
        }
        return forge ? Arrays.asList("forge", "legacy-fabric", "fabric") : Arrays.asList("legacy-fabric", "fabric");
    }

    private String filterLabel() {
        if (filter == 1 && NyxLaunch.isForge()) {
            return Lang.t("dl_filter_forge");
        }
        return Lang.t(filter == 0 ? "dl_filter_all" : "dl_filter_fabric");
    }

    private void search(boolean append) {
        final int token = ++generation;
        final String text = query.toString();
        final List<String> loaders = loaders();
        final int offset = append ? results.size() : 0;
        if (!append) {
            results.clear();
            selected = null;
            listScroll = 0;
        }
        final int source = site;
        if (source == 1 && !CurseForge.hasKey()) {
            searching = false;
            total = 0;
            listStatus = Lang.t("dl_cf_nokey");
            return;
        }
        searching = true;
        listStatus = Lang.t("dl_searching");
        Thread thread = new Thread(() -> {
            try {
                Modrinth.Page page = source == 1 ? CurseForge.search(text, loaders, offset, PAGE, packs)
                        : Modrinth.search(text, loaders, offset, PAGE, packs);
                if (token != generation) {
                    return;
                }
                results.addAll(page.projects);
                total = page.total;
                listStatus = null;
            } catch (Throwable error) {
                if (token == generation) {
                    listStatus = Lang.t("dl_error") + error.getMessage();
                }
                NyxRuntime.log("Search failed: " + error);
            } finally {
                if (token == generation) {
                    searching = false;
                }
            }
        }, "Nyx-Modrinth");
        thread.setDaemon(true);
        thread.start();
    }

    public void method_1025(int mouseX, int mouseY, float partialTicks) {
        layout();
        tipLabel = null;
        if (searchAt > 0L && System.currentTimeMillis() >= searchAt) {
            searchAt = 0L;
            search(false);
        }
        class_370 font = field_1229.field_3814;
        Gfx.gradient(0, 0, field_1230, field_1231, 0xFF2B0F3F, 0xFF0B0B10);
        String title = Lang.t(packs ? "dl_title_packs" : "dl_title");
        text(font, title, listX + (listW - font.method_954(title)) / 2, 6, 0xFFFFFFFF);
        drawSearch(font, mouseX, mouseY);
        String count = listStatus != null ? listStatus : String.format(Lang.t(packs ? "dl_count_packs" : "dl_count"), results.size(), total);
        text(font, font.method_955(count, listW), listX + 2, COUNT_Y, 0xFFB8B8B8);
        drawList(font, mouseX, mouseY);
        drawDetails(font, mouseX, mouseY);
        button(font, Lang.t("dl_back"), listX, buttonY, listW, true, mouseX, mouseY);
        if (needsKey()) {
            button(font, Lang.t("dl_cf_paste"), rightX, buttonY, rightW, true, mouseX, mouseY);
        }
        drawTip(font);
    }

    private void drawTip(class_370 font) {
        if (tipLabel == null) {
            return;
        }
        int width = font.method_954(tipLabel);
        int x = Math.min(tipX, field_1230 - width - 10);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        Gfx.rect(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xF0100010);
        Gfx.border(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xFF57197F);
        text(font, tipLabel, x + 3, SEARCH_Y + SEARCH_H + 4, 0xFFFFFFFF);
        tipLabel = null;
    }

    private void text(class_370 font, String value, int x, int y, int color) {
        font.method_956(value, x, y, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void button(class_370 font, String label, int x, int y, int width, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && in(mouseX, mouseY, x, y, width, BUTTON_H);
        Gfx.button(x, y, width, BUTTON_H, hover, enabled);
        String shown = font.method_955(label, width - 6);
        text(font, shown, x + (width - font.method_954(shown)) / 2, y + 6, enabled ? 0xFFFFFFFF : 0xFF707070);
    }

    private void drawSearch(class_370 font, int mouseX, int mouseY) {
        int searchW = searchWidth();
        Gfx.rect(listX, SEARCH_Y, searchW, SEARCH_H, 0xFFFFFFFF);
        Gfx.rect(listX + 1, SEARCH_Y + 1, searchW - 2, SEARCH_H - 2, 0xFF000000);
        String shown = query.toString();
        int textY = SEARCH_Y + (SEARCH_H - 8) / 2;
        if (shown.isEmpty()) {
            text(font, Lang.t(packs ? "dl_search_packs" : "dl_search"), listX + 5, textY, 0xFF707070);
        } else {
            text(font, font.method_955(shown, searchW - 14), listX + 5, textY, 0xFFFFFFFF);
        }
        if (System.currentTimeMillis() / 500L % 2L == 0L) {
            int caretX = listX + 5 + Math.min(font.method_954(shown), searchW - 14);
            Gfx.rect(caretX, textY + 8, 5, 1, 0xFFFFFFFF);
        }
        int siteX = siteButtonX();
        boolean siteHover = in(mouseX, mouseY, siteX, SEARCH_Y, SEARCH_H, SEARCH_H);
        Gfx.button(siteX, SEARCH_Y, SEARCH_H, SEARCH_H, siteHover, true);
        String mark = site == 1 ? "CF" : "MR";
        text(font, mark, siteX + (SEARCH_H - font.method_954(mark)) / 2, SEARCH_Y + 5, site == 1 ? 0xFFFF9A55 : 0xFF55DD88);
        if (siteHover) {
            tipLabel = Lang.t(site == 1 ? "dl_site_curseforge" : "dl_site_modrinth");
            tipX = siteX;
        }
        if (!packs) {
            int filterX = filterButtonX();
            boolean hover = in(mouseX, mouseY, filterX, SEARCH_Y, SEARCH_H, SEARCH_H);
            Gfx.button(filterX, SEARCH_Y, SEARCH_H, SEARCH_H, hover, true);
            int cx = filterX + SEARCH_H / 2;
            int top = SEARCH_Y + 4;
            Gfx.rect(cx - 5, top, 10, 2, 0xFFFFFFFF);
            Gfx.rect(cx - 4, top + 2, 8, 2, 0xFFFFFFFF);
            Gfx.rect(cx - 3, top + 4, 6, 2, 0xFFFFFFFF);
            Gfx.rect(cx - 1, top + 6, 2, 4, 0xFFFFFFFF);
            if (filter != 0) {
                Gfx.rect(filterX + SEARCH_H - 6, SEARCH_Y + 2, 4, 4, 0xFF55FF55);
            }
            if (hover) {
                tipLabel = filterLabel();
                tipX = filterX;
            }
        }
    }

    private int searchWidth() {
        return listW - (packs ? 1 : 2) * (SEARCH_H + 4);
    }

    private int siteButtonX() {
        return listX + searchWidth() + 4;
    }

    private int filterButtonX() {
        return siteButtonX() + SEARCH_H + 4;
    }

    private boolean hasMore() {
        return !searching && listStatus == null && results.size() < total;
    }

    private int rowCount() {
        return results.size() + (hasMore() ? 1 : 0);
    }

    private int maxListScroll() {
        return Math.max(0, rowCount() * ROW - (listBottom - LIST_TOP));
    }

    private void drawList(class_370 font, int mouseX, int mouseY) {
        int viewH = listBottom - LIST_TOP;
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        Gfx.rect(listX, LIST_TOP, listW, viewH, 0x66000000);
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, listX, LIST_TOP, listW, viewH);
        List<Modrinth.Project> snapshot;
        synchronized (results) {
            snapshot = new ArrayList<Modrinth.Project>(results);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            int y = LIST_TOP + i * ROW - listScroll;
            if (y + ROW < LIST_TOP || y > listBottom) {
                continue;
            }
            drawRow(font, snapshot.get(i), y, mouseX, mouseY);
        }
        if (hasMore()) {
            int y = LIST_TOP + snapshot.size() * ROW - listScroll;
            boolean hover = in(mouseX, mouseY, listX, Math.max(y, LIST_TOP), listW - BAR_HIT, ROW);
            Gfx.rect(listX + 4, y + 8, listW - 16, BUTTON_H, hover ? 0xFF57197F : 0xFF3A1752);
            String more = Lang.t("dl_more");
            text(font, more, listX + (listW - font.method_954(more)) / 2, y + 14, 0xFFFFFFFF);
        }
        Gfx.noScissor();
        if (maxListScroll() > 0) {
            int trackX = listX + listW - BAR_W;
            boolean active = barDragging || overBar(mouseX, mouseY);
            Gfx.rect(trackX, LIST_TOP, BAR_W, viewH, 0xFF000000);
            Gfx.rect(trackX, thumbY(), BAR_W, thumbHeight(), active ? 0xFF57197F : 0xFF808080);
            Gfx.rect(trackX, thumbY(), BAR_W - 1, thumbHeight() - 1, active ? 0xFF8A3FBF : 0xFFC0C0C0);
        }
    }

    private void drawRow(class_370 font, Modrinth.Project project, int y, int mouseX, int mouseY) {
        boolean isSelected = project == selected;
        boolean hover = in(mouseX, mouseY, listX, Math.max(y, LIST_TOP), listW, Math.min(y + ROW, listBottom) - Math.max(y, LIST_TOP));
        if (isSelected) {
            Gfx.rect(listX, y, listW, ROW, 0xFFFFFFFF);
            Gfx.rect(listX + 1, y + 1, listW - 2, ROW - 2, 0xFF000000);
        } else if (hover) {
            Gfx.rect(listX, y, listW, ROW, 0x33FFFFFF);
        }
        Gfx.texture(Gfx.remoteIcon(project.iconUrl), listX + 3, y + 2, 32);
        int textX = listX + 40;
        int maxW = listW - 46;
        text(font, font.method_955(project.title, maxW), textX, y + 3, 0xFFFFFFFF);
        String info = (project.author == null ? "" : project.author + " · ") + downloads(project.downloads);
        text(font, font.method_955(info, maxW), textX, y + 13, 0xFF9A9A9A);
        String state = STATES.get(key(project));
        String line = state != null ? state : project.description == null ? "" : project.description;
        text(font, font.method_955(line, maxW), textX, y + 23, state != null ? 0xFF8AE68A : 0xFFA0A0A0);
    }

    private static String downloads(long count) {
        if (count >= 1000000L) {
            return String.format(Locale.ROOT, "%.1fM", count / 1000000.0D) + " ↓";
        }
        if (count >= 1000L) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0D) + " ↓";
        }
        return count + " ↓";
    }

    private int thumbHeight() {
        int view = listBottom - LIST_TOP;
        return Math.min(view, Math.max(16, view * view / Math.max(1, rowCount() * ROW)));
    }

    private int thumbY() {
        int max = maxListScroll();
        return LIST_TOP + (max <= 0 ? 0 : (listBottom - LIST_TOP - thumbHeight()) * listScroll / max);
    }

    private boolean overBar(int mouseX, int mouseY) {
        return maxListScroll() > 0 && in(mouseX, mouseY, listX + listW - BAR_HIT, LIST_TOP, BAR_HIT, listBottom - LIST_TOP);
    }

    private void dragBar(int mouseY) {
        int range = listBottom - LIST_TOP - thumbHeight();
        if (range > 0) {
            listScroll = Math.max(0, Math.min(maxListScroll(), (mouseY - barGrab - LIST_TOP) * maxListScroll() / range));
        }
    }

    private int installY() {
        return SEARCH_Y + 38;
    }

    private void drawDetails(class_370 font, int mouseX, int mouseY) {
        Modrinth.Project project = selected;
        if (project == null) {
            String hint = Lang.t(needsKey() ? "dl_cf_hint" : "dl_hint");
            List<?> lines = font.method_971(hint, rightW - 6);
            int y = LIST_TOP;
            for (Object line : lines) {
                text(font, String.valueOf(line), rightX + 3, y, 0xFF9A9A9A);
                y += 10;
            }
            return;
        }
        Gfx.texture(Gfx.remoteIcon(project.iconUrl), rightX, SEARCH_Y, 32);
        int textX = rightX + 36;
        text(font, font.method_955(project.title, rightW - 40), textX, SEARCH_Y + 1, 0xFFFFFFFF);
        if (project.author != null) {
            text(font, font.method_955(Lang.t("by") + project.author, rightW - 40), textX, SEARCH_Y + 12, 0xFF9A9A9A);
        }
        text(font, downloads(project.downloads), textX, SEARCH_Y + 23, 0xFF9A9A9A);
        String state = STATES.get(key(project));
        boolean busy = state != null && state.endsWith("...");
        boolean done = state != null && STATES.containsKey(key(project) + ":done");
        button(font, done ? Lang.t("dl_installed") : busy ? state : Lang.t("dl_install"), rightX, installY(), rightW,
                !busy && !done, mouseX, mouseY);
        int top = installY() + BUTTON_H + 4;
        Gfx.rect(rightX, top - 2, rightW, 1, 0xFF4A4A4A);
        int viewH = listBottom - top;
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, rightX, top, rightW, viewH);
        int y = top + 2 - detailScroll;
        int start = y;
        if (state != null && !busy) {
            for (Object line : font.method_971(state, rightW - 6)) {
                text(font, String.valueOf(line), rightX + 3, y, 0xFF8AE68A);
                y += 10;
            }
            y += 4;
        }
        StringBuilder tags = new StringBuilder();
        for (String loader : project.loaders) {
            tags.append(tags.length() == 0 ? "" : ", ").append(loaderName(loader));
        }
        if (tags.length() > 0) {
            text(font, Lang.t("dl_loaders") + " " + tags, rightX + 3, y, 0xFFB8B8B8);
            y += 14;
        }
        if (project.description != null) {
            for (Object line : font.method_971(project.description, rightW - 6)) {
                text(font, String.valueOf(line), rightX + 3, y, 0xFFFFFFFF);
                y += 10;
            }
        }
        y += 6;
        y = drawRich(font, project, y, top, listBottom);
        text(font, (project.site.equals("curseforge") ? "curseforge.com/minecraft/" + (packs ? "texture-packs/" : "mc-mods/")
                : "modrinth.com/" + (packs ? "resourcepack/" : "mod/")) + project.slug, rightX + 3, y, 0xFF707070);
        y += 12;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, y - start - viewH)));
        Gfx.noScissor();
    }

    private int drawRich(class_370 font, Modrinth.Project project, int y, int viewTop, int viewBottom) {
        Detail detail = details.get(key(project));
        if (detail == null) {
            return y;
        }
        if (detail.blocks == null && detail.error == null) {
            text(font, Lang.t("dl_details_loading"), rightX + 3, y, 0xFF9A9A9A);
            return y + 14;
        }
        if (detail.error != null) {
            text(font, font.method_955(Lang.t("dl_error") + detail.error, rightW - 6), rightX + 3, y, 0xFFFF7777);
            return y + 14;
        }
        int width = rightW - 6;
        for (RichText.Block block : detail.blocks) {
            if (block.image != null) {
                y = drawImage(font, block.image, null, y, viewTop, viewBottom, width);
            } else {
                for (Object line : font.method_971(block.text, width)) {
                    if (y + 10 >= viewTop && y <= viewBottom) {
                        text(font, String.valueOf(line), rightX + 3, y, 0xFFE0E0E0);
                    }
                    y += 10;
                }
                y += 4;
            }
        }
        List<String[]> gallery = detail.gallery;
        if (gallery != null && !gallery.isEmpty()) {
            y += 4;
            text(font, Lang.t("dl_gallery"), rightX + 3, y, 0xFFB8B8B8);
            y += 12;
            for (String[] item : gallery) {
                y = drawImage(font, item[0], item[1], y, viewTop, viewBottom, width);
            }
        }
        return y + 4;
    }

    private int drawImage(class_370 font, String url, String caption, int y, int viewTop, int viewBottom, int width) {
        int estimate = 60;
        if (y > viewBottom + 300 || y < viewTop - 600) {
            return y + estimate + 4;
        }
        int[] image = Gfx.remoteImage(url);
        if (image == null) {
            text(font, Lang.t("dl_image_loading"), rightX + 3, y, 0xFF707070);
            return y + 14;
        }
        if (image[0] == 0) {
            return y;
        }
        int w = Math.min(width, image[1]);
        int h = Math.max(1, image[2] * w / image[1]);
        if (h > 180) {
            w = Math.max(1, w * 180 / h);
            h = 180;
        }
        if (y + h >= viewTop && y <= viewBottom) {
            Gfx.textureRect(image[0], rightX + 3, y, w, h);
        }
        y += h + 2;
        if (caption != null && !caption.isEmpty()) {
            if (y + 10 >= viewTop && y <= viewBottom) {
                text(font, font.method_955(caption, width), rightX + 3, y, 0xFF9A9A9A);
            }
            y += 10;
        }
        return y + 4;
    }

    private void loadDetails(Modrinth.Project project) {
        final String id = key(project);
        if (details.containsKey(id)) {
            return;
        }
        final Detail detail = new Detail();
        details.put(id, detail);
        Thread thread = new Thread(() -> {
            try {
                Modrinth.Details loaded = project.site.equals("curseforge") ? CurseForge.details(project.id)
                        : Modrinth.details(project.id);
                detail.gallery = loaded.gallery;
                detail.blocks = RichText.parse(loaded.body);
            } catch (Throwable error) {
                NyxRuntime.log("Details of " + project.slug + " failed: " + error);
                detail.error = String.valueOf(error.getMessage());
            }
        }, "Nyx-Details");
        thread.setDaemon(true);
        thread.start();
    }

    private static String loaderName(String loader) {
        if ("forge".equals(loader)) {
            return "Forge";
        }
        if ("legacy-fabric".equals(loader)) {
            return "Legacy Fabric";
        }
        return "Fabric";
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    protected void method_1024(char character, int keyCode) {
        if (keyCode == KEY_ESCAPE) {
            leave();
        } else if (keyCode == KEY_BACKSPACE) {
            if (query.length() > 0) {
                query.deleteCharAt(query.length() - 1);
                searchAt = System.currentTimeMillis() + SEARCH_DELAY;
            }
        } else if (character >= ' ' && character != 127 && query.length() < 48) {
            query.append(character);
            searchAt = System.currentTimeMillis() + SEARCH_DELAY;
        }
    }

    protected void method_1026(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        layout();
        if (in(mouseX, mouseY, siteButtonX(), SEARCH_Y, SEARCH_H, SEARCH_H)) {
            site = 1 - site;
            search(false);
            return;
        }
        if (!packs && in(mouseX, mouseY, filterButtonX(), SEARCH_Y, SEARCH_H, SEARCH_H)) {
            filter = NyxLaunch.isForge() ? (filter + 1) % 3 : filter == 0 ? 2 : 0;
            search(false);
            return;
        }
        if (in(mouseX, mouseY, listX, buttonY, listW, BUTTON_H)) {
            leave();
            return;
        }
        if (needsKey() && in(mouseX, mouseY, rightX, buttonY, rightW, BUTTON_H)) {
            pasteKey();
            return;
        }
        Modrinth.Project project = selected;
        if (project != null && in(mouseX, mouseY, rightX, installY(), rightW, BUTTON_H)) {
            String state = STATES.get(key(project));
            if (state == null || !state.endsWith("...") && !STATES.containsKey(key(project) + ":done")) {
                install(project);
            }
            return;
        }
        if (overBar(mouseX, mouseY)) {
            int thumb = thumbHeight();
            int top = thumbY();
            barGrab = mouseY >= top && mouseY < top + thumb ? mouseY - top : thumb / 2;
            barDragging = true;
            dragBar(mouseY);
            return;
        }
        pressedRow = -1;
        if (in(mouseX, mouseY, listX, LIST_TOP, listW, listBottom - LIST_TOP)) {
            int row = (mouseY - LIST_TOP + listScroll) / ROW;
            if (row >= 0 && row < rowCount()) {
                pressedRow = row;
            }
        }
    }

    protected void method_4259(int mouseX, int mouseY, int button, long time) {
        if (barDragging) {
            dragBar(mouseY);
        }
    }

    protected void method_1032(int mouseX, int mouseY, int button) {
        if (button == 0 && pressedRow >= 0 && !barDragging) {
            int row = (mouseY - LIST_TOP + listScroll) / ROW;
            if (row == pressedRow) {
                if (row < results.size()) {
                    selected = results.get(row);
                    detailScroll = 0;
                    loadDetails(selected);
                } else if (hasMore()) {
                    search(true);
                }
            }
        }
        pressedRow = -1;
        barDragging = false;
    }

    public void method_1035() {
        super.method_1035();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int step = wheel > 0 ? -ROW / 2 : ROW / 2;
        int mouseX = Mouse.getEventX() * field_1230 / field_1229.field_3801;
        if (mouseX >= rightX) {
            detailScroll = Math.max(0, detailScroll + step);
        } else {
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + step));
        }
    }

    private void pasteKey() {
        try {
            String clip = org.lwjgl.Sys.getClipboard();
            if (clip == null || clip.trim().isEmpty()) {
                listStatus = Lang.t("dl_cf_empty");
                return;
            }
            CurseForge.setKey(clip.trim());
            listStatus = Lang.t("dl_cf_saved");
            search(false);
        } catch (Throwable error) {
            listStatus = Lang.t("dl_error") + error.getMessage();
        }
    }

    private static void state(Modrinth.Project project, String text) {
        synchronized (STATES) {
            STATES.put(key(project), text);
        }
    }

    private Modrinth.Version best(Modrinth.Project project, String id, List<String> loaders) throws java.io.IOException {
        return project.site.equals("curseforge") ? CurseForge.bestVersion(id, loaders, packs)
                : Modrinth.bestVersion(id, loaders, packs);
    }

    private void install(Modrinth.Project project) {
        final List<String> loaders = new ArrayList<String>(loaders());
        loaders.retainAll(project.loaders.isEmpty() ? loaders : project.loaders);
        if (loaders.isEmpty()) {
            loaders.addAll(loaders());
        }
        final boolean curse = project.site.equals("curseforge");
        state(project, Lang.t("dl_resolving"));
        Thread thread = new Thread(() -> {
            try {
                Modrinth.Version version = best(project, project.id, loaders);
                if (version == null) {
                    state(project, Lang.t("dl_no_version"));
                    return;
                }
                File target = packs ? new File(NyxRuntime.getGameDirectory(), "resourcepacks") : NyxRuntime.getModsDirectory();
                List<File> downloaded = new ArrayList<File>();
                if (!packs) {
                    for (String[] dependency : version.requiredDependencies) {
                        Modrinth.Version required = curse ? best(project, dependency[0], loaders)
                                : dependency[1] != null ? Modrinth.versionById(dependency[1])
                                : dependency[0] != null ? Modrinth.bestVersion(dependency[0], loaders) : null;
                        if (required == null || new File(target, required.file.filename).isFile()) {
                            continue;
                        }
                        state(project, Lang.t("dl_dependency") + " " + required.file.filename + "...");
                        downloaded.add(Modrinth.download(required.file, target, null));
                    }
                }
                if (new File(target, version.file.filename).isFile()) {
                    state(project, Lang.t("dl_already"));
                    STATES.put(key(project) + ":done", "");
                    return;
                }
                downloaded.add(Modrinth.download(version.file, target, (done, total) -> {
                    int percent = total > 0 ? (int) (done * 100L / total) : 0;
                    state(project, Lang.t("dl_downloading") + " " + percent + "%...");
                }));
                if (packs) {
                    installedPack = true;
                    STATES.put(key(project) + ":done", "");
                    state(project, Lang.t("dl_pack_done"));
                    return;
                }
                state(project, Lang.t("dl_loading"));
                NyxRuntime.runOnRenderThread(() -> finish(project, downloaded));
            } catch (Throwable error) {
                NyxRuntime.log("Install of " + project.slug + " failed: " + error);
                state(project, Lang.t("dl_error") + error.getMessage());
            }
        }, "Nyx-Install");
        thread.setDaemon(true);
        thread.start();
    }

    private static void finish(Modrinth.Project project, List<File> files) {
        List<String> restart = new ArrayList<String>();
        for (File file : files) {
            HotLoader.Result result = HotLoader.load(file);
            if (!result.loaded) {
                restart.add(file.getName() + " (" + reason(result.reason) + ")");
            }
        }
        STATES.put(key(project) + ":done", "");
        if (restart.isEmpty()) {
            state(project, Lang.t("dl_loaded"));
        } else {
            state(project, Lang.t("dl_restart") + " " + String.join(", ", restart));
        }
    }

    private static String reason(String code) {
        if (code == null) {
            return "?";
        }
        if (code.equals("forge")) {
            return Lang.t("dl_reason_forge");
        }
        if (code.startsWith("mixin: ")) {
            return Lang.t("dl_reason_mixin") + " " + code.substring(7);
        }
        if (code.equals("mixins") || code.equals("access widener")) {
            return Lang.t("dl_reason_mixins");
        }
        if (code.startsWith("entrypoint")) {
            return Lang.t("dl_reason_content");
        }
        if (code.startsWith("missing ")) {
            return Lang.t("dl_reason_missing") + " " + code.substring(8);
        }
        if (code.equals("already loaded")) {
            return Lang.t("dl_reason_loaded");
        }
        return code;
    }
}
