package spk.local;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * R83 OFFLINE AUDIT ONLY. A short-lived, in-memory observation of when
 * individual projection callbacks started and returned. This is NOT a
 * transactional checkpoint: RETURNED does not prove a durable commit,
 * and UNCERTAIN does not prove a write occurred.
 *
 * The local sequence is for human-readable diagnostics ONLY. It is not
 * an authenticated event ID, is not persisted, and cannot be used as a
 * replay key across JVMs, group instances or actor generations.
 *
 * Install only behind the R79 source-attested PvP bus callback, R80
 * paired-actor owner gate and R81 at-most-once delivery guard.
 */
final class R83OfflineCombatCheckpointAudit {
    enum Consumer { OBJECTIVE, MATCH, COLLECTION }
    enum Phase { ABSENT, ENTERED, RETURNED, UNCERTAIN }
    enum RecoveryDecision { DENY_AUTOMATIC_REPLAY }

    private static final class EventRecord {
        final long diagnosticSequence;
        final EnumMap<Consumer,Phase> phases =
            new EnumMap<>(Consumer.class);

        EventRecord(long sequence) {
            diagnosticSequence=sequence;
        }
    }

    private final IdentityHashMap<CombatOutcome,EventRecord> events =
        new IdentityHashMap<>();
    private long nextSequence;
    private int refusedRepeatBegins;

    synchronized long begin(CombatOutcome fact,Consumer consumer) {
        Objects.requireNonNull(fact,"fact");
        Objects.requireNonNull(consumer,"consumer");
        EventRecord record=events.get(fact);
        if(record==null) {
            record=new EventRecord(++nextSequence);
            events.put(fact,record);
        }
        if(record.phases.containsKey(consumer)) {
            refusedRepeatBegins++;
            throw new IllegalStateException(
                "R83_CHECKPOINT_DENIED repeated consumer entry="+consumer
            );
        }
        record.phases.put(consumer,Phase.ENTERED);
        return record.diagnosticSequence;
    }

    synchronized void returned(CombatOutcome fact,Consumer consumer) {
        transition(fact,consumer,Phase.RETURNED);
    }

    synchronized void uncertain(CombatOutcome fact,Consumer consumer) {
        transition(fact,consumer,Phase.UNCERTAIN);
    }

    private void transition(CombatOutcome fact,Consumer consumer,Phase to) {
        EventRecord record=events.get(Objects.requireNonNull(fact,"fact"));
        if(record==null || record.phases.get(Objects.requireNonNull(
            consumer,"consumer"))!=Phase.ENTERED)
            throw new IllegalStateException(
                "R83_CHECKPOINT_DENIED invalid phase transition"
            );
        record.phases.put(consumer,to);
    }

    synchronized Phase phase(CombatOutcome fact,Consumer consumer) {
        EventRecord record=events.get(Objects.requireNonNull(fact,"fact"));
        return record==null ? Phase.ABSENT :
            record.phases.getOrDefault(
                Objects.requireNonNull(consumer,"consumer"),Phase.ABSENT
            );
    }

    synchronized long diagnosticSequence(CombatOutcome fact) {
        EventRecord record=events.get(Objects.requireNonNull(fact,"fact"));
        return record==null ? 0L : record.diagnosticSequence;
    }

    synchronized int factCountForTest() {return events.size();}
    synchronized int deniedRepeatCountForTest() {return refusedRepeatBegins;}

    /**
     * Even if all callbacks RETURNED, process death can occur before any
     * durable checkpoint publication. No restart/replay authorization is
     * derivable from these volatile records.
     */
    RecoveryDecision recoveryDecision(CombatOutcome fact) {
        Objects.requireNonNull(fact,"fact");
        return RecoveryDecision.DENY_AUTOMATIC_REPLAY;
    }

    static String candidateTuple(CombatOutcome fact) {
        Objects.requireNonNull(fact,"fact");
        return fact.attacker()+"|"+fact.victim()+"|"+
            fact.context()+"|"+fact.type()+"|"+
            fact.worldTick()+"|"+fact.sourceAuthority();
    }
}
