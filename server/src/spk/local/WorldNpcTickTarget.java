package spk.local;

/**
 * NPC-owned logical tick adapter for the one authoritative WorldPulse.
 *
 * Unlike WorldTickTarget this contract is not player-generation-owned.
 * Canonical NPC identity is revalidated by World/AI layers around each tick.
 */
interface WorldNpcTickTarget {
    EntityId npcId();
    void onWorldNpcTick(
        long worldTick,
        long nowMillis
    ) throws Exception;
}
