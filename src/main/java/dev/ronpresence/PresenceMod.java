package dev.ronpresence;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(PresenceMod.ID)
public final class PresenceMod {
    public static final String ID = "ron_discord_presence";

    public PresenceMod() {
        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PresenceConfig.SPEC, "ron-discord-presence-client.toml");
        }
    }
}
