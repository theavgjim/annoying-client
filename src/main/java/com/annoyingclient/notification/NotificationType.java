package com.annoyingclient.notification;

import com.annoyingclient.render.Icons.Icon;

public enum NotificationType {
    SUCCESS(0xFF22C55E, Icon.CHECK),
    ERROR(0xFFEF4444, Icon.CLOSE),
    INFO(0xFF3B82F6, Icon.INFO),
    WARNING(0xFFF59E0B, Icon.WARNING),
    MODULE_ENABLED(0xFF22C55E, Icon.PLAY),
    MODULE_DISABLED(0xFFEF4444, Icon.STOP),
    CONFIG(0xFFA855F7, Icon.CONFIG);

    private final int color;
    private final Icon icon;

    NotificationType(int color, Icon icon) {
        this.color = color;
        this.icon = icon;
    }

    public int getColor() {
        return color;
    }

    public Icon getIcon() {
        return icon;
    }
}
