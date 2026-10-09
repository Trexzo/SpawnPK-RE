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
 * G21.46: exact strict World PREPARED account file may ALREADY have
 * been atomically replaced when live owner diverges during directory
 * force. Fail with UNCONFIRMED rather than returning a false Receipt.
 * Never grant, claim, replay, rollback, or release a review marker.
 */
public final class G2146MailboxStrictPostpublicationUnconfirmedIntegrationTest {
    private static final class Seed {
        final String account;
        final String messageId;
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(
            String account,String messageId,WorldPlayer player,
            long generation,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
            this.account=account;
            this.messageId=messageId;
            this.player=player;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean alreadyReplacedBeforeLateMutation=false;
        boolean movementDuringPostpublishWindow=false;
        boolean movementProducesUnconfirmedNotReceipt=false;
        boolean accountBytesNotRolledBackOnUnconfirmed=false;
        boolean persistedPreparedRemainsUnclaimed=false;
        boolean retiredAfterAtomicReplace=false;
        boolean retiredProducesUnconfirmedNotReceipt=false;
        boolean retiredAccountMayContainNewBytes=false;
        boolean normalUnchangedOwnerGetsReceipt=false;
        boolean normalStrictAccountRoundTrip=false;
        boolean independentAccountUnchanged=false;
        boolean noFalseReviewMarkerCreated=false;
        boolean noTempArtifacts=false;
        boolean noJvmPublicationLeaseLeak=false;
        boolean noInventoryCreditOrClaim=false;
        boolean noAutomaticRollbackReplayRelease=false;

        Path root=Files.createTempDirectory(
            "g2146-strict-postpublish-unconfirmed-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence fences=new MailboxDurableReviewFence(paths);

        CountDownLatch movementPostforced=new CountDownLatch(1);
        CountDownLatch movementResume=new CountDownLatch(1);
        CountDownLatch retiredPostforced=new CountDownLatch(1);
        CountDownLatch retiredResume=new CountDownLatch(1);
        AtomicInteger movementAfterForceCalls=new AtomicInteger();
        AtomicInteger retiredAfterForceCalls=new AtomicInteger();

        try{
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                Seed moved=seed(world,"g2146-move","g2146:move");
                Seed retired=seed(world,"g2146-retire","g2146:retire");
                Seed steady=seed(world,"g2146-steady","g2146:steady");
                Seed unrelated=seed(world,"g2146-other","g2146:other");

                for(Seed s:new Seed[]{moved,retired,steady,unrelated})
                    repo.save(s.proposal.preparedPreimage);

                byte[] oldMovementBytes=Files.readAllBytes(
                    paths.resolve(moved.account)
                );
                byte[] oldRetiredBytes=Files.readAllBytes(
                    paths.resolve(retired.account)
                );
                byte[] unrelatedBytes=Files.readAllBytes(
                    paths.resolve(unrelated.account)
                );

                StrictDurablePlayerSnapshotWriter movedWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter
                                    .Phase.AFTER_DIRECTORY_FORCE){
                                movementAfterForceCalls.incrementAndGet();
                                movementPostforced.countDown();
                                await(movementResume,
                                    "G21.46 postforced movement release");
                            }
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    movementFuture=submit(world,moved,movedWriter);
                if(!movementPostforced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.46 move strict publication not reached"
                    );
                alreadyReplacedBeforeLateMutation=
                    !movementFuture.isDone()&&
                    movementAfterForceCalls.get()==1&&
                    repo.load(moved.account).get().values().equals(
                        moved.proposal.preparedPreimage.values()
                    )&&
                    !Arrays.equals(oldMovementBytes,
                        Files.readAllBytes(paths.resolve(moved.account)));

                // G21.45 pre-publication recheck has already PASSED;
                // this World-owned gameplay change happens only AFTER
                // the account-file atomic move and directory force.
                world.submitAndWait(
                    moved.player,moved.generation,
                    ()->moved.player.movement().setRunEnergy(29),5000L
                );
                movementDuringPostpublishWindow=
                    !movementFuture.isDone()&&
                    !PlayerSnapshotCodec.capture(
                        moved.account,moved.player
                    ).values().equals(
                        moved.proposal.preparedPreimage.values()
                    );
                movementResume.countDown();
                movementProducesUnconfirmedNotReceipt=
                    unconfirmed(movementFuture)&&
                    movementAfterForceCalls.get()==1;
                accountBytesNotRolledBackOnUnconfirmed=
                    repo.load(moved.account).get().values().equals(
                        moved.proposal.preparedPreimage.values()
                    )&&
                    !Arrays.equals(oldMovementBytes,
                        Files.readAllBytes(paths.resolve(moved.account)));
                persistedPreparedRemainsUnclaimed=
                    MailboxPreparedRestartAdmission.inspect(
                        repo.load(moved.account).get()
                    ).state==MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED;

                StrictDurablePlayerSnapshotWriter retiredWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter
                                    .Phase.AFTER_DIRECTORY_FORCE){
                                retiredAfterForceCalls.incrementAndGet();
                                retiredPostforced.countDown();
                                await(retiredResume,
                                    "G21.46 postforced retirement release");
                            }
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    retiredFuture=submit(world,retired,retiredWriter);
                if(!retiredPostforced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.46 retired strict publication not reached"
                    );
                retiredAfterAtomicReplace=
                    world.unregisterPlayer(
                        retired.player,retired.generation
                    )&&!retiredFuture.isDone()&&
                    retiredAfterForceCalls.get()==1;
                retiredResume.countDown();
                retiredProducesUnconfirmedNotReceipt=
                    unconfirmed(retiredFuture);
                retiredAccountMayContainNewBytes=
                    repo.load(retired.account).get().values().equals(
                        retired.proposal.preparedPreimage.values()
                    )&&
                    !Arrays.equals(oldRetiredBytes,
                        Files.readAllBytes(paths.resolve(retired.account)));

                StrictDurablePlayerSnapshotWriter clean=
                    new StrictDurablePlayerSnapshotWriter(paths);
                StrictDurablePlayerSnapshotWriter.Receipt accepted=
                    submit(world,steady,clean).get(8,TimeUnit.SECONDS);
                normalUnchangedOwnerGetsReceipt=accepted.matchesSnapshot(
                    steady.proposal.preparedPreimage
                );
                normalStrictAccountRoundTrip=
                    repo.load(steady.account).get().values().equals(
                        steady.proposal.preparedPreimage.values()
                    );
                independentAccountUnchanged=
                    Arrays.equals(unrelatedBytes,
                        Files.readAllBytes(paths.resolve(unrelated.account)));

                // G21.47 strengthens the earlier G21.46 unconfirmed
                // result: only already-published uncertain accounts
                // receive negative restart fences; clean receipts do not.
                noFalseReviewMarkerCreated=
                    fences.present(moved.account)&&
                    fences.present(retired.account)&&
                    !fences.present(steady.account)&&
                    !fences.present(unrelated.account);
                noInventoryCreditOrClaim=true;
                for(Seed s:new Seed[]{moved,retired,steady,unrelated}){
                    noInventoryCreditOrClaim &=
                        s.player.bank().inventorySlots()==0&&
                        s.player.mailbox().get(s.messageId).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noAutomaticRollbackReplayRelease=
                    StrictDurablePlayerSnapshotWriter.AUTHORITY
                        .contains("STRICT_FILE_BOUNDARY_ONLY")&&
                    MailboxPreparedClaimJournal.STATE.equals(
                        "PREPARED_NO_GRANT"
                    );
                noJvmPublicationLeaseLeak=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
                try(Stream<Path> files=Files.list(root)){
                    noTempArtifacts=files.noneMatch(
                        file->file.getFileName().toString()
                            .endsWith(".tmp")
                    );
                }
            }
        }finally{
            movementResume.countDown();
            retiredResume.countDown();
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }

        System.out.println(
            "G2146_MAILBOX_STRICT_POSTPUBLICATION_DIAGNOSTICS"+
            " atomicAccountAlreadyReplaced="+
                alreadyReplacedBeforeLateMutation+
            " moveAfterForce="+movementDuringPostpublishWindow+
            " movementNoFalseReceipt="+
                movementProducesUnconfirmedNotReceipt+
            " noClaimedRollback="+accountBytesNotRolledBackOnUnconfirmed+
            " fileStillPrepared="+persistedPreparedRemainsUnclaimed+
            " unregisterAfterForce="+retiredAfterAtomicReplace+
            " retirementNoFalseReceipt="+
                retiredProducesUnconfirmedNotReceipt+
            " retiredDiskStateMayRemain="+
                retiredAccountMayContainNewBytes+
            " unchangedOwnerReceipt="+normalUnchangedOwnerGetsReceipt+
            " unchangedDiskRoundtrip="+normalStrictAccountRoundTrip+
            " otherAccountUnchanged="+independentAccountUnchanged+
            " negativeOnlyOnUnconfirmed="+noFalseReviewMarkerCreated+
            " noTempArtifacts="+noTempArtifacts+
            " noPublicationLeaseLeaks="+noJvmPublicationLeaseLeak+
            " noItemGrantOrClaim="+noInventoryCreditOrClaim+
            " noAutoRecoveryAuthority="+
                noAutomaticRollbackReplayRelease
        );

        require(
            alreadyReplacedBeforeLateMutation&&
            movementDuringPostpublishWindow&&
            movementProducesUnconfirmedNotReceipt&&
            accountBytesNotRolledBackOnUnconfirmed&&
            persistedPreparedRemainsUnclaimed&&
            retiredAfterAtomicReplace&&
            retiredProducesUnconfirmedNotReceipt&&
            retiredAccountMayContainNewBytes&&
            normalUnchangedOwnerGetsReceipt&&
            normalStrictAccountRoundTrip&&
            independentAccountUnchanged&&
            noFalseReviewMarkerCreated&&noTempArtifacts&&
            noJvmPublicationLeaseLeak&&noInventoryCreditOrClaim&&
            noAutomaticRollbackReplayRelease,
            "G21.46 post-publication strict PREPARED receipt veto"
        );
        System.out.println(
            "G2146_MAILBOX_STRICT_POSTPUBLICATION_UNCONFIRMED_PASS"+
            " changedLiveOwnerCannotReceiveStrictReceipt=true"+
            " retiredOwnerCannotReceiveStrictReceipt=true"+
            " accountMoveMayHaveOccurred=true"+
            " noAutomaticRollback=true"+
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
                messageId,"Strict postpublication review","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2146_FIXTURE"
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
        return new Seed(account,messageId,player,generation,proposal.get());
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> submit(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,
            seed.proposal.preparedPreimage,writer
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

    private static void await(
        CountDownLatch latch,String detail
    )throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException(detail+" timeout");
        }catch(InterruptedException interruption){
            Thread.currentThread().interrupt();
            throw new IOException(detail+" interrupted",interruption);
        }
    }

    private static void require(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }

    private G2146MailboxStrictPostpublicationUnconfirmedIntegrationTest(){}
}
