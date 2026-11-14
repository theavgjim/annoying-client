package com.annoyingclient.hud.element;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.setting.BooleanSetting;

public class CpsHud extends SimpleTextHud {

    private final BooleanSetting showRight = add(new BooleanSetting("Show Right Click", "Also show right-click CPS", true));

    public CpsHud() {
        super("CPS Display", "Shows clicks per second", false, ANCHOR_START, ANCHOR_START, 4, 83);
    }

    @Override
    protected String label() {
        return "CPS";
    }

    @Override
    protected long valueKey() {
        var cps = AnnoyingClient.cps();
        return ((long) cps.getLeft() << 32) | (showRight.isEnabled() ? cps.getRight() : 0xFFFF);
    }

    @Override
    protected String formatValue() {
        var cps = AnnoyingClient.cps();
        return showRight.isEnabled() ? cps.getLeft() + " | " + cps.getRight() : Integer.toString(cps.getLeft());
    }
}
