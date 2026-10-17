package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.39: per-account JVM publication lock isolation without losing
 * G21.38's cross-process FileLock and review-marker no-grant authority.
 */
public final class G2139MailboxAccountLockIsolationIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean sameAccountWaits=false;
        boolean otherAccountProceedsWhileHeld=false;
        boolean sameAccountSerialized=false;
        boolean sharedAccountLeaseRetained=false;
        boolean registryCleanAfterContendingWriters=false;
        boolean ioFailureDoesNotLeakLease=false;
        boolean repeatedDistinctAccountsNoLeak=false;
        boolean realUnrelatedWorldSaveNotBlocked=false;
        boolean markerStillNoClobber=false;
        boolean restartReviewVetoPreserved=false;
        boolean unrelatedAccountStillLoads=false;
        boolean noLiveClaimOrGrant=false;
        boolean noAutomaticRelease=false;
        boolean canonicalStableLockPaths=false;

        Path dir=Files.createTempDirectory(
            "g2139-mailbox-account-local-lock-"
        );
        Path a=dir.resolve("first.properties");
        Path b=dir.resolve("second.properties");
        CountDownLatch firstInside=new CountDownLatch(1);
        CountDownLatch releaseFirst=new CountDownLatch(1);
        CountDownLatch sameStarted=new CountDownLatch(1);
        CountDownLatch sameInside=new CountDownLatch(1);
        AtomicInteger activeA=new AtomicInteger();
        AtomicInteger maximumA=new AtomicInteger();
        ExecutorService executor=Executors.newFixedThreadPool(4);

        try{
            canonicalStableLockPaths=
                MailboxAccountPublicationCoordinator.lockPath(a)
                    .equals(MailboxAccountPublicationCoordinator
                        .lockPath(a.toAbsolutePath().normalize()))&&
                !MailboxAccountPublicationCoordinator.lockPath(a)
                    .equals(MailboxAccountPublicationCoordinator
                        .lockPath(b));

            Future<Void> first=executor.submit(()->{
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(a,()->{
                        int count=activeA.incrementAndGet();
                        maximumA.accumulateAndGet(count,Math::max);
                        firstInside.countDown();
                        try{
                            await(releaseFirst,
                                "G21.39 first publication release");
                        }finally{
                            activeA.decrementAndGet();
                        }
                        return null;
                    });
                return null;
            });
            if(!firstInside.await(8,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.39 first account never locked"
                );

            Future<Void> second=executor.submit(()->{
                sameStarted.countDown();
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(a,()->{
                        int count=activeA.incrementAndGet();
                        maximumA.accumulateAndGet(count,Math::max);
                        sameInside.countDown();
                        activeA.decrementAndGet();
                        return null;
                    });
                return null;
            });
            if(!sameStarted.await(8,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.39 second thread did not start"
                );

            // This would deadlock until first is released if G21.38's
            // SINGLE global JVM monitor were still in use.
            Future<Integer> independent=executor.submit(()->{
                return MailboxAccountPublicationCoordinator
                    .withExclusivePublication(b,()->42);
            });
            otherAccountProceedsWhileHeld=
                independent.get(5,TimeUnit.SECONDS)==42&&
                !first.isDone()&&!second.isDone();
            sameAccountWaits=
                !second.isDone()&&sameInside.getCount()==1;
            sharedAccountLeaseRetained=
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==1;

            releaseFirst.countDown();
            first.get(8,TimeUnit.SECONDS);
            second.get(8,TimeUnit.SECONDS);
            sameAccountSerialized=
                sameInside.getCount()==0&&maximumA.get()==1;
            registryCleanAfterContendingWriters=
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;

            boolean expectedThrown=false;
            try{
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(a,()->{
                        throw new IOException(
                            "G21.39 deliberate critical-section failure"
                        );
                    });
            }catch(IOException expected){
                expectedThrown=expected.getMessage().contains(
                    "deliberate critical-section failure"
                );
            }
            ioFailureDoesNotLeakLease=
                expectedThrown&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;

            boolean allWavesPassed=true;
            for(int i=0;i<25;i++){
                final int index=i;
                Path file=dir.resolve("wave-"+i+".properties");
                int actual=MailboxAccountPublicationCoordinator
                    .withExclusivePublication(file,()->index);
                allWavesPassed &=actual==index;
            }
            repeatedDistinctAccountsNoLeak=
                allWavesPassed&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;

            FilePlayerRepository.PathResolver paths=
                account->dir.resolve(account+".properties");
            FilePlayerRepository repository=
                new FilePlayerRepository(paths);
            MailboxDurableReviewFence observer=
                new MailboxDurableReviewFence(paths);
            final String reviewAccount="g2139-review";
            final String unaffected="g2139-unaffected";

            CountDownLatch markerInside=new CountDownLatch(1);
            CountDownLatch releaseMarker=new CountDownLatch(1);

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                WorldPlayer markedPlayer=new WorldPlayer();
                long markedGeneration=
                    world.registerPlayer(markedPlayer,reviewAccount);
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    proposalRef=new AtomicReference<>();
                world.submitAndWait(markedPlayer,markedGeneration,()->{
                    markedPlayer.mailbox().deliver(
                        new RewardDeliveryMessage(
                            "g2139:gift","Lock isolation gift","NO_GRANT",
                            Collections.singletonList(
                                new RewardDeliveryMessage.Attachment(
                                    995,25
                                )
                            ),
                            "CUSTOM_LOCALLAB_G2139_FIXTURE"
                        )
                    );
                    MailboxRewardDeliveryService.Snapshot row=
                        markedPlayer.mailbox().get("g2139:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        markedPlayer,
                        MailboxPreparedClaimJournal.prepare(
                            markedPlayer,row
                        )
                    );
                    proposalRef.set(
                        MailboxSettlementPostimagePlanner.plan(
                            markedPlayer,markedGeneration,row
                        )
                    );
                },5000L);
                MailboxSettlementPostimagePlanner.Proposal proposal=
                    proposalRef.get();
                repository.save(proposal.preparedPreimage);

                MailboxDurableReviewFence blockedMarker=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .INSIDE_EXCLUSIVE_PUBLICATION_BEFORE_LINK){
                            markerInside.countDown();
                            await(releaseMarker,
                                "G21.39 release marker publication");
                        }
                    });
                Future<MailboxDurableReviewFence.Receipt> markerTask=
                    executor.submit(()->blockedMarker.arm(proposal));
                try{
                    if(!markerInside.await(8,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "G21.39 review marker never held lock"
                        );

                    // The actual SINGLE World persistence worker can
                    // save an unrelated account even while a separate
                    // marker is held in its per-account JVM/FileLock.
                    WorldPlayer healthy=new WorldPlayer();
                    long healthyGeneration=
                        world.registerPlayer(healthy,unaffected);
                    AtomicReference<WorldPlayerPersistence.SaveTicket>
                        saveTicket=new AtomicReference<>();
                    world.submitAndWait(
                        healthy,healthyGeneration,()->{
                            saveTicket.set(
                                world.persistence().captureAndSave(
                                    unaffected,healthy,healthyGeneration,
                                    0,"[g2139] ","UNRELATED_DURING_FENCE"
                                )
                            );
                        },5000L
                    );
                    saveTicket.get().completion.get(5,TimeUnit.SECONDS);
                    realUnrelatedWorldSaveNotBlocked=
                        !markerTask.isDone()&&
                        !observer.present(reviewAccount)&&
                        repository.load(unaffected).isPresent()&&
                        !observer.present(unaffected);
                }finally{
                    releaseMarker.countDown();
                }

                MailboxDurableReviewFence.Receipt receipt=
                    markerTask.get(8,TimeUnit.SECONDS);
                markerStillNoClobber=
                    observer.inspect(reviewAccount).matches(proposal)&&
                    receipt.record.matches(proposal)&&
                    !receipt.grantAuthorized&&!receipt.replayAuthorized;
                noLiveClaimOrGrant=
                    markedPlayer.bank().inventorySlots()==0&&
                    markedPlayer.mailbox().get(
                        "g2139:gift"
                    ).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                noAutomaticRelease=
                    !receipt.record.releaseAuthorized&&
                    observer.present(reviewAccount)&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }

            try(World reboot=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                reboot.start();
                restartReviewVetoPreserved=
                    rejected(reboot,reviewAccount);
                unrelatedAccountStillLoads=
                    reboot.persistence().load(unaffected).isPresent();
            }
        }finally{
            releaseFirst.countDown();
            executor.shutdownNow();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(
                    Comparator.reverseOrder()
                ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2139_MAILBOX_ACCOUNT_LOCK_DIAGNOSTICS"+
            " sameAccountQueued="+sameAccountWaits+
            " foreignAccountConcurrent="+otherAccountProceedsWhileHeld+
            " sameAccountSerial="+sameAccountSerialized+
            " waiterMonitorStable="+sharedAccountLeaseRetained+
            " contentionRegistryClean="+
                registryCleanAfterContendingWriters+
            " IOExceptionReleasesLease="+ioFailureDoesNotLeakLease+
            " repeatedAccountsRegistryClean="+
                repeatedDistinctAccountsNoLeak+
            " realWorldForeignSaveProgress="+
                realUnrelatedWorldSaveNotBlocked+
            " negativeMarkerNoClobber="+markerStillNoClobber+
            " restartStillDenied="+restartReviewVetoPreserved+
            " unrelatedAccountLoad="+unrelatedAccountStillLoads+
            " noLiveItemCredit="+noLiveClaimOrGrant+
            " markerNotAutoReleased="+noAutomaticRelease+
            " canonicalPathIdentity="+canonicalStableLockPaths
        );

        require(
            sameAccountWaits&&otherAccountProceedsWhileHeld&&
            sameAccountSerialized&&sharedAccountLeaseRetained&&
            registryCleanAfterContendingWriters&&
            ioFailureDoesNotLeakLease&&
            repeatedDistinctAccountsNoLeak&&
            realUnrelatedWorldSaveNotBlocked&&markerStillNoClobber&&
            restartReviewVetoPreserved&&
            unrelatedAccountStillLoads&&noLiveClaimOrGrant&&
            noAutomaticRelease&&canonicalStableLockPaths,
            "G21.39 account-local lock isolation"
        );

        System.out.println(
            "G2139_MAILBOX_ACCOUNT_LOCK_ISOLATION_PASS"+
            " sameAccountSerialized=true"+
            " differentAccountsConcurrent=true"+
            " retainedCrossJvmFileLock=true"+
            " noJvmRegistryLeak=true"+
            " realWorldSaveNotBlockedByForeignMarker=true"+
            " grant=false replay=false release=false"
        );
    }

    private static boolean rejected(World world,String account)
        throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException error){
            return error.getMessage()!=null&&
                error.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void await(CountDownLatch latch,String reason)
        throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException(reason+" timed out");
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException(reason+" interrupted",e);
        }
    }

    private static void require(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }

    private G2139MailboxAccountLockIsolationIntegrationTest(){}
}
