package dev.nyx.mc171;

import dev.nyx.loader.HotLoader;
import dev.nyx.loader.NyxRuntime;
import dev.nyx.loader.launch.NyxLaunch;
import dev.nyx.loader.modrinth.Modrinth;
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

    private final class_388 parent;
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
        this.parent = parent;
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
        searching = true;
        listStatus = Lang.t("dl_searching");
        Thread thread = new Thread(() -> {
            try {
                Modrinth.Page page = Modrinth.search(text, loaders, offset, PAGE);
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
                NyxRuntime.log("Modrinth search failed: " + error);
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
        if (searchAt > 0L && System.currentTimeMillis() >= searchAt) {
            searchAt = 0L;
            search(false);
        }
        class_370 font = field_1229.field_3814;
        Gfx.gradient(0, 0, field_1230, field_1231, 0xFF2B0F3F, 0xFF0B0B10);
        String title = Lang.t("dl_title");
        text(font, title, listX + (listW - font.method_954(title)) / 2, 6, 0xFFFFFFFF);
        drawSearch(font, mouseX, mouseY);
        String count = listStatus != null ? listStatus : String.format(Lang.t("dl_count"), results.size(), total);
        text(font, font.method_955(count, listW), listX + 2, COUNT_Y, 0xFFB8B8B8);
        drawList(font, mouseX, mouseY);
        drawDetails(font, mouseX, mouseY);
        button(font, Lang.t("dl_back"), listX, buttonY, listW, true, mouseX, mouseY);
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
        int searchW = listW - SEARCH_H - 4;
        Gfx.rect(listX, SEARCH_Y, searchW, SEARCH_H, 0xFFFFFFFF);
        Gfx.rect(listX + 1, SEARCH_Y + 1, searchW - 2, SEARCH_H - 2, 0xFF000000);
        String shown = query.toString();
        int textY = SEARCH_Y + (SEARCH_H - 8) / 2;
        if (shown.isEmpty()) {
            text(font, Lang.t("dl_search"), listX + 5, textY, 0xFF707070);
        } else {
            text(font, font.method_955(shown, searchW - 14), listX + 5, textY, 0xFFFFFFFF);
        }
        if (System.currentTimeMillis() / 500L % 2L == 0L) {
            int caretX = listX + 5 + Math.min(font.method_954(shown), searchW - 14);
            Gfx.rect(caretX, textY + 8, 5, 1, 0xFFFFFFFF);
        }
        int filterX = listX + searchW + 4;
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
            String label = filterLabel();
            int width = font.method_954(label);
            int x = Math.min(filterX, field_1230 - width - 10);
            Gfx.rect(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xF0100010);
            Gfx.border(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xFF57197F);
            text(font, label, x + 3, SEARCH_Y + SEARCH_H + 4, 0xFFFFFFFF);
        }
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
        String state = STATES.get(project.id);
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
            String hint = Lang.t("dl_hint");
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
        String state = STATES.get(project.id);
        boolean busy = state != null && state.endsWith("...");
        boolean done = state != null && STATES.containsKey(project.id + ":done");
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
        text(font, "modrinth.com/mod/" + project.slug, rightX + 3, y, 0xFF707070);
        y += 12;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, y - start - viewH)));
        Gfx.noScissor();
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
            field_1229.method_2928(parent);
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
        int searchW = listW - SEARCH_H - 4;
        if (in(mouseX, mouseY, listX + searchW + 4, SEARCH_Y, SEARCH_H, SEARCH_H)) {
            filter = NyxLaunch.isForge() ? (filter + 1) % 3 : filter == 0 ? 2 : 0;
            search(false);
            return;
        }
        if (in(mouseX, mouseY, listX, buttonY, listW, BUTTON_H)) {
            field_1229.method_2928(parent);
            return;
        }
        Modrinth.Project project = selected;
        if (project != null && in(mouseX, mouseY, rightX, installY(), rightW, BUTTON_H)) {
            String state = STATES.get(project.id);
            if (state == null || !state.endsWith("...") && !STATES.containsKey(project.id + ":done")) {
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

    private static void state(Modrinth.Project project, String text) {
        synchronized (STATES) {
            STATES.put(project.id, text);
        }
    }

    private void install(Modrinth.Project project) {
        final List<String> loaders = new ArrayList<String>(loaders());
        loaders.retainAll(project.loaders.isEmpty() ? loaders : project.loaders);
        if (loaders.isEmpty()) {
            loaders.addAll(loaders());
        }
        state(project, Lang.t("dl_resolving"));
        Thread thread = new Thread(() -> {
            try {
                Modrinth.Version version = Modrinth.bestVersion(project.id, loaders);
                if (version == null) {
                    state(project, Lang.t("dl_no_version"));
                    return;
                }
                File mods = NyxRuntime.getModsDirectory();
                List<File> downloaded = new ArrayList<File>();
                for (String[] dependency : version.requiredDependencies) {
                    Modrinth.Version required = dependency[1] != null ? Modrinth.versionById(dependency[1])
                            : dependency[0] != null ? Modrinth.bestVersion(dependency[0], loaders) : null;
                    if (required == null || new File(mods, required.file.filename).isFile()) {
                        continue;
                    }
                    state(project, Lang.t("dl_dependency") + " " + required.file.filename + "...");
                    downloaded.add(Modrinth.download(required.file, mods, null));
                }
                if (new File(mods, version.file.filename).isFile()) {
                    state(project, Lang.t("dl_already"));
                    STATES.put(project.id + ":done", "");
                    return;
                }
                downloaded.add(Modrinth.download(version.file, mods, (done, total) -> {
                    int percent = total > 0 ? (int) (done * 100L / total) : 0;
                    state(project, Lang.t("dl_downloading") + " " + percent + "%...");
                }));
                state(project, Lang.t("dl_loading"));
                NyxRuntime.runOnRenderThread(() -> finish(project, downloaded));
            } catch (Throwable error) {
                NyxRuntime.log("Modrinth install of " + project.slug + " failed: " + error);
                state(project, Lang.t("dl_error") + error.getMessage());
            }
        }, "Nyx-Modrinth-Install");
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
        STATES.put(project.id + ":done", "");
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
