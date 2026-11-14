package com.annoyingclient.module.impl.visual;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.event.KeyInputEvent;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.notification.NotificationType;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Screenshot management: a toast with the file name whenever a screenshot is saved, a key that
 * opens the screenshots folder, and a count of screenshots taken this session.
 */
public class ScreenshotModule extends Module {

    private final BooleanSetting notify = add(new BooleanSetting("Notify", "Show a notification when a screenshot is saved", true));
    private final KeybindSetting openFolderKey = add(new KeybindSetting("Open Folder Key", "Opens the screenshots folder", KeybindSetting.NONE));

    private int sessionCount;

    public ScreenshotModule() {
        super("Screenshot Manager", "Screenshot notifications and quick access to the screenshots folder", Category.VISUAL, true, KeybindSetting.NONE);
        AnnoyingClient.events().subscribe(KeyInputEvent.class, e -> {
            if (isEnabled() && e.isPress() && mc.screen == null && openFolderKey.matchesKey(e.getKey())) {
                openFolder();
            }
        });
    }

    public Path getFolder() {
        return new File(mc.gameDirectory, "screenshots").toPath();
    }

    public void openFolder() {
        Path folder = getFolder();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openPath(folder);
        } catch (IOException | RuntimeException e) {
            AnnoyingClient.notifications().error("Cannot open folder", folder.toString());
        }
    }

    /** Called (from any thread) by the screenshot mixin with the chat message vanilla produced. */
    public void onScreenshotResult(boolean success, String message) {
        if (!isEnabled()) {
            return;
        }
        mc.execute(() -> {
            if (success) {
                sessionCount++;
            }
            if (notify.isEnabled()) {
                AnnoyingClient.notifications().push(success ? NotificationType.SUCCESS : NotificationType.ERROR,
                        success ? "Screenshot saved" : "Screenshot failed", message);
            }
        });
    }

    @Override
    public String getDisplayInfo() {
        return sessionCount == 0 ? "" : Integer.toString(sessionCount);
    }

}
