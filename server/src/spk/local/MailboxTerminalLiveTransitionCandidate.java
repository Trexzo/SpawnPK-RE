package spk.local;

import java.util.Objects;

/**
 * G21.68: immutable proof that a terminal account snapshot can be
 * hydrated onto a DETACHED fresh player while its matching original
 * PREPARED account remains the live World owner.
 *
 * This is a single-use, NO_GRANT transition CANDIDATE, not permission
 * to apply the postimage to a registered WorldPlayer, publish a save,
 * release a reservation, replay an attachment or send client packets.
 */
final class MailboxTerminalLiveTransitionCandidate {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2168_WORLD_CANDIDATE_NO_GRANT";
    final String account;
    final String messageId;
    final String intentKey;
    final long generation;
    final String terminalSnapshotSha256;
    final PlayerSnapshot projectedTerminal;
    final boolean projectedClaimed=true;
    final boolean projectedInventoryMatchesIntent=true;
    final boolean liveApplied=false;
    final boolean grantAuthorized=false;
    final boolean replayAuthorized=false;
    final boolean rollbackAuthorized=false;
    final boolean releaseAuthorized=false;
    final boolean clientAckAuthorized=false;

    private MailboxTerminalLiveTransitionCandidate(
        MailboxSettlementPostimagePlanner.Proposal proposal,
        PlayerSnapshot terminal
    ){
        account=proposal.account;
        messageId=proposal.messageId;
        intentKey=proposal.idempotencyKey;
        generation=proposal.ownerGeneration;
        terminalSnapshotSha256=
            StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(terminal);
        projectedTerminal=terminal;
    }

    /**
     * Decode the complete inventory+Mailbox+terminal identity onto a
     * fresh detached WorldPlayer, never the registered live owner.
     * Work happens BEFORE enqueueing a World command.
     */
    static MailboxTerminalLiveTransitionCandidate stageDetached(
        MailboxSettlementPostimagePlanner.Proposal proposal,
        PlayerSnapshot terminal
    ){
        MailboxSettlementPostimagePlanner.Proposal p=
            Objects.requireNonNull(proposal,"proposal");
        PlayerSnapshot after=Objects.requireNonNull(
            terminal,"terminal");
        if(!p.account.equals(after.username())||
           after.version()!=PlayerSnapshot.CURRENT_VERSION||
           !MailboxAtomicTerminalSnapshot.compose(p).values()
               .equals(after.values())||
           MailboxAtomicTerminalSnapshot.inspect(after).state!=
               MailboxAtomicTerminalSnapshot.State
                   .COHERENT_TERMINAL_NO_GRANT)
            throw new IllegalArgumentException(
                "G21.68 terminal candidate is not exact postimage");
        WorldPlayer detached=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(after,detached);
        MailboxPreparedClaimJournal.Intent intent=
            MailboxPreparedClaimJournal.inspectPrepared(detached);
        MailboxRewardDeliveryService.Snapshot claim=
            detached.mailbox().get(p.messageId);
        if(intent==null||
           !intent.idempotencyKey.equals(p.idempotencyKey)||
           claim==null||
           claim.claimState!=
               MailboxRewardDeliveryService.ClaimState.CLAIMED)
            throw new IllegalArgumentException(
                "G21.68 detached terminal claim identity mismatch");
        int[] ids=intent.proposedItemIds();
        int[] quantities=intent.proposedQuantities();
        if(ids.length!=BankState.INVENTORY_CAPACITY||
           quantities.length!=BankState.INVENTORY_CAPACITY)
            throw new IllegalArgumentException(
                "G21.68 detached inventory invalid");
        for(int i=0;i<ids.length;i++){
            BankState.InventorySlotSnapshot slot=
                detached.bank().inventorySlotSnapshot(i);
            if(ids[i]!=(slot.occupied?slot.itemId:-1)||
               quantities[i]!=(slot.occupied?slot.quantity:0))
                throw new IllegalArgumentException(
                    "G21.68 detached inventory differs at slot "+i);
        }
        PlayerSnapshot roundTrip=PlayerSnapshotCodec.capture(
            p.account,detached,
            PlayerSnapshotCodec.accessoryItem(after));
        if(!roundTrip.values().equals(after.values())||
           !roundTrip.username().equals(after.username())||
           roundTrip.version()!=after.version())
            throw new IllegalStateException(
                "G21.68 detached roundtrip differs from terminal");
        return new MailboxTerminalLiveTransitionCandidate(p,after);
    }

    private MailboxTerminalLiveTransitionCandidate(){}
}
