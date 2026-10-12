package dev.ronpresence;
import java.lang.reflect.*;
import java.util.*;
/** Read-only access to the supported release's public client state. */
final class RonBridge {
    private static final String ROOT="com.solegendary.reignofnether.";
    private final Method player, ortho, tutorial, stage, pos, allies, bloodMoon, factionLabel;
    private final Field mode, locked, faction, selectedFaction, starting, map, players, name, ready, clock, worldTime, coop;
    private final Set<String> observed = new TreeSet<>();
    private long generation=-1;
    RonBridge() throws ReflectiveOperationException {
        factionLabel=type("faction.Faction").getMethod("getName");
        player=type("player.PlayerClientEvents").getMethod("getRTSPlayer");
        ortho=type("orthoview.OrthoviewClientEvents").getMethod("isEnabled");
        tutorial=type("tutorial.TutorialClientEvents").getMethod("isEnabled");
        stage=type("tutorial.TutorialClientEvents").getMethod("getStage");
        pos=type("startpos.StartPosClientEvents").getMethod("getPos");
        allies=type("alliance.AlliancesClient").getMethod("getAllAllies",String.class);
        bloodMoon=type("time.TimeClientEvents").getMethod("isBloodMoonActive");
        mode=type("gamemode.ClientGameModeHelper").getField("gameMode");
        locked=type("gamemode.ClientGameModeHelper").getField("gameModeLocked");
        faction=type("player.RTSPlayer").getField("faction");
        name=type("player.RTSPlayer").getField("name");
        selectedFaction=type("startpos.StartPosClientEvents").getField("selectedFaction");
        starting=type("startpos.StartPosClientEvents").getField("isStarting");
        ready=type("startpos.StartPos").getField("ready");
        map=type("rtsmap.RTSMapInfoClientEvents").getField("mapName");
        players=type("player.PlayerClientEvents").getField("rtsPlayers");
        clock=type("player.PlayerClientEvents").getField("rtsGameTicks");
        worldTime=type("time.TimeClientEvents").getField("serverNormDayTime");
        coop=type("gamerules.GameruleClient").getField("coopMode");
    }
    PresenceState read(boolean singleplayer, boolean spectator, String localName, String worldName, boolean daylightCycle) throws ReflectiveOperationException {
        if (generation!=MatchClock.generation()) { observed.clear(); generation=MatchClock.generation(); }
        Object localPlayer=player.invoke(null);
        String gameMode=((Enum<?>)mode.get(null)).name();
        boolean topDown=(boolean)ortho.invoke(null);
        var current=new TreeSet<String>();
        var list=(List<?>)players.get(null);
        synchronized(list) { for (Object p:list) current.add((String)name.get(p)); }
        boolean active=!current.isEmpty() && locked.getBoolean(null);
        if (!active) observed.clear(); else observed.addAll(current);
        PresenceState.Phase phase;
        if ((boolean)tutorial.invoke(null)) phase=PresenceState.Phase.TUTORIAL;
        else if (gameMode.equals("SANDBOX") && topDown) phase=PresenceState.Phase.SANDBOX;
        else if (starting.getBoolean(null)) phase=PresenceState.Phase.COUNTDOWN;
        else if (localPlayer!=null) phase=PresenceState.Phase.PLAYING;
        else if (active && (spectator || topDown || observed.contains(localName))) phase=PresenceState.Phase.SPECTATING;
        else if (topDown) phase=PresenceState.Phase.LOBBY;
        else phase=PresenceState.Phase.WORLD;
        Object selected=localPlayer==null ? selectedFaction.get(null) : faction.get(localPlayer);
        String race=selected==null ? "" : factionName((String)factionLabel.invoke(selected));
        Object localPos=pos.invoke(null);
        String mapName=(String)map.get(null);
        if (mapName==null) mapName="";
        if (phase==PresenceState.Phase.WORLD) mapName=worldName;
        var relations=new HashMap<String,Set<String>>();
        for (String n:observed) {
            var set=new HashSet<String>();
            for (Object ally:(Set<?>)allies.invoke(null,n)) set.add((String)ally);
            relations.put(n,set);
        }
        var roster=MatchRoster.classify(observed,localName,(a,b)->relations.getOrDefault(a,Set.of()).contains(b));
        Object tutorialStage=stage.invoke(null);
        long ticks=active ? MatchClock.ticks(clock.getLong(null)) : -1;
        String daylight=daylightCycle && !(boolean)bloodMoon.invoke(null) ? PresenceState.daylight(worldTime.getLong(null)) : "";
        return new PresenceState(phase,gameMode,race,mapName,singleplayer,localPos!=null && ready.getBoolean(localPos),
            tutorialStage==null ? "" : ((Enum<?>)tutorialStage).name(),ticks,daylight,localName,roster,coop.getBoolean(null));
    }
    static String factionName(String name) { return name == null ? "" : name.toLowerCase(Locale.ROOT); }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(ROOT+name,false,RonBridge.class.getClassLoader()); }
}
