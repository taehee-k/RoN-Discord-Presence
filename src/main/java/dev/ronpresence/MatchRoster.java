package dev.ronpresence;
import java.util.*;
import java.util.function.BiPredicate;
/** Classifies only reciprocal, complete alliance groups as teams. */
public record MatchRoster(List<List<String>> teams, boolean known) {
    public static MatchRoster empty() { return new MatchRoster(List.of(), false); }
    public static MatchRoster classify(Collection<String> players, String local, BiPredicate<String,String> allied) {
        var remaining = new TreeSet<>(players);
        var groups = new ArrayList<List<String>>();
        boolean valid = true;
        while (!remaining.isEmpty()) {
            var group = new TreeSet<String>();
            var queue = new ArrayDeque<String>(); queue.add(remaining.first());
            while (!queue.isEmpty()) {
                String a = queue.remove();
                if (!group.add(a)) continue;
                remaining.remove(a);
                for (String b : players) if (!a.equals(b) && (allied.test(a,b) || allied.test(b,a))) queue.add(b);
            }
            for (String a : group) for (String b : group)
                if (!a.equals(b) && (!allied.test(a,b) || !allied.test(b,a))) valid = false;
            groups.add(List.copyOf(group));
        }
        groups.sort(Comparator.comparing((List<String> g) -> !g.contains(local)).thenComparing(g -> g.get(0)));
        return new MatchRoster(List.copyOf(groups), valid && !groups.isEmpty());
    }
    public String format() {
        if (!known) return "Classic";
        int count = teams.stream().mapToInt(List::size).sum();
        if (count == 1) return "Solo Classic";
        if (teams.size() == 2) return teams.get(0).size() + "v" + teams.get(1).size();
        if (teams.size() > 2 && teams.stream().allMatch(g -> g.size() == 1)) return "FFA";
        return "Classic";
    }
    public boolean duel() { return format().equals("1v1"); }
    public String opponent(String local) {
        return duel() ? teams.stream().flatMap(List::stream).filter(n -> !n.equals(local)).findFirst().orElse("") : "";
    }
    public String versus() { return duel() ? teams.get(0).get(0) + " vs " + teams.get(1).get(0) : ""; }
    public String hover(String local, boolean spectating) {
        if (teams.isEmpty()) return "";
        if (spectating) {
            var lines = new ArrayList<String>();
            int budget = Math.max(10, 120 / teams.size() - 10);
            for (int i=0; i<teams.size(); i++) lines.add("Team " + (i+1) + ": " + names(teams.get(i), budget));
            return Activity.truncate(String.join("\n", lines),128);
        }
        var allies = new ArrayList<String>(); var enemies = new ArrayList<String>();
        for (var team : teams) for (String n : team) if (!n.equals(local)) {
            if (known && team.contains(local)) allies.add(n); else enemies.add(n);
        }
        if (!known) return "Players: " + names(enemies, 115);
        int budget = allies.isEmpty() || enemies.isEmpty() ? 115 : 49;
        return PresenceState.join("\n", allies.isEmpty() ? "" : "With: " + names(allies,budget),
                enemies.isEmpty() ? "" : "Against: " + names(enemies,budget));
    }
    static String names(List<String> names, int budget) {
        for (int count=names.size(); count>=0; count--) {
            String head = String.join(", ", names.subList(0,count));
            String tail = count==names.size() ? "" : (count==0 ? "" : ", ") + "+" + (names.size()-count) + " more";
            String result = head + tail;
            if (Activity.truncate(result,budget).equals(result)) return result;
        }
        return "";
    }
}
