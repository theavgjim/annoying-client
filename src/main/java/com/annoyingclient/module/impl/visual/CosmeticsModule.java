package com.annoyingclient.module.impl.visual;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.StringSetting;

/** Capes and trails. Pick and buy them in the ClickGUI's Wardrobe tab. */
public class CosmeticsModule extends Module {

    /** The official Annoying Client cosmetics server. */
    public static final String DEFAULT_SERVER = "https://annoying-client.onrender.com";

    private final StringSetting serverUrl = add(new StringSetting("Server URL", "Cosmetics server address. Empty = the official server, \"off\" = no server", "", 200));
    private final BooleanSetting showOthers = add(new BooleanSetting("Show Other Players", "Show other Annoying Client players' cosmetics", true));
    private final BooleanSetting showTrails = add(new BooleanSetting("Show Trails", "Draw particle trails", true));
    private final StringSetting previewCape = add(new StringSetting("Preview Cape", "", "", 40).hide());
    private final StringSetting previewTrail = add(new StringSetting("Preview Trail", "", "", 40).hide());

    public CosmeticsModule() {
        super("Cosmetics", "Capes and particle trails (Wardrobe tab in the ClickGUI)", Category.VISUAL, true, KeybindSetting.NONE);
    }

    @Override
    protected boolean listedByDefault() {
        return false;
    }

    /** The server to use: the setting, else ANNOYING_COSMETICS_URL (tests), else the official server; "off" disables it. */
    public String serverUrl() {
        String v = serverUrl.getValue().trim();
        if (v.equalsIgnoreCase("off")) {
            return "";
        }
        if (!v.isEmpty()) {
            return v;
        }
        String env = System.getenv("ANNOYING_COSMETICS_URL");
        return env != null && !env.isBlank() ? env.trim() : DEFAULT_SERVER;
    }

    /** What the Wardrobe's address field shows. */
    public String serverUrlSetting() {
        return serverUrl.getValue();
    }

    public void setServerUrl(String url) {
        serverUrl.setValue(url);
    }

    public boolean showOthers() {
        return showOthers.isEnabled();
    }

    public boolean showTrails() {
        return showTrails.isEnabled();
    }

    public String previewCape() {
        return previewCape.getValue();
    }

    public String previewTrail() {
        return previewTrail.getValue();
    }

    public void setPreview(String type, String id) {
        (type.equals("cape") ? previewCape : previewTrail).setValue(id == null ? "" : id);
    }
}
