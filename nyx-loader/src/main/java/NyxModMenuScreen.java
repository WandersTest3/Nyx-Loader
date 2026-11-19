import dev.nyx.loader.ModContainer;
import dev.nyx.loader.NyxRuntime;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import dev.nyx.mc171.Gfx;
import dev.nyx.mc171.Lang;

public class NyxModMenuScreen extends bdw {

    private static final int ROW = 36;
    private static final int MARGIN = 6;
    private static final int TOP_MARGIN = 6;
    private static final int SEARCH_Y = 18;
    private static final int SEARCH_H = 18;
    private static final int COUNT_Y = 42;
    private static final int LIST_TOP = 54;
    private static final int BUTTON_H = 20;
    private static final int KEY_ESCAPE = 1;
    private static final int KEY_BACKSPACE = 14;
    private static final int KEY_UP = 200;
    private static final int KEY_DOWN = 208;

    private final bdw parent;
    private final List<ModContainer> all = new ArrayList<ModContainer>();
    private final List<ModContainer> visible = new ArrayList<ModContainer>();
    private final StringBuilder query = new StringBuilder();

    private ModContainer selected;
    private int filter;
    private int listScroll;
    private int detailScroll;
    private int detailHeight;
    private int pressedRow = -1;
    private int pressY;
    private int lastDragY;
    private boolean dragging;
    private String toast;
    private long toastUntil;

    private int listX;
    private int listW;
    private int listBottom;
    private int rightX;
    private int rightW;
    private int buttonY;

    public NyxModMenuScreen(bdw parent) {
        this.parent = parent;
        all.addAll(NyxRuntime.getMods());
        all.addAll(NyxRuntime.getBuiltinEntries());
        refilter();
        if (!visible.isEmpty()) {
            selected = visible.get(0);
        }
    }

    public void b() {
        super.b();
        layout();
    }

    private void layout() {
        listX = MARGIN;
        listW = (int) (l * 0.47D);
        rightX = listX + listW + 10;
        rightW = l - rightX - MARGIN;
        listBottom = m - 34;
        buttonY = m - 26;
    }

    private void refilter() {
        visible.clear();
        String needle = query.toString().trim().toLowerCase();
        for (ModContainer mod : all) {
            if (filter == 1 && mod.library || filter == 2 && !mod.library) {
                continue;
            }
            if (needle.length() > 0 && !matches(mod, needle)) {
                continue;
            }
            visible.add(mod);
        }
        if (selected != null && !visible.contains(selected)) {
            selected = visible.isEmpty() ? null : visible.get(0);
        }
        listScroll = 0;
    }

    private static boolean matches(ModContainer mod, String needle) {
        return contains(mod.name, needle) || contains(mod.id, needle) || contains(mod.description, needle)
                || contains(mod.authors, needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle);
    }

    private int count(boolean libraries) {
        int total = 0;
        for (ModContainer mod : visible) {
            if (mod.library == libraries) {
                total++;
            }
        }
        return total;
    }

    private void showToast(String message) {
        toast = message;
        toastUntil = System.currentTimeMillis() + 3500L;
    }

    private void close() {
        k.a(parent);
    }

    public void a(int mouseX, int mouseY, float partialTicks) {
        layout();
        bbu font = k.l;
        Gfx.gradient(0, 0, l, m, 0xFF2B0F3F, 0xFF0B0B10);
        drawTitle(font);
        drawSearch(font, mouseX, mouseY);
        String showing = String.format(Lang.t("showing"), count(false), count(true));
        text(font, showing, listX + 2, COUNT_Y, 0xFFB8B8B8);
        drawList(font, mouseX, mouseY);
        drawDetails(font, mouseX, mouseY);
        drawButtons(font, mouseX, mouseY);
        drawToast(font);
    }

    private void text(bbu font, String value, int x, int y, int color) {
        font.a(value, x, y, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTitle(bbu font) {
        String title = Lang.t("mods");
        text(font, title, listX + (listW - font.a(title)) / 2, TOP_MARGIN, 0xFFFFFFFF);
    }

    private void drawSearch(bbu font, int mouseX, int mouseY) {
        int searchW = listW - SEARCH_H - 4;
        Gfx.rect(listX, SEARCH_Y, searchW, SEARCH_H, 0xFFFFFFFF);
        Gfx.rect(listX + 1, SEARCH_Y + 1, searchW - 2, SEARCH_H - 2, 0xFF000000);
        String shown = query.toString();
        int textY = SEARCH_Y + (SEARCH_H - 8) / 2;
        if (shown.length() == 0) {
            text(font, Lang.t("search"), listX + 5, textY, 0xFF707070);
        } else {
            text(font, font.a(shown, searchW - 14), listX + 5, textY, 0xFFFFFFFF);
        }
        if (System.currentTimeMillis() / 500L % 2L == 0L) {
            int caretX = listX + 5 + Math.min(font.a(shown), searchW - 14);
            Gfx.rect(caretX, textY - 1, 5, 1, 0xFFFFFFFF);
            Gfx.rect(caretX, textY + 8, 5, 1, 0xFFFFFFFF);
        }
        int filterX = listX + searchW + 4;
        boolean hover = mouseX >= filterX && mouseX < filterX + SEARCH_H && mouseY >= SEARCH_Y
                && mouseY < SEARCH_Y + SEARCH_H;
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
            String label = Lang.t(filter == 0 ? "filter_all" : filter == 1 ? "filter_mods" : "filter_libs");
            int width = font.a(label);
            int x = Math.min(filterX, l - width - 10);
            Gfx.rect(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xF0100010);
            Gfx.border(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xFF57197F);
            text(font, label, x + 3, SEARCH_Y + SEARCH_H + 4, 0xFFFFFFFF);
        }
    }

    private int maxListScroll() {
        return Math.max(0, visible.size() * ROW - (listBottom - LIST_TOP));
    }

    private void drawList(bbu font, int mouseX, int mouseY) {
        int viewH = listBottom - LIST_TOP;
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        Gfx.rect(listX, LIST_TOP, listW, viewH, 0x66000000);
        Gfx.scissor(k.d, k.e, l, m, listX, LIST_TOP, listW, viewH);
        if (visible.isEmpty()) {
            String empty = Lang.t("empty");
            text(font, empty, listX + (listW - font.a(empty)) / 2, LIST_TOP + 12, 0xFF9A9A9A);
            String hint = Lang.t("hint");
            text(font, font.a(hint, listW - 8), listX + 4, LIST_TOP + 26, 0xFF707070);
        }
        for (int i = 0; i < visible.size(); i++) {
            int y = LIST_TOP + i * ROW - listScroll;
            if (y + ROW < LIST_TOP || y > listBottom) {
                continue;
            }
            drawRow(font, visible.get(i), y, mouseX, mouseY);
        }
        Gfx.noScissor();
        int max = maxListScroll();
        if (max > 0) {
            int trackX = listX + listW - 4;
            Gfx.rect(trackX, LIST_TOP, 4, viewH, 0xFF000000);
            int thumb = Math.max(16, viewH * viewH / (visible.size() * ROW));
            int thumbY = LIST_TOP + (viewH - thumb) * listScroll / max;
            Gfx.rect(trackX, thumbY, 4, thumb, 0xFF808080);
            Gfx.rect(trackX, thumbY, 3, thumb - 1, 0xFFC0C0C0);
        }
    }

    private void drawRow(bbu font, ModContainer mod, int y, int mouseX, int mouseY) {
        boolean isSelected = mod == selected;
        boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= Math.max(y, LIST_TOP)
                && mouseY < Math.min(y + ROW, listBottom);
        if (isSelected) {
            Gfx.rect(listX, y, listW, ROW, 0xFFFFFFFF);
            Gfx.rect(listX + 1, y + 1, listW - 2, ROW - 2, 0xFF000000);
        } else if (hover) {
            Gfx.rect(listX, y, listW, ROW, 0x33FFFFFF);
        }
        Gfx.texture(Gfx.icon(mod), listX + 3, y + 2, 32);
        int textX = listX + 40;
        int maxW = listW - 46;
        String name = font.a(mod.name, maxW - 40);
        text(font, name, textX, y + 3, 0xFFFFFFFF);
        String tag = Lang.t(mod.library ? "library" : "client");
        int tagX = textX + font.a(name) + 4;
        int tagW = font.a(tag) + 6;
        if (tagX + tagW <= listX + listW - 6) {
            Gfx.rect(tagX, y + 2, tagW, 10, mod.library ? 0xFF4A3A66 : 0xFF1F3E7A);
            Gfx.border(tagX, y + 2, tagW, 10, mod.library ? 0xFF8A72B8 : 0xFF4C7DDB);
            text(font, tag, tagX + 3, y + 3, 0xFFFFFFFF);
        }
        String description = mod.description == null ? Lang.t("no_description") : mod.description;
        List<?> lines = font.c(description, maxW);
        for (int i = 0; i < 2 && lines != null && i < lines.size(); i++) {
            String line = (String) lines.get(i);
            if (i == 1 && lines.size() > 2) {
                line = font.a(line, maxW - font.a("...")) + "...";
            }
            text(font, line, textX, y + 14 + i * 10, 0xFFA0A0A0);
        }
    }

    private void drawDetails(bbu font, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        ModContainer mod = selected;
        Gfx.texture(Gfx.icon(mod), rightX, SEARCH_Y, 32);
        int textX = rightX + 36;
        text(font, font.a(mod.name, rightW - 40), textX, SEARCH_Y + 1, 0xFFFFFFFF);
        text(font, "v" + mod.version, textX, SEARCH_Y + 12, 0xFF9A9A9A);
        if (mod.authors != null) {
            text(font, font.a(Lang.t("by") + mod.authors, rightW - 40), textX, SEARCH_Y + 23, 0xFF9A9A9A);
        }
        int half = (rightW - 4) / 2;
        int linkY = LIST_TOP - 2;
        drawLink(font, Lang.t("site"), rightX, linkY, half, mod.website != null, mouseX, mouseY);
        drawLink(font, Lang.t("issues"), rightX + half + 4, linkY, rightW - half - 4, mod.issues != null, mouseX,
                mouseY);
        int detailTop = linkY + BUTTON_H + 4;
        Gfx.rect(rightX, detailTop - 2, rightW, 1, 0xFF4A4A4A);
        int viewH = listBottom - detailTop;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, detailHeight - viewH)));
        Gfx.scissor(k.d, k.e, l, m, rightX, detailTop, rightW, viewH);
        int y = detailTop + 2 - detailScroll;
        int start = y;
        String description = mod.description == null ? Lang.t("no_description") : mod.description;
        List<?> lines = font.c(description, rightW - 6);
        for (Object line : lines) {
            text(font, (String) line, rightX + 3, y, 0xFFFFFFFF);
            y += 10;
        }
        y += 8;
        if (mod.license != null) {
            text(font, Lang.t("license"), rightX + 3, y, 0xFFB8B8B8);
            y += 10;
            text(font, mod.license, rightX + 12, y, 0xFFFFFFFF);
            y += 14;
        }
        if (mod.authors != null) {
            text(font, Lang.t("credits"), rightX + 3, y, 0xFFB8B8B8);
            y += 10;
            text(font, Lang.t("authors"), rightX + 12, y, 0xFFB8B8B8);
            y += 10;
            for (String author : mod.authors.split(",")) {
                text(font, author.trim(), rightX + 21, y, 0xFFFFFFFF);
                y += 10;
            }
            y += 4;
        }
        if (mod.file != null) {
            text(font, Lang.t("file"), rightX + 3, y, 0xFFB8B8B8);
            y += 10;
            text(font, font.a(mod.file.getName(), rightW - 18), rightX + 12, y, 0xFFFFFFFF);
            y += 14;
        }
        text(font, "ID: " + mod.id, rightX + 3, y, 0xFF707070);
        y += 12;
        detailHeight = y - start;
        Gfx.noScissor();
    }

    private void drawLink(bbu font, String label, int x, int y, int width, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_H;
        Gfx.button(x, y, width, BUTTON_H, hover, enabled);
        int color = enabled ? 0xFFFFFFFF : 0xFF707070;
        text(font, label, x + (width - font.a(label)) / 2, y + (BUTTON_H - 8) / 2, color);
    }

    private void drawButtons(bbu font, int mouseX, int mouseY) {
        drawLink(font, Lang.t("folder"), listX, buttonY, listW, true, mouseX, mouseY);
        drawLink(font, Lang.t("done"), rightX, buttonY, rightW, true, mouseX, mouseY);
    }

    private void drawToast(bbu font) {
        if (toast == null || System.currentTimeMillis() > toastUntil) {
            return;
        }
        String shown = font.a(toast, l - 24);
        int width = font.a(shown) + 10;
        int x = (l - width) / 2;
        int y = buttonY - 18;
        Gfx.rect(x, y, width, 14, 0xF0100010);
        Gfx.border(x, y, width, 14, 0xFF57197F);
        text(font, shown, x + 5, y + 3, 0xFFFFFFFF);
    }

    protected void a(char character, int keyCode) {
        if (keyCode == KEY_ESCAPE) {
            close();
        } else if (keyCode == KEY_BACKSPACE) {
            if (query.length() > 0) {
                query.deleteCharAt(query.length() - 1);
                refilter();
            }
        } else if (keyCode == KEY_UP || keyCode == KEY_DOWN) {
            moveSelection(keyCode == KEY_UP ? -1 : 1);
        } else if (character >= ' ' && character != 127 && query.length() < 48) {
            query.append(character);
            refilter();
        }
    }

    private void moveSelection(int delta) {
        if (visible.isEmpty()) {
            return;
        }
        int index = selected == null ? 0 : visible.indexOf(selected) + delta;
        index = Math.max(0, Math.min(visible.size() - 1, index));
        selected = visible.get(index);
        detailScroll = 0;
        int rowTop = index * ROW;
        int viewH = listBottom - LIST_TOP;
        if (rowTop < listScroll) {
            listScroll = rowTop;
        } else if (rowTop + ROW > listScroll + viewH) {
            listScroll = rowTop + ROW - viewH;
        }
    }

    private boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    protected void a(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        layout();
        int searchW = listW - SEARCH_H - 4;
        if (in(mouseX, mouseY, listX + searchW + 4, SEARCH_Y, SEARCH_H, SEARCH_H)) {
            filter = (filter + 1) % 3;
            refilter();
            return;
        }
        if (in(mouseX, mouseY, listX, buttonY, listW, BUTTON_H)) {
            openFolder();
            return;
        }
        if (in(mouseX, mouseY, rightX, buttonY, rightW, BUTTON_H)) {
            close();
            return;
        }
        int half = (rightW - 4) / 2;
        int linkY = LIST_TOP - 2;
        if (selected != null && in(mouseX, mouseY, rightX, linkY, half, BUTTON_H)) {
            openLink(selected.website);
            return;
        }
        if (selected != null && in(mouseX, mouseY, rightX + half + 4, linkY, rightW - half - 4, BUTTON_H)) {
            openLink(selected.issues);
            return;
        }
        pressedRow = -1;
        dragging = false;
        if (in(mouseX, mouseY, listX, LIST_TOP, listW, listBottom - LIST_TOP)) {
            int row = (mouseY - LIST_TOP + listScroll) / ROW;
            pressedRow = row >= 0 && row < visible.size() ? row : -1;
            pressY = mouseY;
            lastDragY = mouseY;
        }
    }

    protected void a(int mouseX, int mouseY, int button, long time) {
        if (pressedRow < 0 && !dragging) {
            return;
        }
        if (!dragging && Math.abs(mouseY - pressY) > 3) {
            dragging = true;
        }
        if (dragging) {
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - (mouseY - lastDragY)));
            lastDragY = mouseY;
        }
    }

    protected void b(int mouseX, int mouseY, int button) {
        if (button == 0 && pressedRow >= 0 && !dragging && pressedRow < visible.size()) {
            ModContainer picked = visible.get(pressedRow);
            if (picked != selected) {
                selected = picked;
                detailScroll = 0;
            }
        }
        pressedRow = -1;
        dragging = false;
    }

    public void k() {
        super.k();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int step = wheel > 0 ? -ROW / 2 : ROW / 2;
        int mouseX = Mouse.getEventX() * l / k.d;
        if (mouseX >= rightX) {
            detailScroll = Math.max(0, detailScroll + step);
        } else {
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + step));
        }
    }

    private void openLink(String url) {
        if (url == null) {
            return;
        }
        if (!browse(url)) {
            bdw.e(url);
            showToast(Lang.t("copied") + url);
        }
    }

    private void openFolder() {
        File directory = NyxRuntime.getModsDirectory();
        if (!browse(directory.toURI().toString())) {
            showToast(Lang.t("folder_path") + directory.getAbsolutePath());
        }
    }

    private static boolean browse(String uri) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            if (!((Boolean) desktop.getMethod("isDesktopSupported").invoke(null)).booleanValue()) {
                return false;
            }
            Object instance = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", java.net.URI.class).invoke(instance, new java.net.URI(uri));
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
