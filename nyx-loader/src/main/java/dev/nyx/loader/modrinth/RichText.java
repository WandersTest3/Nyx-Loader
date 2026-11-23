package dev.nyx.loader.modrinth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RichText {

    public static final class Block {
        public final String text;
        public final String image;

        Block(String text, String image) {
            this.text = text;
            this.image = image;
        }
    }

    private static final Pattern IMAGE = Pattern.compile(
            "<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>|!\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)>?[^)]*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_IMAGES = 16;

    private RichText() {
    }

    public static List<Block> parse(String body) {
        List<Block> blocks = new ArrayList<Block>();
        if (body == null) {
            return blocks;
        }
        body = body.replaceAll("\\[\\s*(!\\[[^\\]]*\\]\\([^)]*\\))\\s*\\]\\([^)]*\\)", "$1");
        Matcher matcher = IMAGE.matcher(body);
        int last = 0;
        int images = 0;
        while (matcher.find()) {
            addText(blocks, body.substring(last, matcher.start()));
            last = matcher.end();
            String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (images < MAX_IMAGES && usable(url)) {
                blocks.add(new Block(null, url.replace("&amp;", "&")));
                images++;
            }
        }
        addText(blocks, body.substring(last));
        return blocks;
    }

    private static boolean usable(String url) {
        if (url == null || !url.startsWith("http")) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        String path = query >= 0 ? lower.substring(0, query) : lower;
        return !lower.contains("shields.io") && !lower.contains("badge") && !path.endsWith(".svg")
                && !path.endsWith(".gif");
    }

    private static void addText(List<Block> blocks, String raw) {
        String text = raw.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</h[1-6]>|</tr>", "\n")
                .replaceAll("(?i)<li[^>]*>", "\n\u2022 ")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s*[\\[\\]]+\\s*$", "")
                .replaceAll("\\]\\([^)]*\\)", "")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "\u2022 ")
                .replace("**", "").replace("__", "").replace("`", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&")
                .replace("\r", "");
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blank++;
                continue;
            }
            if (blank > 0 && out.length() > 0) {
                out.append('\n');
            }
            blank = 0;
            out.append(trimmed).append('\n');
        }
        String result = out.toString().trim();
        if (!result.isEmpty()) {
            blocks.add(new Block(result, null));
        }
    }
}
