package com.annoyingclient.util;

/**
 * A value that moves toward a target over time, independent of frame rate.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link #Animation(float, float)} — exponential smoothing: good for hover glows, scrolling and
 *       anything whose target changes often.</li>
 *   <li>{@link #timed(float, long, Easing)} — fixed duration with an easing curve: good for open/close.</li>
 * </ul>
 * The global speed multiplier comes from the theme's animation speed. Instances are reusable and allocate nothing per frame.
 */
public final class Animation {

    private static float globalSpeed = 1f;

    private float value;
    private float target;
    private final float sharpness;

    // timed mode
    private final boolean timed;
    private final long durationMs;
    private final Easing easing;
    private float start;
    private long startTime;

    private long lastUpdate = System.nanoTime();

    /**
     * @param initial   starting value
     * @param sharpness how quickly the value approaches its target (higher is faster; ~12 feels snappy)
     */
    public Animation(float initial, float sharpness) {
        this.value = initial;
        this.target = initial;
        this.sharpness = sharpness;
        this.timed = false;
        this.durationMs = 0;
        this.easing = Easing.LINEAR;
    }

    private Animation(float initial, long durationMs, Easing easing) {
        this.value = initial;
        this.target = initial;
        this.start = initial;
        this.sharpness = 0;
        this.timed = true;
        this.durationMs = Math.max(1, durationMs);
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
    }

    public static Animation timed(float initial, long durationMs, Easing easing) {
        return new Animation(initial, durationMs, easing);
    }

    public static void setGlobalSpeed(float speed) {
        globalSpeed = Math.max(0.05f, speed);
    }

    public static float getGlobalSpeed() {
        return globalSpeed;
    }

    public void setTarget(float newTarget) {
        if (newTarget == target) {
            return;
        }
        if (timed) {
            start = value;
            startTime = System.currentTimeMillis();
        }
        target = newTarget;
    }

    /** Jumps straight to {@code v} with no animation. */
    public void snap(float v) {
        value = v;
        target = v;
        start = v;
        lastUpdate = System.nanoTime();
    }

    /** Advances the animation to the current time and returns the new value. Call once per frame. */
    public float update() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastUpdate) / 1_000_000_000f);
        lastUpdate = now;
        if (timed) {
            float progress = (System.currentTimeMillis() - startTime) * globalSpeed / durationMs;
            if (progress >= 1f) {
                value = target;
            } else {
                value = start + (target - start) * easing.apply(Math.max(0f, progress));
            }
        } else {
            float factor = 1f - (float) Math.exp(-sharpness * globalSpeed * dt);
            value += (target - value) * factor;
            if (Math.abs(target - value) < 0.0005f) {
                value = target;
            }
        }
        return value;
    }

    public float get() {
        return value;
    }

    public float getTarget() {
        return target;
    }

    public boolean isDone() {
        return value == target;
    }
}
