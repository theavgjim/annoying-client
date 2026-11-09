package com.annoyingclient.hud.element;

import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Armor pieces (and optionally held items) with remaining durability. */
public class ArmorHud extends HudModule {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack[] PREVIEW = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};

    private final ModeSetting layout = add(new ModeSetting("Layout", "Stack items vertically or horizontally", "Vertical", "Vertical", "Horizontal"));
    private final ModeSetting durability = add(new ModeSetting("Durability", "How to show durability", "Percent", "Percent", "Value", "None"));
    private final BooleanSetting heldItems = add(new BooleanSetting("Held Items", "Also show main hand and offhand items", false));

    private final ItemStack[] items = new ItemStack[6];
    private final String[] texts = new String[6];
    private final int[] lastDamage = new int[6];

    public ArmorHud() {
        super("Armor HUD", "Shows your armor and its durability", false, ANCHOR_END, ANCHOR_CENTER, 4, 0);
    }

    @Override
    protected boolean defaultBackground() {
        return false;
    }

    @Override
    protected boolean hasContent() {
        return collect(false) > 0;
    }

    /** Fills {@link #items}; in the editor an empty inventory shows diamond armor so the element can be placed. */
    private int collect(boolean editing) {
        Player player = mc.player;
        int count = 0;
        if (player != null) {
            if (heldItems.isEnabled()) {
                count = put(count, player.getMainHandItem());
                count = put(count, player.getOffhandItem());
            }
            for (EquipmentSlot slot : ARMOR) {
                count = put(count, player.getItemBySlot(slot));
            }
        }
        if (count == 0 && editing) {
            for (ItemStack preview : PREVIEW) {
                count = put(count, preview);
            }
        }
        return count;
    }

    private int put(int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return index;
        }
        items[index] = stack;
        int key = stack.isDamageableItem() ? stack.getDamageValue() : -1 - stack.getCount();
        key = key * 4 + durability.getIndex();
        if (texts[index] == null || lastDamage[index] != key) {
            lastDamage[index] = key;
            texts[index] = describe(stack);
        }
        return index + 1;
    }

    private String describe(ItemStack stack) {
        if (durability.is("None")) {
            return "";
        }
        if (!stack.isDamageableItem()) {
            return stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "";
        }
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        if (durability.is("Value")) {
            return Integer.toString(remaining);
        }
        return Math.round(remaining * 100f / stack.getMaxDamage()) + "%";
    }

    private int durabilityColor(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return textColor();
        }
        float f = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
        if (f > 0.6f) {
            return 0xFF4ADE80;
        }
        return f > 0.25f ? 0xFFFACC15 : 0xFFF87171;
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        int count = collect(editing);
        boolean vertical = layout.is("Vertical");
        float w;
        float h;
        if (vertical) {
            int textWidth = 0;
            for (int i = 0; i < count; i++) {
                textWidth = Math.max(textWidth, TextRenderer.width(texts[i]));
            }
            w = 18 + (textWidth > 0 ? textWidth + 3 : 0);
            h = Math.max(16, count * 17 - 1);
        } else {
            w = Math.max(16, count * 22 - 6);
            h = durability.is("None") ? 16 : 26;
        }
        setSize(w, h);
        drawBackground(g, w, h);
        boolean right = isRightSide() && vertical;
        for (int i = 0; i < count; i++) {
            ItemStack stack = items[i];
            String text = texts[i];
            if (vertical) {
                float y = i * 17;
                float itemX = right ? w - 16 : 0;
                g.renderItem(stack, (int) itemX, (int) y);
                if (!text.isEmpty()) {
                    float tx = right ? itemX - 3 - TextRenderer.width(text) : 19;
                    TextRenderer.draw(g, text, tx, y + 4, durabilityColor(stack));
                }
            } else {
                float x = i * 22;
                g.renderItem(stack, (int) x, 0);
                if (!text.isEmpty()) {
                    TextRenderer.drawScaled(g, text, x + 8 - TextRenderer.width(text) * 0.375f, 18, 0.75f, durabilityColor(stack), false);
                }
            }
        }
    }
}
