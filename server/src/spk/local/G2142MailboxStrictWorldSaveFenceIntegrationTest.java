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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.42: actual World strict PREPARED task now honors the same account
 * lock as the G21.34 negative marker. The historical direct strict
 * primitive remains separate; no live widget, item grant or release.
 */
public final class G2142MailboxStrictWorldSaveFenceIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final String account;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer player,long generation,String account,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.player=player;
            this.generation=generation;
            this.account=account;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean markerAlreadyPresentStrictDenied=false;
        boolean existingAccountUnchangedOnVeto=false;
        boolean strictReachedPreReplace=false;
        boolean markerAppearedBeforeStrictLock=false;
        boolean strictVetoesWhenMarkerWins=false;
        boolean oldFilePreservedAfterMarkerWins=false;
        boolean strictHeldPublicationLock=false;
        boolean markerCannotPublishWhileStrictHeld=false;
        boolean strictSaveCompletesThenMarker=false;
        boolean unrelatedStrictSaveSucceeds=false;
        boolean mismatchedStrictWriterPathDenied=false;
        boolean hypotheticalClaimedPreimageDenied=false;
        boolean noOrphanStrictTemps=false;
        boolean freshWorldStillReviewFenced=false;
        boolean noGrantOrClaim=false;
        boolean noMarkerRelease=false;
        boolean leaseRegistryClean=false;

        Path dir=Files.createTempDirectory(
            "g2142-strict-world-review-fence-"
        );
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter normalWriter=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableReviewFence marker=
            new MailboxDurableReviewFence(paths);
        CountDownLatch midAtPreReplace=new CountDownLatch(1);
        CountDownLatch midContinue=new CountDownLatch(1);
        CountDownLatch firstInsideLock=new CountDownLatch(1);
        CountDownLatch firstContinue=new CountDownLatch(1);
        CountDownLatch markerAtFinalStage=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);

        try{
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                Seed prior=seed(world,"g2142-prior","g2142:prior");
                Seed mid=seed(world,"g2142-mid","g2142:mid");
                Seed first=seed(world,"g2142-first","g2142:first");
                Seed other=seed(world,"g2142-other","g2142:other");
                Seed mismatch=seed(world,"g2142-mismatch","g2142:mismatch");
                Seed hypothetical=seed(world,"g2142-hypo","g2142:hypo");
                for(Seed s:new Seed[]{
                    prior,mid,first,other,mismatch,hypothetical
                })repo.save(s.proposal.preparedPreimage);

                byte[] beforePrior=Files.readAllBytes(
                    paths.resolve(prior.account)
                );
                MailboxDurableReviewFence.Receipt priorMarker=
                    marker.armVerifiedAgainstCurrentFile(prior.proposal);
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    priorTask=strict(world,prior,normalWriter);
                markerAlreadyPresentStrictDenied=
                    fails(priorTask,"G21.42 STRICT_WORLD_MAILBOX_REVIEW_SAVE_VETO")&&
                    marker.present(prior.account);
                existingAccountUnchangedOnVeto=
                    Arrays.equals(beforePrior,
                        Files.readAllBytes(paths.resolve(prior.account)));

                // A REAL single-FIFO World strict barrier encodes
                // its temporary file, pauses BEFORE publication lock.
                // An independent G21.34 marker then publishes first.
                byte[] beforeMid=Files.readAllBytes(
                    paths.resolve(mid.account)
                );
                StrictDurablePlayerSnapshotWriter midWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .BEFORE_ATOMIC_REPLACE){
                                midAtPreReplace.countDown();
                                await(midContinue,
                                    "G21.42 release mid strict writer");
                            }
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    midTask=strict(world,mid,midWriter);
                strictReachedPreReplace=
                    midAtPreReplace.await(8,TimeUnit.SECONDS)&&
                    !midTask.isDone()&&!marker.present(mid.account);
                MailboxDurableReviewFence.Receipt midReceipt=
                    marker.armVerifiedAgainstCurrentFile(mid.proposal);
                markerAppearedBeforeStrictLock=
                    midReceipt.record.matches(mid.proposal)&&
                    marker.present(mid.account);
                midContinue.countDown();
                strictVetoesWhenMarkerWins=
                    fails(midTask,"G21.42 STRICT_WORLD_MAILBOX_REVIEW_SAVE_VETO");
                oldFilePreservedAfterMarkerWins=
                    Arrays.equals(beforeMid,
                        Files.readAllBytes(paths.resolve(mid.account)));

                // The reverse: strict writer's directory force phase is
                // INSIDE the shared publication lock. Independent marker
                // publication must wait until the strict receipt returns.
                StrictDurablePlayerSnapshotWriter firstWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .BEFORE_DIRECTORY_FORCE){
                                firstInsideLock.countDown();
                                await(firstContinue,
                                    "G21.42 release strict-first writer");
                            }
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    firstTask=strict(world,first,firstWriter);
                strictHeldPublicationLock=
                    firstInsideLock.await(8,TimeUnit.SECONDS)&&
                    !firstTask.isDone();

                MailboxDurableReviewFence afterWriter=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            markerAtFinalStage.countDown();
                    });
                Future<MailboxDurableReviewFence.Receipt> afterMarker=
                    pool.submit(()->afterWriter.arm(first.proposal));
                markerCannotPublishWhileStrictHeld=
                    markerAtFinalStage.await(8,TimeUnit.SECONDS)&&
                    !afterMarker.isDone()&&!marker.present(first.account);
                firstContinue.countDown();

                StrictDurablePlayerSnapshotWriter.Receipt firstSaved=
                    firstTask.get(8,TimeUnit.SECONDS);
                MailboxDurableReviewFence.Receipt afterPublished=
                    afterMarker.get(8,TimeUnit.SECONDS);
                strictSaveCompletesThenMarker=
                    firstSaved.matchesSnapshot(
                        first.proposal.preparedPreimage
                    )&&afterPublished.record.matches(first.proposal)&&
                    marker.present(first.account);

                StrictDurablePlayerSnapshotWriter.Receipt otherSaved=
                    strict(world,other,normalWriter).get(
                        8,TimeUnit.SECONDS
                    );
                unrelatedStrictSaveSucceeds=
                    otherSaved.matchesSnapshot(
                        other.proposal.preparedPreimage
                    )&&!marker.present(other.account);

                FilePlayerRepository.PathResolver wrong=
                    account->dir.resolve("not-the-world-account.properties");
                StrictDurablePlayerSnapshotWriter wrongWriter=
                    new StrictDurablePlayerSnapshotWriter(wrong);
                mismatchedStrictWriterPathDenied=
                    fails(strict(world,mismatch,wrongWriter),
                        "G21.42 STRICT_WORLD_ACCOUNT_PATH_MISMATCH")&&
                    !Files.exists(dir.resolve(
                        "not-the-world-account.properties"
                    ));

                // PREPARED-only normal strict barrier never treats the
                // hypothetical inventory+CLAIMED postimage as a valid
                // journal or a grant, even without a marker.
                StrictDurablePlayerSnapshotWriter independent=
                    new StrictDurablePlayerSnapshotWriter(paths);
                boolean rejected=false;
                try{
                    independent.saveStrictForWorld(
                        hypothetical.proposal.hypotheticalPostimage,
                        repo.accountFilePath(hypothetical.account)
                    );
                }catch(IOException expected){
                    rejected=expected.getMessage().contains(
                        "G21.42 STRICT_WORLD_PREPARED_QUARANTINE"
                    );
                }
                hypotheticalClaimedPreimageDenied=rejected;

                noGrantOrClaim=true;
                for(Seed s:new Seed[]{
                    prior,mid,first,other,mismatch,hypothetical
                }){
                    noGrantOrClaim &=
                        s.player.bank().inventorySlots()==0&&
                        s.player.mailbox().get(s.proposal.messageId)
                            .claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noMarkerRelease=
                    !priorMarker.grantAuthorized&&
                    !midReceipt.replayAuthorized&&
                    !afterPublished.record.releaseAuthorized&&
                    marker.present(prior.account)&&
                    marker.present(mid.account)&&
                    marker.present(first.account);

                try(Stream<Path> files=Files.list(dir)){
                    noOrphanStrictTemps=files.noneMatch(path->
                        path.getFileName().toString().contains(
                            ".g2123-"
                        )&&path.getFileName()
                            .toString().endsWith(".tmp"));
                }
                leaseRegistryClean=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                freshWorldStillReviewFenced=
                    denies(restarted,"g2142-prior")&&
                    denies(restarted,"g2142-mid")&&
                    denies(restarted,"g2142-first")&&
                    restarted.persistence().load("g2142-other").isPresent();
            }
        }finally{
            midContinue.countDown();
            firstContinue.countDown();
            pool.shutdownNow();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println(
            "G2142_MAILBOX_STRICT_WORLD_DIAGNOSTICS"+
            " preexistingMarkerVeto="+markerAlreadyPresentStrictDenied+
            " existingFileUnchanged="+existingAccountUnchangedOnVeto+
            " serializationReached="+strictReachedPreReplace+
            " markerWonPreLock="+markerAppearedBeforeStrictLock+
            " strictTicketVeto="+strictVetoesWhenMarkerWins+
            " preimageRetained="+oldFilePreservedAfterMarkerWins+
            " strictHeldLock="+strictHeldPublicationLock+
            " markerWaitedForStrict="+markerCannotPublishWhileStrictHeld+
            " strictThenMarker="+strictSaveCompletesThenMarker+
            " unrelatedStrictPass="+unrelatedStrictSaveSucceeds+
            " writerPathMismatchVeto="+mismatchedStrictWriterPathDenied+
            " hypotheticalPostimageVeto="+hypotheticalClaimedPreimageDenied+
            " noTempLeaks="+noOrphanStrictTemps+
            " restartAdmissionVeto="+freshWorldStillReviewFenced+
            " noLiveCreditOrClaim="+noGrantOrClaim+
            " noAutoRelease="+noMarkerRelease+
            " noJvmLeaseLeaks="+leaseRegistryClean
        );

        require(
            markerAlreadyPresentStrictDenied&&
            existingAccountUnchangedOnVeto&&strictReachedPreReplace&&
            markerAppearedBeforeStrictLock&&
            strictVetoesWhenMarkerWins&&
            oldFilePreservedAfterMarkerWins&&
            strictHeldPublicationLock&&
            markerCannotPublishWhileStrictHeld&&
            strictSaveCompletesThenMarker&&
            unrelatedStrictSaveSucceeds&&
            mismatchedStrictWriterPathDenied&&
            hypotheticalClaimedPreimageDenied&&noOrphanStrictTemps&&
            freshWorldStillReviewFenced&&noGrantOrClaim&&
            noMarkerRelease&&leaseRegistryClean,
            "G21.42 strict PREPARED World write lock gate"
        );
        System.out.println(
            "G2142_MAILBOX_STRICT_WORLD_SAVE_FENCE_PASS"+
            " realStrictBarrierGuarded=true"+
            " markerBeforeStrictDenied=true"+
            " strictBeforeMarkerSerialized=true"+
            " pathMismatchDenied=true"+
            " noGrantReplayOrRelease=true"
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
                messageId,"Strict barrier review gate","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2142_FIXTURE"
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
        return new Seed(player,generation,account,proposal.get());
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> strict(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,
            seed.proposal.preparedPreimage,writer
        );
    }

    private static boolean fails(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> work,
        String expected
    )throws Exception{
        try{
            work.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException caught){
            Throwable failed=caught.getCause();
            return failed instanceof IOException&&
                failed.getMessage()!=null&&
                failed.getMessage().contains(expected);
        }
    }

    private static boolean denies(
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

    private static void await(
        CountDownLatch gate,String label
    )throws IOException{
        try{
            if(!gate.await(8,TimeUnit.SECONDS))
                throw new IOException(label+" timeout");
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(label+" interrupted",interrupted);
        }
    }

    private static void require(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }

    private G2142MailboxStrictWorldSaveFenceIntegrationTest(){}
}
