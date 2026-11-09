package com.annoyingclient.render;

import com.annoyingclient.AnnoyingClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Small anti-aliased textures generated once at startup and reused for every rounded shape.
 *
 * <p>Drawing corners from a high-resolution, linearly filtered circle gives smooth edges at any GUI
 * scale while keeping each rounded rectangle down to 4 textured quads plus 3 fills.
 */
public final class Textures {

    public static final Identifier CIRCLE = Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "generated/circle");
    public static final Identifier SHADOW = Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "generated/shadow");

    /** Circle texture is CIRCLE_SIZE square; each quarter is CIRCLE_SIZE / 2. */
    public static final int CIRCLE_SIZE = 128;
    /** Shadow texture is SHADOW_SIZE square; SHADOW_CORNER is the 9-slice corner size in texels. */
    public static final int SHADOW_SIZE = 64;
    public static final int SHADOW_CORNER = 28;

    private static boolean ready;

    private Textures() {
    }

    public static boolean isReady() {
        return ready;
    }

    /** Must run on the render thread after the GPU device exists (CLIENT_STARTED). Safe to call twice. */
    public static void init() {
        if (ready) {
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        manager.register(CIRCLE, new LinearTexture("annoyingclient circle", createCircle()));
        manager.register(SHADOW, new LinearTexture("annoyingclient shadow", createShadow()));
        ready = true;
    }

    private static NativeImage createCircle() {
        int n = CIRCLE_SIZE;
        NativeImage image = new NativeImage(n, n, true);
        float center = n / 2f;
        float radius = n / 2f;
        // 4x4 supersampling for a clean edge.
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int inside = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float dx = x + (sx + 0.5f) / 4f - center;
                        float dy = y + (sy + 0.5f) / 4f - center;
                        if (dx * dx + dy * dy <= radius * radius) {
                            inside++;
                        }
                    }
                }
                int alpha = inside * 255 / 16;
                image.setPixel(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return image;
    }

    private static NativeImage createShadow() {
        int n = SHADOW_SIZE;
        int pad = 16;
        float sigma = 6f;
        // Separable Gaussian blur of a solid box [pad, n - pad).
        float[] profile = new float[n];
        for (int i = 0; i < n; i++) {
            // Integral of a Gaussian over the box = difference of two error functions.
            float a = (i + 0.5f - pad) / (sigma * (float) Math.sqrt(2));
            float b = (i + 0.5f - (n - pad)) / (sigma * (float) Math.sqrt(2));
            profile[i] = 0.5f * (erf(a) - erf(b));
        }
        NativeImage image = new NativeImage(n, n, true);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int alpha = Math.round(Math.max(0, Math.min(1, profile[x] * profile[y])) * 255);
                image.setPixel(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return image;
    }

    /** Abramowitz-Stegun approximation; accurate to ~1e-7, plenty for an 8-bit alpha channel. */
    private static float erf(float x) {
        float sign = Math.signum(x);
        x = Math.abs(x);
        float t = 1f / (1f + 0.3275911f * x);
        float y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f)
                * t * (float) Math.exp(-x * x);
        return sign * y;
    }

    /** A dynamic texture sampled with linear filtering and clamped edges, so scaled corners stay smooth. */
    private static final class LinearTexture extends DynamicTexture {
        LinearTexture(String label, NativeImage image) {
            super(() -> label, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
