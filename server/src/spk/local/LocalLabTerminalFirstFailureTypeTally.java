package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Process-local, fixed-size audit of first blocking stage and coarse
 * exception category. Never retain arbitrary exception class names in
 * long-lived aggregates; detailed type names remain bounded by the
 * existing 128-report trace ring.
 *
 * No original SpawnPK server attribution, credit, grant ACK, persistent
 * identity or automatic replay is established by any outcome.
 */
final class LocalLabTerminalFirstFailureTypeTally {
    enum Kind {
        NO_EXCEPTION,
        ILLEGAL_STATE,
        ILLEGAL_ARGUMENT,
        OTHER_EXCEPTION
    }

    private final EnumMap<
        LocalLabTerminalFailureBoundaryTally.Boundary,
        EnumMap<Kind,Long>
    > counts=new EnumMap<
        LocalLabTerminalFailureBoundaryTally.Boundary,
        EnumMap<Kind,Long>
    >(LocalLabTerminalFailureBoundaryTally.Boundary.class);

    LocalLabTerminalFirstFailureTypeTally(){
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                LocalLabTerminalFailureBoundaryTally.Boundary.values()){
            EnumMap<Kind,Long> row=new EnumMap<Kind,Long>(Kind.class);
            for(Kind kind:Kind.values())row.put(kind,0L);
            counts.put(boundary,row);
        }
    }

    static Kind classify(
        LocalLabSequentialTerminalStageTrace.Report report,
        LocalLabTerminalFailureBoundaryTally.Boundary firstBoundary
    ){
        LocalLabSequentialTerminalStageTrace.Report checked=
            Objects.requireNonNull(report,"report");
        LocalLabTerminalFailureBoundaryTally.Boundary boundary=
            Objects.requireNonNull(firstBoundary,"firstBoundary");
        if(boundary==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_THROW_RECORDED||
           boundary==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_CURRENT_RECIPIENT)
            return Kind.NO_EXCEPTION;

        String name;
        if(boundary==LocalLabTerminalFailureBoundaryTally.Boundary.G1)
            name=checked.g1FailureType;
        else
            name=checked.failureType(
                LocalLabSequentialTerminalStageTrace.Stage.valueOf(
                    boundary.name()
                )
            );
        if(name==null||name.trim().isEmpty())
            throw new IllegalStateException(
                "TERMINAL_FIRST_FAILURE_TYPE_MISSING_"+boundary);

        if(IllegalStateException.class.getName().equals(name))
            return Kind.ILLEGAL_STATE;
        if(IllegalArgumentException.class.getName().equals(name))
            return Kind.ILLEGAL_ARGUMENT;
        return Kind.OTHER_EXCEPTION;
    }

    void acceptValidated(
        LocalLabTerminalFailureBoundaryTally.Boundary boundary,
        Kind kind
    ){
        LocalLabTerminalFailureBoundaryTally.Boundary b=
            Objects.requireNonNull(boundary,"boundary");
        Kind k=Objects.requireNonNull(kind,"kind");
        if((b==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_THROW_RECORDED||
            b==LocalLabTerminalFailureBoundaryTally.Boundary
                .NO_CURRENT_RECIPIENT)!=(k==Kind.NO_EXCEPTION))
            throw new IllegalArgumentException(
                "TERMINAL_FIRST_FAILURE_TYPE_BOUNDARY_KIND_MISMATCH");
        EnumMap<Kind,Long> row=counts.get(b);
        long current=row.get(k);
        if(current<Long.MAX_VALUE)row.put(k,current+1L);
    }

    Snapshot snapshot(){return new Snapshot(counts);}

    static final class Snapshot {
        final Map<
            LocalLabTerminalFailureBoundaryTally.Boundary,
            Map<Kind,Long>
        > counts;

        private Snapshot(EnumMap<
            LocalLabTerminalFailureBoundaryTally.Boundary,
            EnumMap<Kind,Long>
        > source){
            EnumMap<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                Map<Kind,Long>
            > detached=new EnumMap<
                LocalLabTerminalFailureBoundaryTally.Boundary,
                Map<Kind,Long>
            >(LocalLabTerminalFailureBoundaryTally.Boundary.class);
            for(LocalLabTerminalFailureBoundaryTally.Boundary b:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values())
                detached.put(b,Collections.unmodifiableMap(
                    new EnumMap<Kind,Long>(source.get(b))
                ));
            counts=Collections.unmodifiableMap(detached);
        }

        long count(
            LocalLabTerminalFailureBoundaryTally.Boundary boundary,
            Kind kind
        ){
            return counts.get(Objects.requireNonNull(boundary,"boundary"))
                .get(Objects.requireNonNull(kind,"kind"));
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }
}
