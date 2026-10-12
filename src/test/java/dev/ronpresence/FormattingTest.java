package dev.ronpresence;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ronpresence.PresenceState.Phase.*;
class FormattingTest {
    private static final PresenceState.Options ALL=new PresenceState.Options(true,true,true,true,true,false);
    private static final MatchRoster DUEL=MatchRoster.classify(List.of("You","Alex"),"You",(a,b)->false);
    private PresenceState sample(PresenceState.Phase phase, String mode, MatchRoster roster) {
        return new PresenceState(phase,mode,"piglins","Emerald Valley",false,false,"BUILD_TOWN_CENTRE",15080,"Night in 2:10","You",roster,false);
    }
    @Test void duelUsesOpponentAndActualTimerWithoutDiscordTimestamp() {
        var a=sample(PLAYING,"CLASSIC",DUEL).activity(ALL,"logo");
        assertEquals("Emerald Valley · 1v1 vs Alex",a.details());
        assertEquals("Piglins · 12:34",a.state());
        assertFalse(a.json().has("timestamps"));
        assertEquals("Reign of Nether",a.hover());
    }
    @Test void spectatorDuelAndTeamCards() {
        var a=sample(SPECTATING,"CLASSIC",DUEL).activity(ALL,"");
        assertEquals("Emerald Valley · Spectating 1v1",a.details());
        assertEquals("You vs Alex · 12:34",a.state());
        var teams=MatchRoster.classify(List.of("You","Steve","Alex","Sam"),"You",(x,y)->Set.of("You","Steve").contains(x)==Set.of("You","Steve").contains(y));
        var team=sample(PLAYING,"CLASSIC",teams).activity(ALL,"logo");
        assertEquals("Emerald Valley · 2v2",team.details());
        assertEquals("With: Steve\nAgainst: Alex, Sam",team.hover());
        assertEquals("12:34",sample(SPECTATING,"CLASSIC",teams).activity(ALL,"logo").state());
    }
    @Test void allNonMatchPhases() {
        assertEquals("In the main menu",sample(MENU,"",DUEL).activity(ALL,"").details());
        assertEquals("Connecting to a world",sample(CONNECTING,"",DUEL).activity(ALL,"").details());
        assertEquals("Exploring Emerald Valley",sample(WORLD,"",DUEL).activity(ALL,"").details());
        assertEquals("Emerald Valley · Preparing for battle",sample(LOBBY,"",DUEL).activity(ALL,"").details());
        assertEquals("Emerald Valley · Starting soon",sample(COUNTDOWN,"",DUEL).activity(ALL,"").details());
        assertEquals("Editing map",sample(SANDBOX,"",DUEL).activity(ALL,"").state());
        assertEquals("Build Town Centre",sample(TUTORIAL,"",DUEL).activity(ALL,"").state());
        var lobby=new PresenceState(LOBBY,"CLASSIC","none","",false,false,"",-1,"","",MatchRoster.empty(),false);
        assertEquals("Choosing a faction",lobby.activity(ALL,"").state());
        var ready=new PresenceState(LOBBY,"CLASSIC","piglins","",false,true,"",-1,"","",MatchRoster.empty(),false);
        assertEquals("Ready",ready.activity(ALL,"").details());
    }
    @Test void modeAndPrivacyTogglesApplyToVisibleAndHoverText() {
        assertEquals("Emerald Valley · Survival",sample(PLAYING,"SURVIVAL",DUEL).activity(ALL,"").details());
        assertEquals("Emerald Valley · Scenario",sample(PLAYING,"SCENARIO",DUEL).activity(ALL,"").details());
        var privateOptions=new PresenceState.Options(false,false,false,false,false,false);
        var a=sample(PLAYING,"CLASSIC",DUEL).activity(privateOptions,"logo");
        assertEquals("Playing",a.details()); assertEquals("",a.state());
        assertFalse(a.json().toString().contains("Alex")); assertFalse(a.json().toString().contains("Emerald"));
        assertFalse(a.json().toString().contains("Piglins"));
        var day=new PresenceState.Options(true,true,true,true,true,true);
        assertEquals("Piglins · 12:34 · Night in 2:10",sample(PLAYING,"CLASSIC",DUEL).activity(day,"").state());
    }
    @Test void matchClassificationDoesNotInventTeamsFromNonTransitiveAlliances() {
        assertEquals("FFA",MatchRoster.classify(List.of("a","b","c"),"a",(x,y)->false).format());
        assertEquals("Classic",MatchRoster.classify(List.of("a","b","c"),"a",(x,y)->x.equals("b")||y.equals("b")).format());
        assertEquals("Classic",MatchRoster.classify(List.of("a","b"),"a",(x,y)->x.equals("a")).format());
        assertEquals("Solo Classic",MatchRoster.classify(List.of("a"),"a",(x,y)->false).format());
        assertEquals("1v2",MatchRoster.classify(List.of("a","b","c"),"a",(x,y)->!x.equals("a")&&!y.equals("a")).format());
        assertEquals("Classic",MatchRoster.empty().format());
    }
    @Test void clockWaitsForServerSyncAndResetsBetweenMatches() {
        MatchClock.reset(); assertEquals(-1,MatchClock.ticks(99999));
        MatchClock.sync(100L); assertEquals(120,MatchClock.ticks(120));
        MatchClock.reset(); assertEquals(-1,MatchClock.ticks(130));
        assertEquals("1:02:34",PresenceState.duration(75080));
        assertEquals("0:00",PresenceState.duration(0));
        assertEquals("Night in 2:10",PresenceState.daylight(9900));
        assertEquals("Dawn in 1:45",PresenceState.daylight(22400));
    }
    @Test void utf8AndRosterLimitsPreserveCharactersAndSignalOmissions() {
        String value="한글😀".repeat(100);
        String result=Activity.truncate(value,128);
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length<=128);
        assertTrue(value.startsWith(result));
        assertFalse(Character.isHighSurrogate(result.charAt(result.length()-1)));
        assertTrue(MatchRoster.names(List.of("LongPlayerName1","LongPlayerName2","LongPlayerName3"),32).contains("more"));
        var a=new Activity(value,value,"logo",value).json();
        assertTrue(a.getAsJsonObject("assets").get("large_text").getAsString().getBytes(StandardCharsets.UTF_8).length<=128);
        assertFalse(new Activity("Menu","","","").json().has("assets"));
    }
}
