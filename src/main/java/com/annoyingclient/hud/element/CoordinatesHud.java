package com.annoyingclient.hud.element;

import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Locale;

public class CoordinatesHud extends HudModule {

    private final BooleanSetting decimals = add(new BooleanSetting("Decimals", "Show one decimal place", false));
    private final BooleanSetting direction = add(new BooleanSetting("Direction", "Show the direction you are facing", true));
    private final BooleanSetting otherDimension = add(new BooleanSetting("Nether/Overworld", "Show the matching coordinates in the other dimension", true));

    private final String[] lines = new String[3];
    private final String[] labels = new String[3];
    private int lineCount;
    private long lastX = Long.MIN_VALUE, lastY, lastZ;
    private int lastDir = -1;
    private int lastFlags = -1;

    public CoordinatesHud() {
        super("Coordinates", "Shows your position, facing and nether coordinates", true, ANCHOR_START, ANCHOR_START, 4, 21);
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        Entity e = mc.getCameraEntity();
        double x = e != null ? e.getX() : 0, y = e != null ? e.getY() : 64, z = e != null ? e.getZ() : 0;
        Direction facing = e != null ? e.getDirection() : Direction.NORTH;
        boolean nether = mc.level != null && mc.level.dimension() == Level.NETHER;
        boolean overworld = mc.level == null || mc.level.dimension() == Level.OVERWORLD;
        int flags = (decimals.isEnabled() ? 1 : 0) | (direction.isEnabled() ? 2 : 0) | (otherDimension.isEnabled() ? 4 : 0)
                | (nether ? 8 : 0) | (overworld ? 16 : 0);
        long qx = Math.round(x * 10), qy = Math.round(y * 10), qz = Math.round(z * 10);
        if (qx != lastX || qy != lastY || qz != lastZ || facing.ordinal() != lastDir || flags != lastFlags) {
            lastX = qx;
            lastY = qy;
            lastZ = qz;
            lastDir = facing.ordinal();
            lastFlags = flags;
            rebuild(x, y, z, facing, nether, overworld);
        }

        int labelWidth = 0;
        int valueWidth = 0;
        for (int i = 0; i < lineCount; i++) {
            labelWidth = Math.max(labelWidth, TextRenderer.width(labels[i]));
            valueWidth = Math.max(valueWidth, TextRenderer.width(lines[i]));
        }
        float lineHeight = 11;
        float w = 6 + labelWidth + 5 + valueWidth + 6;
        float h = 4 + lineCount * lineHeight + 1;
        setSize(w, h);
        drawBackground(g, w, h);
        for (int i = 0; i < lineCount; i++) {
            float ly = 4 + i * lineHeight;
            TextRenderer.draw(g, labels[i], 6, ly, accentColor(i * 150L));
            TextRenderer.draw(g, lines[i], 6 + labelWidth + 5, ly, textColor());
        }
    }

    private void rebuild(double x, double y, double z, Direction facing, boolean nether, boolean overworld) {
        lineCount = 0;
        labels[lineCount] = "XYZ";
        lines[lineCount++] = format(x) + ", " + format(y) + ", " + format(z);
        if (otherDimension.isEnabled() && (nether || overworld)) {
            double factor = nether ? 8 : 0.125;
            labels[lineCount] = nether ? "Overworld" : "Nether";
            lines[lineCount++] = format(x * factor) + ", " + format(z * factor);
        }
        if (direction.isEnabled()) {
            labels[lineCount] = "Facing";
            lines[lineCount++] = switch (facing) {
                case NORTH -> "North (-Z)";
                case SOUTH -> "South (+Z)";
                case EAST -> "East (+X)";
                case WEST -> "West (-X)";
                default -> facing.getName();
            };
        }
    }

    private String format(double v) {
        return decimals.isEnabled() ? String.format(Locale.ROOT, "%.1f", v) : Long.toString((long) Math.floor(v));
    }
}
