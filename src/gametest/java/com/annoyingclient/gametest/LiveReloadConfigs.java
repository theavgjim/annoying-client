package com.annoyingclient.gametest;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/** Real Cloth Config and YACL configs, used to prove saved files reach the running mod without a restart. */
public final class LiveReloadConfigs {

    @Config(name = "annoying-test-cloth")
    public static final class ClothConfig implements ConfigData {
        public ClothConfig() {
        }

        public boolean fancyMode = false;
        public int amount = 1;
    }

    public static final class YaclConfig {
        public YaclConfig() {
        }

        @SerialEntry
        public boolean fancyMode = false;
        @SerialEntry
        public int amount = 1;
    }

    static final Path YACL_FILE = FabricLoader.getInstance().getConfigDir().resolve("annoying-test-yacl.json");
    static final Path CLOTH_FILE = FabricLoader.getInstance().getConfigDir().resolve("annoying-test-cloth.json");

    static ConfigHolder<ClothConfig> cloth;
    static ConfigClassHandler<YaclConfig> yacl;

    private LiveReloadConfigs() {
    }

    /** Registers both configs and writes their files with the defaults. */
    static void register() {
        if (cloth == null) {
            cloth = AutoConfig.register(ClothConfig.class, GsonConfigSerializer::new);
        }
        cloth.getConfig().fancyMode = false;
        cloth.getConfig().amount = 1;
        cloth.save();
        if (yacl == null) {
            yacl = ConfigClassHandler.createBuilder(YaclConfig.class)
                    .id(Identifier.fromNamespaceAndPath("annoyingclient-gametest", "live_reload"))
                    .serializer(h -> GsonConfigSerializerBuilder.create(h).setPath(YACL_FILE).build())
                    .build();
        }
        yacl.instance().fancyMode = false;
        yacl.instance().amount = 1;
        yacl.save();
    }
}
