package spk.local;

final class GameClock {
    static final long TICK_MILLIS=600L;
    private long tick;
    synchronized long tick(){ return tick; }
    synchronized long advance(){ return ++tick; }
    long millisForTicks(long ticks){ return Math.multiplyExact(ticks,TICK_MILLIS); }
}
