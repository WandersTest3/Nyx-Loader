package dev.nyx.mc171;

import dev.nyx.loader.ModContainer;
import dev.nyx.loader.NyxRuntime;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;

public final class Gfx {

    private static final String DEFAULT_ICON = "/assets/nyx/default_icon.png";
    private static final Map<String, Integer> TEXTURES = new HashMap<String, Integer>();

    private Gfx() {
    }

    public static int guiScale(int displayWidth, int displayHeight) {
        int setting = Lang.guiScale();
        int limit = setting == 0 ? 1000 : setting;
        int factor = 1;
        while (factor < limit && displayWidth / (factor + 1) >= 320 && displayHeight / (factor + 1) >= 240) {
            factor++;
        }
        return factor;
    }

    private static void color(int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F,
                (argb >>> 24) / 255.0F);
    }

    public static void rect(int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void gradient(int x, int y, int width, int height, int top, int bottom) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        color(bottom);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        color(top);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void border(int x, int y, int width, int height, int argb) {
        rect(x, y, width, 1, argb);
        rect(x, y + height - 1, width, 1, argb);
        rect(x, y + 1, 1, height - 2, argb);
        rect(x + width - 1, y + 1, 1, height - 2, argb);
    }

    public static void button(int x, int y, int width, int height, boolean hover, boolean enabled) {
        int face = !enabled ? 0xFF3A3A3A : hover ? 0xFF7C7C9C : 0xFF6B6B6B;
        rect(x, y, width, height, 0xFF000000);
        rect(x + 1, y + 1, width - 2, height - 2, face);
        if (enabled) {
            rect(x + 1, y + 1, width - 3, 1, 0xFFC4C4C4);
            rect(x + 1, y + 1, 1, height - 3, 0xFFC4C4C4);
            rect(x + 1, y + height - 2, width - 2, 1, 0xFF3B3B3B);
            rect(x + width - 2, y + 1, 1, height - 2, 0xFF3B3B3B);
        }
    }

    public static void texture(int id, int x, int y, int size) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + size);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + size, y + size);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + size, y);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }

    public static void scissor(int displayWidth, int displayHeight, int guiWidth, int guiHeight, int x, int y, int width,
            int height) {
        double sx = displayWidth / (double) guiWidth;
        double sy = displayHeight / (double) guiHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.floor(x * sx), (int) Math.floor((guiHeight - (y + height)) * sy),
                (int) Math.ceil(width * sx), (int) Math.ceil(height * sy));
    }

    public static void noScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static int icon(ModContainer mod) {
        String key = mod.id;
        Integer cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        int id = 0;
        try {
            InputStream input = openIcon(mod);
            if (input != null) {
                try {
                    id = upload(ImageIO.read(input));
                } finally {
                    input.close();
                }
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not load icon of " + mod.id + ": " + error);
        }
        if (id == 0) {
            id = defaultIcon();
        }
        TEXTURES.put(key, id);
        return id;
    }

    private static int defaultIcon() {
        Integer cached = TEXTURES.get("");
        if (cached != null) {
            return cached;
        }
        int id = 0;
        try {
            InputStream input = Gfx.class.getResourceAsStream(DEFAULT_ICON);
            if (input != null) {
                try {
                    id = upload(ImageIO.read(input));
                } finally {
                    input.close();
                }
            }
        } catch (Throwable error) {
            NyxRuntime.log("Could not load default icon: " + error);
        }
        TEXTURES.put("", id);
        return id;
    }

    private static InputStream openIcon(ModContainer mod) throws IOException {
        String path = mod.iconPath;
        if (path == null) {
            return null;
        }
        if (path.startsWith("classpath:")) {
            return Gfx.class.getResourceAsStream(path.substring("classpath:".length()));
        }
        if (mod.file == null) {
            return null;
        }
        JarFile jar = new JarFile(mod.file);
        try {
            String entryName = path.startsWith("/") ? path.substring(1) : path;
            ZipEntry entry = jar.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            InputStream stream = jar.getInputStream(entry);
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            stream.close();
            return new java.io.ByteArrayInputStream(buffer.toByteArray());
        } finally {
            jar.close();
        }
    }

    private static int upload(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
        for (int pixel : pixels) {
            buffer.put((byte) (pixel >> 16 & 255));
            buffer.put((byte) (pixel >> 8 & 255));
            buffer.put((byte) (pixel & 255));
            buffer.put((byte) (pixel >>> 24));
        }
        buffer.flip();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, buffer);
        return id;
    }
}
