package com.annoyingclient.hud.element;

import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ModeSetting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Your active potion effects: icon, name with level, and time left (blinking for the last 10 seconds). */
public class PotionEffectsHud extends HudModule {

    private static final int HARMFUL_COLOR = 0xFFF87171;
    private static final int ROW = 20;

    private final ModeSetting sort = add(new ModeSetting("Sort", "Order of the list", "Time left", "Time left", "Name", "Good first"));
    private final BooleanSetting icons = add(new BooleanSetting("Icons", "Show the effect icons", true));
    private final BooleanSetting levelOne = add(new BooleanSetting("Show Level I", "Write \"I\" for level-one effects", false));
    private final BooleanSetting colorHarmful = add(new BooleanSetting("Red For Harmful", "Harmful effects (Poison, Slowness...) in red", true));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("Hide Vanilla Icons", "Hide the icons in the top-right corner while this HUD is on", true));

    private final List<MobEffectInstance> shown = new ArrayList<>();

    public PotionEffectsHud() {
        super("Potion Effects", "Lists your active effects with time left", false, ANCHOR_END, ANCHOR_CENTER, 4, 76);
    }

    public boolean hidesVanillaIcons() {
        return isEnabled() && hideVanilla.isEnabled();
    }

    @Override
    protected boolean hasContent() {
        return mc.player != null && !mc.player.getActiveEffects().isEmpty();
    }

    private void collect(boolean editing) {
        shown.clear();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                if (e.showIcon()) {
                    shown.add(e);
                }
            }
        }
        if (shown.isEmpty() && editing) {
            // Something to place in the HUD editor.
            shown.add(new MobEffectInstance(MobEffects.SPEED, 20 * 90, 1));
            shown.add(new MobEffectInstance(MobEffects.STRENGTH, 20 * 45, 0));
            shown.add(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0));
        }
        Comparator<MobEffectInstance> order = switch (sort.getValue()) {
            case "Name" -> Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString());
            case "Good first" -> Comparator.<MobEffectInstance, Boolean>comparing(e -> !e.getEffect().value().isBeneficial())
                    .thenComparingInt(PotionEffectsHud::ticksLeft);
            default -> Comparator.comparingInt(PotionEffectsHud::ticksLeft);
        };
        shown.sort(order);
    }

    private static int ticksLeft(MobEffectInstance e) {
        return e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration();
    }

    private String title(MobEffectInstance e) {
        String name = e.getEffect().value().getDisplayName().getString();
        int level = e.getAmplifier() + 1;
        if (level == 1 && !levelOne.isEnabled()) {
            return name;
        }
        return name + " " + Component.translatable("enchantment.level." + level).getString();
    }

    private String time(MobEffectInstance e) {
        float tickRate = mc.level == null ? 20f : mc.level.tickRateManager().tickrate();
        return MobEffectUtil.formatDuration(e, 1.0f, tickRate).getString();
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        collect(editing);
        boolean withIcons = icons.isEnabled();
        float textX = withIcons ? 21 : 0;
        int widest = 0;
        for (MobEffectInstance e : shown) {
            widest = Math.max(widest, Math.max(TextRenderer.width(title(e)), TextRenderer.width(time(e))));
        }
        float w = textX + widest;
        float h = Math.max(ROW, shown.size() * ROW) - 2;
        setSize(w, h);
        drawBackground(g, w, h);
        boolean right = isRightSide();
        for (int i = 0; i < shown.size(); i++) {
            MobEffectInstance e = shown.get(i);
            float y = i * ROW;
            String title = title(e);
            String time = time(e);
            int color = colorHarmful.isEnabled() && !e.getEffect().value().isBeneficial() ? HARMFUL_COLOR : textColor();
            // Blink during the last 10 seconds, like the vanilla icons.
            boolean ending = !e.isInfiniteDuration() && e.endsWithin(200);
            int timeColor = ending && (mc.gui.getGuiTicks() / 10) % 2 == 0 ? HARMFUL_COLOR : mutedColor();
            if (withIcons) {
                float ix = right ? w - 18 : 0;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), (int) ix, (int) y, 18, 18, ARGB.white(1f));
            }
            float tx = right ? w - textX - TextRenderer.width(title) : textX;
            float dx = right ? w - textX - TextRenderer.width(time) : textX;
            TextRenderer.draw(g, title, tx, y, color);
            TextRenderer.draw(g, time, dx, y + 9, timeColor);
        }
    }
}
