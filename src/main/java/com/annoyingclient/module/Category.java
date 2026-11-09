package com.annoyingclient.module;

/** Module categories, in the order they appear in the ClickGUI sidebar. */
public enum Category {
    RENDER("Render", "World and camera rendering tweaks"),
    HUD("HUD", "On-screen information elements"),
    UTILITY("Utility", "Client tools and quality of life"),
    VISUAL("Visual", "Visual customization"),
    MISC("Miscellaneous", "Everything else");

    private final String displayName;
    private final String description;

    Category(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
