package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fixed-size, World-process-local 5 origin x 8 first-boundary x 4 coarse
 * exception-kind diagnostic. Only enums and saturating longs survive the
 * 128-report eviction limit, NEVER raw exception names or player objects.
 *
 * No original SpawnPK kill authority, durable grant ACK or replay permission
 * can be inferred from a matching diagnostic cross-tab.
 */
final class LocalLabOriginFirstFailureKindTally {
    private final EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<
            LocalLabTerminalFailureBoundaryTally.Boundary,
            EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
        >
    > counts=new EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<
            LocalLabTerminalFailureBoundaryTally.Boundary,
            EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
        >
    >(LocalLabSettledDeathEnvelope.OriginStatus.class);

    LocalLabOriginFirstFailureKindTally(){
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values()){
            EnumMap<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
            > boundaries=new EnumMap<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
            >(LocalLabTerminalFailureBoundaryTally.Boundary.class);
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values()){
                EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long> row=
                    new EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>(
                        LocalLabTerminalFirstFailureTypeTally.Kind.class);
                for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                        LocalLabTerminalFirstFailureTypeTally.Kind.values())
                    row.put(kind,0L);
                boundaries.put(boundary,row);
            }
            counts.put(origin,boundaries);
        }
    }

    void acceptValidated(
        LocalLabSettledDeathEnvelope.OriginStatus origin,
        LocalLabTerminalFailureBoundaryTally.Boundary boundary,
        LocalLabTerminalFirstFailureTypeTally.Kind kind
    ){
        LocalLabSettledDeathEnvelope.OriginStatus o=
            Objects.requireNonNull(origin,"origin");
        LocalLabTerminalFailureBoundaryTally.Boundary b=
            Objects.requireNonNull(boundary,"boundary");
        LocalLabTerminalFirstFailureTypeTally.Kind k=
            Objects.requireNonNull(kind,"kind");
        boolean noThrow=b==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_THROW_RECORDED||
            b==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_CURRENT_RECIPIENT;
        if(noThrow!=(k==LocalLabTerminalFirstFailureTypeTally.Kind.NO_EXCEPTION))
            throw new IllegalArgumentException(
                "ORIGIN_FIRST_FAILURE_KIND_BOUNDARY_KIND_MISMATCH");
        EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long> row=
            counts.get(o).get(b);
        long n=row.get(k);
        if(n<Long.MAX_VALUE)row.put(k,n+1L);
    }

    Snapshot snapshot(){return new Snapshot(counts);}

    static final class Snapshot {
        final Map<
            LocalLabSettledDeathEnvelope.OriginStatus,
            Map<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                Map<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
            >
        > counts;

        private Snapshot(EnumMap<
            LocalLabSettledDeathEnvelope.OriginStatus,
            EnumMap<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
            >
        > source){
            EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<
                    LocalLabTerminalFailureBoundaryTally.Boundary,
                    Map<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
                >
            > origins=new EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<
                    LocalLabTerminalFailureBoundaryTally.Boundary,
                    Map<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
                >
            >(LocalLabSettledDeathEnvelope.OriginStatus.class);
            for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                    LocalLabSettledDeathEnvelope.OriginStatus.values()){
                EnumMap<
                    LocalLabTerminalFailureBoundaryTally.Boundary,
                    Map<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
                > stages=new EnumMap<
                    LocalLabTerminalFailureBoundaryTally.Boundary,
                    Map<LocalLabTerminalFirstFailureTypeTally.Kind,Long>
                >(LocalLabTerminalFailureBoundaryTally.Boundary.class);
                for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                        LocalLabTerminalFailureBoundaryTally.Boundary.values())
                    stages.put(boundary,Collections.unmodifiableMap(
                        new EnumMap<LocalLabTerminalFirstFailureTypeTally.Kind,Long>(
                            source.get(origin).get(boundary))));
                origins.put(origin,Collections.unmodifiableMap(stages));
            }
            counts=Collections.unmodifiableMap(origins);
        }

        long count(
            LocalLabSettledDeathEnvelope.OriginStatus origin,
            LocalLabTerminalFailureBoundaryTally.Boundary boundary,
            LocalLabTerminalFirstFailureTypeTally.Kind kind
        ){
            return counts.get(Objects.requireNonNull(origin,"origin"))
                .get(Objects.requireNonNull(boundary,"boundary"))
                .get(Objects.requireNonNull(kind,"kind"));
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }
}
