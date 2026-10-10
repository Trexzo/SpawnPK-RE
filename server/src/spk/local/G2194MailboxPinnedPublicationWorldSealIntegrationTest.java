package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Real disk + cooperating lock + G21.93 World owner handoff. */
public final class G2194MailboxPinnedPublicationWorldSealIntegrationTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.94 "+message);
    }

    private static WorldPlayer seed(
        World source,String account,FilePlayerRepository.PathResolver path,
        StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord record
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=source.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2194_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,row));
        MailboxSettlementPostimagePlanner.Proposal proposal=
            MailboxSettlementPostimagePlanner.plan(player,generation,row);
        writer.saveStrict(proposal.preparedPreimage);
        journal.publishPreparedIntent(proposal);
        PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(
            proposal);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(
                terminal,path.resolve(account),
                StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                    proposal.preparedPreimage),()->{},()->{});
        check(receipt.matchesSnapshot(terminal),"strict terminal receipt");
        record.recordConfirmedDiskTerminal(proposal,receipt);
        return player;
    }

    private static WorldPlayerPersistence.CommittedRecoveryReservation reserve(
        World world,String account,WorldPlayer receiver,long generation
    )throws Exception{
        WorldPlayerPersistence.CommittedRecoveryReservation token=
            world.persistence().reserveCommittedRecovery(
                receiver,generation,account);
        token.drained().get(5,TimeUnit.SECONDS);
        return token;
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2194-pinned-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord record=
            new MailboxGuardedDiskCommitRecord(paths);
        MailboxCommittedDetachedRestartRecovery restorer=
            new MailboxCommittedDetachedRestartRecovery(paths);
        boolean pinned=false,repeatDenied=false,afterPinCancelDenied=false;
        boolean inodeSwapRejected=false,staleDenied=false;
        boolean heldCooperatingWriterOut=false;
        boolean diskAndOwnerUnchanged=false,ordinarySaveVeto=false;
        boolean allAuthorityFalse=false,noLeases=false;
        final String good="g2194-good",swap="g2194-swap",
            stale="g2194-stale";
        try(World original=World.isolatedForTest(60000L);
            World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths))){
            WorldPlayer originalGood=seed(original,good,paths,writer,
                journal,record);
            WorldPlayer originalSwap=seed(original,swap,paths,writer,
                journal,record);
            WorldPlayer originalStale=seed(original,stale,paths,writer,
                journal,record);
            byte[] accountBefore=Files.readAllBytes(paths.resolve(good));
            byte[] journalBefore=Files.readAllBytes(journal.journalPath(good));
            byte[] commitBefore=Files.readAllBytes(record.recordPath(good));

            WorldPlayer goodReceiver=new WorldPlayer();
            long goodGeneration=world.registerPlayer(goodReceiver,good);
            WorldPlayerPersistence.CommittedRecoveryReservation goodToken=
                reserve(world,good,goodReceiver,goodGeneration);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence goodProof=
                goodToken.inspectOneShotHandoff(restorer);
            allAuthorityFalse=!goodProof.transactionCommitted&&
                !goodProof.liveApplied&&!goodProof.grantAuthorized&&
                !goodProof.replayAuthorized&&
                !goodProof.restartAdmissionAuthorized&&
                !goodProof.releaseAuthorized&&!goodProof.clientAckAuthorized;

            // A cooperating writer attempting publication during the
            // outer pinned critical section cannot acquire the same
            // account OS/JVM lock. The hook executes before the pinned
            // second disk read and World ownership seal.
            CountDownLatch writerStarted=new CountDownLatch(1);
            CountDownLatch writerDone=new CountDownLatch(1);
            AtomicBoolean writerInside=new AtomicBoolean();
            AtomicReference<Throwable> writerFailure=new AtomicReference<>();
            MailboxCommittedDetachedRestartRecovery.PinnedReadOnlyAction<
                WorldPlayerPersistence.RecoverySealDecision> hook=
                before->{
                    Thread competing=new Thread(()->{
                        writerStarted.countDown();
                        try{
                            MailboxAccountPublicationCoordinator
                                .withExclusivePublicationBounded(
                                    paths.resolve(good),2000L,()->{
                                        writerInside.set(true);
                                        return null;
                                    });
                        }catch(Throwable error){
                            writerFailure.set(error);
                        }finally{
                            writerDone.countDown();
                        }
                    },"g2194-competing-publisher");
                    competing.start();
                    check(writerStarted.await(1,TimeUnit.SECONDS),
                        "cooperating writer did not start");
                    heldCooperatingWriterOut=!writerInside.get()&&
                        !writerDone.await(100,TimeUnit.MILLISECONDS);
                    return WorldPlayerPersistence.RecoverySealDecision
                        .REJECT_EVIDENCE;
                };
            // Verify OS lock without consuming the one-shot seal.
            WorldPlayerPersistence.RecoverySealDecision hookVerdict=
                restorer.withPinnedPublication(good,hook);
            check(hookVerdict==WorldPlayerPersistence
                    .RecoverySealDecision.REJECT_EVIDENCE,
                "cooperating lock probe");
            check(writerDone.await(3,TimeUnit.SECONDS)&&
                writerFailure.get()==null&&writerInside.get(),
                "competing writer acquired after pin release");
            pinned=goodToken.sealWithPinnedPublication(restorer,goodProof)==
                WorldPlayerPersistence.RecoverySealDecision
                    .SEALED_QUARANTINE_NO_ADMISSION&&
                goodToken.publicationPinVerified()&&
                goodToken.sealedNoAdmission();
            repeatDenied=goodToken.sealWithPinnedPublication(
                restorer,goodProof)==
                    WorldPlayerPersistence.RecoverySealDecision
                        .REJECT_PIN_ALREADY_ATTEMPTED;
            afterPinCancelDenied=!goodToken.cancelIfStillFresh()&&
                goodToken.isActive();

            WorldPlayer swapReceiver=new WorldPlayer();
            long swapGeneration=world.registerPlayer(swapReceiver,swap);
            WorldPlayerPersistence.CommittedRecoveryReservation swapToken=
                reserve(world,swap,swapReceiver,swapGeneration);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence swapProof=
                swapToken.inspectOneShotHandoff(restorer);
            Path originalAccount=paths.resolve(swap);
            Path replacement=originalAccount.resolveSibling(
                originalAccount.getFileName()+".replaced");
            Files.copy(originalAccount,replacement);
            Files.move(replacement,originalAccount,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
            inodeSwapRejected=swapToken.sealWithPinnedPublication(
                restorer,swapProof)==
                    WorldPlayerPersistence.RecoverySealDecision
                        .REJECT_DISK_CHANGED&&
                !swapToken.publicationPinVerified()&&
                !swapToken.cancelIfStillFresh()&&swapToken.isActive();

            WorldPlayer staleReceiver=new WorldPlayer();
            long staleGeneration=world.registerPlayer(
                staleReceiver,stale);
            WorldPlayerPersistence.CommittedRecoveryReservation staleToken=
                reserve(world,stale,staleReceiver,staleGeneration);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence staleProof=
                staleToken.inspectOneShotHandoff(restorer);
            world.unregisterPlayer(staleReceiver,staleGeneration);
            staleDenied=staleToken.sealWithPinnedPublication(
                restorer,staleProof)==
                    WorldPlayerPersistence.RecoverySealDecision
                        .REJECT_NOT_OWNED&&
                !staleToken.cancelIfStillFresh()&&staleToken.isActive();

            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> save=
                new AtomicReference<>();
            world.commands().submit(goodReceiver,goodGeneration,()->{
                save.set(world.persistence().captureAndSave(
                    good,goodReceiver,goodGeneration,0,
                    "[g2194] ","PINNED_QUARANTINE_SAVE"));
            }).get(5,TimeUnit.SECONDS);
            check(save.get()!=null,"World save ticket missing");
            try{
                save.get().completion.get(5,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException expected){
                ordinarySaveVeto=String.valueOf(
                    expected.getCause().getMessage()).contains(
                        "G21.92 COMMITTED_RECOVERY_WRITE_FENCED");
            }
            diskAndOwnerUnchanged=
                Arrays.equals(accountBefore,Files.readAllBytes(
                    paths.resolve(good)))&&
                Arrays.equals(journalBefore,Files.readAllBytes(
                    journal.journalPath(good)))&&
                Arrays.equals(commitBefore,Files.readAllBytes(
                    record.recordPath(good)))&&
                originalGood.bank().inventorySlots()==0&&
                originalSwap.bank().inventorySlots()==0&&
                originalStale.bank().inventorySlots()==0&&
                originalGood.mailbox().get(good+":gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                goodReceiver.bank().inventorySlots()==0&&
                goodReceiver.mailbox().size()==0&&
                world.persistence().committedRecoveryReservationsForTest()==3;
        }finally{
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2194_PINNED_WORLD_SEAL_DIAGNOSTICS"+
            " pinned="+pinned+
            " repeatDenied="+repeatDenied+
            " pinnedCancelDenied="+afterPinCancelDenied+
            " sameBytesNewInodeDenied="+inodeSwapRejected+
            " staleGenerationDenied="+staleDenied+
            " cooperatingWriterExcludedDuringPin="+
                heldCooperatingWriterOut+
            " realWorldSaveDenied="+ordinarySaveVeto+
            " diskAndOwnerUnchanged="+diskAndOwnerUnchanged+
            " allAuthorityFalse="+allAuthorityFalse+
            " noPublicationLeases="+noLeases);
        check(pinned&&repeatDenied&&afterPinCancelDenied&&
            inodeSwapRejected&&staleDenied&&heldCooperatingWriterOut&&
            ordinarySaveVeto&&diskAndOwnerUnchanged&&
            allAuthorityFalse&&noLeases,
            "pinned publication/World negative recovery seal");
        System.out.println("G2194_PINNED_PUBLICATION_WORLD_SEAL_PASS"+
            " boundedCooperatingLock=true"+
            " threePhysicalFileKeys=true"+
            " preAndPostDiskValidation=true"+
            " noFilesystemIoInsideWorldOwnership=true"+
            " grant=false liveApply=false session=false"+
            " replay=false release=false ack=false");
    }
}
