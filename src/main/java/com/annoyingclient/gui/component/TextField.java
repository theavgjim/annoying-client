package com.annoyingclient.gui.component;

import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Single-line text input with cursor, select-all, clipboard support and horizontal scrolling. */
public class TextField extends UIComponent {

    private String placeholder;
    private final int maxLength;
    private final float height;
    private String text = "";
    private int cursor;
    private boolean allSelected;
    private boolean focused;
    private float scrollX;
    private Consumer<String> onChange = s -> { };
    private Runnable onSubmit = () -> { };
    private IntPredicate filter = c -> c >= 32 && c != 127 && c != '§';
    private Icons.Icon icon;
    private final Animation focusAnim = new Animation(0f, 16f);
    private final Animation hoverAnim = new Animation(0f, 16f);

    public TextField(String placeholder, int maxLength, float height) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.height = height;
    }

    public TextField onChange(Consumer<String> listener) {
        this.onChange = listener;
        return this;
    }

    public TextField onSubmit(Runnable listener) {
        this.onSubmit = listener;
        return this;
    }

    public TextField filter(IntPredicate predicate) {
        this.filter = predicate;
        return this;
    }

    public TextField icon(Icons.Icon icon) {
        this.icon = icon;
        return this;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getText() {
        return text;
    }

    public void setText(String value) {
        String v = value == null ? "" : value;
        if (v.length() > maxLength) {
            v = v.substring(0, maxLength);
        }
        if (!v.equals(text)) {
            text = v;
            cursor = text.length();
            allSelected = false;
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            cursor = text.length();
        } else {
            allSelected = false;
        }
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        focusAnim.setTarget(focused ? 1f : 0f);
        hoverAnim.setTarget(isHovered(mouseX, mouseY) ? 1f : 0f);
        float f = focusAnim.update();
        float h = hoverAnim.update();
        var t = theme();
        RenderUtil.roundedRect(g, x, y, width, height, 4, ColorUtil.shade(t.control(), h * 0.05f));
        if (f > 0.01f) {
            RenderUtil.roundedOutline(g, x, y, width, height, 4, 1f, ColorUtil.fade(t.accent(), f));
        }
        float textX = x + 5;
        if (icon != null) {
            Icons.draw(g, icon, textX + 3.5f, y + height / 2f, 7, t.textMuted());
            textX += 11;
        }
        float innerW = x + width - 5 - textX;
        float ty = y + height / 2f - 4;
        if (text.isEmpty() && !focused) {
            TextRenderer.draw(g, TextRenderer.trim(placeholder, (int) innerW), textX, ty, t.textDisabled());
            return;
        }
        // keep the cursor in view
        float cursorPx = TextRenderer.width(text.substring(0, cursor));
        if (cursorPx - scrollX > innerW - 2) {
            scrollX = cursorPx - innerW + 2;
        } else if (cursorPx - scrollX < 0) {
            scrollX = cursorPx;
        }
        scrollX = Math.max(0, scrollX);
        RenderUtil.scissor(g, textX, y, innerW, height);
        float drawX = textX - scrollX;
        if (allSelected && !text.isEmpty()) {
            RenderUtil.rect(g, drawX, ty - 1, TextRenderer.width(text), 10, ColorUtil.withAlpha(t.accent(), 110));
        }
        TextRenderer.draw(g, text, drawX, ty, t.text());
        if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
            RenderUtil.rect(g, drawX + cursorPx, ty - 1, 0.75f, 10, t.text());
        }
        RenderUtil.endScissor(g);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean inside = isHovered(mouseX, mouseY);
        setFocused(inside);
        if (inside && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            setText("");
            onChange.accept(text);
        }
        if (inside && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // place cursor at click position
            float textX = x + 5 + (icon != null ? 11 : 0) - scrollX;
            int best = text.length();
            for (int i = 0; i <= text.length(); i++) {
                if (textX + TextRenderer.width(text.substring(0, i)) >= mouseX) {
                    best = Math.max(0, i - (i > 0 && textX + TextRenderer.width(text.substring(0, i)) - mouseX > 2 ? 1 : 0));
                    break;
                }
            }
            cursor = MathUtil.clamp(best, 0, text.length());
        }
        return inside;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.isSelectAll()) {
            allSelected = true;
            return true;
        }
        if (event.isCopy()) {
            mc.keyboardHandler.setClipboard(text);
            return true;
        }
        if (event.isPaste()) {
            insert(mc.keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCut()) {
            mc.keyboardHandler.setClipboard(text);
            replaceAll("");
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (allSelected) {
                    replaceAll("");
                } else if (cursor > 0) {
                    int from = cursor - 1;
                    if (event.hasControlDownWithQuirk()) {
                        from = wordStart(cursor);
                    }
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                    onChange.accept(text);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) {
                    replaceAll("");
                } else if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    onChange.accept(text);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                allSelected = false;
                cursor = Math.max(0, event.hasControlDownWithQuirk() ? wordStart(cursor) : cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                allSelected = false;
                cursor = Math.min(text.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                allSelected = false;
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                allSelected = false;
                cursor = text.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.run();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                setFocused(false);
                return true;
            }
            default -> {
                // Swallow other keys while focused so they do not trigger GUI shortcuts.
                return event.key() != GLFW.GLFW_KEY_TAB;
            }
        }
    }

    private int wordStart(int from) {
        int i = from - 1;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return Math.max(0, i);
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (!focused || !filter.test(codepoint)) {
            return false;
        }
        insert(Character.toString(codepoint));
        return true;
    }

    private void insert(String s) {
        StringBuilder clean = new StringBuilder();
        s.codePoints().filter(filter).forEach(clean::appendCodePoint);
        if (allSelected) {
            text = "";
            cursor = 0;
            allSelected = false;
        }
        int room = maxLength - text.length();
        if (room <= 0 || clean.isEmpty()) {
            return;
        }
        String add = clean.length() > room ? clean.substring(0, room) : clean.toString();
        text = text.substring(0, cursor) + add + text.substring(cursor);
        cursor += add.length();
        onChange.accept(text);
    }

    private void replaceAll(String value) {
        text = value;
        cursor = text.length();
        allSelected = false;
        onChange.accept(text);
    }

    @Override
    public boolean isCapturingInput() {
        return focused;
    }

    @Override
    public void unfocus() {
        setFocused(false);
    }
}
