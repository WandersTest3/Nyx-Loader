package dev.nyx.mc171;

import dev.nyx.loader.CrashAnalyzer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_370;
import net.minecraft.class_388;
import net.minecraft.class_624;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class NyxCrashScreen extends class_388 {

    private static final int BUTTON_H = 20;
    private static final int TOP = 40;
    private static final int LINE = 10;
    private static final int BAR_W = 6;
    private static final int BAR_HIT = 14;
    private static final int KEY_ESCAPE = 1;

    private final CrashAnalyzer.Result result;
    private final String file;
    private final String shaderPack;
    private final String reportText;
    private final List<String[]> rows = new ArrayList<String[]>();

    private int scroll;
    private boolean barDragging;
    private int barGrab;
    private long copiedUntil;
    private int boxX;
    private int boxW;
    private int boxBottom;
    private int buttonY;

    public NyxCrashScreen(CrashAnalyzer.Result result, String file, String shaderPack, String reportText) {
        this.result = result;
        this.file = file;
        this.shaderPack = shaderPack;
        this.reportText = reportText;
    }

    private void layout(class_370 font) {
        boxX = 12;
        boxW = field_1230 - 24;
        buttonY = field_1231 - 28;
        boxBottom = buttonY - 8;
        rows.clear();
        String suspects = result.suspectNames();
        add(font, Lang.t("crash_suspect"), 0xFFFF7070);
        add(font, "  " + (suspects == null ? Lang.t("crash_unknown") : suspects), 0xFFFFFFFF);
        if (shaderPack != null) {
            add(font, Lang.t("crash_shaders_off") + " " + shaderPack, 0xFFFFE060);
        }
        add(font, "", 0);
        add(font, Lang.t("crash_error"), 0xFFFF7070);
        for (String line : result.excerpt) {
            add(font, line, line.startsWith("  ") ? 0xFFA0A0A0 : 0xFFFFC080);
        }
        if (file != null) {
            add(font, "", 0);
            add(font, Lang.t("crash_report") + " " + file, 0xFFB8B8B8);
        }
    }

    private void add(class_370 font, String text, int color) {
        if (text.isEmpty()) {
            rows.add(new String[] {"", "0"});
            return;
        }
        List<?> wrapped = font.method_971(text, boxW - 16);
        for (Object line : wrapped) {
            rows.add(new String[] {String.valueOf(line), String.valueOf(color)});
        }
    }

    private int viewHeight() {
        return boxBottom - TOP - 8;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * LINE - viewHeight());
    }

    public void method_1025(int mouseX, int mouseY, float partialTicks) {
        class_370 font = field_1229.field_3814;
        layout(font);
        Gfx.gradient(0, 0, field_1230, field_1231, 0xFF3F0F17, 0xFF0B0B10);
        String title = Lang.t("crash_title");
        text(font, title, (field_1230 - font.method_954(title)) / 2, 10, 0xFFFF5555);
        String subtitle = Lang.t("crash_saved");
        text(font, subtitle, (field_1230 - font.method_954(subtitle)) / 2, 22, 0xFFD0D0D0);
        Gfx.rect(boxX, TOP - 4, boxW, boxBottom - TOP + 4, 0x99000000);
        Gfx.border(boxX, TOP - 4, boxW, boxBottom - TOP + 4, 0xFF57197F);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, boxX, TOP, boxW, viewHeight());
        int y = TOP - scroll;
        for (String[] row : rows) {
            if (y + LINE >= TOP && y <= TOP + viewHeight() && !row[0].isEmpty()) {
                text(font, row[0], boxX + 6, y, Integer.parseInt(row[1]));
            }
            y += LINE;
        }
        Gfx.noScissor();
        if (maxScroll() > 0) {
            int trackX = boxX + boxW - BAR_W - 2;
            boolean active = barDragging || overBar(mouseX, mouseY);
            Gfx.rect(trackX, TOP, BAR_W, viewHeight(), 0xFF000000);
            Gfx.rect(trackX, thumbY(), BAR_W, thumbHeight(), active ? 0xFF57197F : 0xFF808080);
            Gfx.rect(trackX, thumbY(), BAR_W - 1, thumbHeight() - 1, active ? 0xFF8A3FBF : 0xFFC0C0C0);
        }
        int half = (boxW - 6) / 2;
        button(font, Lang.t("crash_menu"), boxX, half, mouseX, mouseY);
        String copy = System.currentTimeMillis() < copiedUntil ? Lang.t("crash_copied") : Lang.t("crash_copy");
        button(font, copy, boxX + half + 6, boxW - half - 6, mouseX, mouseY);
    }

    private void button(class_370 font, String label, int x, int width, int mouseX, int mouseY) {
        boolean hover = in(mouseX, mouseY, x, buttonY, width, BUTTON_H);
        Gfx.button(x, buttonY, width, BUTTON_H, hover, true);
        text(font, label, x + (width - font.method_954(label)) / 2, buttonY + 6, 0xFFFFFFFF);
    }

    private void text(class_370 font, String value, int x, int y, int color) {
        font.method_956(value, x, y, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int thumbHeight() {
        int view = viewHeight();
        return Math.min(view, Math.max(16, view * view / Math.max(1, rows.size() * LINE)));
    }

    private int thumbY() {
        int max = maxScroll();
        return TOP + (max <= 0 ? 0 : (viewHeight() - thumbHeight()) * scroll / max);
    }

    private boolean overBar(int mouseX, int mouseY) {
        return maxScroll() > 0 && in(mouseX, mouseY, boxX + boxW - BAR_HIT - 2, TOP, BAR_HIT, viewHeight());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void menu() {
        field_1229.method_2928(new class_624());
    }

    protected void method_1024(char character, int keyCode) {
        if (keyCode == KEY_ESCAPE) {
            menu();
        }
    }

    protected void method_1026(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        int half = (boxW - 6) / 2;
        if (in(mouseX, mouseY, boxX, buttonY, half, BUTTON_H)) {
            menu();
            return;
        }
        if (in(mouseX, mouseY, boxX + half + 6, buttonY, boxW - half - 6, BUTTON_H)) {
            StringBuilder text = new StringBuilder();
            if (reportText != null) {
                text.append(reportText);
            } else {
                for (String line : result.excerpt) {
                    text.append(line).append('\n');
                }
            }
            class_388.method_1036(text.toString());
            copiedUntil = System.currentTimeMillis() + 2500L;
            return;
        }
        if (overBar(mouseX, mouseY)) {
            int thumb = thumbHeight();
            int top = thumbY();
            barGrab = mouseY >= top && mouseY < top + thumb ? mouseY - top : thumb / 2;
            barDragging = true;
            dragBar(mouseY);
        }
    }

    private void dragBar(int mouseY) {
        int range = viewHeight() - thumbHeight();
        if (range > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), (mouseY - barGrab - TOP) * maxScroll() / range));
        }
    }

    protected void method_4259(int mouseX, int mouseY, int button, long time) {
        if (barDragging) {
            dragBar(mouseY);
        }
    }

    protected void method_1032(int mouseX, int mouseY, int button) {
        barDragging = false;
    }

    public void method_1035() {
        super.method_1035();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -LINE * 3 : LINE * 3)));
        }
    }
}
