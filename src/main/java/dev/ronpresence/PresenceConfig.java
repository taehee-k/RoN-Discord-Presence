package dev.ronpresence;
import net.minecraftforge.common.ForgeConfigSpec;
public final class PresenceConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, SHOW_MENUS, SHOW_FACTION, SHOW_MAP, SHOW_GAME_TYPE, SHOW_PLAYER_NAMES, SHOW_TIMER, SHOW_DAYLIGHT;
    public static final ForgeConfigSpec.ConfigValue<String> APPLICATION_ID, LARGE_IMAGE;
    static {
        var b=new ForgeConfigSpec.Builder();
        ENABLED=b.comment("Requires the Discord desktop app.").define("enabled",true);
        APPLICATION_ID=b.comment("Public Discord application ID. No bot or token needed.").define("applicationId","1557642898630123560");
        SHOW_MENUS=b.define("showMenus",true);
        SHOW_FACTION=b.define("showFaction",true);
        SHOW_MAP=b.comment("Share the map name, or singleplayer world name outside RTS.").define("showMap",true);
        SHOW_GAME_TYPE=b.define("showGameType",true);
        SHOW_PLAYER_NAMES=b.comment("Share player names: 1v1 on the card, others on image hover.").define("showPlayerNames",true);
        SHOW_TIMER=b.comment("RoN's synchronized match timer, refreshed about every five seconds.").define("showTimer",true);
        SHOW_DAYLIGHT=b.comment("Optional time until dawn/night. No day number.").define("showDaylight",false);
        LARGE_IMAGE=b.comment("Public image URL or Discord asset key. Blank disables image and hover.")
            .define("largeImage","https://cdn.modrinth.com/data/FdbVHPk2/7cbaf56091f614a3d2d4df136a59c89fcda0b43c_96.webp");
        SPEC=b.build();
    }
    public static PresenceState.Options options() { return new PresenceState.Options(SHOW_MAP.get(),SHOW_FACTION.get(),SHOW_GAME_TYPE.get(),SHOW_PLAYER_NAMES.get(),SHOW_TIMER.get(),SHOW_DAYLIGHT.get()); }
    private PresenceConfig() {}
}
