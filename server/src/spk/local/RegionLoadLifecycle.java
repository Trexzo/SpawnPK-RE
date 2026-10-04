package spk.local;

/**
 * Session-local packet-73 -> opcode-121 region loading lifecycle.
 *
 * Exact v308 runtime evidence establishes packet 73 as the region/base
 * reconfiguration trigger and opcode 121 as the scene-load completion ACK.
 * The server-side threshold for deciding when to send packet 73 remains a
 * separate LocalLab policy.
 */
final class RegionLoadLifecycle {
    static final class Begin {
        final long sequence;
        final boolean superseded;
        final long supersededSequence;

        Begin(
            long sequence,
            boolean superseded,
            long supersededSequence
        ){
            this.sequence=sequence;
            this.superseded=superseded;
            this.supersededSequence=supersededSequence;
        }
    }

    static final class Completion {
        final boolean matched;
        final long sequence;
        final int centerX,centerY,baseX,baseY;
        final String reason;

        private Completion(
            boolean matched,
            long sequence,
            int centerX,
            int centerY,
            int baseX,
            int baseY,
            String reason
        ){
            this.matched=matched;
            this.sequence=sequence;
            this.centerX=centerX;
            this.centerY=centerY;
            this.baseX=baseX;
            this.baseY=baseY;
            this.reason=reason;
        }

        static Completion unmatched(){
            return new Completion(
                false,0L,0,0,0,0,"NONE"
            );
        }
    }

    private static final class Pending {
        final long sequence;
        final int centerX,centerY,baseX,baseY;
        final String reason;

        Pending(
            long sequence,
            int centerX,
            int centerY,
            int baseX,
            int baseY,
            String reason
        ){
            this.sequence=sequence;
            this.centerX=centerX;
            this.centerY=centerY;
            this.baseX=baseX;
            this.baseY=baseY;
            this.reason=reason;
        }
    }

    static final class Snapshot {
        final long nextSequence;
        final Pending pending;

        private Snapshot(
            long nextSequence,
            Pending pending
        ){
            this.nextSequence=nextSequence;
            this.pending=pending;
        }
    }

    private long nextSequence=1L;
    private Pending pending;

    synchronized Snapshot snapshot(){
        Pending copy=
            pending==null
                ?null
                :new Pending(
                    pending.sequence,
                    pending.centerX,
                    pending.centerY,
                    pending.baseX,
                    pending.baseY,
                    pending.reason
                );

        return new Snapshot(
            nextSequence,
            copy
        );
    }

    synchronized void restore(
        Snapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "region load snapshot"
            );

        nextSequence=snapshot.nextSequence;
        pending=
            snapshot.pending==null
                ?null
                :new Pending(
                    snapshot.pending.sequence,
                    snapshot.pending.centerX,
                    snapshot.pending.centerY,
                    snapshot.pending.baseX,
                    snapshot.pending.baseY,
                    snapshot.pending.reason
                );
    }

    synchronized Begin begin(
        int centerX,
        int centerY,
        int baseX,
        int baseY,
        String reason
    ){
        if(reason==null||reason.trim().isEmpty())
            throw new IllegalArgumentException("reason");

        Pending previous=pending;
        long sequence=nextSequence++;

        pending=
            new Pending(
                sequence,
                centerX,
                centerY,
                baseX,
                baseY,
                reason
            );

        return new Begin(
            sequence,
            previous!=null,
            previous==null
                ?0L
                :previous.sequence
        );
    }

    synchronized Completion prepareComplete(){
        Pending current=pending;

        if(current==null)
            return Completion.unmatched();

        return new Completion(
            true,
            current.sequence,
            current.centerX,
            current.centerY,
            current.baseX,
            current.baseY,
            current.reason
        );
    }

    synchronized boolean commitCompletion(
        Completion completion
    ){
        if(completion==null)
            throw new NullPointerException(
                "completion"
            );

        if(!completion.matched)
            return false;

        Pending current=pending;

        if(current==null||
           current.sequence!=
                completion.sequence)
            return false;

        pending=null;
        return true;
    }

    synchronized Completion complete(){
        Completion completion=
            prepareComplete();

        if(completion.matched&&
           !commitCompletion(
                completion
           ))
            throw new IllegalStateException(
                "region completion identity changed"
            );

        return completion;
    }

    synchronized boolean pending(){
        return pending!=null;
    }

    synchronized long pendingSequence(){
        return pending==null
            ?0L
            :pending.sequence;
    }
}
