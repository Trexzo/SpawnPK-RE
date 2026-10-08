package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.41: exact disk PREPARED preimage must still exist when a
 * verified-only negative review marker obtains the SAME publication
 * lock used by cooperating World account writers.
 */
public final class G2141MailboxVerifiedReviewMarkerIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final String account;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(
            WorldPlayer player,String account,long generation,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
            this.player=player;
            this.account=account;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean exactDiskPreimageAccepted=false;
        boolean matchingMarkerRestartVeto=false;
        boolean missingFileRejectedNoMarker=false;
        boolean invalidFileRejectedNoMarker=false;
        boolean hypotheticalClaimedRejected=false;
        boolean changedCanonicalSnapshotRejected=false;
        boolean changedFileNotOverwritten=false;
        boolean competingWorldSaveReachedBoundary=false;
        boolean cooperatingSaveWinsThenVerifiedMarkerRefused=false;
        boolean concurrentOldFileNotReverted=false;
        boolean unchangedOtherAccountCanPublish=false;
        boolean duplicateExistingMarkerRefused=false;
        boolean priorUnconditionalNegativeArmPreserved=false;
        boolean noOrphanMarkerTemps=false;
        boolean noLiveGrantOrClaim=false;
        boolean noAutomaticReleaseOrReplay=false;
        boolean publicationLeaseClean=false;

        Path dir=Files.createTempDirectory(
            "g2141-verified-mailbox-marker-"
        );
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(paths);
        MailboxDurableReviewFence marker=
            new MailboxDurableReviewFence(paths);

        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch armBeforeLock=new CountDownLatch(1);
        CountDownLatch allowArm=new CountDownLatch(1);
        try{
            try(World world=World.isolatedForTest(60000L,disk)){
                world.start();
                Seed exact=seed(world,"g2141-exact","g2141:exact");
                Seed missing=seed(world,"g2141-missing","g2141:missing");
                Seed invalid=seed(world,"g2141-invalid","g2141:invalid");
                Seed hypothetical=seed(world,"g2141-hypo","g2141:hypo");
                Seed changed=seed(world,"g2141-changed","g2141:changed");
                Seed raced=seed(world,"g2141-raced","g2141:raced");
                Seed other=seed(world,"g2141-other","g2141:other");
                Seed legacy=seed(world,"g2141-legacy","g2141:legacy");

                disk.save(exact.proposal.preparedPreimage);
                disk.save(hypothetical.proposal.hypotheticalPostimage);
                disk.save(changed.proposal.preparedPreimage);
                disk.save(raced.proposal.preparedPreimage);
                disk.save(other.proposal.preparedPreimage);
                Files.writeString(
                    paths.resolve(invalid.account),
                    "broken-player-snapshot",
                    StandardCharsets.US_ASCII
                );

                MailboxDurableReviewFence.Receipt exactReceipt=
                    marker.armVerifiedAgainstCurrentFile(exact.proposal);
                exactDiskPreimageAccepted=
                    exactReceipt.record.matches(exact.proposal)&&
                    marker.present(exact.account)&&
                    !exactReceipt.grantAuthorized&&
                    !exactReceipt.replayAuthorized;

                missingFileRejectedNoMarker=
                    refused(()->marker.armVerifiedAgainstCurrentFile(
                        missing.proposal
                    ))&&!marker.present(missing.account)&&
                    !Files.exists(paths.resolve(missing.account));

                invalidFileRejectedNoMarker=
                    refused(()->marker.armVerifiedAgainstCurrentFile(
                        invalid.proposal
                    ))&&!marker.present(invalid.account);

                hypotheticalClaimedRejected=
                    refused(()->marker.armVerifiedAgainstCurrentFile(
                        hypothetical.proposal
                    ))&&!marker.present(hypothetical.account)&&
                    disk.load(hypothetical.account).get().values().equals(
                        hypothetical.proposal.hypotheticalPostimage.values()
                    );

                world.submitAndWait(
                    changed.player,changed.generation,
                    ()->changed.player.movement().setRunEnergy(47),
                    5000L
                );
                PlayerSnapshot changedSnapshot=PlayerSnapshotCodec.capture(
                    changed.account,changed.player
                );
                disk.save(changedSnapshot);
                changedCanonicalSnapshotRejected=
                    refused(()->marker.armVerifiedAgainstCurrentFile(
                        changed.proposal
                    ))&&!marker.present(changed.account);
                changedFileNotOverwritten=
                    disk.load(changed.account).get().values().equals(
                        changedSnapshot.values()
                    );

                // Deterministic cooperating writer interleaving: the
                // verified marker is paused before taking the SAME
                // G21.38 publication lock. A real World save publishes
                // a newer PREPARED account snapshot, then marker must
                // inspect disk while holding lock and reject the stale
                // original proposal WITHOUT creating a marker.
                MailboxDurableReviewFence pausedMarker=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            armBeforeLock.countDown();
                            try{
                                if(!allowArm.await(8,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.41 verified arm release timeout"
                                    );
                            }catch(InterruptedException interrupt){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.41 verified arm interrupted",
                                    interrupt
                                );
                            }
                        }
                    });
                Future<Boolean> pending=executor.submit(
                    ()->refused(
                        ()->pausedMarker.armVerifiedAgainstCurrentFile(
                            raced.proposal
                        )
                    )
                );
                if(!armBeforeLock.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.41 verified marker never reached race gate"
                    );

                AtomicReference<WorldPlayerPersistence.SaveTicket>
                    saved=new AtomicReference<>();
                world.submitAndWait(
                    raced.player,raced.generation,()->{
                        raced.player.movement().setRunEnergy(39);
                        saved.set(world.persistence().captureAndSave(
                            raced.account,raced.player,raced.generation,0,
                            "[g2141] ","COOPERATING_SAVE_BEFORE_VERIFIED_ARM"
                        ));
                    },5000L
                );
                saved.get().completion.get(8,TimeUnit.SECONDS);
                competingWorldSaveReachedBoundary=
                    !marker.present(raced.account)&&
                    !pending.isDone();
                PlayerSnapshot persisted=disk.load(raced.account).get();
                allowArm.countDown();
                cooperatingSaveWinsThenVerifiedMarkerRefused=
                    pending.get(8,TimeUnit.SECONDS)&&
                    !marker.present(raced.account);
                concurrentOldFileNotReverted=
                    disk.load(raced.account).get().values().equals(
                        persisted.values()
                    )&&
                    !persisted.values().equals(
                        raced.proposal.preparedPreimage.values()
                    );

                MailboxDurableReviewFence.Receipt otherReceipt=
                    marker.armVerifiedAgainstCurrentFile(other.proposal);
                unchangedOtherAccountCanPublish=
                    otherReceipt.record.matches(other.proposal)&&
                    marker.present(other.account);

                duplicateExistingMarkerRefused=
                    refused(()->marker.armVerifiedAgainstCurrentFile(
                        exact.proposal
                    ))&&marker.inspect(exact.account).matches(
                        exact.proposal
                    );

                // Preserve older G21.32 contract: a negative-only
                // marker may be armed even when disk is absent, because
                // that earlier path intentionally means REVIEW REQUIRED.
                MailboxDurableReviewFence.Receipt prior=
                    marker.arm(legacy.proposal);
                priorUnconditionalNegativeArmPreserved=
                    !Files.exists(paths.resolve(legacy.account))&&
                    prior.record.matches(legacy.proposal)&&
                    marker.present(legacy.account);

                noLiveGrantOrClaim=true;
                for(Seed seed:new Seed[]{
                        exact,missing,invalid,hypothetical,
                        changed,raced,other,legacy}){
                    noLiveGrantOrClaim &=
                        seed.player.bank().inventorySlots()==0&&
                        seed.player.mailbox().get(
                            seed.proposal.messageId
                        ).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noAutomaticReleaseOrReplay=
                    !exactReceipt.grantAuthorized&&
                    !exactReceipt.replayAuthorized&&
                    !otherReceipt.grantAuthorized&&
                    !otherReceipt.replayAuthorized&&
                    !prior.grantAuthorized&&
                    !prior.replayAuthorized&&
                    !prior.record.releaseAuthorized;

                try(Stream<Path> files=Files.list(dir)){
                    noOrphanMarkerTemps=files.noneMatch(path->
                        path.getFileName().toString().contains(
                            ".g2132-"
                        )&&path.getFileName()
                            .toString().endsWith(".tmp")
                    );
                }
                publicationLeaseClean=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                matchingMarkerRestartVeto=
                    loadRejected(restarted,"g2141-exact")&&
                    loadRejected(restarted,"g2141-other")&&
                    loadRejected(restarted,"g2141-legacy");
            }
        }finally{
            allowArm.countDown();
            executor.shutdownNow();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2141_MAILBOX_VERIFIED_REVIEW_DIAGNOSTICS"+
            " exactPersistedPreparedAllowed="+exactDiskPreimageAccepted+
            " restartedSessionVeto="+matchingMarkerRestartVeto+
            " absentFileRejected="+missingFileRejectedNoMarker+
            " corruptFileRejected="+invalidFileRejectedNoMarker+
            " hypotheticalClaimRejected="+hypotheticalClaimedRejected+
            " canonicalChangedRejected="+changedCanonicalSnapshotRejected+
            " changedAccountPreserved="+changedFileNotOverwritten+
            " realWorldSavePrecededArm="+competingWorldSaveReachedBoundary+
            " staleArmRejectedAfterRealSave="+
                cooperatingSaveWinsThenVerifiedMarkerRefused+
            " latestWorldSaveKept="+concurrentOldFileNotReverted+
            " independentPreparedAccountAllowed="+
                unchangedOtherAccountCanPublish+
            " duplicateReviewMarkerDenied="+
                duplicateExistingMarkerRefused+
            " priorNegativeOnlyArmPreserved="+
                priorUnconditionalNegativeArmPreserved+
            " noTempArtifacts="+noOrphanMarkerTemps+
            " noLiveRewardOrClaim="+noLiveGrantOrClaim+
            " noAutoReplayOrRelease="+noAutomaticReleaseOrReplay+
            " accountLockLeasesClean="+publicationLeaseClean
        );
        require(
            exactDiskPreimageAccepted&&matchingMarkerRestartVeto&&
            missingFileRejectedNoMarker&&invalidFileRejectedNoMarker&&
            hypotheticalClaimedRejected&&
            changedCanonicalSnapshotRejected&&changedFileNotOverwritten&&
            competingWorldSaveReachedBoundary&&
            cooperatingSaveWinsThenVerifiedMarkerRefused&&
            concurrentOldFileNotReverted&&
            unchangedOtherAccountCanPublish&&
            duplicateExistingMarkerRefused&&
            priorUnconditionalNegativeArmPreserved&&
            noOrphanMarkerTemps&&noLiveGrantOrClaim&&
            noAutomaticReleaseOrReplay&&publicationLeaseClean,
            "G21.41 verified negative marker gate"
        );
        System.out.println(
            "G2141_MAILBOX_VERIFIED_REVIEW_MARKER_PASS"+
            " exactDiskPreimageUnderPublicationLock=true"+
            " staleDiskRejectsMarker=true"+
            " cooperatingWorldSaveWinsBeforeArm=true"+
            " oldNegativeOnlyArmStillSupported=true"+
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
                messageId,"Verify review identity","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2141_FIXTURE"
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
        return new Seed(player,account,generation,proposal.get());
    }

    private interface CheckedOperation {
        void execute()throws Exception;
    }

    private static boolean refused(CheckedOperation operation)
        throws Exception{
        try{
            operation.execute();
            return false;
        }catch(IOException expected){
            return true;
        }
    }

    private static boolean loadRejected(
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

    private static void require(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }

    private G2141MailboxVerifiedReviewMarkerIntegrationTest(){}
}
