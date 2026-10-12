package dev.ronpresence;

import com.mojang.logging.LogUtils;
import dev.ronpresence.ipc.DiscordClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = PresenceMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientPresence {
    private static final Logger LOG = LogUtils.getLogger();
    private static DiscordClient discord;
    private static RonBridge bridge;
    private static boolean bridgeFailed, warnedId;
    private static int ticks;
    private static Object connection;


    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || !PresenceConfig.SPEC.isLoaded()) return;
        if (discord == null) {
            LOG.info("RoN Discord Presence started; enabled={}, applicationId={}, showMenus={}",
                    PresenceConfig.ENABLED.get(), PresenceConfig.APPLICATION_ID.get(), PresenceConfig.SHOW_MENUS.get());
            discord = new DiscordClient(LOG::info);
            Runtime.getRuntime().addShutdownHook(new Thread(discord::close, "ron-presence-shutdown"));
        }
        String appId = PresenceConfig.APPLICATION_ID.get().trim();
        if (!PresenceConfig.ENABLED.get() || !DiscordClient.validApplicationId(appId)) {
            discord.update("", null);
            if (PresenceConfig.ENABLED.get() && !warnedId) {
                LOG.info("Discord presence needs a numeric applicationId in config/ron-discord-presence-client.toml.");
                warnedId = true;
            }
            return;
        }
        warnedId = false;
        Minecraft mc = Minecraft.getInstance();
        if (connection != mc.getConnection()) {
            connection = mc.getConnection();
            bridge = null;
        }
        PresenceState state;
        if (mc.level == null || mc.player == null) {
            if (!PresenceConfig.SHOW_MENUS.get()) {
                discord.update(appId, null);
                bridge = null;
                return;
            }
            state = PresenceState.basic(mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen || mc.getConnection() != null
                    ? PresenceState.Phase.CONNECTING : PresenceState.Phase.MENU, false, "");
        } else {
            String worldName = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
                    ? mc.getSingleplayerServer().getWorldData().getLevelName() : "";
            state = PresenceState.basic(PresenceState.Phase.WORLD, mc.hasSingleplayerServer(), worldName);
            if (!bridgeFailed) {
                try {
                    if (bridge == null) bridge = new RonBridge();
                    state = bridge.read(mc.hasSingleplayerServer(), mc.player.isSpectator(),
                            mc.player.getGameProfile().getName(), worldName, mc.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                    bridgeFailed = true;
                    LOG.warn("RoN client API changed; using generic presence for this session.", error);
                }
            }
        }
        discord.update(appId, state.activity(PresenceConfig.options(), PresenceConfig.LARGE_IMAGE.get().trim()));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        MatchClock.reset();
        bridge = null;
        bridgeFailed = false;
        connection = null;
        if (discord != null) discord.update("", null);
    }
}
