package spk.local;

import java.util.Arrays;
import java.util.Objects;

/**
 * G21.25: compute a HYPOTHETICAL single-account inventory+Mailbox
 * postimage without changing a live WorldPlayer or writing a repository.
 *
 * Do not treat this immutable proposal as a grant, a CLAIMED ack, a
 * durable transaction, or evidence of original SpawnPK server behavior.
 * Native C2S185 widget 32181 remains preview-only.
 */
final class MailboxSettlementPostimagePlanner {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2125_HYPOTHETICAL_POSTIMAGE_NO_GRANT";

    enum RecoveryClass {
        EXACT_PREPARED_PREIMAGE,
        EXACT_HYPOTHETICAL_POSTIMAGE,
        DIVERGENT_REQUIRES_MANUAL_RECONCILIATION
    }

    static final class Proposal {
        final String account;
        final String messageId;
        final String idempotencyKey;
        final long ownerGeneration;
        final PlayerSnapshot preparedPreimage;
        final PlayerSnapshot hypotheticalPostimage;
        final String authority;

        private Proposal(
            String account,String messageId,String key,long generation,
            PlayerSnapshot before,PlayerSnapshot after
        ){
            this.account=account;
            this.messageId=messageId;
            this.idempotencyKey=key;
            this.ownerGeneration=generation;
            this.preparedPreimage=before;
            this.hypotheticalPostimage=after;
            this.authority=AUTHORITY;
        }

        /**
         * Classification is read-only and accepts exact, normalized account
         * snapshots only. A divergent file is never automatically replayed,
         * credited, rolled back, or treated as a successful receipt.
         */
        RecoveryClass classify(PlayerSnapshot disk){
            Objects.requireNonNull(disk,"disk");
            if(!account.equals(disk.username())||
               disk.version()!=PlayerSnapshot.CURRENT_VERSION)
                return RecoveryClass
                    .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION;
            final PlayerSnapshot normalized;
            try{
                normalized=PlayerSnapshotCodec.validateAndNormalize(disk);
            }catch(IllegalArgumentException|
                    IllegalStateException rejected){
                return RecoveryClass
                    .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION;
            }

            if(normalized.values().equals(
                    preparedPreimage.values()))
                return RecoveryClass.EXACT_PREPARED_PREIMAGE;
            if(normalized.values().equals(
                    hypotheticalPostimage.values()))
                return RecoveryClass.EXACT_HYPOTHETICAL_POSTIMAGE;
            return RecoveryClass
                .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION;
        }
    }

    /**
     * Require a current World-owned generation, immutable selected message
     * identity and the exact, already staged G21.22 PREPARED intent.
     * Recompute all 28 slots via the strict G21.21 inventory preflight.
     * The only semantic mutation happens on a fresh detached clone.
     */
    static Proposal plan(
        WorldPlayer owner,
        long expectedGeneration,
        MailboxRewardDeliveryService.Snapshot selected
    ){
        WorldPlayer player=Objects.requireNonNull(owner,"owner");
        MailboxRewardDeliveryService.Snapshot row=
            Objects.requireNonNull(selected,"selected");

        synchronized(player.mutationLock()){
            if(!player.accepts(expectedGeneration))
                throw new IllegalStateException(
                    "stale Mailbox settlement postimage owner"
                );
            MailboxRewardDeliveryService.Snapshot current=
                player.mailbox().get(row.message.messageId);
            if(current==null||current.message!=row.message||
               current.claimState!=
                   MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                throw new IllegalStateException(
                    "stale or non-UNCLAIMED Mailbox envelope"
                );
            MailboxPreparedClaimJournal.Intent staged=
                MailboxPreparedClaimJournal.inspectPrepared(player);
            if(staged==null)
                throw new IllegalStateException(
                    "missing PREPARED_NO_GRANT intent"
                );
            MailboxPreparedClaimJournal.Intent recomputed=
                MailboxPreparedClaimJournal.prepare(player,current);
            if(!recomputed.idempotencyKey.equals(
                    staged.idempotencyKey)||
               !recomputed.messageId.equals(
                    staged.messageId)||
               !recomputed.account.equals(staged.account))
                throw new IllegalStateException(
                    "prepared Mailbox intent or inventory changed"
                );

            MailboxInventoryClaimPreflight.Preview preview=
                MailboxInventoryClaimPreflight.inspect(player,current);
            if(!preview.eligible)
                throw new IllegalStateException(
                    "Mailbox attachment preflight veto "+preview.reason
                );
            if(!Arrays.equals(
                    preview.itemIds,staged.proposedItemIds())||
               !Arrays.equals(
                    preview.quantities,staged.proposedQuantities()))
                throw new IllegalStateException(
                    "staged inventory postimage mismatch"
                );

            PlayerSnapshot before=PlayerSnapshotCodec.capture(
                staged.account,player
            );

            // Detached clone only. There must never be a direct item or
            // claim-state mutation on the source WorldPlayer here.
            WorldPlayer hypothetical=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(before,hypothetical);
            hypothetical.bank().replaceInventorySemantic(
                staged.proposedItemIds(),staged.proposedQuantities()
            );
            boolean marked=hypothetical.mailbox()
                .acknowledgeAttachmentSettlement(staged.messageId);
            if(!marked)
                throw new IllegalStateException(
                    "hypothetical claim was not UNCLAIMED"
                );
            hypothetical.markMailboxSnapshotKnown();

            PlayerSnapshot after=PlayerSnapshotCodec.capture(
                staged.account,hypothetical,
                PlayerSnapshotCodec.accessoryItem(before)
            );

            // Proof of immutable, complete account encoding. Do not
            // publish this proposal to the persistence I/O worker.
            PlayerSnapshot normalizedAfter=
                PlayerSnapshotCodec.validateAndNormalize(after);
            if(!normalizedAfter.values().equals(after.values()))
                throw new IllegalStateException(
                    "noncanonical hypothetical account postimage"
                );
            if(after.values().equals(before.values()))
                throw new IllegalStateException(
                    "hypothetical postimage made no change"
                );

            return new Proposal(
                staged.account,staged.messageId,
                staged.idempotencyKey,expectedGeneration,
                before,after
            );
        }
    }

    private MailboxSettlementPostimagePlanner(){}
}
