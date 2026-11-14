package com.annoyingclient.hud.element;

public class FpsHud extends SimpleTextHud {

    public FpsHud() {
        super("FPS Display", "Shows frames per second", true, ANCHOR_START, ANCHOR_START, 4, 4);
    }

    @Override
    protected String label() {
        return "FPS";
    }

    @Override
    protected long valueKey() {
        return mc.getFps();
    }

    @Override
    protected String formatValue() {
        return Integer.toString(mc.getFps());
    }
}
