package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded-size, process-local cross-tab of recipient spawn-origin observation
 * and the FIRST failing/default sequential terminal delivery boundary.
 *
 * Distinct from the existing origin × individual-consumer tally: a first
 * blocking boundary describes fail-fast chronology, not just stage outcomes.
 * It remains a purely negative/non-granting diagnostic after detailed report
 * eviction; NONE of these counts is a persisted outbox, reward ACK or
 * evidence of original SpawnPK server killer attribution.
 */
final class LocalLabOriginFirstFailureBoundaryTally {
    private final EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
    > crossCounts=new EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
    >(LocalLabSettledDeathEnvelope.OriginStatus.class);

    LocalLabOriginFirstFailureBoundaryTally(){
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values()){
            EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long> row=
                new EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>(
                    LocalLabTerminalFailureBoundaryTally.Boundary.class);
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values())
                row.put(boundary,0L);
            crossCounts.put(origin,row);
        }
    }

    void acceptValidated(
        LocalLabSettledDeathEnvelope.OriginStatus origin,
        LocalLabTerminalFailureBoundaryTally.Boundary boundary
    ){
        LocalLabSettledDeathEnvelope.OriginStatus checkedOrigin=
            Objects.requireNonNull(origin,"origin");
        LocalLabTerminalFailureBoundaryTally.Boundary checkedBoundary=
            Objects.requireNonNull(boundary,"firstBoundary");
        // Called only after source World/collector/capture and COMPLETE
        // typed fail-fast chronology validation succeeded. All enum rows
        // are exhaustively initialized and mutable only inside this tally.
        EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long> row=
            crossCounts.get(checkedOrigin);
        long old=row.get(checkedBoundary);
        if(old<Long.MAX_VALUE)
            row.put(checkedBoundary,old+1L);
    }

    Snapshot snapshot(){return new Snapshot(crossCounts);}

    static final class Snapshot {
        final Map<
            LocalLabSettledDeathEnvelope.OriginStatus,
            Map<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
        > crossCounts;

        private Snapshot(
            EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
            > source
        ){
            EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
            > detached=new EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
            >(LocalLabSettledDeathEnvelope.OriginStatus.class);
            for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                    LocalLabSettledDeathEnvelope.OriginStatus.values())
                detached.put(origin,Collections.unmodifiableMap(
                    new EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>(
                        source.get(origin))));
            crossCounts=Collections.unmodifiableMap(detached);
        }

        long count(
            LocalLabSettledDeathEnvelope.OriginStatus origin,
            LocalLabTerminalFailureBoundaryTally.Boundary boundary
        ){
            return crossCounts.get(Objects.requireNonNull(origin,"origin")).get(
                Objects.requireNonNull(boundary,"firstBoundary"));
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }
}
