package com.annoyingclient.hud.element;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.StringSetting;
import net.minecraft.client.gui.GuiGraphics;

/** The ANNOYING CLIENT watermark with an animated accent gradient. */
public class WatermarkHud extends HudModule {

    private final StringSetting text = add(new StringSetting("Text", "Watermark text", AnnoyingClient.BRANDING, 32));
    private final BooleanSetting showVersion = add(new BooleanSetting("Show Version", "Show the client version", true));
    private final BooleanSetting gradient = add(new BooleanSetting("Gradient Text", "Animate the text with the accent colors", true));

    private String cachedVersion;
    private String cachedLabel = "";
    private String[] labelChars = new String[0];

    public WatermarkHud() {
        super("Watermark", "Shows the ANNOYING CLIENT watermark", true, ANCHOR_START, ANCHOR_START, 4, 4);
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        String label = text.getValue().isEmpty() ? AnnoyingClient.BRANDING : text.getValue();
        if (cachedVersion == null) {
            AnnoyingClient client = AnnoyingClient.getInstance();
            String v = client != null ? client.getVersion() : "dev";
            int plus = v.indexOf('+');
            cachedVersion = "v" + (plus > 0 ? v.substring(0, plus) : v);
        }
        int labelWidth = TextRenderer.width(label, true);
        int versionWidth = showVersion.isEnabled() ? TextRenderer.width(cachedVersion) + 5 : 0;
        float w = 10 + labelWidth + versionWidth + 6;
        float h = 15;
        setSize(w, h);
        drawBackground(g, w, h);
        RenderUtil.roundedRect(g, 3, 3, 2.5f, h - 6, 1.25f, accentColor(0));

        float x = 9;
        if (gradient.isEnabled()) {
            if (!label.equals(cachedLabel)) {
                cachedLabel = label;
                labelChars = new String[label.length()];
                for (int i = 0; i < label.length(); i++) {
                    labelChars[i] = String.valueOf(label.charAt(i));
                }
            }
            for (int i = 0; i < labelChars.length; i++) {
                String ch = labelChars[i];
                TextRenderer.drawBold(g, ch, x, 4, accentColor(i * 90L));
                x += TextRenderer.width(ch, true);
            }
        } else {
            TextRenderer.drawBold(g, label, x, 4, accentColor(0));
            x += labelWidth;
        }
        if (showVersion.isEnabled()) {
            TextRenderer.draw(g, cachedVersion, x + 5, 4, mutedColor());
        }
    }
}
