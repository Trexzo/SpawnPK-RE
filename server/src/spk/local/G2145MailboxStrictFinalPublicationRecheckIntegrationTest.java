package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.45: test the genuine queued file-backed PREPARED strict writer
 * after G21.44 worker validation, while a strict temp is already forced
 * but before the G21.42 account-file replacement/publication lock.
 *
 * No native widget or reward grant is involved.
 */
public final class G2145MailboxStrictFinalPublicationRecheckIntegrationTest {
    private static final class Seed {
        final String account;
        final String messageId;
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;

        Seed(String account,String messageId,WorldPlayer player,
             long generation,MailboxSettlementPostimagePlanner.Proposal proposal){
            this.account=account;
            this.messageId=messageId;
            this.player=player;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean passedAdmissionAndStartedStrictSerialization=false;
        boolean ownerMovedWhileTempForced=false;
        boolean mutatedOwnerPublicationVeto=false;
        boolean mutatedOwnerOriginalDiskPreserved=false;
        boolean mutatedOwnerNoStrictReceipt=false;
        boolean movedOwnerWasNotLockedDuringSerialization=false;
        boolean retiredOwnerEnteredStrictWriter=false;
        boolean ownerRetiredDuringSerialization=false;
        boolean retiredOwnerPublicationVeto=false;
        boolean retiredOwnerDiskPreserved=false;
        boolean normalUnchangedOwnerStillPublishes=false;
        boolean normalStrictReceiptMatches=false;
        boolean markerStillWinsBeforeStrictReplacement=false;
        boolean markerRequiresRestartReview=false;
        boolean independentAccountUnaffected=false;
        boolean noTempArtifacts=false;
        boolean noGrantOrClaim=false;
        boolean noAutomaticMarkerRelease=false;
        boolean publicationLeasesClean=false;

        Path root=Files.createTempDirectory(
            "g2145-strict-final-owner-check-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence fence=new MailboxDurableReviewFence(paths);

        CountDownLatch movedAtFinalBoundary=new CountDownLatch(1);
        CountDownLatch movedResume=new CountDownLatch(1);
        CountDownLatch retiredAtFinalBoundary=new CountDownLatch(1);
        CountDownLatch retiredResume=new CountDownLatch(1);
        CountDownLatch markerAtFinalBoundary=new CountDownLatch(1);
        CountDownLatch markerResume=new CountDownLatch(1);
        AtomicInteger movedReachedDirForce=new AtomicInteger();
        AtomicInteger retiredReachedDirForce=new AtomicInteger();

        try{
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                Seed moved=seed(world,"g2145-moved","g2145:moved");
                Seed retired=seed(world,"g2145-retired","g2145:retired");
                Seed normal=seed(world,"g2145-normal","g2145:normal");
                Seed fenced=seed(world,"g2145-marker","g2145:marker");
                Seed independent=seed(
                    world,"g2145-independent","g2145:independent"
                );
                for(Seed seed:new Seed[]{
                        moved,retired,normal,fenced,independent})
                    repo.save(seed.proposal.preparedPreimage);

                byte[] movedBefore=Files.readAllBytes(
                    paths.resolve(moved.account)
                );
                byte[] retiredBefore=Files.readAllBytes(
                    paths.resolve(retired.account)
                );
                byte[] independentBefore=Files.readAllBytes(
                    paths.resolve(independent.account)
                );

                StrictDurablePlayerSnapshotWriter movedWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            movedAtFinalBoundary.countDown();
                            await(movedResume,"G21.45 moved writer");
                        }
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_DIRECTORY_FORCE)
                            movedReachedDirForce.incrementAndGet();
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    movedFuture=submit(world,moved,movedWriter);

                // We are AFTER G21.44's worker-start check and AFTER
                // writing/forcing the temp; disk replacement has not begun.
                passedAdmissionAndStartedStrictSerialization=
                    movedAtFinalBoundary.await(8,TimeUnit.SECONDS)&&
                    !movedFuture.isDone();

                // The World pulse must NOT be blocked by I/O holding
                // owner.mutationLock throughout serialization.
                world.submitAndWait(moved.player,moved.generation,()->{
                    moved.player.movement().setRunEnergy(31);
                },5000L);
                movedOwnerWasNotLockedDuringSerialization=true;
                ownerMovedWhileTempForced=
                    !PlayerSnapshotCodec.capture(
                        moved.account,moved.player
                    ).values().equals(
                        moved.proposal.preparedPreimage.values()
                    )&&!movedFuture.isDone();

                movedResume.countDown();
                mutatedOwnerPublicationVeto=failed(
                    movedFuture,
                    "G21.45 STRICT_PREPARED_FINAL_RECHECK_VETO"
                );
                mutatedOwnerOriginalDiskPreserved=
                    Arrays.equals(movedBefore,
                        Files.readAllBytes(paths.resolve(moved.account)));
                mutatedOwnerNoStrictReceipt=
                    movedReachedDirForce.get()==0&&
                    !fence.present(moved.account);

                StrictDurablePlayerSnapshotWriter retiredWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            retiredAtFinalBoundary.countDown();
                            await(retiredResume,"G21.45 retired writer");
                        }
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_DIRECTORY_FORCE)
                            retiredReachedDirForce.incrementAndGet();
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    retiredFuture=submit(world,retired,retiredWriter);
                retiredOwnerEnteredStrictWriter=
                    retiredAtFinalBoundary.await(8,TimeUnit.SECONDS)&&
                    !retiredFuture.isDone();
                ownerRetiredDuringSerialization=
                    world.unregisterPlayer(
                        retired.player,retired.generation
                    )&&!retiredFuture.isDone();
                retiredResume.countDown();
                retiredOwnerPublicationVeto=failed(
                    retiredFuture,
                    "G21.45 STRICT_PREPARED_FINAL_RECHECK_VETO"
                )&&retiredReachedDirForce.get()==0;
                retiredOwnerDiskPreserved=
                    Arrays.equals(retiredBefore,
                        Files.readAllBytes(paths.resolve(retired.account)));

                StrictDurablePlayerSnapshotWriter strict=
                    new StrictDurablePlayerSnapshotWriter(paths);
                StrictDurablePlayerSnapshotWriter.Receipt accepted=
                    submit(world,normal,strict).get(8,TimeUnit.SECONDS);
                normalUnchangedOwnerStillPublishes=
                    repo.load(normal.account).get().values().equals(
                        normal.proposal.preparedPreimage.values()
                    )&&!fence.present(normal.account);
                normalStrictReceiptMatches=accepted.matchesSnapshot(
                    normal.proposal.preparedPreimage
                );

                // Existing G21.42 disk-marker veto takes priority even
                // if live state still matches the strict PREPARED input.
                StrictDurablePlayerSnapshotWriter markerWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            markerAtFinalBoundary.countDown();
                            await(markerResume,"G21.45 marker writer");
                        }
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    markedFuture=submit(world,fenced,markerWriter);
                if(!markerAtFinalBoundary.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.45 marker race never reached publication"
                    );
                MailboxDurableReviewFence.Receipt negative=
                    fence.armVerifiedAgainstCurrentFile(fenced.proposal);
                markerResume.countDown();
                markerStillWinsBeforeStrictReplacement=failed(
                    markedFuture,
                    "G21.42 STRICT_WORLD_MAILBOX_REVIEW_SAVE_VETO"
                )&&negative.record.matches(fenced.proposal)&&
                    fence.present(fenced.account);

                independentAccountUnaffected=
                    Arrays.equals(independentBefore,
                        Files.readAllBytes(paths.resolve(
                            independent.account
                        )))&&repo.load(independent.account).isPresent();

                noGrantOrClaim=true;
                for(Seed seed:new Seed[]{
                        moved,retired,normal,fenced,independent}){
                    noGrantOrClaim &=
                        seed.player.bank().inventorySlots()==0&&
                        seed.player.mailbox().get(seed.messageId).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noAutomaticMarkerRelease=
                    !negative.grantAuthorized&&
                    !negative.replayAuthorized&&
                    !negative.record.releaseAuthorized&&
                    fence.present(fenced.account);
                try(Stream<Path> files=Files.list(root)){
                    noTempArtifacts=files.noneMatch(
                        file->file.getFileName().toString()
                            .endsWith(".tmp")
                    );
                }
                publicationLeasesClean=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                markerRequiresRestartReview=loadRefused(
                    restarted,"g2145-marker"
                )&&restarted.persistence().load(
                    "g2145-normal"
                ).isPresent();
            }
        }finally{
            movedResume.countDown();
            retiredResume.countDown();
            markerResume.countDown();
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }

        System.out.println(
            "G2145_MAILBOX_STRICT_FINAL_DIAGNOSTICS"+
            " genuineStrictWriterReached="+
                passedAdmissionAndStartedStrictSerialization+
            " liveMoveDuringSerialization="+ownerMovedWhileTempForced+
            " movedFinalPublicationVeto="+mutatedOwnerPublicationVeto+
            " originalMovedFilePreserved="+
                mutatedOwnerOriginalDiskPreserved+
            " noFalseMovedReceipt="+mutatedOwnerNoStrictReceipt+
            " worldPulseFreeDuringStrictIO="+
                movedOwnerWasNotLockedDuringSerialization+
            " retiredReachedStrictBoundary="+
                retiredOwnerEnteredStrictWriter+
            " retiredDuringSerialization="+
                ownerRetiredDuringSerialization+
            " retiredFinalPublicationVeto="+
                retiredOwnerPublicationVeto+
            " originalRetiredFilePreserved="+retiredOwnerDiskPreserved+
            " unchangedOwnerPublishes="+normalUnchangedOwnerStillPublishes+
            " unchangedReceiptExact="+normalStrictReceiptMatches+
            " negativeReviewMarkerWins="+
                markerStillWinsBeforeStrictReplacement+
            " markerPersistsAcrossRestart="+
                markerRequiresRestartReview+
            " unrelatedAccountPreserved="+independentAccountUnaffected+
            " noStrictTemps="+noTempArtifacts+
            " noLiveItemCredit="+noGrantOrClaim+
            " noAutoRelease="+noAutomaticMarkerRelease+
            " noJvmLeaseLeaks="+publicationLeasesClean
        );
        require(
            passedAdmissionAndStartedStrictSerialization&&
            ownerMovedWhileTempForced&&mutatedOwnerPublicationVeto&&
            mutatedOwnerOriginalDiskPreserved&&
            mutatedOwnerNoStrictReceipt&&
            movedOwnerWasNotLockedDuringSerialization&&
            retiredOwnerEnteredStrictWriter&&
            ownerRetiredDuringSerialization&&
            retiredOwnerPublicationVeto&&retiredOwnerDiskPreserved&&
            normalUnchangedOwnerStillPublishes&&
            normalStrictReceiptMatches&&
            markerStillWinsBeforeStrictReplacement&&
            markerRequiresRestartReview&&
            independentAccountUnaffected&&noTempArtifacts&&
            noGrantOrClaim&&noAutomaticMarkerRelease&&
            publicationLeasesClean,
            "G21.45 strict final publication owner recheck"
        );
        System.out.println(
            "G2145_MAILBOX_STRICT_FINAL_PUBLICATION_RECHECK_PASS"+
            " ownerChangedDuringSerializationDenied=true"+
            " generationRetiredDuringSerializationDenied=true"+
            " unchangedOwnerSaved=true"+
            " originalAccountBytesPreservedOnVeto=true"+
            " noWorldLockHeldAcrossSerialization=true"+
            " grant=false replay=false release=false"
        );
    }

    private static Seed seed(
        World world,String account,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            proposal=new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"Final publication recheck","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2145_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(
                    player,selected
                )
            );
            proposal.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,selected
            ));
        },5000L);
        return new Seed(account,messageId,player,generation,
            proposal.get());
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> submit(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,seed.proposal.preparedPreimage,
            writer
        );
    }

    private static boolean failed(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> future,
        String marker
    )throws Exception{
        try{
            future.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException expected){
            Throwable cause=expected.getCause();
            return cause instanceof IOException&&
                cause.getMessage()!=null&&
                cause.getMessage().contains(marker);
        }
    }

    private static boolean loadRefused(
        World world,String account
    )throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void await(CountDownLatch latch,String where)
        throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException(where+" timeout");
        }catch(InterruptedException interruption){
            Thread.currentThread().interrupt();
            throw new IOException(where+" interrupted",interruption);
        }
    }

    private static void require(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
    }

    private G2145MailboxStrictFinalPublicationRecheckIntegrationTest(){}
}
