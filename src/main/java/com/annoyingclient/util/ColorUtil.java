package com.annoyingclient.util;

/** Allocation-free ARGB color helpers. All colors are packed {@code 0xAARRGGBB} ints. */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int withAlpha(int argb, int alpha) {
        return (clamp255(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha by {@code factor} (0..1); used for fades. */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    public static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    /** Lightens (positive amount) or darkens (negative amount) a color, keeping alpha. */
    public static int shade(int argb, float amount) {
        int target = amount >= 0 ? 0xFFFFFFFF : 0xFF000000;
        int mixed = lerp(argb, target, Math.abs(amount));
        return withAlpha(mixed, alpha(argb));
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        hue = hue - (float) Math.floor(hue);
        float h = hue * 6f;
        int sector = (int) h % 6;
        float f = h - (int) h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return argb(255, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** Writes hue, saturation and value (each 0..1) of {@code argb} into {@code out}. */
    public static void rgbToHsv(int argb, float[] out) {
        float r = red(argb) / 255f, g = green(argb) / 255f, b = blue(argb) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / delta) % 6f;
        } else if (max == g) {
            h = (b - r) / delta + 2f;
        } else {
            h = (r - g) / delta + 4f;
        }
        h /= 6f;
        if (h < 0) {
            h += 1f;
        }
        out[0] = h;
        out[1] = max == 0 ? 0 : delta / max;
        out[2] = max;
    }

    /**
     * A hue-cycling color.
     *
     * @param offsetMillis shifts the phase so neighbouring items get neighbouring hues
     * @param speed        full cycles per ~4 seconds
     */
    public static int rainbow(long offsetMillis, float speed, float saturation, float value) {
        double period = 4000.0 / Math.max(0.05f, speed);
        double hue = ((System.currentTimeMillis() + offsetMillis) % (long) period) / period;
        return hsvToRgb((float) hue, saturation, value);
    }

    /** A color that waves smoothly between {@code a} and {@code b}. */
    public static int wave(int a, int b, long offsetMillis, float speed) {
        double period = 3000.0 / Math.max(0.05f, speed);
        double phase = ((System.currentTimeMillis() + offsetMillis) % (long) period) / period;
        float t = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
        return lerp(a, b, t);
    }

    public static String toHex(int argb, boolean includeAlpha) {
        return includeAlpha ? String.format("#%08X", argb) : String.format("#%06X", argb & 0xFFFFFF);
    }

    /** Parses #RGB, #RRGGBB or #AARRGGBB (the '#' is optional). Returns null when malformed. */
    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        try {
            return switch (s.length()) {
                case 3 -> {
                    int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                    yield argb(255, r, g, b);
                }
                case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
                case 8 -> (int) Long.parseLong(s, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
