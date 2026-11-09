package com.annoyingclient.hud.element;

import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ModeSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ClockHud extends SimpleTextHud {

    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ROOT);

    private final ModeSetting format = add(new ModeSetting("Format", "12 or 24 hour clock", "24 Hour", "24 Hour", "12 Hour"));
    private final BooleanSetting seconds = add(new BooleanSetting("Seconds", "Show seconds", false));

    public ClockHud() {
        super("Clock", "Shows the real-world time", false, ANCHOR_CENTER, ANCHOR_START, 0, 4);
    }

    @Override
    protected String label() {
        return "Time";
    }

    @Override
    protected long valueKey() {
        // Changes once per second (or minute), plus whenever the format settings change.
        long unit = seconds.isEnabled() ? 1000 : 60_000;
        return (System.currentTimeMillis() / unit) * 4 + format.getIndex() * 2 + (seconds.isEnabled() ? 1 : 0);
    }

    @Override
    protected String formatValue() {
        boolean twelve = format.is("12 Hour");
        DateTimeFormatter f = seconds.isEnabled() ? (twelve ? H12S : H24S) : (twelve ? H12 : H24);
        return LocalTime.now().format(f);
    }
}
