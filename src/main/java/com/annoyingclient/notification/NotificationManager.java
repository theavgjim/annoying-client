package com.annoyingclient.notification;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Queues, animates and draws toast notifications.
 *
 * <p>{@link #push} may be called from any thread; notifications from background threads are handed to
 * the render thread. At most {@link #MAX_VISIBLE} are shown at once; the rest wait their turn.
 */
public final class NotificationManager {

    public static final int MAX_VISIBLE = 5;
    private static final float MIN_WIDTH = 170;
    private static final float MAX_WIDTH = 280;
    private static final float HEIGHT = 30;
    private static final float GAP = 5;
    private static final float MARGIN = 8;

    private final List<Notification> active = new ArrayList<>();
    private final List<Notification> waiting = new ArrayList<>();

    private boolean enabled = true;
    private boolean moduleToggles = true;
    private long durationMs = 2500;
    private boolean bottomRight = true;

    public void configure(boolean enabled, boolean moduleToggles, long durationMs, boolean bottomRight) {
        this.enabled = enabled;
        this.moduleToggles = moduleToggles;
        this.durationMs = durationMs;
        this.bottomRight = bottomRight;
    }

    public void success(String title, String message) {
        push(NotificationType.SUCCESS, title, message);
    }

    public void error(String title, String message) {
        push(NotificationType.ERROR, title, message);
    }

    public void info(String title, String message) {
        push(NotificationType.INFO, title, message);
    }

    public void push(NotificationType type, String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> push(type, title, message));
            return;
        }
        if (!enabled && type != NotificationType.ERROR) {
            return;
        }
        if (!moduleToggles && (type == NotificationType.MODULE_ENABLED || type == NotificationType.MODULE_DISABLED)) {
            return;
        }
        // Collapse rapid repeats of the same toast (e.g. spamming a keybind) into the newest one.
        for (Notification n : active) {
            if (!n.isClosing() && n.getTitle().equals(title)) {
                n.close();
            }
        }
        waiting.removeIf(n -> n.getTitle().equals(title));
        waiting.add(new Notification(type, title, message, durationMs));
        AnnoyingClient.LOGGER.debug("Notification [{}] {}: {}", type, title, message);
    }

    public void clear() {
        active.clear();
        waiting.clear();
    }

    /** Draws all notifications in the bottom-right (or top-right) corner. Call once per frame. */
    public void render(GuiGraphics g, ThemeManager theme) {
        if (active.isEmpty() && waiting.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        while (!waiting.isEmpty() && countOpen() < MAX_VISIBLE) {
            Notification n = waiting.removeFirst();
            n.start(now);
            active.add(n);
        }

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int slot = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            Notification n = active.get(i);
            if (n.shouldClose(now)) {
                n.close();
            }
            float slide = n.slide.update();
            if (n.isFinished()) {
                active.remove(i);
                continue;
            }
            if (n.offset.get() < 0) {
                n.offset.snap(slot);
            }
            n.offset.setTarget(slot);
            float offset = n.offset.update();
            if (!n.isClosing()) {
                slot++;
            }
            float y = bottomRight
                    ? sh - MARGIN - HEIGHT - offset * (HEIGHT + GAP)
                    : MARGIN + offset * (HEIGHT + GAP);
            if (n.width < 0) {
                float text = Math.max(TextRenderer.width(n.getTitle(), true), TextRenderer.width(n.getMessage()));
                n.width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, text + 40));
            }
            float x = sw - MARGIN - n.width * Math.max(0f, slide) + (1f - Math.min(1f, slide)) * MARGIN;
            draw(g, theme, n, x, y, Math.min(1f, Math.max(0f, slide)), n.progress(now));
        }
    }

    private int countOpen() {
        int count = 0;
        for (Notification n : active) {
            if (!n.isClosing()) {
                count++;
            }
        }
        return count;
    }

    private void draw(GuiGraphics g, ThemeManager theme, Notification n, float x, float y, float alpha, float progress) {
        final float WIDTH = n.width;
        int typeColor = n.getType().getColor();
        RenderUtil.shadow(g, x, y, WIDTH, HEIGHT, 6, ColorUtil.fade(0x90000000, alpha));
        RenderUtil.roundedRect(g, x, y, WIDTH, HEIGHT, 5, ColorUtil.fade(ColorUtil.withAlpha(theme.background(), 240), alpha));
        // icon badge
        RenderUtil.circle(g, x + 15, y + HEIGHT / 2f - 1, 8, ColorUtil.fade(ColorUtil.withAlpha(typeColor, 50), alpha));
        Icons.draw(g, n.getType().getIcon(), x + 15, y + HEIGHT / 2f - 1, 7, ColorUtil.fade(typeColor, alpha));
        // text
        float textX = x + 29;
        int maxText = (int) (WIDTH - 36);
        TextRenderer.draw(g, TextRenderer.trim(n.getTitle(), maxText), textX, y + 6, ColorUtil.fade(theme.text(), alpha), true, false);
        TextRenderer.draw(g, TextRenderer.trim(n.getMessage(), maxText), textX, y + 16, ColorUtil.fade(theme.textMuted(), alpha), false, false);
        // remaining-time bar
        float barWidth = (WIDTH - 10) * (1f - progress);
        RenderUtil.roundedRect(g, x + 5, y + HEIGHT - 3, barWidth, 1.5f, 0.75f, ColorUtil.fade(typeColor, alpha));
    }
}
