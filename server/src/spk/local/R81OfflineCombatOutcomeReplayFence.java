package spk.local;

import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * R81 OFFLINE-ONLY identity-scoped replay ledger for one explicitly installed
 * combat projection group. Install behind R79's real producer/object proof
 * AND R80's dual-owner admission; this ledger alone does not attest a fact.
 *
 * A fact is marked consumed before its first observer executes. If a later
 * observer fails or the owner quiesces, re-delivery is refused and previous
 * writes remain committed. This is at-most-once, not a recovery protocol.
 *
 * Object identity deliberately avoids collapsing two distinct real combat
 * outcomes that happen to share attacker, victim, type and world tick.
 * The identity map retains only this isolated test fixture's events; it is
 * NOT a bounded or durable production deduplication strategy.
 */
final class R81OfflineCombatOutcomeReplayFence {
    private final IdentityHashMap<CombatOutcome,Boolean> entered=
        new IdentityHashMap<>();
    private int duplicatesDenied;

    synchronized boolean beginFirstDelivery(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        if(entered.containsKey(fact)){
            duplicatesDenied++;
            return false;
        }
        entered.put(fact,Boolean.TRUE);
        return true;
    }

    synchronized int acceptedForTest(){return entered.size();}
    synchronized int deniedForTest(){return duplicatesDenied;}
}
