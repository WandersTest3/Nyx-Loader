package dev.nyx.mc171;

import dev.nyx.api.NyxConfig;
import dev.nyx.api.NyxOptions;
import java.util.List;
import net.minecraft.class_370;
import net.minecraft.class_388;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class NyxOptionsScreen extends class_388 {

    private static final int TOP = 30;
    private static final int ROW = 24;
    private static final int CONTROL_W = 120;
    private static final int BUTTON_H = 20;
    private static final int BAR_W = 6;
    private static final int BAR_HIT = 14;
    private static final int KEY_ESCAPE = 1;

    private final class_388 parent;
    private final NyxOptions options;
    private final NyxConfig config;

    private int scroll;
    private int sliding = -1;
    private boolean barDragging;
    private int barGrab;
    private int panelX;
    private int panelW;
    private int bottom;
    private int buttonY;

    public NyxOptionsScreen(class_388 parent, NyxOptions options) {
        this.parent = parent;
        this.options = options;
        this.config = options.getConfig();
    }

    private void layout() {
        panelW = Math.min(field_1230 - 20, 360);
        panelX = (field_1230 - panelW) / 2;
        buttonY = field_1231 - 26;
        bottom = buttonY - 18;
    }

    private List<NyxOptions.Option> list() {
        return options.getOptions();
    }

    private int viewHeight() {
        return bottom - TOP;
    }

    private int maxScroll() {
        return Math.max(0, list().size() * ROW - viewHeight());
    }

    private int controlX() {
        return panelX + panelW - CONTROL_W - 10;
    }

    public void method_1025(int mouseX, int mouseY, float partialTicks) {
        layout();
        class_370 font = field_1229.field_3814;
        Gfx.gradient(0, 0, field_1230, field_1231, 0xFF2B0F3F, 0xFF0B0B10);
        String title = options.getTitle();
        text(font, title, (field_1230 - font.method_954(title)) / 2, 10, 0xFFFFFFFF);
        Gfx.rect(panelX, TOP - 2, panelW, viewHeight() + 4, 0x66000000);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        Gfx.scissor(field_1229.field_3801, field_1229.field_3802, field_1230, field_1231, panelX, TOP, panelW, viewHeight());
        String hint = null;
        for (int i = 0; i < list().size(); i++) {
            int y = TOP + i * ROW - scroll;
            if (y + ROW < TOP || y > bottom) {
                continue;
            }
            NyxOptions.Option option = list().get(i);
            boolean hover = in(mouseX, mouseY, panelX, Math.max(y, TOP), panelW, Math.min(y + ROW, bottom) - Math.max(y, TOP));
            if (hover) {
                Gfx.rect(panelX, y, panelW, ROW, 0x22FFFFFF);
                hint = option.description;
            }
            text(font, font.method_955(option.label, controlX() - panelX - 14), panelX + 8, y + 8, 0xFFFFFFFF);
            drawControl(font, option, controlX(), y + 2, mouseX, mouseY, i);
        }
        Gfx.noScissor();
        if (maxScroll() > 0) {
            int trackX = panelX + panelW - BAR_W;
            boolean active = barDragging || overBar(mouseX, mouseY);
            Gfx.rect(trackX, TOP, BAR_W, viewHeight(), 0xFF000000);
            Gfx.rect(trackX, thumbY(), BAR_W, thumbHeight(), active ? 0xFF57197F : 0xFF808080);
            Gfx.rect(trackX, thumbY(), BAR_W - 1, thumbHeight() - 1, active ? 0xFF8A3FBF : 0xFFC0C0C0);
        }
        if (hint != null) {
            text(font, font.method_955(hint, panelW), panelX, bottom + 5, 0xFFB8B8B8);
        }
        boolean hover = in(mouseX, mouseY, panelX, buttonY, panelW, BUTTON_H);
        Gfx.button(panelX, buttonY, panelW, BUTTON_H, hover, true);
        String done = Lang.t("done");
        text(font, done, panelX + (panelW - font.method_954(done)) / 2, buttonY + 6, 0xFFFFFFFF);
    }

    private void drawControl(class_370 font, NyxOptions.Option option, int x, int y, int mouseX, int mouseY, int index) {
        boolean hover = in(mouseX, mouseY, x, y, CONTROL_W, BUTTON_H);
        String value = config.get(option.key, option.defaultValue);
        if (option.type == NyxOptions.Type.SLIDER) {
            int current = clamp(option, parse(value, option));
            Gfx.rect(x, y, CONTROL_W, BUTTON_H, 0xFF000000);
            Gfx.border(x, y, CONTROL_W, BUTTON_H, hover || sliding == index ? 0xFF8A3FBF : 0xFF808080);
            int knob = x + 2 + (CONTROL_W - 12) * (current - option.min) / (option.max - option.min);
            Gfx.rect(knob, y + 2, 8, BUTTON_H - 4, sliding == index ? 0xFF8A3FBF : 0xFF57197F);
            String label = String.valueOf(current);
            text(font, label, x + (CONTROL_W - font.method_954(label)) / 2, y + 6, 0xFFFFFFFF);
            return;
        }
        Gfx.button(x, y, CONTROL_W, BUTTON_H, hover, true);
        String label;
        int color = 0xFFFFFFFF;
        if (option.type == NyxOptions.Type.TOGGLE) {
            boolean on = "true".equalsIgnoreCase(value);
            label = Lang.t(on ? "on" : "off");
            color = on ? 0xFF55FF55 : 0xFFFF5555;
        } else {
            label = value;
        }
        text(font, font.method_955(label, CONTROL_W - 8), x + (CONTROL_W - Math.min(font.method_954(label), CONTROL_W - 8)) / 2,
                y + 6, color);
    }

    private static int parse(String value, NyxOptions.Option option) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return Integer.parseInt(option.defaultValue);
        }
    }

    private static int clamp(NyxOptions.Option option, int value) {
        return Math.max(option.min, Math.min(option.max, value));
    }

    private void text(class_370 font, String value, int x, int y, int color) {
        font.method_956(value, x, y, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int thumbHeight() {
        int view = viewHeight();
        return Math.min(view, Math.max(16, view * view / Math.max(1, list().size() * ROW)));
    }

    private int thumbY() {
        int max = maxScroll();
        return TOP + (max <= 0 ? 0 : (viewHeight() - thumbHeight()) * scroll / max);
    }

    private boolean overBar(int mouseX, int mouseY) {
        return maxScroll() > 0 && in(mouseX, mouseY, panelX + panelW - BAR_HIT, TOP, BAR_HIT, viewHeight());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void close() {
        options.save();
        field_1229.method_2928(parent);
    }

    protected void method_1024(char character, int keyCode) {
        if (keyCode == KEY_ESCAPE) {
            close();
        }
    }

    protected void method_1026(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        layout();
        if (in(mouseX, mouseY, panelX, buttonY, panelW, BUTTON_H)) {
            close();
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
        if (mouseY < TOP || mouseY >= bottom) {
            return;
        }
        int index = (mouseY - TOP + scroll) / ROW;
        if (index < 0 || index >= list().size()) {
            return;
        }
        int y = TOP + index * ROW - scroll + 2;
        if (!in(mouseX, mouseY, controlX(), y, CONTROL_W, BUTTON_H)) {
            return;
        }
        NyxOptions.Option option = list().get(index);
        String value = config.get(option.key, option.defaultValue);
        if (option.type == NyxOptions.Type.TOGGLE) {
            config.set(option.key, !"true".equalsIgnoreCase(value));
        } else if (option.type == NyxOptions.Type.CYCLE) {
            int position = option.values.indexOf(value);
            config.set(option.key, option.values.get((position + 1) % option.values.size()));
        } else {
            sliding = index;
            slide(mouseX);
        }
    }

    private void slide(int mouseX) {
        if (sliding < 0 || sliding >= list().size()) {
            return;
        }
        NyxOptions.Option option = list().get(sliding);
        double ratio = (mouseX - controlX() - 6) / (double) (CONTROL_W - 12);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        config.set(option.key, (int) Math.round(option.min + ratio * (option.max - option.min)));
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
        } else if (sliding >= 0) {
            slide(mouseX);
        }
    }

    protected void method_1032(int mouseX, int mouseY, int button) {
        barDragging = false;
        sliding = -1;
    }

    public void method_1035() {
        super.method_1035();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -ROW : ROW)));
        }
    }
}
