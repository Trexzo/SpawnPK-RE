package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.47: persist a separate NO_GRANT review obligation after a strict
 * World account file has ALREADY been replaced, but before its uncertain
 * result returns. Restart loader and guarded saves must fail closed.
 */
public final class G2147MailboxStrictUnconfirmedRestartFenceIntegrationTest {
    private static final class Seed {
        final String account;
        final String messageId;
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;

        Seed(String account,String messageId,WorldPlayer player,
             long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.account=account;
            this.messageId=messageId;
            this.player=player;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean changedAfterFileForce=false;
        boolean lateMovementStillUnconfirmed=false;
        boolean changedAccountBytesNotRolledBack=false;
        boolean exactDurableNegativeRecord=false;
        boolean negativeRecordHasNoGrantAuthority=false;
        boolean realWorldSaveDeniedByStrictMarker=false;
        boolean generationRetiredAfterFileForce=false;
        boolean retiredStrictResultUnconfirmed=false;
        boolean retiredNegativeRecordMatches=false;
        boolean stableAccountStrictReceipt=true;
        boolean stableAccountNeverMarked=false;
        boolean sameMarkerNoClobber=true;
        boolean corruptMarkerStillBlocks=true;
        boolean corruptMarkerRejectedByInspector=true;
        boolean restartedAffectedAccountDenied=false;
        boolean restartedRetiredAccountDenied=false;
        boolean unaffectedAccountCanLoad=false;
        boolean noUnpublishedTemps=true;
        boolean noLiveGrantOrClaim=true;
        boolean noJvmPublicationLeaseLeaks=false;

        Path root=Files.createTempDirectory(
            "g2147-mailbox-strict-uncertain-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        MailboxDurableReviewFence shared=new MailboxDurableReviewFence(paths);

        CountDownLatch movedForced=new CountDownLatch(1);
        CountDownLatch releaseMoved=new CountDownLatch(1);
        CountDownLatch retiredForced=new CountDownLatch(1);
        CountDownLatch releaseRetired=new CountDownLatch(1);
        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Seed moved=seed(world,"g2147-moved","g2147:moved");
                Seed retired=seed(world,"g2147-retired","g2147:retired");
                Seed stable=seed(world,"g2147-stable","g2147:stable");
                for(Seed one:new Seed[]{moved,retired,stable})
                    repository.save(one.proposal.preparedPreimage);
                byte[] original=Files.readAllBytes(
                    paths.resolve(moved.account)
                );

                StrictDurablePlayerSnapshotWriter changedWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_DIRECTORY_FORCE){
                            movedForced.countDown();
                            await(releaseMoved,
                                "G21.47 changed owner force seam");
                        }
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    changedTask=strict(world,moved,changedWriter);

                if(!movedForced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.47 changed account never reached force seam"
                    );
                changedAfterFileForce=
                    !changedTask.isDone()&&
                    !Arrays.equals(original,Files.readAllBytes(
                        paths.resolve(moved.account)
                    ));
                world.submitAndWait(
                    moved.player,moved.generation,
                    ()->moved.player.movement().setRunEnergy(34),
                    5000L
                );
                releaseMoved.countDown();
                lateMovementStillUnconfirmed=unconfirmed(changedTask);
                changedAccountBytesNotRolledBack=
                    repository.load(moved.account).get().values().equals(
                        moved.proposal.preparedPreimage.values()
                    );

                Path movedFile=paths.resolve(moved.account);
                Path movedFence=
                    MailboxStrictUnconfirmedReviewFence.fencePath(
                        movedFile
                    );
                MailboxStrictUnconfirmedReviewFence.Record movedRecord=
                    MailboxStrictUnconfirmedReviewFence.inspect(movedFile);
                exactDurableNegativeRecord=
                    Files.isRegularFile(movedFence)&&
                    shared.present(moved.account)&&
                    movedRecord.account.equals(moved.account)&&
                    movedRecord.strictSnapshotSha256.equals(
                        StrictDurablePlayerSnapshotWriter
                            .canonicalSnapshotSha256(
                                moved.proposal.preparedPreimage
                            )
                    );
                negativeRecordHasNoGrantAuthority=
                    !movedRecord.grantAuthorized&&
                    !movedRecord.replayAuthorized&&
                    !movedRecord.rollbackAuthorized&&
                    !movedRecord.releaseAuthorized&&
                    !movedRecord.admissionAuthorized;

                // A real guarded World save of the marked account is
                // now refused: the negative sidecar is not merely a
                // diagnostic exception in the old worker process.
                AtomicReference<WorldPlayerPersistence.SaveTicket>
                    blocked=new AtomicReference<>();
                world.submitAndWait(
                    moved.player,moved.generation,()->{
                        blocked.set(world.persistence().captureAndSave(
                            moved.account,moved.player,moved.generation,
                            0,"[g2147] ","STRICT_UNCONFIRMED_MARKED_SAVE"
                        ));
                    },5000L
                );
                realWorldSaveDeniedByStrictMarker=
                    failsWith(blocked.get().completion,
                        "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO")&&
                    shared.present(moved.account);

                StrictDurablePlayerSnapshotWriter retirementWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_DIRECTORY_FORCE){
                            retiredForced.countDown();
                            await(releaseRetired,
                                "G21.47 retired owner force seam");
                        }
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    retiredTask=strict(world,retired,retirementWriter);
                if(!retiredForced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.47 retired account never reached force seam"
                    );
                generationRetiredAfterFileForce=
                    world.unregisterPlayer(
                        retired.player,retired.generation
                    )&&!retiredTask.isDone();
                releaseRetired.countDown();
                retiredStrictResultUnconfirmed=unconfirmed(retiredTask);
                MailboxStrictUnconfirmedReviewFence.Record retiredRecord=
                    MailboxStrictUnconfirmedReviewFence.inspect(
                        paths.resolve(retired.account)
                    );
                retiredNegativeRecordMatches=
                    shared.present(retired.account)&&
                    retiredRecord.account.equals(retired.account)&&
                    retiredRecord.strictSnapshotSha256.equals(
                        StrictDurablePlayerSnapshotWriter
                            .canonicalSnapshotSha256(
                                retired.proposal.preparedPreimage
                            )
                    );

                StrictDurablePlayerSnapshotWriter.Receipt stableReceipt=
                    strict(world,stable,
                        new StrictDurablePlayerSnapshotWriter(paths))
                    .get(8,TimeUnit.SECONDS);
                stableAccountStrictReceipt=
                    stableReceipt.matchesSnapshot(
                        stable.proposal.preparedPreimage
                    );
                stableAccountNeverMarked=
                    !shared.present(stable.account)&&
                    !MailboxStrictUnconfirmedReviewFence.present(
                        paths.resolve(stable.account)
                    );

                byte[] beforeSecond=Files.readAllBytes(movedFence);
                boolean duplicateFailed=false;
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(movedFile,()->{
                            MailboxStrictUnconfirmedReviewFence
                                .armInsidePublicationLock(
                                    movedFile,moved.account,
                                    movedRecord.strictSnapshotSha256
                                );
                            return null;
                        });
                }catch(IOException expected){
                    duplicateFailed=expected.getMessage()!=null&&
                        expected.getMessage().contains("already exists");
                }
                sameMarkerNoClobber=duplicateFailed&&
                    Arrays.equals(
                        beforeSecond,Files.readAllBytes(movedFence)
                    );

                // Even deliberate external damage cannot make the
                // existing file-backed login/save gates treat a marker
                // as absent or authorize replay.
                Files.writeString(
                    movedFence,"corrupted-but-still-negative",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING
                );
                corruptMarkerStillBlocks=shared.present(moved.account);
                try{
                    MailboxStrictUnconfirmedReviewFence.inspect(movedFile);
                    corruptMarkerRejectedByInspector=false;
                }catch(IOException expected){
                    corruptMarkerRejectedByInspector=true;
                }

                for(Seed one:new Seed[]{moved,retired,stable}){
                    noLiveGrantOrClaim &=
                        one.player.bank().inventorySlots()==0&&
                        one.player.mailbox().get(
                            one.messageId
                        ).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noJvmPublicationLeaseLeaks=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
                try(Stream<Path> found=Files.list(root)){
                    noUnpublishedTemps=found.noneMatch(
                        p->p.getFileName().toString().endsWith(".tmp")
                    );
                }
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                restartedAffectedAccountDenied=
                    denied(restarted,"g2147-moved");
                restartedRetiredAccountDenied=
                    denied(restarted,"g2147-retired");
                unaffectedAccountCanLoad=
                    restarted.persistence().load(
                        "g2147-stable"
                    ).isPresent();
            }
        }finally{
            releaseMoved.countDown();
            releaseRetired.countDown();
            try(Stream<Path> found=Files.walk(root)){
                for(Path path:found.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2147_MAILBOX_STRICT_RESTART_QUARANTINE_DIAGNOSTICS"+
            " accountAlreadyReplaced="+changedAfterFileForce+
            " liveMutationUnconfirmed="+lateMovementStillUnconfirmed+
            " accountNotRolledBack="+changedAccountBytesNotRolledBack+
            " exactNegativeRecord="+exactDurableNegativeRecord+
            " negativeOnlyRecord="+negativeRecordHasNoGrantAuthority+
            " guardedSaveDenied="+realWorldSaveDeniedByStrictMarker+
            " generationRetiredPostForce="+generationRetiredAfterFileForce+
            " retiredOutcomeUnconfirmed="+retiredStrictResultUnconfirmed+
            " retiredDigestRecord="+retiredNegativeRecordMatches+
            " stableStrictReceipt="+stableAccountStrictReceipt+
            " stableNotFenced="+stableAccountNeverMarked+
            " noClobberDuplicateRefused="+sameMarkerNoClobber+
            " corruptStillPresent="+corruptMarkerStillBlocks+
            " corruptInspectorRejected="+corruptMarkerRejectedByInspector+
            " restartChangedDenied="+restartedAffectedAccountDenied+
            " restartRetiredDenied="+restartedRetiredAccountDenied+
            " restartStableAllowed="+unaffectedAccountCanLoad+
            " noTempFiles="+noUnpublishedTemps+
            " noLiveRewardCredit="+noLiveGrantOrClaim+
            " noJvmLockLeaks="+noJvmPublicationLeaseLeaks
        );
        require(
            changedAfterFileForce&&lateMovementStillUnconfirmed&&
            changedAccountBytesNotRolledBack&&exactDurableNegativeRecord&&
            negativeRecordHasNoGrantAuthority&&
            realWorldSaveDeniedByStrictMarker&&
            generationRetiredAfterFileForce&&
            retiredStrictResultUnconfirmed&&retiredNegativeRecordMatches&&
            stableAccountStrictReceipt&&stableAccountNeverMarked&&
            sameMarkerNoClobber&&corruptMarkerStillBlocks&&
            corruptMarkerRejectedByInspector&&
            restartedAffectedAccountDenied&&
            restartedRetiredAccountDenied&&unaffectedAccountCanLoad&&
            noUnpublishedTemps&&noLiveGrantOrClaim&&
            noJvmPublicationLeaseLeaks,
            "G21.47 strict postpublication restart quarantine"
        );
        System.out.println(
            "G2147_MAILBOX_STRICT_RESTART_QUARANTINE_PASS"+
            " uncertainStrictPersistedNegativeMarker=true"+
            " restartedUncertainAccountsBlocked=true"+
            " guardedWorldSavesBlocked=true"+
            " normalStrictAccountAllowed=true"+
            " grant=false replay=false rollback=false release=false"
        );
    }

    private static Seed seed(
        World world,String account,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            ref=new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"Strict review sidecar","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2147_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot row=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(
                    player,row
                )
            );
            ref.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,row
            ));
        },5000L);
        return new Seed(account,messageId,player,generation,ref.get());
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> strict(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,seed.proposal.preparedPreimage,
            writer
        );
    }

    private static boolean unconfirmed(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> task
    )throws Exception{
        try{
            task.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException failure){
            Throwable cause=failure.getCause();
            return cause instanceof
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException&&
                cause.getMessage()!=null&&
                cause.getMessage().contains(
                    "G21.46 STRICT_PREPARED_POSTPUBLICATION_UNCONFIRMED"
                );
        }
    }

    private static boolean failsWith(
        CompletableFuture<Void> future,String message
    )throws Exception{
        try{
            future.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException failed){
            Throwable cause=failed.getCause();
            return cause instanceof IOException&&
                cause.getMessage()!=null&&
                cause.getMessage().contains(message);
        }
    }

    private static boolean denied(
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

    private static void await(CountDownLatch latch,String detail)
        throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException(detail+" timed out");
        }catch(InterruptedException stop){
            Thread.currentThread().interrupt();
            throw new IOException(detail+" interrupted",stop);
        }
    }

    private static void require(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }

    private G2147MailboxStrictUnconfirmedRestartFenceIntegrationTest(){}
}
