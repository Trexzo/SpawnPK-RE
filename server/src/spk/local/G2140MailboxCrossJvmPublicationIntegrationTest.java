package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.40: real child JVM acquires G21.38/39 FileLock. The parent
 * independently runs actual G21.34 negative marker publication and
 * G21.36 guarded World account saves against that lock.
 *
 * This does not grant/claim/replay/clear any Mailbox reward.
 */
public final class G2140MailboxCrossJvmPublicationIntegrationTest {
    private static final long DEADLINE_SECONDS=12L;

    private static final class ChildLock implements AutoCloseable {
        final Process process;
        final Path ready;
        final Path release;
        final Path log;

        ChildLock(
            Path accountFile,Path root,String prefix
        )throws IOException{
            ready=root.resolve(prefix+"-ready");
            release=root.resolve(prefix+"-release");
            log=root.resolve(prefix+"-child.log");

            String binary=System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win")
                    ?"java.exe":"java";
            Path executable=Paths.get(
                System.getProperty("java.home"),"bin",binary
            );
            ProcessBuilder launch=new ProcessBuilder(
                executable.toString(),
                "-cp",System.getProperty("java.class.path"),
                G2140MailboxCrossJvmPublicationIntegrationTest
                    .class.getName(),
                "--hold-lock",
                accountFile.toAbsolutePath().normalize().toString(),
                ready.toAbsolutePath().toString(),
                release.toAbsolutePath().toString()
            );
            launch.redirectErrorStream(true);
            launch.redirectOutput(log.toFile());
            process=launch.start();
        }

        long awaitReady()throws Exception{
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            while(!Files.exists(ready)&&
                  System.nanoTime()<deadline){
                if(!process.isAlive())
                    throw new AssertionError(
                        "G21.40 child exited before holding lock "+
                        "exit="+process.exitValue()+
                        " log="+logText()
                    );
                Thread.sleep(15L);
            }
            if(!Files.exists(ready))
                throw new AssertionError(
                    "G21.40 child never acquired lock log="+logText()
                );
            return Long.parseLong(
                Files.readString(ready,StandardCharsets.US_ASCII).trim()
            );
        }

        boolean releaseAndJoin()throws Exception{
            Files.writeString(
                release,"RELEASE",StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            boolean done=process.waitFor(
                DEADLINE_SECONDS,TimeUnit.SECONDS
            );
            if(!done)
                throw new AssertionError(
                    "G21.40 child would not exit after release log="+
                    logText()
                );
            if(process.exitValue()!=0)
                throw new AssertionError(
                    "G21.40 child failed exit="+
                    process.exitValue()+" log="+logText()
                );
            return true;
        }

        String logText()throws IOException{
            return Files.exists(log)
                ?Files.readString(log,StandardCharsets.UTF_8)
                :"(empty log)";
        }

        @Override public void close(){
            try{
                if(!Files.exists(release))
                    Files.writeString(
                        release,"RELEASE",StandardCharsets.US_ASCII
                    );
                if(process.isAlive()&&
                   !process.waitFor(2L,TimeUnit.SECONDS)){
                    process.destroyForcibly();
                    process.waitFor(2L,TimeUnit.SECONDS);
                }
            }catch(IOException|InterruptedException cleanup){
                process.destroyForcibly();
                if(cleanup instanceof InterruptedException)
                    Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--hold-lock".equals(args[0])){
            childMain(
                Paths.get(args[1]),Paths.get(args[2]),
                Paths.get(args[3])
            );
            return;
        }
        if(args.length!=0)
            throw new IllegalArgumentException(
                "G21.40 unsupported test invocation"
            );

        boolean distinctJvmPids=false;
        boolean childHoldsMarkerLock=false;
        boolean markerWaitsForChild=false;
        boolean unrelatedWorldSaveCompletes=false;
        boolean markerPublishesAfterRelease=false;
        boolean firstChildExitedCleanly=false;
        boolean childHoldsAccountSaveLock=false;
        boolean actualWorldSaveWaitsForChild=false;
        boolean oldAccountUnchangedWhileLocked=false;
        boolean guardedWorldSaveCompletesAfterRelease=false;
        boolean secondChildExitedCleanly=false;
        boolean markerRetainsNoGrant=false;
        boolean freshWorldRejectsMarkedAccount=false;
        boolean otherWorldAccountsLoad=false;
        boolean noOrphanTemporaryFiles=false;
        boolean parentJvmLeaseRegistryClean=false;
        boolean liveMailboxStillUnclaimed=false;

        Path dir=Files.createTempDirectory(
            "g2140-mailbox-cross-jvm-"
        );
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        String fenced="g2140-review";
        String concurrent="g2140-unrelated";
        String saveHeld="g2140-save-held";
        CountDownLatch markerAtFinalPublication=
            new CountDownLatch(1);
        CountDownLatch saveReachedFinalPublication=
            new CountDownLatch(1);

        FilePlayerRepository disk=new FilePlayerRepository(
            paths,account->{
                if(saveHeld.equals(account))
                    saveReachedFinalPublication.countDown();
            }
        );
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(paths);
        ExecutorService tasks=Executors.newFixedThreadPool(2);
        try{
            try(World world=World.isolatedForTest(60000L,disk)){
                world.start();

                WorldPlayer preparedOwner=new WorldPlayer();
                long preparedGeneration=
                    world.registerPlayer(preparedOwner,fenced);
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    proposal=new AtomicReference<>();
                world.submitAndWait(
                    preparedOwner,preparedGeneration,()->{
                        preparedOwner.mailbox().deliver(
                            new RewardDeliveryMessage(
                                "g2140:gift","Cross-JVM publication","NO_GRANT",
                                Collections.singletonList(
                                    new RewardDeliveryMessage.Attachment(
                                        995,25
                                    )
                                ),
                                "CUSTOM_LOCALLAB_G2140_FIXTURE"
                            )
                        );
                        MailboxRewardDeliveryService.Snapshot row=
                            preparedOwner.mailbox().get("g2140:gift");
                        MailboxPreparedClaimJournal.stageOnly(
                            preparedOwner,
                            MailboxPreparedClaimJournal.prepare(
                                preparedOwner,row
                            )
                        );
                        proposal.set(
                            MailboxSettlementPostimagePlanner.plan(
                                preparedOwner,preparedGeneration,row
                            )
                        );
                    },5000L
                );
                disk.save(proposal.get().preparedPreimage);

                WorldPlayer independent=new WorldPlayer();
                long independentGeneration=
                    world.registerPlayer(independent,concurrent);

                MailboxDurableReviewFence competingFence=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            markerAtFinalPublication.countDown();
                    });

                Future<MailboxDurableReviewFence.Receipt> markerTask;
                try(ChildLock child=new ChildLock(
                        paths.resolve(fenced),dir,"marker")){
                    long childPid=child.awaitReady();
                    distinctJvmPids=childPid!=
                        ProcessHandle.current().pid();
                    childHoldsMarkerLock=child.process.isAlive()&&
                        !fence.present(fenced);

                    // The separate OS process owns the account's
                    // stable file lock. Our negative marker publisher
                    // MUST wait at the coordinator, not publish.
                    markerTask=tasks.submit(
                        ()->competingFence.arm(proposal.get())
                    );
                    if(!markerAtFinalPublication.await(
                            8,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "G21.40 marker never reached final stage"
                        );
                    markerWaitsForChild=
                        !markerTask.isDone()&&
                        !fence.present(fenced)&&
                        child.process.isAlive();

                    AtomicReference<WorldPlayerPersistence.SaveTicket>
                        otherTicket=new AtomicReference<>();
                    world.submitAndWait(
                        independent,independentGeneration,()->{
                            otherTicket.set(
                                world.persistence().captureAndSave(
                                    concurrent,independent,
                                    independentGeneration,0,
                                    "[g2140] ","FOREIGN_ACCOUNT_WHILE_LOCK_HELD"
                                )
                            );
                        },5000L
                    );
                    otherTicket.get().completion.get(
                        8,TimeUnit.SECONDS
                    );
                    unrelatedWorldSaveCompletes=
                        disk.load(concurrent).isPresent()&&
                        !fence.present(concurrent)&&
                        !markerTask.isDone()&&
                        child.process.isAlive();
                    firstChildExitedCleanly=child.releaseAndJoin();
                }

                MailboxDurableReviewFence.Receipt published=
                    markerTask.get(8,TimeUnit.SECONDS);
                markerPublishesAfterRelease=
                    fence.inspect(fenced).matches(proposal.get())&&
                    published.record.matches(proposal.get())&&
                    fence.present(fenced);
                markerRetainsNoGrant=
                    !published.grantAuthorized&&
                    !published.replayAuthorized&&
                    !published.record.releaseAuthorized;

                // Separate process blocks the FINAL account save
                // replacement boundary, too—not merely marker arm.
                WorldPlayer savePlayer=new WorldPlayer();
                long saveGeneration=world.registerPlayer(
                    savePlayer,saveHeld
                );
                disk.save(PlayerSnapshotCodec.capture(
                    saveHeld,savePlayer
                ));
                byte[] before=Files.readAllBytes(
                    paths.resolve(saveHeld)
                );
                try(ChildLock child=new ChildLock(
                        paths.resolve(saveHeld),dir,"worldsave")){
                    long childPid=child.awaitReady();
                    distinctJvmPids &=childPid!=
                        ProcessHandle.current().pid();
                    childHoldsAccountSaveLock=child.process.isAlive();

                    AtomicReference<WorldPlayerPersistence.SaveTicket>
                        blockedTicket=new AtomicReference<>();
                    world.submitAndWait(
                        savePlayer,saveGeneration,()->{
                            blockedTicket.set(
                                world.persistence().captureAndSave(
                                    saveHeld,savePlayer,saveGeneration,0,
                                    "[g2140] ","SAME_ACCOUNT_CHILD_LOCKED"
                                )
                            );
                        },5000L
                    );
                    if(!saveReachedFinalPublication.await(
                            8,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "G21.40 real World worker missed lock boundary"
                        );
                    actualWorldSaveWaitsForChild=
                        !blockedTicket.get().completion.isDone()&&
                        child.process.isAlive();
                    oldAccountUnchangedWhileLocked=
                        Arrays.equals(
                            before,Files.readAllBytes(
                                paths.resolve(saveHeld)
                            )
                        );
                    secondChildExitedCleanly=child.releaseAndJoin();
                    blockedTicket.get().completion.get(
                        8,TimeUnit.SECONDS
                    );
                    guardedWorldSaveCompletesAfterRelease=
                        disk.load(saveHeld).isPresent()&&
                        !fence.present(saveHeld);
                }

                liveMailboxStillUnclaimed=
                    preparedOwner.bank().inventorySlots()==0&&
                    preparedOwner.mailbox().get(
                        "g2140:gift"
                    ).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

                parentJvmLeaseRegistryClean=
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
                try(Stream<Path> files=Files.list(dir)){
                    noOrphanTemporaryFiles=files.noneMatch(
                        path->path.getFileName()
                            .toString().endsWith(".tmp")
                    );
                }
            }
            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                freshWorldRejectsMarkedAccount=
                    refused(restarted,fenced);
                otherWorldAccountsLoad=
                    restarted.persistence().load(concurrent).isPresent()&&
                    restarted.persistence().load(saveHeld).isPresent();
            }
        }finally{
            tasks.shutdownNow();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2140_MAILBOX_CROSS_JVM_DIAGNOSTICS"+
            " realDifferentPids="+distinctJvmPids+
            " childMarkerLockHeld="+childHoldsMarkerLock+
            " markerCannotPublishBeforeRelease="+markerWaitsForChild+
            " unrelatedWorldSaveProceeds="+
                unrelatedWorldSaveCompletes+
            " markerPublishesAfterRelease="+
                markerPublishesAfterRelease+
            " markerChildCleanExit="+firstChildExitedCleanly+
            " childWorldSaveLockHeld="+childHoldsAccountSaveLock+
            " actualWorldSaveWaits="+actualWorldSaveWaitsForChild+
            " oldAccountBytesPreservedWhileHeld="+
                oldAccountUnchangedWhileLocked+
            " WorldSaveCompletesAfterChildExit="+
                guardedWorldSaveCompletesAfterRelease+
            " saveChildCleanExit="+secondChildExitedCleanly+
            " negativeReceiptOnly="+markerRetainsNoGrant+
            " restartLoginQuarantined="+
                freshWorldRejectsMarkedAccount+
            " independentAccountsLoad="+otherWorldAccountsLoad+
            " noOrphanTemps="+noOrphanTemporaryFiles+
            " noParentJvmLeaseLeaks="+parentJvmLeaseRegistryClean+
            " inventoryAndClaimUnchanged="+liveMailboxStillUnclaimed
        );

        require(
            distinctJvmPids&&childHoldsMarkerLock&&
            markerWaitsForChild&&unrelatedWorldSaveCompletes&&
            markerPublishesAfterRelease&&firstChildExitedCleanly&&
            childHoldsAccountSaveLock&&actualWorldSaveWaitsForChild&&
            oldAccountUnchangedWhileLocked&&
            guardedWorldSaveCompletesAfterRelease&&
            secondChildExitedCleanly&&markerRetainsNoGrant&&
            freshWorldRejectsMarkedAccount&&otherWorldAccountsLoad&&
            noOrphanTemporaryFiles&&parentJvmLeaseRegistryClean&&
            liveMailboxStillUnclaimed,
            "G21.40 real separate-JVM publication ordering"
        );
        System.out.println(
            "G2140_MAILBOX_CROSS_JVM_FILE_LOCK_PASS"+
            " realChildJvms=true"+
            " markerPublisherCrossProcessWaits=true"+
            " WorldSaveCrossProcessWaits=true"+
            " unrelatedAccountsProgress=true"+
            " grant=false replay=false release=false"
        );
    }

    private static void childMain(
        Path accountFile,Path ready,Path release
    )throws Exception{
        MailboxAccountPublicationCoordinator
            .withExclusivePublication(accountFile,()->{
                Files.writeString(
                    ready,
                    Long.toString(ProcessHandle.current().pid()),
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                );
                long deadline=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
                while(!Files.exists(release)){
                    if(System.nanoTime()>=deadline)
                        throw new IOException(
                            "G21.40 child timed out waiting for release"
                        );
                    try{
                        Thread.sleep(15L);
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "G21.40 child interrupted",interrupted
                        );
                    }
                }
                return null;
            });
        System.out.println("G2140_CHILD_LOCK_RELEASED");
    }

    private static boolean refused(World world,String account)
        throws Exception{
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

    private static void require(boolean value,String label){
        if(!value)
            throw new AssertionError(label);
    }

    private G2140MailboxCrossJvmPublicationIntegrationTest(){}
}
