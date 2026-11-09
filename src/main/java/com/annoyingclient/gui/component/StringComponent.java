package com.annoyingclient.gui.component;

import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.StringSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

public class StringComponent extends SettingComponent<StringSetting> {

    private final TextField field;

    public StringComponent(StringSetting setting, String label) {
        super(setting, label);
        field = new TextField(setting.getDefaultValue(), setting.getMaxLength(), 14);
        field.setText(setting.getValue());
        field.onChange(setting::setValue);
    }

    @Override
    public float getHeight() {
        return 18;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        if (!field.isFocused() && !field.getText().equals(setting.getValue())) {
            field.setText(setting.getValue()); // changed elsewhere (e.g. profile load)
        }
        TextRenderer.draw(g, label, x, y + 5, theme().text());
        float fw = Math.min(width * 0.55f, 130);
        field.setBounds(x + width - fw, y + 2, fw);
        field.render(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        return field.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return field.keyPressed(event);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return field.charTyped(codepoint);
    }

    @Override
    public boolean isCapturingInput() {
        return field.isCapturingInput();
    }

    @Override
    public void unfocus() {
        field.unfocus();
    }
}
