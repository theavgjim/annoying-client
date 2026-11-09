package com.annoyingclient.hud.element;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingHud extends SimpleTextHud {

    public PingHud() {
        super("Ping Display", "Shows your latency to the server", false, ANCHOR_START, ANCHOR_START, 4, 84);
    }

    private int latency() {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            return -1;
        }
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    protected String label() {
        return "Ping";
    }

    @Override
    protected long valueKey() {
        return latency() + (mc.isLocalServer() ? 100_000L : 0);
    }

    @Override
    protected String formatValue() {
        if (mc.isLocalServer()) {
            return "Singleplayer";
        }
        int ping = latency();
        return ping < 0 ? "--" : ping + " ms";
    }
}
