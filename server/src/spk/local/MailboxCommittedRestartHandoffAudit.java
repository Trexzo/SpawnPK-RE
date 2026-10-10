package spk.local;

import java.util.Objects;

/**
 * G21.91: independently re-observe disk COMMIT and World generation
 * without crossing either lock domain. A stable observation is
 * evidence for a future handoff design, NEVER session admission,
 * live application, item settlement, replay, release, or ACK.
 *
 * In particular, the final check does not pin the disk or player for
 * later use. It is not safe to use its result as a capability.
 */
final class MailboxCommittedRestartHandoffAudit {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2191_DISK_WORLD_CONTINUITY_NO_ADMISSION";

    enum State {
        STABLE_CANDIDATE_NO_ADMISSION,
        REJECT_FIRST_WORLD,
        REJECT_DISK_OBJECT_CHANGED,
        REJECT_DISK_CONTENT_CHANGED,
        REJECT_FINAL_WORLD
    }

    static final class Result {
        final State state;
        final String account;
        final String terminalSha256;
        final MailboxCommittedWorldGenerationCandidate.Decision worldFirst;
        final MailboxCommittedWorldGenerationCandidate.Decision worldLast;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        Result(State s,String account,String sha,
               MailboxCommittedWorldGenerationCandidate.Decision first,
               MailboxCommittedWorldGenerationCandidate.Decision last){
            this.state=s;
            this.account=account;
            this.terminalSha256=sha;
            this.worldFirst=first;
            this.worldLast=last;
        }
    }

    interface Checkpoint {
        void run()throws Exception;
    }

    static Result inspect(
        World world,WorldPlayer receiver,long expectedGeneration,
        String account,MailboxCommittedDetachedRestartRecovery restorer
    )throws Exception{
        return inspect(world,receiver,expectedGeneration,account,restorer,
            ()->{},()->{});
    }

    // Package-local deterministic test fault points. Never used by any
    // production session, native Mailbox widget, or normal World load.
    static Result inspect(
        World world,WorldPlayer receiver,long expectedGeneration,
        String account,MailboxCommittedDetachedRestartRecovery restorer,
        Checkpoint afterFirstWorld,Checkpoint beforeLastWorld
    )throws Exception{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(receiver,"receiver");
        Objects.requireNonNull(restorer,"restorer");
        Objects.requireNonNull(afterFirstWorld,"afterFirstWorld");
        Objects.requireNonNull(beforeLastWorld,"beforeLastWorld");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.91 invalid canonical account");

        // No World lifecycle/mutation lock is held for either recovery.
        // Invalid records throw IOException and fail closed.
        MailboxCommittedDetachedRestartRecovery.Result first=
            restorer.recoverDetached(account);
        MailboxCommittedWorldGenerationCandidate.Decision firstWorld=
            MailboxCommittedWorldGenerationCandidate.inspect(
                world,receiver,expectedGeneration,first);
        if(firstWorld!=MailboxCommittedWorldGenerationCandidate.Decision
                .CANDIDATE_ONLY_NO_ADMISSION)
            return new Result(State.REJECT_FIRST_WORLD,account,
                first.terminalSha256,firstWorld,null);

        afterFirstWorld.run();
        MailboxCommittedDetachedRestartRecovery.Result second=
            restorer.recoverDetached(account);
        if(!first.sameDiskObjects(second))
            return new Result(State.REJECT_DISK_OBJECT_CHANGED,account,
                first.terminalSha256,firstWorld,null);

        // Even with stable file identity, reject any content, image,
        // mailbox, or transaction-identity divergence. Never grant
        // based merely on a matching recorded hash.
        if(!first.account.equals(second.account)||
           !first.messageId.equals(second.messageId)||
           !first.idempotencyKey.equals(second.idempotencyKey)||
           !first.terminalSha256.equals(second.terminalSha256)||
           first.occupiedInventorySlots!=second.occupiedInventorySlots||
           first.exactRestoredSnapshot.version()!=
               second.exactRestoredSnapshot.version()||
           !first.exactRestoredSnapshot.values().equals(
               second.exactRestoredSnapshot.values()))
            return new Result(State.REJECT_DISK_CONTENT_CHANGED,account,
                first.terminalSha256,firstWorld,null);

        beforeLastWorld.run();
        MailboxCommittedWorldGenerationCandidate.Decision lastWorld=
            MailboxCommittedWorldGenerationCandidate.inspect(
                world,receiver,expectedGeneration,second);
        if(lastWorld!=MailboxCommittedWorldGenerationCandidate.Decision
                .CANDIDATE_ONLY_NO_ADMISSION)
            return new Result(State.REJECT_FINAL_WORLD,account,
                first.terminalSha256,firstWorld,lastWorld);

        // No accepted snapshot, owner handle, reservation, or token
        // escapes here. Another concurrent change immediately makes
        // this historical observation stale.
        return new Result(State.STABLE_CANDIDATE_NO_ADMISSION,account,
            first.terminalSha256,firstWorld,lastWorld);
    }

    private MailboxCommittedRestartHandoffAudit(){}
}
