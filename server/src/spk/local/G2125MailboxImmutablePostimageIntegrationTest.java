package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;

/**
 * G21.25: one hypothetical account record contains BOTH inventory credit
 * and Mailbox CLAIMED without mutating the actual WorldPlayer. All generated
 * results are inert, non-durable proposals; native item grant is still OFF.
 */
public final class G2125MailboxImmutablePostimageIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean exactPreparedIdentity=false;
        boolean completeInventoryAndClaimedPostimage=false;
        boolean otherMailboxStateRetained=false;
        boolean liveInventoryNeverCredited=false;
        boolean liveUnclaimedNeverAcknowledged=false;
        boolean deterministicRepeatedProposal=false;
        boolean reloadPreimageInert=false;
        boolean reloadHypotheticalPostimageClassified=false;
        boolean divergedDiskRejected=false;
        boolean foreignAccountRejected=false;
        boolean changedInventoryRejected=false;
        boolean sameIdRecycleRejected=false;
        boolean staleGenerationRejected=false;
        boolean alreadyClaimedRejected=false;
        boolean unstagedIntentRejected=false;
        boolean postimageCannotAuthorizeSettlement=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2125-alice");
            long bg=world.registerPlayer(bob,"g2125-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=new WorldMailboxGateway(world,bob,bg);

            a.deliver(message(
                "g2125:gift","Selected gift",995,50,560,5
            ));
            a.deliver(message(
                "g2125:other","Other mail"
            ));
            b.deliver(message(
                "g2125:bob","Bob only",995,10
            ));

            MailboxRewardDeliveryService.Snapshot selected=
                alice.mailbox().get("g2125:gift");
            MailboxPreparedClaimJournal.Intent intent=
                MailboxPreparedClaimJournal.prepare(alice,selected);
            boolean staged=MailboxPreparedClaimJournal.stageOnly(
                alice,intent
            );

            PlayerSnapshot original=PlayerSnapshotCodec.capture(
                "g2125-alice",alice
            );
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    alice,ag,selected
                );
            exactPreparedIdentity=
                staged&&intent.idempotencyKey.equals(
                    proposal.idempotencyKey
                )&&
                "g2125-alice".equals(proposal.account)&&
                "g2125:gift".equals(proposal.messageId)&&
                proposal.ownerGeneration==ag&&
                original.values().equals(
                    proposal.preparedPreimage.values()
                );

            WorldPlayer hypothetical=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                proposal.hypotheticalPostimage,hypothetical
            );
            completeInventoryAndClaimedPostimage=
                hypothetical.bank().inventoryCount(995)==50&&
                hypothetical.bank().inventoryCount(560)==5&&
                hypothetical.bank().inventorySlots()==2&&
                hypothetical.mailbox().get("g2125:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                hypothetical.mailbox().get("g2125:gift").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            otherMailboxStateRetained=
                hypothetical.mailbox().size()==2&&
                hypothetical.mailbox().get("g2125:other").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                hypothetical.mailbox().get("g2125:other").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD&&
                hypothetical.mailbox().get("g2125:bob")==null;

            liveInventoryNeverCredited=
                alice.bank().inventorySlots()==0&&
                alice.bank().inventoryCount(995)==0&&
                alice.bank().inventoryCount(560)==0;
            liveUnclaimedNeverAcknowledged=
                alice.mailbox().get("g2125:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2125:other").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                original.values().equals(
                    PlayerSnapshotCodec.capture(
                        "g2125-alice",alice
                    ).values()
                );

            MailboxSettlementPostimagePlanner.Proposal repeated=
                MailboxSettlementPostimagePlanner.plan(
                    alice,ag,selected
                );
            deterministicRepeatedProposal=
                repeated.idempotencyKey.equals(
                    proposal.idempotencyKey
                )&&
                repeated.preparedPreimage.values().equals(
                    proposal.preparedPreimage.values()
                )&&
                repeated.hypotheticalPostimage.values().equals(
                    proposal.hypotheticalPostimage.values()
                )&&
                alice.bank().inventorySlots()==0;

            WorldPlayer restoredPrepared=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                proposal.preparedPreimage,restoredPrepared
            );
            reloadPreimageInert=
                proposal.classify(
                    proposal.preparedPreimage
                )==MailboxSettlementPostimagePlanner.RecoveryClass
                    .EXACT_PREPARED_PREIMAGE&&
                restoredPrepared.bank().inventorySlots()==0&&
                restoredPrepared.mailbox().get("g2125:gift")
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                MailboxPreparedClaimJournal.inspectPrepared(
                    restoredPrepared
                )!=null;

            WorldPlayer restoredHypothetical=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                proposal.hypotheticalPostimage,
                restoredHypothetical
            );
            reloadHypotheticalPostimageClassified=
                proposal.classify(
                    proposal.hypotheticalPostimage
                )==MailboxSettlementPostimagePlanner.RecoveryClass
                    .EXACT_HYPOTHETICAL_POSTIMAGE&&
                restoredHypothetical.bank().inventoryCount(995)==50&&
                restoredHypothetical.mailbox().get("g2125:gift")
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                MailboxPreparedClaimJournal.inspectPrepared(
                    restoredHypothetical
                )!=null;

            TreeMap<String,String> tampered=new TreeMap<>(
                proposal.hypotheticalPostimage.values()
            );
            tampered.put(
                "extension.mailbox-claim-intent.key","0".repeat(64)
            );
            PlayerSnapshot bad=new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "g2125-alice",tampered
            );
            divergedDiskRejected=
                proposal.classify(bad)==
                    MailboxSettlementPostimagePlanner.RecoveryClass
                        .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION;
            tampered=new TreeMap<>(
                proposal.hypotheticalPostimage.values()
            );
            tampered.put("extension.unrelated.rogue","untrusted");
            PlayerSnapshot changed=new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "g2125-alice",tampered
            );
            divergedDiskRejected &=
                proposal.classify(changed)==
                    MailboxSettlementPostimagePlanner.RecoveryClass
                        .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION;

            foreignAccountRejected=
                proposal.classify(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,
                    "g2125-bob",
                    proposal.hypotheticalPostimage.values()
                ))==MailboxSettlementPostimagePlanner.RecoveryClass
                    .DIVERGENT_REQUIRES_MANUAL_RECONCILIATION&&
                bob.mailbox().get("g2125:gift")==null&&
                bob.bank().inventorySlots()==0&&
                bob.mailbox().get("g2125:bob").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            WorldPlayer changedInventory=new WorldPlayer();
            long changedGen=world.registerPlayer(
                changedInventory,"g2125-changed"
            );
            changedInventory.mailbox().deliver(message(
                "g2125:changed","Changed item",995,5
            ));
            MailboxRewardDeliveryService.Snapshot changedRow=
                changedInventory.mailbox().get("g2125:changed");
            MailboxPreparedClaimJournal.Intent changedIntent=
                MailboxPreparedClaimJournal.prepare(
                    changedInventory,changedRow
                );
            MailboxPreparedClaimJournal.stageOnly(
                changedInventory,changedIntent
            );
            int[] ids=new int[28],qty=new int[28];
            Arrays.fill(ids,-1);
            ids[0]=995;
            qty[0]=1;
            changedInventory.bank().replaceInventorySemantic(
                ids,qty
            );
            changedInventoryRejected=rejects(
                ()->MailboxSettlementPostimagePlanner.plan(
                    changedInventory,changedGen,changedRow
                )
            )&&changedInventory.mailbox().get(
                "g2125:changed"
            ).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            WorldPlayer recycled=new WorldPlayer();
            long recycledGen=world.registerPlayer(
                recycled,"g2125-recycled"
            );
            recycled.mailbox().deliver(message(
                "g2125:recycled","First",995,1
            ));
            MailboxRewardDeliveryService.Snapshot old=
                recycled.mailbox().get("g2125:recycled");
            MailboxPreparedClaimJournal.stageOnly(
                recycled,MailboxPreparedClaimJournal.prepare(
                    recycled,old
                )
            );
            recycled.mailbox().delete("g2125:recycled");
            recycled.mailbox().deliver(message(
                "g2125:recycled","Replacement",995,1
            ));
            sameIdRecycleRejected=rejects(
                ()->MailboxSettlementPostimagePlanner.plan(
                    recycled,recycledGen,old
                )
            )&&recycled.mailbox().get("g2125:recycled")
                .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            boolean oldRemoved=world.unregisterPlayer(alice,ag);
            staleGenerationRejected=oldRemoved&&rejects(
                ()->MailboxSettlementPostimagePlanner.plan(
                    alice,ag,selected
                )
            );
            long nextGen=world.registerPlayer(
                alice,"g2125-alice"
            );
            staleGenerationRejected &=
                nextGen!=ag&&rejects(
                    ()->MailboxSettlementPostimagePlanner.plan(
                        alice,ag,selected
                    )
                );

            WorldPlayer claimed=new WorldPlayer();
            long claimedGen=world.registerPlayer(
                claimed,"g2125-claimed"
            );
            claimed.mailbox().deliver(message(
                "g2125:claimed","Claimed",995,5
            ));
            MailboxRewardDeliveryService.Snapshot row=
                claimed.mailbox().get("g2125:claimed");
            MailboxPreparedClaimJournal.stageOnly(
                claimed,MailboxPreparedClaimJournal.prepare(
                    claimed,row
                )
            );
            claimed.mailbox().acknowledgeAttachmentSettlement(
                "g2125:claimed"
            );
            alreadyClaimedRejected=rejects(
                ()->MailboxSettlementPostimagePlanner.plan(
                    claimed,claimedGen,row
                )
            );

            WorldPlayer unstaged=new WorldPlayer();
            long unstagedGen=world.registerPlayer(
                unstaged,"g2125-unstaged"
            );
            unstaged.mailbox().deliver(message(
                "g2125:unstaged","No intent",995,2
            ));
            unstagedIntentRejected=rejects(
                ()->MailboxSettlementPostimagePlanner.plan(
                    unstaged,unstagedGen,
                    unstaged.mailbox().get("g2125:unstaged")
                )
            );

            postimageCannotAuthorizeSettlement=
                MailboxSettlementPostimagePlanner.AUTHORITY.equals(
                    "CUSTOM_LOCALLAB_G2125_HYPOTHETICAL_POSTIMAGE_NO_GRANT"
                )&&
                proposal.hypotheticalPostimage.version()==
                    PlayerSnapshot.CURRENT_VERSION&&
                Arrays.stream(
                    MailboxSettlementPostimagePlanner.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().contains("commit")||
                    method.getName().contains("settle")||
                    method.getName().contains("grant")
                )&&
                alice.bank().inventorySlots()==0;
        }

        System.out.println(
            "G2125_MAILBOX_HYPOTHETICAL_POSTIMAGE_DIAGNOSTICS"+
            " exactPreparedIdentity="+exactPreparedIdentity+
            " completeInventoryAndClaimedPostimage="+
                completeInventoryAndClaimedPostimage+
            " otherMailboxStateRetained="+otherMailboxStateRetained+
            " liveInventoryNeverCredited="+liveInventoryNeverCredited+
            " liveUnclaimedNeverAcknowledged="+
                liveUnclaimedNeverAcknowledged+
            " deterministicRepeatedProposal="+
                deterministicRepeatedProposal+
            " reloadPreimageInert="+reloadPreimageInert+
            " reloadHypotheticalPostimageClassified="+
                reloadHypotheticalPostimageClassified+
            " divergedDiskRejected="+divergedDiskRejected+
            " foreignAccountRejected="+foreignAccountRejected+
            " changedInventoryRejected="+changedInventoryRejected+
            " sameIdRecycleRejected="+sameIdRecycleRejected+
            " staleGenerationRejected="+staleGenerationRejected+
            " alreadyClaimedRejected="+alreadyClaimedRejected+
            " unstagedIntentRejected="+unstagedIntentRejected+
            " postimageCannotAuthorizeSettlement="+
                postimageCannotAuthorizeSettlement
        );
        require(
            exactPreparedIdentity&&completeInventoryAndClaimedPostimage&&
            otherMailboxStateRetained&&liveInventoryNeverCredited&&
            liveUnclaimedNeverAcknowledged&&deterministicRepeatedProposal&&
            reloadPreimageInert&&reloadHypotheticalPostimageClassified&&
            divergedDiskRejected&&foreignAccountRejected&&
            changedInventoryRejected&&sameIdRecycleRejected&&
            staleGenerationRejected&&alreadyClaimedRejected&&
            unstagedIntentRejected&&postimageCannotAuthorizeSettlement,
            "G21.25 immutable Mailbox postimage acceptance"
        );
        System.out.println(
            "G2125_MAILBOX_HYPOTHETICAL_POSTIMAGE_PASS"+
            " allAttachmentsInOneAccountPostimage=true"+
            " selectedMailboxClaimedOnlyInDetachedClone=true"+
            " liveInventoryUnchanged=true"+
            " replayRecoveryClassifierExact=true"+
            " otherMailPreserved=true"+
            " staleReplayFailClosed=true"+
            " noNetworkOrPersistenceWrite=true"+
            " grantEnabled=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,long... pairs
    ){
        java.util.ArrayList<RewardDeliveryMessage.Attachment> items=
            new java.util.ArrayList<>();
        for(int i=0;i<pairs.length;i+=2)
            items.add(new RewardDeliveryMessage.Attachment(
                (int)pairs[i],pairs[i+1]
            ));
        return new RewardDeliveryMessage(
            id,subject,"G21.25 immutable postimage",
            Collections.unmodifiableList(items),
            "CUSTOM_LOCALLAB_G2125_FIXTURE"
        );
    }

    private interface Action{ Object run()throws Exception; }

    private static boolean rejects(Action action){
        try{action.run();return false;}
        catch(IllegalArgumentException|
              IllegalStateException expected){return true;}
        catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
    }

    private G2125MailboxImmutablePostimageIntegrationTest(){}
}
