package com.annoyingclient.module.impl.visual;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Shows the hidden half of hunger: saturation (gold outlines on the hunger bar, which run out before the
 * drumsticks do) and what the food in your hand would restore (flashing drumsticks). Uses only values the
 * game already sends to your client.
 */
public class SaturationModule extends Module {

    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final int GOLD = 0xFFFFC21A;

    private final BooleanSetting overlay = add(new BooleanSetting("Saturation Outline", "Gold outlines on the hunger bar show your saturation", true));
    private final BooleanSetting preview = add(new BooleanSetting("Food Preview", "Flashing drumsticks show what the food in your hand restores", true));
    private final BooleanSetting numbers = add(new BooleanSetting("Show Numbers", "Write the saturation (and the food's gain) above the hunger bar", false));

    public SaturationModule() {
        super("Saturation", "Shows saturation and food gain on the hunger bar", Category.VISUAL, false, KeybindSetting.NONE);
    }

    /** The food a held item restores, if any (main hand first). */
    private static FoodProperties heldFood(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                return food;
            }
        }
        return null;
    }

    /**
     * Draws on top of the vanilla hunger bar. {@code top} is its y, {@code right} its right edge; icon
     * {@code i} (0 = rightmost) sits at x = right - i * 8 - 9.
     */
    public void renderOnFoodBar(GuiGraphics g, Player player, int top, int right, int guiTicks) {
        FoodData data = player.getFoodData();
        int food = data.getFoodLevel();
        float saturation = data.getSaturationLevel();
        FoodProperties held = preview.isEnabled() ? heldFood(player) : null;
        int newFood = food;
        float newSaturation = saturation;
        if (held != null && (food < 20 || held.canAlwaysEat())) {
            // Same rule as FoodData.eat: saturation can't exceed the food level.
            newFood = Math.min(20, food + held.nutrition());
            newSaturation = Math.min(newFood, saturation + held.saturation());
        }
        float pulse = 0.35f + 0.35f * (float) Math.sin(guiTicks * 0.25f);
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int point = i * 2;
            // Drumsticks the held food would add (flashing).
            if (newFood > food && point + 1 > food && point < newFood) {
                Identifier sprite = point + 1 < newFood ? FOOD_FULL : FOOD_HALF;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, top, 9, 9, ARGB.white(pulse + 0.25f));
            }
            if (overlay.isEnabled()) {
                outline(g, x, top, saturation - point, GOLD);
                if (newSaturation > saturation) {
                    float gained = Math.min(newSaturation, point + 2) - Math.max(saturation, point);
                    if (gained > 0) {
                        outline(g, x, top, Math.min(2, newSaturation - point), ARGB.color(Math.round(255 * (pulse + 0.25f)), GOLD));
                    }
                }
            }
        }
        if (numbers.isEnabled()) {
            String text = String.format(Locale.ROOT, "%.1f", saturation);
            if (newSaturation > saturation || newFood > food) {
                text += String.format(Locale.ROOT, "  +%d  +%.1f", newFood - food, newSaturation - saturation);
            }
            TextRenderer.drawScaled(g, text, right - TextRenderer.width(text) * 0.75f, top - 7, 0.75f, GOLD, false);
        }
    }

    /** A 1-pixel gold frame around a drumstick: full for 2 saturation points, the right half for 1. */
    private static void outline(GuiGraphics g, int x, int y, float amount, int color) {
        if (amount <= 0) {
            return;
        }
        boolean full = amount >= 2;
        int left = full ? x : x + 4;
        g.fill(left, y, x + 9, y + 1, color);
        g.fill(left, y + 8, x + 9, y + 9, color);
        g.fill(x + 8, y + 1, x + 9, y + 8, color);
        if (full) {
            g.fill(x, y + 1, x + 1, y + 8, color);
        }
    }
}
