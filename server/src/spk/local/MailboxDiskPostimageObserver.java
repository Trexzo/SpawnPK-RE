package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.26: read-only disk observation of a hypothetical Mailbox settlement.
 *
 * WorldPlayerPersistence.observeUntrustedMailboxAccount() queues the
 * repository read behind already
 * admitted saves on its SINGLE bounded I/O worker. The observation tells us
 * which exact account snapshot is visible at this point in that queue.
 * It is NOT a durability receipt, a commit decision, a reward-grant permit,
 * a replay instruction or a claim acknowledgement.
 */
final class MailboxDiskPostimageObserver {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2126_DISK_OBSERVATION_NO_GRANT";

    enum State {
        MISSING_ACCOUNT_RECORD,
        EXACT_PREPARED_ACCOUNT,
        EXACT_HYPOTHETICAL_ACCOUNT,
        DIVERGENT_ACCOUNT
    }

    static final class Observation {
        final State state;
        final String account;
        final String intentKey;
        final long ownerGeneration;
        final boolean durabilityReceipt;
        final boolean grantAuthorized;

        private Observation(
            State state,String account,String intentKey,long generation
        ){
            this.state=Objects.requireNonNull(state,"state");
            this.account=account;
            this.intentKey=intentKey;
            this.ownerGeneration=generation;
            this.durabilityReceipt=false;
            this.grantAuthorized=false;
        }
    }

    /**
     * Invoke from outside World execution context. Admission, then read,
     * then revalidate that the same immutable prepared postimage is still
     * justified by this exact player registration. If a player logs out,
     * its selected envelope is recycled or inventory changes while disk
     * I/O is queued, reject instead of reporting stale positive evidence.
     *
     * The FIFO read can return an exact hypothetical snapshot even when
     * its prior writer failed AFTER rename. That is observation only:
     * without a completed fsync receipt and atomic live-state transition
     * the game must NOT grant items or acknowledge the message.
     */
    static Observation observe(
        World world,
        WorldPlayer owner,
        long generation,
        MailboxSettlementPostimagePlanner.Proposal planned
    )throws IOException{
        World checkedWorld=Objects.requireNonNull(world,"world");
        WorldPlayer player=Objects.requireNonNull(owner,"owner");
        MailboxSettlementPostimagePlanner.Proposal proposal=
            Objects.requireNonNull(planned,"planned");

        verifyCurrent(checkedWorld,player,generation,proposal);
        // Preserve the *existing* persistence FIFO. This observation
        // intentionally reads untrusted bytes, unlike the G21.31
        // session-admission load() which must reject CLAIMED postimages.
        // Never return the observed snapshot to session hydration.
        Optional<PlayerSnapshot> candidate=
            checkedWorld.persistence().observeUntrustedMailboxAccount(
                proposal.account
            );
        verifyCurrent(checkedWorld,player,generation,proposal);

        State state;
        if(!candidate.isPresent()){
            state=State.MISSING_ACCOUNT_RECORD;
        }else{
            MailboxSettlementPostimagePlanner.RecoveryClass classification=
                proposal.classify(candidate.get());
            switch(classification){
                case EXACT_PREPARED_PREIMAGE:
                    state=State.EXACT_PREPARED_ACCOUNT;
                    break;
                case EXACT_HYPOTHETICAL_POSTIMAGE:
                    state=State.EXACT_HYPOTHETICAL_ACCOUNT;
                    break;
                default:
                    state=State.DIVERGENT_ACCOUNT;
                    break;
            }
        }

        return new Observation(
            state,proposal.account,proposal.idempotencyKey,generation
        );
    }

    private static void verifyCurrent(
        World world,
        WorldPlayer owner,
        long generation,
        MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        synchronized(owner.mutationLock()){
            if(!world.players().owns(owner,generation)||
               !owner.accepts(generation)||
               !proposal.account.equals(owner.username())||
               proposal.ownerGeneration!=generation)
                throw new IllegalStateException(
                    "G21.26 disk observation owner/generation changed"
                );

            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(proposal.messageId);
            if(row==null)
                throw new IllegalStateException(
                    "G21.26 selected envelope no longer exists"
                );

            MailboxSettlementPostimagePlanner.Proposal current=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,row
                );
            if(!current.idempotencyKey.equals(
                    proposal.idempotencyKey)||
               !current.preparedPreimage.values().equals(
                    proposal.preparedPreimage.values())||
               !current.hypotheticalPostimage.values().equals(
                    proposal.hypotheticalPostimage.values()))
                throw new IllegalStateException(
                    "G21.26 immutable postimage became stale"
                );
        }
    }

    private MailboxDiskPostimageObserver(){}
}
