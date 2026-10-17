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
 * G21.44: a real file-backed World persistence worker must recheck the
 * live owner/snapshot after FIFO queue delay, before strict disk I/O.
 *
 * The actual single World persistence worker is blocked in an earlier
 * ordinary save; concurrent World commands change/retire a different
 * owner while its PREPARED strict barrier is already queued.
 */
public final class G2144MailboxStrictWorkerOwnerRecheckIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final String account;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer player,String account,long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.player=player;
            this.account=account;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean workerReallyBlockedBeforeStrict=false;
        boolean admissionInitiallyAccepted=false;
        boolean mutationWhileQueuedExecuted=false;
        boolean queuedStaleSnapshotDenied=false;
        boolean staleWriterNeverEntered=false;
        boolean staleDiskFilePreserved=false;
        boolean freshAfterRejectionSucceeds=false;
        boolean ownerUnregisteredWhileQueued=false;
        boolean queuedRetiredGenerationDenied=false;
        boolean retiredWriterNeverEntered=false;
        boolean retiredDiskFilePreserved=false;
        boolean unchangedQueuedOwnerStillAccepted=false;
        boolean unchangedQueuedReceiptMatches=false;
        boolean unrelatedBlockerSavesSucceed=false;
        boolean noRewardCredits=false;
        boolean mailboxStatesStayUnclaimed=false;
        boolean noReviewMarkerReleasedOrCreated=false;
        boolean noTempLeak=false;
        boolean noJvmPublicationLeaseLeak=false;

        Path root=Files.createTempDirectory(
            "g2144-mailbox-strict-worker-recheck-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        final String blocker="g2144-blocker";
        CountDownLatch[] entered={
            new CountDownLatch(1),new CountDownLatch(1),
            new CountDownLatch(1)
        };
        CountDownLatch[] release={
            new CountDownLatch(1),new CountDownLatch(1),
            new CountDownLatch(1)
        };
        AtomicInteger blockerWrites=new AtomicInteger();
        FilePlayerRepository file=new FilePlayerRepository(
            paths,account->{
                if(!blocker.equals(account))
                    return;
                int wave=blockerWrites.getAndIncrement();
                if(wave>=entered.length)
                    return;
                entered[wave].countDown();
                await(release[wave],
                    "G21.44 blocked ordinary World save wave="+wave);
            }
        );
        StrictDurablePlayerSnapshotWriter strictWriter=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(paths);
        AtomicInteger strictIOTouches=new AtomicInteger();
        StrictDurablePlayerSnapshotWriter observingStrictWriter=
            new StrictDurablePlayerSnapshotWriter(
                paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_TEMP_CREATE)
                        strictIOTouches.incrementAndGet();
                }
            );
        try{
            try(World world=World.isolatedForTest(60000L,file)){
                world.start();
                WorldPlayer blockerOwner=new WorldPlayer();
                long blockerGeneration=
                    world.registerPlayer(blockerOwner,blocker);
                Seed stale=seed(world,"g2144-stale","g2144:stale");
                Seed retired=seed(world,"g2144-retired","g2144:retired");
                Seed stable=seed(world,"g2144-stable","g2144:stable");

                for(Seed seed:new Seed[]{stale,retired,stable})
                    file.save(seed.proposal.preparedPreimage);
                byte[] staleBefore=Files.readAllBytes(
                    paths.resolve(stale.account)
                );
                byte[] retiredBefore=Files.readAllBytes(
                    paths.resolve(retired.account)
                );

                // Wave 1: G21.43 admits an EXACT matching snapshot.
                // Then World movement mutates before the worker can
                // service this queued strict task.
                WorldPlayerPersistence.SaveTicket blocking1=
                    blockWorldWorker(
                        world,blockerOwner,blockerGeneration,
                        "BLOCK_STRICT_QUEUE_ONE"
                    );
                workerReallyBlockedBeforeStrict=
                    entered[0].await(8,TimeUnit.SECONDS)&&
                    !blocking1.completion.isDone();
                int oldIO=strictIOTouches.get();
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    staleTask=strict(world,stale,
                        stale.proposal.preparedPreimage,
                        observingStrictWriter
                    );
                admissionInitiallyAccepted=
                    !staleTask.isDone()&&
                    world.persistence().queuedWrites()>=1;
                world.submitAndWait(
                    stale.player,stale.generation,
                    ()->stale.player.movement().setRunEnergy(33),
                    5000L
                );
                mutationWhileQueuedExecuted=
                    !staleTask.isDone()&&
                    PlayerSnapshotCodec.capture(
                        stale.account,stale.player
                    ).values().equals(
                        stale.proposal.preparedPreimage.values()
                    )==false;
                release[0].countDown();
                blocking1.completion.get(8,TimeUnit.SECONDS);
                queuedStaleSnapshotDenied=rejects(
                    staleTask,
                    "G21.44 STRICT_PREPARED_WORKER_SNAPSHOT_DIVERGED"
                );
                staleWriterNeverEntered=
                    strictIOTouches.get()==oldIO;
                staleDiskFilePreserved=
                    Arrays.equals(staleBefore,
                        Files.readAllBytes(paths.resolve(stale.account)));

                // A fresh still-PREPARED capture may be enqueued and
                // saved after the older rejected barrier.
                PlayerSnapshot fresh=PlayerSnapshotCodec.capture(
                    stale.account,stale.player
                );
                StrictDurablePlayerSnapshotWriter.Receipt freshReceipt=
                    strict(world,stale,fresh,strictWriter)
                        .get(8,TimeUnit.SECONDS);
                freshAfterRejectionSucceeds=
                    freshReceipt.matchesSnapshot(fresh)&&
                    file.load(stale.account).get().values().equals(
                        fresh.values()
                    );

                // Wave 2: the registered owner is retired entirely
                // while its strict barrier is queued. Generation is
                // rechecked before invoking the file writer.
                WorldPlayerPersistence.SaveTicket blocking2=
                    blockWorldWorker(
                        world,blockerOwner,blockerGeneration,
                        "BLOCK_STRICT_QUEUE_TWO"
                    );
                if(!entered[1].await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.44 second blocking save missing"
                    );
                oldIO=strictIOTouches.get();
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    retiredTask=strict(
                        world,retired,retired.proposal.preparedPreimage,
                        observingStrictWriter
                    );
                ownerUnregisteredWhileQueued=
                    world.unregisterPlayer(
                        retired.player,retired.generation
                    )&&!retiredTask.isDone();
                release[1].countDown();
                blocking2.completion.get(8,TimeUnit.SECONDS);
                queuedRetiredGenerationDenied=rejects(
                    retiredTask,
                    "G21.44 STRICT_PREPARED_WORKER_OWNER_RETIRED"
                );
                retiredWriterNeverEntered=
                    strictIOTouches.get()==oldIO;
                retiredDiskFilePreserved=
                    Arrays.equals(retiredBefore,
                        Files.readAllBytes(paths.resolve(retired.account)));

                // Wave 3: a stable queued PREPARED account must still
                // succeed once the earlier worker operation releases.
                WorldPlayerPersistence.SaveTicket blocking3=
                    blockWorldWorker(
                        world,blockerOwner,blockerGeneration,
                        "BLOCK_STRICT_QUEUE_THREE"
                    );
                if(!entered[2].await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.44 third blocking save missing"
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    stableTask=strict(
                        world,stable,stable.proposal.preparedPreimage,
                        strictWriter
                    );
                unchangedQueuedOwnerStillAccepted=
                    !stableTask.isDone()&&
                    world.persistence().queuedWrites()>=1;
                release[2].countDown();
                blocking3.completion.get(8,TimeUnit.SECONDS);
                StrictDurablePlayerSnapshotWriter.Receipt stableReceipt=
                    stableTask.get(8,TimeUnit.SECONDS);
                unchangedQueuedReceiptMatches=
                    stableReceipt.matchesSnapshot(
                        stable.proposal.preparedPreimage
                    )&&
                    file.load(stable.account).get().values().equals(
                        stable.proposal.preparedPreimage.values()
                    );
                unrelatedBlockerSavesSucceed=
                    blockerWrites.get()==3&&
                    file.load(blocker).isPresent()&&
                    !fence.present(blocker);

                noRewardCredits=
                    stale.player.bank().inventorySlots()==0&&
                    retired.player.bank().inventorySlots()==0&&
                    stable.player.bank().inventorySlots()==0;
                mailboxStatesStayUnclaimed=true;
                for(Seed seed:new Seed[]{stale,retired,stable})
                    mailboxStatesStayUnclaimed &=
                        seed.player.mailbox().get(
                            seed.proposal.messageId
                        ).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                noReviewMarkerReleasedOrCreated=
                    !fence.present(stale.account)&&
                    !fence.present(retired.account)&&
                    !fence.present(stable.account);
                noJvmPublicationLeaseLeak=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
                try(Stream<Path> files=Files.list(root)){
                    noTempLeak=files.noneMatch(
                        path->path.getFileName().toString()
                            .endsWith(".tmp")
                    );
                }
            }
        }finally{
            for(CountDownLatch gate:release)
                gate.countDown();
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(
                    Comparator.reverseOrder()
                ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2144_MAILBOX_STRICT_WORKER_DIAGNOSTICS"+
            " actualWorkerBlocked="+workerReallyBlockedBeforeStrict+
            " exactAtAdmission="+admissionInitiallyAccepted+
            " ownerChangedWhileQueued="+mutationWhileQueuedExecuted+
            " staleSnapshotWorkerRejected="+queuedStaleSnapshotDenied+
            " staleWriterNeverStarted="+staleWriterNeverEntered+
            " staleDiskPreserved="+staleDiskFilePreserved+
            " refreshedPreparedSuccess="+freshAfterRejectionSucceeds+
            " ownerRetiredWhileQueued="+ownerUnregisteredWhileQueued+
            " retiredWorkerRefused="+queuedRetiredGenerationDenied+
            " retiredWriterNeverStarted="+retiredWriterNeverEntered+
            " retiredDiskPreserved="+retiredDiskFilePreserved+
            " stableQueuedAccepted="+unchangedQueuedOwnerStillAccepted+
            " stableReceiptMatches="+unchangedQueuedReceiptMatches+
            " earlierWorldSavesComplete="+unrelatedBlockerSavesSucceed+
            " noInventoryGrant="+noRewardCredits+
            " mailboxUnclaimed="+mailboxStatesStayUnclaimed+
            " noReviewMarkerMutation="+
                noReviewMarkerReleasedOrCreated+
            " noTempArtifacts="+noTempLeak+
            " noJvmLockLeaks="+noJvmPublicationLeaseLeak
        );

        require(
            workerReallyBlockedBeforeStrict&&admissionInitiallyAccepted&&
            mutationWhileQueuedExecuted&&queuedStaleSnapshotDenied&&
            staleWriterNeverEntered&&staleDiskFilePreserved&&
            freshAfterRejectionSucceeds&&ownerUnregisteredWhileQueued&&
            queuedRetiredGenerationDenied&&retiredWriterNeverEntered&&
            retiredDiskFilePreserved&&unchangedQueuedOwnerStillAccepted&&
            unchangedQueuedReceiptMatches&&
            unrelatedBlockerSavesSucceed&&noRewardCredits&&
            mailboxStatesStayUnclaimed&&
            noReviewMarkerReleasedOrCreated&&noTempLeak&&
            noJvmPublicationLeaseLeak,
            "G21.44 strict barrier worker recheck"
        );
        System.out.println(
            "G2144_MAILBOX_STRICT_WORKER_RECHECK_PASS"+
            " ownerChangedBetweenAdmissionAndWorkerDenied=true"+
            " retiredGenerationDenied=true"+
            " unaffectedQueuedStrictSaved=true"+
            " noDiskIOForRejectedBarrier=true"+
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
                messageId,"Worker strict proof","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2144_FIXTURE"
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

    private static WorldPlayerPersistence.SaveTicket blockWorldWorker(
        World world,WorldPlayer owner,long generation,String why
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
            new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            ticket.set(world.persistence().captureAndSave(
                owner.username(),owner,generation,0,
                "[g2144] ",why
            ));
        },5000L);
        return ticket.get();
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> strict(
        World world,Seed owner,PlayerSnapshot snapshot,
        StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            owner.player,owner.generation,snapshot,writer
        );
    }

    private static boolean rejects(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> task,
        String message
    )throws Exception{
        try{
            task.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException expected){
            Throwable cause=expected.getCause();
            return cause instanceof IllegalStateException&&
                cause.getMessage()!=null&&
                cause.getMessage().contains(message);
        }
    }

    private static void await(
        CountDownLatch latch,String reason
    )throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException(reason+" timeout");
        }catch(InterruptedException interruption){
            Thread.currentThread().interrupt();
            throw new IOException(reason+" interrupted",interruption);
        }
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private G2144MailboxStrictWorkerOwnerRecheckIntegrationTest(){}
}
