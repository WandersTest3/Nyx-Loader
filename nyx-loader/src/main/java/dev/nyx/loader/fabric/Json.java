package dev.nyx.loader.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text.startsWith("﻿") ? text.substring(1) : text);
        Object value = parser.value();
        parser.skip();
        if (parser.pos != parser.text.length()) {
            throw parser.error("Trailing content");
        }
        return value;
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException(message + " at line " + line);
    }

    private void skip() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int end = text.indexOf("*/", pos + 2);
                pos = end < 0 ? text.length() : end + 2;
            } else {
                break;
            }
        }
    }

    private Object value() {
        skip();
        if (pos >= text.length()) {
            throw error("Unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                return number();
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("Invalid literal");
        }
        pos += word.length();
        return value;
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skip();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skip();
            if (peek() != '"') {
                throw error("Expected key");
            }
            String key = string();
            skip();
            if (peek() != ':') {
                throw error("Expected ':'");
            }
            pos++;
            map.put(key, value());
            skip();
            char c = peek();
            pos++;
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("Expected ',' or '}'");
            }
            skip();
            if (peek() == '}') {
                pos++;
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skip();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skip();
            char c = peek();
            pos++;
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("Expected ',' or ']'");
            }
            skip();
            if (peek() == ']') {
                pos++;
                return list;
            }
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unexpected end");
        }
        return text.charAt(pos);
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            char c = peek();
            pos++;
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = peek();
            pos++;
            switch (e) {
                case 'n':
                    out.append('\n');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("Invalid escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    out.append(e);
            }
        }
    }

    private Number number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = text.substring(start, pos);
        if (raw.isEmpty()) {
            throw error("Unexpected character");
        }
        try {
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException error) {
            throw error("Invalid number " + raw);
        }
    }
}
