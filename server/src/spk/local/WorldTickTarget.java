package spk.local;

/** Transitional adapter: existing proven per-player tick logic executes on the one WorldPulse thread. */
interface WorldTickTarget {
    EntityId ownerId();
    long ownerGeneration();
    void onWorldTick(long worldTick,long nowMillis) throws Exception;
}
