package dev.nyx.mc171;

import net.minecraft.class_370;
import net.minecraft.class_388;
import dev.nyx.loader.InstalledMods;
import dev.nyx.loader.ModContainer;
import dev.nyx.loader.NyxRuntime;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class NyxModMenuScreen extends class_388 {

    private static final int ROW = 36;
    private static final int MARGIN = 6;
    private static final int TOP_MARGIN = 6;
    private static final int SEARCH_Y = 18;
    private static final int SEARCH_H = 18;
    private static final int COUNT_Y = 42;
    private static final int LIST_TOP = 54;
    private static final int BUTTON_H = 20;
    private static final int BAR_W = 6;
    private static final int BAR_HIT = 14;
    private static final int KEY_ESCAPE = 1;
    private static final int KEY_BACKSPACE = 14;
    private static final int KEY_UP = 200;
    private static final int KEY_DOWN = 208;

    private final class_388 parent;
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
    private boolean dragging;
    private boolean barDragging;
    private int barGrab;
    private String toast;
    private long toastUntil;

    private int listX;
    private int listW;
    private int listBottom;
    private int rightX;
    private int rightW;
    private int buttonY;

    public NyxModMenuScreen(class_388 parent) {
        this.parent = parent;
        all.addAll(InstalledMods.list());
        refilter();
        if (!visible.isEmpty()) {
            selected = visible.get(0);
        }
    }

    public void reload() {
        ModContainer previous = selected;
        all.clear();
        all.addAll(InstalledMods.list());
        refilter();
        if (previous != null) {
            for (ModContainer mod : visible) {
                if (mod.id.equals(previous.id) && mod.kind.equals(previous.kind)) {
                    selected = mod;
                }
            }
        }
    }

    public void method_1044() {
        super.method_1044();
        reload();
        layout();
    }

    private void layout() {
        listX = MARGIN;
        listW = (int) (field_1230 * 0.47D);
        rightX = listX + listW + 10;
        rightW = field_1230 - rightX - MARGIN;
        listBottom = field_1231 - 34;
        buttonY = field_1231 - 26;
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
        field_1229.method_2928(parent);
    }

    public void method_1025(int mouseX, int mouseY, float partialTicks) {
        layout();
        class_370 font = field_1229.field_3814;
        Gfx.gradient(0, 0, field_1230, field_1231, 0xFF2B0F3F, 0xFF0B0B10);
        drawTitle(font);
        drawSearch(font, mouseX, mouseY);
        String showing = String.format(Lang.t("showing"), count(false), count(true));
        text(font, showing, listX + 2, COUNT_Y, 0xFFB8B8B8);
        drawList(font, mouseX, mouseY);
        drawDetails(font, mouseX, mouseY);
        drawButtons(font, mouseX, mouseY);
        drawToast(font);
    }

    private void text(class_370 font, String value, int x, int y, int color) {
        font.method_956(value, x, y, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTitle(class_370 font) {
        String title = Lang.t("mods");
        text(font, title, listX + (listW - font.method_954(title)) / 2, TOP_MARGIN, 0xFFFFFFFF);
    }

    private void drawSearch(class_370 font, int mouseX, int mouseY) {
        int searchW = listW - SEARCH_H - 4;
        Gfx.rect(listX, SEARCH_Y, searchW, SEARCH_H, 0xFFFFFFFF);
        Gfx.rect(listX + 1, SEARCH_Y + 1, searchW - 2, SEARCH_H - 2, 0xFF000000);
        String shown = query.toString();
        int textY = SEARCH_Y + (SEARCH_H - 8) / 2;
        if (shown.length() == 0) {
            text(font, Lang.t("search"), listX + 5, textY, 0xFF707070);
        } else {
            text(font, font.method_955(shown, searchW - 14), listX + 5, textY, 0xFFFFFFFF);
        }
        if (System.currentTimeMillis() / 500L % 2L == 0L) {
            int caretX = listX + 5 + Math.min(font.method_954(shown), searchW - 14);
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
            int width = font.method_954(label);
            int x = Math.min(filterX, field_1230 - width - 10);
            Gfx.rect(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xF0100010);
            Gfx.border(x, SEARCH_Y + SEARCH_H + 2, width + 6, 12, 0xFF57197F);
            text(font, label, x + 3, SEARCH_Y + SEARCH_H + 4, 0xFFFFFFFF);
        }
    }

    private int maxListScroll() {
        return Math.max(0, visible.size() * ROW - (listBottom - LIST_TOP));
    }

    private void drawList(class_370 font, int mouseX, int mouseY) {
        int viewH = listBottom - LIST_TOP;
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        Gfx.rect(listX, LIST_TOP, listW, viewH, 0x66000000);
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, listX, LIST_TOP, listW, viewH);
        if (visible.isEmpty()) {
            String empty = Lang.t("empty");
            text(font, empty, listX + (listW - font.method_954(empty)) / 2, LIST_TOP + 12, 0xFF9A9A9A);
            String hint = Lang.t("hint");
            text(font, font.method_955(hint, listW - 8), listX + 4, LIST_TOP + 26, 0xFF707070);
        }
        for (int i = 0; i < visible.size(); i++) {
            int y = LIST_TOP + i * ROW - listScroll;
            if (y + ROW < LIST_TOP || y > listBottom) {
                continue;
            }
            drawRow(font, visible.get(i), y, mouseX, mouseY);
        }
        Gfx.noScissor();
        if (maxListScroll() > 0) {
            int trackX = listX + listW - BAR_W;
            int thumb = thumbHeight();
            int thumbY = thumbY();
            boolean active = barDragging || overBar(mouseX, mouseY);
            Gfx.rect(trackX, LIST_TOP, BAR_W, viewH, 0xFF000000);
            Gfx.rect(trackX, thumbY, BAR_W, thumb, active ? 0xFF57197F : 0xFF808080);
            Gfx.rect(trackX, thumbY, BAR_W - 1, thumb - 1, active ? 0xFF8A3FBF : 0xFFC0C0C0);
        }
    }

    private int thumbHeight() {
        int viewH = listBottom - LIST_TOP;
        return Math.min(viewH, Math.max(16, viewH * viewH / Math.max(1, visible.size() * ROW)));
    }

    private int thumbY() {
        int max = maxListScroll();
        int range = listBottom - LIST_TOP - thumbHeight();
        return LIST_TOP + (max <= 0 ? 0 : range * listScroll / max);
    }

    private boolean overBar(int mouseX, int mouseY) {
        return maxListScroll() > 0 && in(mouseX, mouseY, listX + listW - BAR_HIT, LIST_TOP, BAR_HIT, listBottom - LIST_TOP);
    }

    private void dragBar(int mouseY) {
        int range = listBottom - LIST_TOP - thumbHeight();
        if (range <= 0) {
            return;
        }
        int offset = mouseY - barGrab - LIST_TOP;
        listScroll = Math.max(0, Math.min(maxListScroll(), offset * maxListScroll() / range));
    }

    private void drawRow(class_370 font, ModContainer mod, int y, int mouseX, int mouseY) {
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
        if (in(mouseX, mouseY, listX + 3, Math.max(y + 2, LIST_TOP), 32, Math.min(y + 34, listBottom) - Math.max(y + 2, LIST_TOP))
                && ConfigScreens.has(mod)) {
            Gfx.rect(listX + 3, y + 2, 32, 32, 0x40000000);
            bars(listX + 3, y + 2, 32);
        }
        int textX = listX + 40;
        int maxW = listW - 46;
        String name = font.method_955(mod.name, maxW - 40);
        text(font, name, textX, y + 3, 0xFFFFFFFF);
        String tag = Lang.t(mod.library ? "library" : "fabric".equals(mod.kind) ? "fabric" : "forge".equals(mod.kind) ? "forge" : "client");
        int tagX = textX + font.method_954(name) + 4;
        int tagW = font.method_954(tag) + 6;
        if (tagX + tagW <= listX + listW - 6) {
            Gfx.rect(tagX, y + 2, tagW, 10, mod.library ? 0xFF4A3A66 : "fabric".equals(mod.kind) ? 0xFF6B4E1F : "forge".equals(mod.kind) ? 0xFF1F4A2E : 0xFF1F3E7A);
            Gfx.border(tagX, y + 2, tagW, 10, mod.library ? 0xFF8A72B8 : "fabric".equals(mod.kind) ? 0xFFDBA74C : "forge".equals(mod.kind) ? 0xFF4CDB7D : 0xFF4C7DDB);
            text(font, tag, tagX + 3, y + 3, 0xFFFFFFFF);
        }
        String description = mod.description == null ? Lang.t("no_description") : mod.description;
        List<?> lines = font.method_971(description, maxW);
        for (int i = 0; i < 2 && lines != null && i < lines.size(); i++) {
            String line = (String) lines.get(i);
            if (i == 1 && lines.size() > 2) {
                line = font.method_955(line, maxW - font.method_954("...")) + "...";
            }
            text(font, line, textX, y + 14 + i * 10, 0xFFA0A0A0);
        }
    }

    private void drawDetails(class_370 font, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        ModContainer mod = selected;
        Gfx.texture(Gfx.icon(mod), rightX, SEARCH_Y, 32);
        int textX = rightX + 36;
        boolean config = ConfigScreens.has(mod);
        int nameW = rightW - 40 - (config ? BUTTON_H + 4 : 0);
        text(font, font.method_955(mod.name, nameW), textX, SEARCH_Y + 1, 0xFFFFFFFF);
        if (config) {
            int bx = rightX + rightW - BUTTON_H;
            boolean hover = in(mouseX, mouseY, bx, SEARCH_Y, BUTTON_H, BUTTON_H);
            Gfx.button(bx, SEARCH_Y, BUTTON_H, BUTTON_H, hover, true);
            bars(bx, SEARCH_Y, BUTTON_H);
            if (hover) {
                String label = Lang.t("config");
                int width = font.method_954(label);
                int x = Math.min(bx, field_1230 - width - 10);
                Gfx.rect(x, SEARCH_Y + BUTTON_H + 2, width + 6, 12, 0xF0100010);
                Gfx.border(x, SEARCH_Y + BUTTON_H + 2, width + 6, 12, 0xFF57197F);
                text(font, label, x + 3, SEARCH_Y + BUTTON_H + 4, 0xFFFFFFFF);
            }
        }
        text(font, "v" + mod.version, textX, SEARCH_Y + 12, 0xFF9A9A9A);
        if (mod.authors != null) {
            text(font, font.method_955(Lang.t("by") + mod.authors, rightW - 40), textX, SEARCH_Y + 23, 0xFF9A9A9A);
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
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, rightX, detailTop, rightW, viewH);
        int y = detailTop + 2 - detailScroll;
        int start = y;
        String description = mod.description == null ? Lang.t("no_description") : mod.description;
        List<?> lines = font.method_971(description, rightW - 6);
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
            text(font, font.method_955(mod.file.getName(), rightW - 18), rightX + 12, y, 0xFFFFFFFF);
            y += 14;
        }
        text(font, "ID: " + mod.id, rightX + 3, y, 0xFF707070);
        y += 12;
        detailHeight = y - start;
        Gfx.noScissor();
    }

    private static void bars(int x, int y, int size) {
        int width = size / 2;
        int left = x + (size - width) / 2;
        int gap = Math.max(3, size / 6);
        int top = y + size / 2 - gap - 1;
        for (int i = 0; i < 3; i++) {
            Gfx.rect(left, top + i * gap, width, 2, 0xFFFFFFFF);
        }
    }

    private void openConfig(ModContainer mod) {
        try {
            Object screen = ConfigScreens.open(mod, this);
            if (screen == null) {
                showToast(Lang.t("config_none"));
                return;
            }
            field_1229.method_2928((class_388) screen);
        } catch (Throwable error) {
            NyxRuntime.log("Could not open the settings of " + mod.id + ": " + error);
            error.printStackTrace();
            showToast(Lang.t("config_failed") + error.getClass().getSimpleName());
        }
    }

    private void drawLink(class_370 font, String label, int x, int y, int width, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_H;
        Gfx.button(x, y, width, BUTTON_H, hover, enabled);
        int color = enabled ? 0xFFFFFFFF : 0xFF707070;
        text(font, label, x + (width - font.method_954(label)) / 2, y + (BUTTON_H - 8) / 2, color);
    }

    private void drawButtons(class_370 font, int mouseX, int mouseY) {
        int half = (listW - 4) / 2;
        drawLink(font, Lang.t("dl_folder"), listX, buttonY, half, true, mouseX, mouseY);
        drawLink(font, Lang.t("dl_open"), listX + half + 4, buttonY, listW - half - 4, true, mouseX, mouseY);
        drawLink(font, Lang.t("done"), rightX, buttonY, rightW, true, mouseX, mouseY);
    }

    private void drawToast(class_370 font) {
        if (toast == null || System.currentTimeMillis() > toastUntil) {
            return;
        }
        String shown = font.method_955(toast, field_1230 - 24);
        int width = font.method_954(shown) + 10;
        int x = (field_1230 - width) / 2;
        int y = buttonY - 18;
        Gfx.rect(x, y, width, 14, 0xF0100010);
        Gfx.border(x, y, width, 14, 0xFF57197F);
        text(font, shown, x + 5, y + 3, 0xFFFFFFFF);
    }

    protected void method_1024(char character, int keyCode) {
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

    protected void method_1026(int mouseX, int mouseY, int button) {
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
        int bottomHalf = (listW - 4) / 2;
        if (in(mouseX, mouseY, listX, buttonY, bottomHalf, BUTTON_H)) {
            openFolder();
            return;
        }
        if (in(mouseX, mouseY, listX + bottomHalf + 4, buttonY, listW - bottomHalf - 4, BUTTON_H)) {
            field_1229.method_2928(new NyxDownloadScreen(this));
            return;
        }
        if (in(mouseX, mouseY, rightX, buttonY, rightW, BUTTON_H)) {
            close();
            return;
        }
        if (selected != null && in(mouseX, mouseY, rightX + rightW - BUTTON_H, SEARCH_Y, BUTTON_H, BUTTON_H)
                && ConfigScreens.has(selected)) {
            openConfig(selected);
            return;
        }
        if (in(mouseX, mouseY, listX + 3, LIST_TOP, 32, listBottom - LIST_TOP) && !overBar(mouseX, mouseY)) {
            int row = (mouseY - LIST_TOP + listScroll) / ROW;
            int rowY = LIST_TOP + row * ROW - listScroll;
            if (row >= 0 && row < visible.size() && mouseY >= rowY + 2 && mouseY < rowY + 34
                    && ConfigScreens.has(visible.get(row))) {
                selected = visible.get(row);
                detailScroll = 0;
                openConfig(selected);
                return;
            }
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
        barDragging = false;
        if (overBar(mouseX, mouseY)) {
            int thumbY = thumbY();
            int thumb = thumbHeight();
            barGrab = mouseY >= thumbY && mouseY < thumbY + thumb ? mouseY - thumbY : thumb / 2;
            barDragging = true;
            dragBar(mouseY);
            return;
        }
        if (in(mouseX, mouseY, listX, LIST_TOP, listW, listBottom - LIST_TOP)) {
            int row = (mouseY - LIST_TOP + listScroll) / ROW;
            pressedRow = row >= 0 && row < visible.size() ? row : -1;
            pressY = mouseY;
        }
    }

    protected void method_4259(int mouseX, int mouseY, int button, long time) {
        if (barDragging) {
            dragBar(mouseY);
            return;
        }
        if (pressedRow >= 0 && Math.abs(mouseY - pressY) > 3) {
            dragging = true;
        }
    }

    protected void method_1032(int mouseX, int mouseY, int button) {
        if (button == 0 && pressedRow >= 0 && !dragging && pressedRow < visible.size()) {
            ModContainer picked = visible.get(pressedRow);
            if (picked != selected) {
                selected = picked;
                detailScroll = 0;
            }
        }
        pressedRow = -1;
        dragging = false;
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

    private void openLink(String url) {
        if (url == null) {
            return;
        }
        if (!browse(url)) {
            class_388.method_1036(url);
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
