package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.73: bounded recovery observation under competing JVM/OS locks. */
public final class G2173MailboxBoundedRecoveryContentionIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2173-recovery-contention-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean stableFreshInstance=false;
        boolean sameJvmLockTimeout=false;
        boolean boundedAcquisitionElapsed=false;
        boolean interruptedWaiterRejected=false;
        boolean interruptedFlagPreserved=false;
        boolean unrelatedAccountUnaffected=false;
        boolean resumedAfterJvmUnlock=false;
        boolean osFileLockTimeout=false;
        boolean resumedAfterOsUnlock=false;
        boolean twoQueuedObserversSawMarker=false;
        boolean markerSurvivedWriter=false;
        boolean changedWitnessFreshInstance=false;
        boolean restartStillQuarantined=false;
        boolean originalLiveNeverCredited=false;
        boolean noTempOrLeaseLeaks=false;
        boolean noPositiveAuthority=true;
        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repo)){
            restart.start();
            String account="g2173-terminal";
            WorldPlayer owner=new WorldPlayer();
            long generation=fixture.registerPlayer(owner,account);
            String message=account+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Recovery lock contention","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2173_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot item=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,item));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,item);
            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(proposal);
            strict.saveStrict(terminal);
            Path file=paths.resolve(account);
            String baseline=repo.captureRestartContinuityTokenReadOnly(
                account);
            FilePlayerRepository fresh=new FilePlayerRepository(paths);
            stableFreshInstance=unchanged(
                fresh.compareRestartContinuityReadOnly(account,baseline));

            // Cooperating in-JVM publication holder uses the ORIGINAL
            // unbounded World writer lock path, not our new timeout API.
            CountDownLatch held=new CountDownLatch(1);
            CountDownLatch release=new CountDownLatch(1);
            AtomicReference<Throwable> holderFailure=new AtomicReference<>();
            Thread holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(file,()->{
                            held.countDown();
                            await(release,8L);
                            return null;
                        });
                }catch(Throwable t){holderFailure.set(t);}
            },"g2173-standard-publication-holder");
            holder.start();
            try{
                if(!held.await(5,TimeUnit.SECONDS))
                    throw new AssertionError("G21.73 lock holder never entered");

                // A blocked witness is interruptible; it must not
                // leave a stale per-account JVM lease registration.
                AtomicReference<Throwable> interrupted=new AtomicReference<>();
                AtomicBoolean preservedInterrupt=new AtomicBoolean();
                CountDownLatch waiterStarted=new CountDownLatch(1);
                Thread waiter=new Thread(()->{
                    waiterStarted.countDown();
                    try{
                        fresh.captureRestartContinuityTokenReadOnly(account);
                    }catch(Throwable failure){interrupted.set(failure);}
                    preservedInterrupt.set(Thread.currentThread().isInterrupted());
                },"g2173-recovery-interruptible-waiter");
                waiter.start();
                if(!waiterStarted.await(5,TimeUnit.SECONDS))
                    throw new AssertionError("G21.73 waiter never started");
                Thread.sleep(100L);
                waiter.interrupt();
                waiter.join(3000L);
                interruptedWaiterRejected=!waiter.isAlive()&&
                    interrupted.get() instanceof IOException&&
                    interrupted.get().getMessage().contains(
                        "RECOVERY_PUBLICATION_INTERRUPTED_NO_GRANT");
                interruptedFlagPreserved=preservedInterrupt.get();

                long began=System.nanoTime();
                try{
                    repo.captureRestartContinuityTokenReadOnly(account);
                }catch(IOException bounded){
                    sameJvmLockTimeout=bounded.getMessage().contains(
                        "RECOVERY_PUBLICATION_BUSY_NO_GRANT");
                }
                long elapsed=TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime()-began);
                boundedAcquisitionElapsed=elapsed>=1000L&&
                    elapsed<4000L;

                String otherAccount="g2173-independent";
                WorldPlayer independent=new WorldPlayer();
                independent.markRegistered(otherAccount);
                strict.saveStrict(PlayerSnapshotCodec.capture(
                    otherAccount,independent));
                String witness=repo.captureRestartContinuityTokenReadOnly(
                    otherAccount);
                unrelatedAccountUnaffected=unchanged(
                    new FilePlayerRepository(paths)
                        .compareRestartContinuityReadOnly(
                            otherAccount,witness));
            }finally{
                release.countDown();
                holder.join(6000L);
            }
            if(holder.isAlive()||holderFailure.get()!=null)
                throw new AssertionError(
                    "G21.73 standard writer did not release",
                    holderFailure.get());
            resumedAfterJvmUnlock=unchanged(
                fresh.compareRestartContinuityReadOnly(
                    account,baseline));

            // Direct OS-level publication lock conflicts with another
            // channel (same process test fixture for cross-JVM OS path).
            Path osLock=MailboxAccountPublicationCoordinator.lockPath(
                file);
            Files.createDirectories(osLock.getParent());
            try(FileChannel channel=FileChannel.open(
                    osLock,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                FileLock osHeld=channel.lock()){
                try{
                    fresh.captureRestartContinuityTokenReadOnly(account);
                }catch(IOException busy){
                    osFileLockTimeout=busy.getMessage().contains(
                        "RECOVERY_PUBLICATION_BUSY_NO_GRANT");
                }
            }
            resumedAfterOsUnlock=unchanged(
                repo.compareRestartContinuityReadOnly(
                    account,baseline));

            // The writer acquires the ORIGINAL account lock before
            // two independent forensic readers begin their comparisons.
            // They cannot observe the account while the writer changes
            // its review marker, and MUST see CHANGED once it releases.
            CountDownLatch markerHeld=new CountDownLatch(1);
            CountDownLatch allowMarker=new CountDownLatch(1);
            AtomicReference<Throwable> markerError=new AtomicReference<>();
            Thread markerWriter=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(file,()->{
                            markerHeld.countDown();
                            await(allowMarker,5L);
                            new MailboxStrictUncertainFence(paths)
                                .armInsidePublicationLock(
                                    account,
                                    StrictDurablePlayerSnapshotWriter
                                        .canonicalSnapshotSha256(
                                            proposal.preparedPreimage));
                            return null;
                        });
                }catch(Throwable t){markerError.set(t);}
            },"g2173-review-marker-writer");
            markerWriter.start();
            try{
                if(!markerHeld.await(5,TimeUnit.SECONDS))
                    throw new AssertionError("G21.73 marker writer absent");
                AtomicReference<FilePlayerRepository.RestartContinuityComparison>
                    first=new AtomicReference<>(),second=new AtomicReference<>();
                AtomicReference<Throwable> firstError=new AtomicReference<>();
                AtomicReference<Throwable> secondError=new AtomicReference<>();
                CountDownLatch readersStarted=new CountDownLatch(2);
                Thread a=observer(paths,account,baseline,
                    readersStarted,first,firstError,"g2173-reader-a");
                Thread b=observer(paths,account,baseline,
                    readersStarted,second,secondError,"g2173-reader-b");
                a.start();b.start();
                try{
                    if(!readersStarted.await(5,TimeUnit.SECONDS))
                        throw new AssertionError("G21.73 readers never started");
                    Thread.sleep(120L);
                }finally{
                    allowMarker.countDown();
                }
                a.join(5000L);
                b.join(5000L);
                twoQueuedObserversSawMarker=!a.isAlive()&&!b.isAlive()&&
                    firstError.get()==null&&secondError.get()==null&&
                    changed(first.get())&&changed(second.get());
            }finally{
                allowMarker.countDown();
                markerWriter.join(6000L);
            }
            if(markerWriter.isAlive()||markerError.get()!=null)
                throw new AssertionError(
                    "G21.73 marker writer unresolved",
                    markerError.get());
            markerSurvivedWriter=new MailboxStrictUncertainFence(paths)
                .present(account);
            FilePlayerRepository freshlyRestarted=
                new FilePlayerRepository(paths);
            FilePlayerRepository.RestartContinuityComparison compared=
                freshlyRestarted.compareRestartContinuityReadOnly(
                    account,baseline);
            changedWitnessFreshInstance=changed(compared);
            noPositiveAuthority=!compared.grantAuthorized&&
                !compared.replayAuthorized&&!compared.releaseAuthorized&&
                !compared.restartAdmissionAuthorized&&
                !compared.transactionCommitted&&
                !compared.clientAckAuthorized;
            try{
                restart.persistence().load(account);
            }catch(IOException denied){
                restartStillQuarantined=denied.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }
            originalLiveNeverCredited=owner.bank().inventorySlots()==0&&
                owner.mailbox().get(message).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> all=Files.walk(root)){
                noTempOrLeaseLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2173_BOUNDED_RECOVERY_DIAGNOSTICS"+
            " stableFreshInstance="+stableFreshInstance+
            " sameJvmLockTimeout="+sameJvmLockTimeout+
            " boundedAcquisitionElapsed="+boundedAcquisitionElapsed+
            " interruptedWaiterRejected="+interruptedWaiterRejected+
            " interruptedFlagPreserved="+interruptedFlagPreserved+
            " unrelatedAccountUnaffected="+unrelatedAccountUnaffected+
            " resumedAfterJvmUnlock="+resumedAfterJvmUnlock+
            " osFileLockTimeout="+osFileLockTimeout+
            " resumedAfterOsUnlock="+resumedAfterOsUnlock+
            " twoQueuedObserversSawMarker="+twoQueuedObserversSawMarker+
            " markerSurvivedWriter="+markerSurvivedWriter+
            " changedWitnessFreshInstance="+
                changedWitnessFreshInstance+
            " restartStillQuarantined="+restartStillQuarantined+
            " originalLiveNeverCredited="+originalLiveNeverCredited+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks+
            " noPositiveAuthority="+noPositiveAuthority);
        if(!(stableFreshInstance&&sameJvmLockTimeout&&
             boundedAcquisitionElapsed&&interruptedWaiterRejected&&
             interruptedFlagPreserved&&unrelatedAccountUnaffected&&
             resumedAfterJvmUnlock&&osFileLockTimeout&&
             resumedAfterOsUnlock&&twoQueuedObserversSawMarker&&
             markerSurvivedWriter&&changedWitnessFreshInstance&&
             restartStillQuarantined&&originalLiveNeverCredited&&
             noTempOrLeaseLeaks&&noPositiveAuthority))
            throw new AssertionError(
                "G21.73 contention must remain bounded NO_GRANT");
        System.out.println("G2173_BOUNDED_RECOVERY_PASS"+
            " boundedJvm=true boundedOs=true raceClosed=true"+
            " grant=false replay=false admission=false release=false");
    }

    private static Thread observer(
        FilePlayerRepository.PathResolver paths,
        String account,String oldToken,CountDownLatch began,
        AtomicReference<FilePlayerRepository.RestartContinuityComparison>
            response,AtomicReference<Throwable> error,String name
    ){
        return new Thread(()->{
            try{
                began.countDown();
                response.set(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        account,oldToken));
            }catch(Throwable failure){error.set(failure);}
        },name);
    }
    private static void await(CountDownLatch latch,long seconds)
        throws IOException{
        try{
            if(!latch.await(seconds,TimeUnit.SECONDS))
                throw new IOException(
                    "G21.73 fixture holder timeout");
        }catch(InterruptedException interruption){
            Thread.currentThread().interrupt();
            throw new IOException(
                "G21.73 fixture holder interrupted",interruption);
        }
    }
    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison observation
    ){
        return observation!=null&&observation.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .UNCHANGED_FORENSICS_NO_GRANT&&
            !observation.grantAuthorized&&!observation.replayAuthorized;
    }
    private static boolean changed(
        FilePlayerRepository.RestartContinuityComparison observation
    ){
        return observation!=null&&observation.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .CHANGED_FORENSICS_QUARANTINE&&
            !observation.grantAuthorized&&!observation.replayAuthorized;
    }
    private G2173MailboxBoundedRecoveryContentionIntegrationTest(){}
}
