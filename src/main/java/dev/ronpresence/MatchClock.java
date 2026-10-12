package dev.ronpresence;
/** Observes server synchronization; never changes RoN's clock. */
public final class MatchClock {
    private static boolean synced;
    private static long generation;
    public static void sync(Long ticks) { synced = ticks != null && ticks >= 0; }
    public static void reset() { synced = false; generation++; }
    public static long ticks(long upstream) { return synced && upstream >= 0 ? upstream : -1; }
    public static long generation() { return generation; }
    private MatchClock() {}
}
