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

    private long nextSequence=1L;
    private Pending pending;

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

    synchronized Completion complete(){
        Pending current=pending;

        if(current==null)
            return Completion.unmatched();

        pending=null;

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

    synchronized boolean pending(){
        return pending!=null;
    }

    synchronized long pendingSequence(){
        return pending==null
            ?0L
            :pending.sequence;
    }
}
