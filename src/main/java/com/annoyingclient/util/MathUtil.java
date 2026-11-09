package com.annoyingclient.util;

public final class MathUtil {

    private MathUtil() {
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(max, v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(max, v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(max, v);
    }

    public static boolean inside(double px, double py, double x, double y, double w, double h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
