package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.31: an actual WorldPlayerPersistence.load must reject uncertain
 * G21.22 PREPARED account files BEFORE the normal caller can hydrate
 * their inventory/Mailbox. No file rewrite, reward or replay is allowed.
 */
public final class G2131MailboxPreparedRestartAdmissionIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean noJournalUnchanged=false;
        boolean validPreparedClassified=false;
        boolean actualLoadAcceptsPrepared=false;
        boolean loadedPreparedStillUnclaimed=false;
        boolean hypotheticalClassifiedQuarantine=false;
        boolean actualLoadBlocksHypothetical=false;
        boolean diskHypotheticalLeftUntouched=false;
        boolean forensicFifoObservationStillSeesUntrusted=false;
        boolean inventoryOnlyMismatchBlocked=false;
        boolean missingEnvelopeBlocked=false;
        boolean invalidJournalBlocked=false;
        boolean foreignJournalBlocked=false;
        boolean normalForeignAccountStillLoads=false;
        boolean repairedPreparedLoads=false;
        boolean repeatedLoadNoMutation=false;
        boolean noClaimOrReplayAuthorized=false;
        boolean originalLiveOwnerUnchanged=false;

        Path folder=Files.createTempDirectory(
            "g2131-restart-admission-"
        );
        FilePlayerRepository.PathResolver paths=
            account->folder.resolve(account+".properties");
        FilePlayerRepository repository=
            new FilePlayerRepository(paths);
        try(World world=World.isolatedForTest(60000L,repository)){
            WorldPlayer owner=new WorldPlayer();
            long generation=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            world.start();
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                proposalRef=new AtomicReference<>();

            world.submitAndWait(owner,generation,()->{
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    "g2131:reward","Restart-bound gift","No live grant",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2131_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot selected=
                    owner.mailbox().get("g2131:reward");
                MailboxPreparedClaimJournal.stageOnly(
                    owner,MailboxPreparedClaimJournal.prepare(
                        owner,selected
                    )
                );
                proposalRef.set(
                    MailboxSettlementPostimagePlanner.plan(
                        owner,generation,selected
                    )
                );
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=
                proposalRef.get();

            WorldPlayer ordinary=new WorldPlayer();
            ordinary.markRegistered("g2131-ordinary");
            PlayerSnapshot noJournal=
                PlayerSnapshotCodec.capture("g2131-ordinary",ordinary);
            repository.save(noJournal);
            MailboxPreparedRestartAdmission.Decision plain=
                MailboxPreparedRestartAdmission.inspect(noJournal);
            noJournalUnchanged=
                plain.state==
                    MailboxPreparedRestartAdmission.State.NO_JOURNAL&&
                plain.admissionAllowed&&
                world.persistence().load("g2131-ordinary").isPresent();

            repository.save(proposal.preparedPreimage);
            MailboxPreparedRestartAdmission.Decision prepared=
                MailboxPreparedRestartAdmission.inspect(
                    proposal.preparedPreimage
                );
            validPreparedClassified=
                prepared.admissionAllowed&&
                prepared.state==
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED;
            PlayerSnapshot loaded=
                world.persistence().load(proposal.account).get();
            actualLoadAcceptsPrepared=
                loaded.values().equals(proposal.preparedPreimage.values());
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(loaded,restored);
            loadedPreparedStillUnclaimed=
                restored.bank().inventorySlots()==0&&
                restored.mailbox().get("g2131:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                MailboxPreparedClaimJournal.inspectPrepared(restored)!=null;

            repository.save(proposal.hypotheticalPostimage);
            MailboxPreparedRestartAdmission.Decision hypothetical=
                MailboxPreparedRestartAdmission.inspect(
                    proposal.hypotheticalPostimage
                );
            hypotheticalClassifiedQuarantine=
                hypothetical.state==
                    MailboxPreparedRestartAdmission.State
                        .QUARANTINE_CLAIMED_OR_EMPTY_MESSAGE&&
                !hypothetical.admissionAllowed;
            actualLoadBlocksHypothetical=quarantined(
                world,proposal.account,"QUARANTINE_CLAIMED_OR_EMPTY_MESSAGE"
            );
            forensicFifoObservationStillSeesUntrusted=
                world.persistence().observeUntrustedMailboxAccount(
                    proposal.account
                ).get().values().equals(
                    proposal.hypotheticalPostimage.values()
                );
            diskHypotheticalLeftUntouched=
                repository.load(proposal.account).get().values().equals(
                    proposal.hypotheticalPostimage.values()
                );

            WorldPlayer mutated=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                proposal.preparedPreimage,mutated
            );
            int[] ids=new int[BankState.INVENTORY_CAPACITY];
            int[] amounts=new int[BankState.INVENTORY_CAPACITY];
            Arrays.fill(ids,-1);
            ids[0]=995;
            amounts[0]=1;
            mutated.bank().replaceInventorySemantic(ids,amounts);
            PlayerSnapshot altered=PlayerSnapshotCodec.capture(
                proposal.account,mutated,
                PlayerSnapshotCodec.accessoryItem(
                    proposal.preparedPreimage
                )
            );
            repository.save(altered);
            inventoryOnlyMismatchBlocked=quarantined(
                world,proposal.account,
                "QUARANTINE_INVENTORY_PREIMAGE_MISMATCH"
            );

            TreeMap<String,String> stripped=new TreeMap<>(
                proposal.preparedPreimage.values()
            );
            String mailboxPrefix=
                PlayerSnapshotExtensionState.PREFIX+
                LocalLabMailboxPersistence.NAMESPACE+".";
            stripped.keySet().removeIf(
                name->name.startsWith(mailboxPrefix)
            );
            repository.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                proposal.account,stripped
            ));
            missingEnvelopeBlocked=quarantined(
                world,proposal.account,
                "QUARANTINE_MISSING_OR_REPLACED_MESSAGE"
            );

            TreeMap<String,String> brokenJournal=new TreeMap<>(
                proposal.preparedPreimage.values()
            );
            brokenJournal.remove(
                "extension."+
                MailboxPreparedClaimJournal.NAMESPACE+".key"
            );
            repository.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                proposal.account,brokenJournal
            ));
            invalidJournalBlocked=quarantined(
                world,proposal.account,"QUARANTINE_INVALID_JOURNAL"
            );

            PlayerSnapshot foreign=new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,"g2131-foreign",
                proposal.preparedPreimage.values()
            );
            repository.save(foreign);
            foreignJournalBlocked=quarantined(
                world,"g2131-foreign","QUARANTINE_INVALID_JOURNAL"
            );
            normalForeignAccountStillLoads=
                world.persistence().load("g2131-ordinary").isPresent();

            repository.save(proposal.preparedPreimage);
            PlayerSnapshot repaired=
                world.persistence().load(proposal.account).get();
            repairedPreparedLoads=
                repaired.values().equals(
                    proposal.preparedPreimage.values()
                );
            PlayerSnapshot repeated=
                world.persistence().load(proposal.account).get();
            repeatedLoadNoMutation=
                repeated.values().equals(repaired.values())&&
                repository.load(proposal.account).get()
                    .values().equals(repaired.values());
            noClaimOrReplayAuthorized=
                !prepared.grantAuthorized&&
                !prepared.replayAuthorized&&
                !prepared.rollbackAuthorized&&
                !hypothetical.grantAuthorized&&
                !hypothetical.replayAuthorized&&
                !hypothetical.rollbackAuthorized;
            originalLiveOwnerUnchanged=
                owner.bank().inventorySlots()==0&&
                owner.mailbox().get("g2131:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }finally{
            try(Stream<Path> pathsOnDisk=Files.walk(folder)){
                for(Path p:pathsOnDisk.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println(
            "G2131_MAILBOX_RESTART_LOAD_VETO_DIAGNOSTICS"+
            " normalAccountUnchanged="+noJournalUnchanged+
            " preparedClassified="+validPreparedClassified+
            " actualPreparedLoad="+actualLoadAcceptsPrepared+
            " noPreparedGrant="+loadedPreparedStillUnclaimed+
            " hypotheticalQuarantined="+hypotheticalClassifiedQuarantine+
            " actualHypotheticalLoadBlocked="+actualLoadBlocksHypothetical+
            " uncertainFileUntouched="+diskHypotheticalLeftUntouched+
            " forensicFifoObservationOnly="+
                forensicFifoObservationStillSeesUntrusted+
            " inventoryOnlyVeto="+inventoryOnlyMismatchBlocked+
            " missingEnvelopeVeto="+missingEnvelopeBlocked+
            " corruptedJournalVeto="+invalidJournalBlocked+
            " foreignJournalVeto="+foreignJournalBlocked+
            " unrelatedAccountLoads="+normalForeignAccountStillLoads+
            " repairedPreparedLoads="+repairedPreparedLoads+
            " repeatedLoadIdempotent="+repeatedLoadNoMutation+
            " noReplayOrClaim="+noClaimOrReplayAuthorized+
            " liveOwnerUnchanged="+originalLiveOwnerUnchanged
        );
        require(
            noJournalUnchanged&&validPreparedClassified&&
            actualLoadAcceptsPrepared&&loadedPreparedStillUnclaimed&&
            hypotheticalClassifiedQuarantine&&
            actualLoadBlocksHypothetical&&diskHypotheticalLeftUntouched&&
            forensicFifoObservationStillSeesUntrusted&&
            inventoryOnlyMismatchBlocked&&missingEnvelopeBlocked&&
            invalidJournalBlocked&&foreignJournalBlocked&&
            normalForeignAccountStillLoads&&repairedPreparedLoads&&
            repeatedLoadNoMutation&&noClaimOrReplayAuthorized&&
            originalLiveOwnerUnchanged,
            "G21.31 actual restart-load admission"
        );
        System.out.println(
            "G2131_MAILBOX_PREPARED_RESTART_LOAD_PASS"+
            " liveAccountLoaderFenced=true"+
            " uncertainPostimageCannotHydrate=true"+
            " normalAccountsUnaffected=true"+
            " rewardGrant=false autoRollback=false autoReplay=false"
        );
    }

    private static boolean quarantined(
        World world,String username,String reason
    )throws Exception{
        try{
            world.persistence().load(username);
            return false;
        }catch(IOException blocked){
            return blocked.getMessage()!=null&&
                blocked.getMessage().contains(
                    "MAILBOX_PREPARED_LOAD_QUARANTINE"
                )&&blocked.getMessage().contains(reason);
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)
            throw new AssertionError(message);
    }

    private G2131MailboxPreparedRestartAdmissionIntegrationTest(){}
}
