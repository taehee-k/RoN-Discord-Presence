package dev.ronpresence;
import java.util.*;
public record PresenceState(Phase phase, String mode, String faction, String map, boolean singleplayer,
        boolean ready, String stage, long ticks, String daylight, String local, MatchRoster roster, boolean coop) {
    public enum Phase { MENU, CONNECTING, WORLD, LOBBY, COUNTDOWN, PLAYING, SPECTATING, SANDBOX, TUTORIAL }
    public record Options(boolean map, boolean faction, boolean gameType, boolean names, boolean timer, boolean daylight) {}
    public static PresenceState basic(Phase phase, boolean singleplayer, String world) {
        return new PresenceState(phase,"","",world,singleplayer,false,"",-1,"","",MatchRoster.empty(),false);
    }
    public Activity activity(Options opt, String image) {
        String place = opt.map ? map : "";
        String race = opt.faction && !faction.equalsIgnoreCase("none") ? title(faction) : "";
        String duration = opt.timer && ticks >= 0 ? duration(ticks) : "";
        String details, state;
        String hover = "Reign of Nether";
        switch (phase) {
            case MENU -> { details="In the main menu"; state="Reign of Nether"; }
            case CONNECTING -> { details="Connecting to a world"; state="Reign of Nether"; }
            case WORLD -> { details=place.isBlank() ? "Exploring a world" : "Exploring " + place; state=singleplayer ? "Singleplayer" : "Multiplayer"; }
            case LOBBY -> { details=join(" · ",place,ready ? "Ready" : "Preparing for battle"); state=race.isBlank() ? (faction.isBlank() || faction.equalsIgnoreCase("none") ? "Choosing a faction" : "") : race; }
            case COUNTDOWN -> { details=join(" · ",place,"Starting soon"); state=race; }
            case SANDBOX -> { details=join(" · ",place,"Sandbox"); state="Editing map"; }
            case TUTORIAL -> { details="Playing the tutorial"; state=title(stage); }
            default -> {
                boolean spectating=phase==Phase.SPECTATING;
                String kind = switch(mode) {
                    case "SURVIVAL" -> coop ? "Co-op Survival" : "Survival";
                    case "SCENARIO" -> "Scenario";
                    case "CLASSIC" -> roster.format();
                    default -> "";
                };
                if (!opt.gameType) kind="";
                String matchup = !spectating && opt.names && mode.equals("CLASSIC") && roster.duel() ? "vs " + roster.opponent(local) : "";
                String label = join(" ",kind,matchup);
                if (spectating) label=join(" ","Spectating",label.equals("Classic") ? "" : label);
                else if (label.isBlank()) label="Playing";
                details=join(" · ",place,label);
                String names = spectating && opt.names && mode.equals("CLASSIC") && roster.duel() ? roster.versus() : "";
                state=join(" · ",spectating ? names : race,duration,opt.daylight ? daylight : "");
                if (opt.names && !(mode.equals("CLASSIC") && roster.duel())) hover=roster.hover(local,spectating);
            }
        }
        return new Activity(details,state,image,hover);
    }
    public static String duration(long ticks) {
        long s=Math.max(0,ticks)/20;
        return s>=3600 ? String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60) : String.format(Locale.ROOT,"%d:%02d",s/60,s%60);
    }
    public static String daylight(long ticks) {
        long t=Math.floorMod(ticks,24000);
        boolean day=t>500 && t<=12500;
        long remaining=Math.floorMod((day ? 12500 : 500)-t,24000);
        return (day ? "Night in " : "Dawn in ") + duration(remaining);
    }
    static String join(String separator, String... parts) { return String.join(separator,Arrays.stream(parts).filter(s -> s!=null && !s.isBlank()).toList()); }
    public static String title(String value) {
        if (value==null || value.isBlank() || value.equalsIgnoreCase("none")) return "";
        var result=new ArrayList<String>();
        for (String word:value.toLowerCase(Locale.ROOT).replace('_',' ').split(" +")) if (!word.isEmpty()) result.add(Character.toUpperCase(word.charAt(0))+word.substring(1));
        return String.join(" ",result);
    }
}
