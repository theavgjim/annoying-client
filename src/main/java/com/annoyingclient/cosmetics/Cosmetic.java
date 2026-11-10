package com.annoyingclient.cosmetics;

import java.util.List;

/** A cosmetic the client knows how to draw. The server decides who owns what and the prices. */
public record Cosmetic(String id, String name, String type, int price, String rarity) {

    /** Built-in list, used until the server's catalog arrives and for offline previews. */
    public static final List<Cosmetic> BUILTIN = List.of(
            new Cosmetic("cape_annoying", "Annoying Cape", "cape", 0, "Common"),
            new Cosmetic("cape_midnight", "Midnight Stars", "cape", 150, "Rare"),
            new Cosmetic("cape_crimson", "Crimson Flame", "cape", 250, "Rare"),
            new Cosmetic("cape_emerald", "Emerald Wave", "cape", 250, "Rare"),
            new Cosmetic("cape_mono", "Monochrome", "cape", 100, "Common"),
            new Cosmetic("cape_galaxy", "Galaxy", "cape", 600, "Epic"),
            new Cosmetic("cape_admin", "Staff Cape", "cape", -1, "Exclusive"),
            new Cosmetic("trail_hearts", "Heart Trail", "trail", 200, "Rare"),
            new Cosmetic("trail_flames", "Flame Trail", "trail", 300, "Rare"),
            new Cosmetic("trail_sparkle", "Sparkle Trail", "trail", 400, "Epic"),
            new Cosmetic("trail_notes", "Music Trail", "trail", 150, "Common"));

    public static boolean known(String id) {
        for (Cosmetic c : BUILTIN) {
            if (c.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCape() {
        return type.equals("cape");
    }

    public int rarityColor() {
        return switch (rarity) {
            case "Rare" -> 0xFF3B82F6;
            case "Epic" -> 0xFFA855F7;
            case "Exclusive" -> 0xFFF5BE3C;
            default -> 0xFF9CA3AF;
        };
    }
}
