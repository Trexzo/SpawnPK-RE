package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.26: observe committed *file contents*, never grant on observation.
 * FIFO I/O ordering, negative fsync receipts, replays, stale owner and
 * malformed records all remain separate from actual settlement authority.
 */
public final class G2126MailboxDiskReconciliationIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean missingAccountObserved=false;
        boolean inFlightOrdinarySavePrecedesObservation=false;
        boolean strictBarrierPrecedesObservation=false;
        boolean exactlyPreparedOnDisk=false;
        boolean repeatObservationIdempotent=false;
        boolean preRenameFailureLeavesPrepared=false;
        boolean ambiguousPostRenameObservedNotReceipted=false;
        boolean tamperedRecordDiverges=false;
        boolean unrelatedAccountIsolated=false;
        boolean failedReadNoFalseResult=false;
        boolean staleInventoryDenied=false;
        boolean recycledEnvelopeDenied=false;
        boolean staleGenerationDenied=false;
        boolean zeroLiveInventoryGrant=false;
        boolean noClaimOrDurabilityAuthority=false;

        Path dir=Files.createTempDirectory("g2126-observe-");
        BlockingRepository repository=new BlockingRepository(
            account->dir.resolve(account+".properties")
        );
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer alice=new WorldPlayer();
        long generation=world.registerPlayer(alice,"g2126-alice");
        ExecutorService observerThread=
            Executors.newSingleThreadExecutor();
        try{
            world.start();
            world.submitAndWait(alice,generation,()->{
                alice.mailbox().deliver(new RewardDeliveryMessage(
                    "g2126:reward","Reward","G21.26",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,50)
                    ),"CUSTOM_LOCALLAB_G2126_FIXTURE"
                ));
            },5_000L);

            MailboxRewardDeliveryService.Snapshot selected=
                alice.mailbox().get("g2126:reward");
            MailboxPreparedClaimJournal.Intent intent=
                MailboxPreparedClaimJournal.prepare(alice,selected);
            MailboxPreparedClaimJournal.stageOnly(alice,intent);
            MailboxSettlementPostimagePlanner.Proposal plan=
                MailboxSettlementPostimagePlanner.plan(
                    alice,generation,selected
                );

            MailboxDiskPostimageObserver.Observation missing=
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            missingAccountObserved=
                missing.state==MailboxDiskPostimageObserver.State
                    .MISSING_ACCOUNT_RECORD&&
                !missing.durabilityReceipt&&!missing.grantAuthorized&&
                missing.intentKey.equals(intent.idempotencyKey);

            // Captured before the PREPARED writer, the older ordinary
            // account write occupies the same FIFO I/O worker.
            WorldPlayerPersistence.CapturedSave old=
                world.persistence().captureDeferredSave(
                    "g2126-alice",alice,generation,0,
                    "[g2126] ","OLDER_PREPARED_SAVE"
                );
            WorldPlayerPersistence.SaveTicket oldTicket=
                world.persistence().submitCapturedWithBackpressure(
                    old,2_000L
                );
            if(!repository.firstSaveStarted.await(
                    5,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.26 older write never entered FIFO"
                );

            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    username->dir.resolve(username+".properties")
                );
            CompletableFuture<
                StrictDurablePlayerSnapshotWriter.Receipt
            > barrier=world.persistence().submitPreparedStrictBarrier(
                alice,generation,plan.preparedPreimage,strict
            );

            CountDownLatch observeStarted=new CountDownLatch(1);
            Future<MailboxDiskPostimageObserver.Observation>
                pending=observerThread.submit(()->{
                    observeStarted.countDown();
                    return MailboxDiskPostimageObserver.observe(
                        world,alice,generation,plan
                    );
                });
            if(!observeStarted.await(5,TimeUnit.SECONDS))
                throw new AssertionError("observer never started");
            inFlightOrdinarySavePrecedesObservation=
                repository.saves.get()==1&&
                !oldTicket.completion.isDone()&&
                !barrier.isDone()&&
                !pending.isDone();

            repository.releaseFirstSave.countDown();
            oldTicket.completion.get(8,TimeUnit.SECONDS);
            barrier.get(8,TimeUnit.SECONDS);
            MailboxDiskPostimageObserver.Observation observed=
                pending.get(8,TimeUnit.SECONDS);
            strictBarrierPrecedesObservation=
                observed.state==MailboxDiskPostimageObserver.State
                    .EXACT_PREPARED_ACCOUNT&&
                repository.loads.get()>=2;

            FilePlayerRepository fileReader=
                new FilePlayerRepository(
                    name->dir.resolve(name+".properties")
                );
            exactlyPreparedOnDisk=
                fileReader.load("g2126-alice").get()
                    .values().equals(
                        plan.preparedPreimage.values()
                    )&&
                observed.account.equals(plan.account)&&
                observed.ownerGeneration==generation&&
                !observed.durabilityReceipt&&
                !observed.grantAuthorized;

            MailboxDiskPostimageObserver.Observation repeated=
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            repeatObservationIdempotent=
                repeated.state==observed.state&&
                repeated.intentKey.equals(observed.intentKey)&&
                alice.bank().inventorySlots()==0;

            StrictDurablePlayerSnapshotWriter preRenameFail=
                new StrictDurablePlayerSnapshotWriter(
                    name->dir.resolve(name+".properties"),
                    phase->{
                        if(phase==
                            StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            throw new IOException(
                                "G21.26 injected pre-rename failure"
                            );
                    }
                );
            boolean rejected=false;
            try{
                preRenameFail.saveStrict(plan.hypotheticalPostimage);
            }catch(IOException failure){
                rejected=true;
            }
            preRenameFailureLeavesPrepared=
                rejected&&
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                ).state==MailboxDiskPostimageObserver.State
                    .EXACT_PREPARED_ACCOUNT;

            // Rename succeeded but directory force failed. Observing the
            // exact resulting hypothetical record does NOT make it a
            // durable settlement receipt or authorize a replay grant.
            StrictDurablePlayerSnapshotWriter ambiguous=
                new StrictDurablePlayerSnapshotWriter(
                    name->dir.resolve(name+".properties"),
                    phase->{
                        if(phase==
                            StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_DIRECTORY_FORCE)
                            throw new IOException(
                                "G21.26 injected uncertain durability"
                            );
                    }
                );
            boolean unconfirmed=false;
            try{
                ambiguous.saveStrict(plan.hypotheticalPostimage);
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                unconfirmed=true;
            }
            MailboxDiskPostimageObserver.Observation afterRename=
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            ambiguousPostRenameObservedNotReceipted=
                unconfirmed&&
                afterRename.state==MailboxDiskPostimageObserver.State
                    .EXACT_HYPOTHETICAL_ACCOUNT&&
                !afterRename.durabilityReceipt&&
                !afterRename.grantAuthorized;

            TreeMap<String,String> changed=new TreeMap<>(
                plan.hypotheticalPostimage.values()
            );
            changed.put("extension.g2126.unrelated","unexpected");
            strict.saveStrict(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "g2126-alice",changed
            ));
            tamperedRecordDiverges=
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                ).state==MailboxDiskPostimageObserver.State
                    .DIVERGENT_ACCOUNT;

            unrelatedAccountIsolated=
                !Files.exists(
                    dir.resolve("g2126-bob.properties")
                )&&
                alice.bank().inventorySlots()==0;

            repository.rejectNextLoad=true;
            boolean readFailed=false;
            try{
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            }catch(IOException expected){
                readFailed=true;
            }
            failedReadNoFalseResult=
                readFailed&&
                !repository.rejectNextLoad;

            int[] ids=new int[28],qty=new int[28];
            Arrays.fill(ids,-1);
            ids[0]=995;
            qty[0]=1;
            alice.bank().replaceInventorySemantic(ids,qty);
            staleInventoryDenied=rejects(()->{
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            });
            Arrays.fill(ids,-1);
            Arrays.fill(qty,0);
            alice.bank().replaceInventorySemantic(ids,qty);

            alice.mailbox().delete("g2126:reward");
            alice.mailbox().deliver(new RewardDeliveryMessage(
                "g2126:reward","Recycled","G21.26",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,50)
                ),"CUSTOM_LOCALLAB_G2126_FIXTURE"
            ));
            recycledEnvelopeDenied=rejects(()->{
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            });

            boolean unregistered=world.unregisterPlayer(
                alice,generation
            );
            staleGenerationDenied=unregistered&&rejects(()->{
                MailboxDiskPostimageObserver.observe(
                    world,alice,generation,plan
                );
            });
            long newGeneration=world.registerPlayer(
                alice,"g2126-alice"
            );
            staleGenerationDenied &=
                newGeneration!=generation&&rejects(()->{
                    MailboxDiskPostimageObserver.observe(
                        world,alice,generation,plan
                    );
                });

            zeroLiveInventoryGrant=
                alice.bank().inventorySlots()==0&&
                alice.bank().inventoryCount(995)==0&&
                alice.mailbox().get("g2126:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noClaimOrDurabilityAuthority=
                MailboxDiskPostimageObserver.AUTHORITY.equals(
                    "CUSTOM_LOCALLAB_G2126_DISK_OBSERVATION_NO_GRANT"
                )&&
                !afterRename.durabilityReceipt&&
                !afterRename.grantAuthorized&&
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                );
        }finally{
            repository.releaseFirstSave.countDown();
            observerThread.shutdownNow();
            world.close();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path file:files.sorted(
                        java.util.Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }

        System.out.println(
            "G2126_MAILBOX_DISK_OBSERVATION_DIAGNOSTICS"+
            " missingAccountObserved="+missingAccountObserved+
            " inFlightOrdinarySavePrecedesObservation="+
                inFlightOrdinarySavePrecedesObservation+
            " strictBarrierPrecedesObservation="+
                strictBarrierPrecedesObservation+
            " exactlyPreparedOnDisk="+exactlyPreparedOnDisk+
            " repeatObservationIdempotent="+
                repeatObservationIdempotent+
            " preRenameFailureLeavesPrepared="+
                preRenameFailureLeavesPrepared+
            " ambiguousPostRenameObservedNotReceipted="+
                ambiguousPostRenameObservedNotReceipted+
            " tamperedRecordDiverges="+tamperedRecordDiverges+
            " unrelatedAccountIsolated="+unrelatedAccountIsolated+
            " failedReadNoFalseResult="+failedReadNoFalseResult+
            " staleInventoryDenied="+staleInventoryDenied+
            " recycledEnvelopeDenied="+recycledEnvelopeDenied+
            " staleGenerationDenied="+staleGenerationDenied+
            " zeroLiveInventoryGrant="+zeroLiveInventoryGrant+
            " noClaimOrDurabilityAuthority="+
                noClaimOrDurabilityAuthority
        );
        require(
            missingAccountObserved&&
            inFlightOrdinarySavePrecedesObservation&&
            strictBarrierPrecedesObservation&&
            exactlyPreparedOnDisk&&repeatObservationIdempotent&&
            preRenameFailureLeavesPrepared&&
            ambiguousPostRenameObservedNotReceipted&&
            tamperedRecordDiverges&&unrelatedAccountIsolated&&
            failedReadNoFalseResult&&staleInventoryDenied&&
            recycledEnvelopeDenied&&staleGenerationDenied&&
            zeroLiveInventoryGrant&&noClaimOrDurabilityAuthority,
            "G21.26 account snapshot observer acceptance"
        );
        System.out.println(
            "G2126_MAILBOX_DISK_OBSERVATION_PASS"+
            " existingFIFOReadBoundary=true"+
            " olderInFlightSavePrecedesObservation=true"+
            " strictPreparedBarrierPrecedesObservation=true"+
            " exactAccountSnapshotClassification=true"+
            " ambiguousPostRenameNeverReceipt=true"+
            " divergentSnapshotNeverReplay=true"+
            " staleOwnerOrEnvelopeFailClosed=true"+
            " claimAcknowledgement=false"+
            " inventoryGrant=false"+
            " diskObservationIsNotDurabilityProof=true"
        );
    }

    private static final class BlockingRepository
        implements PlayerRepository{
        final FilePlayerRepository file;
        final CountDownLatch firstSaveStarted=new CountDownLatch(1);
        final CountDownLatch releaseFirstSave=new CountDownLatch(1);
        final AtomicInteger saves=new AtomicInteger();
        final AtomicInteger loads=new AtomicInteger();
        volatile boolean rejectNextLoad;

        BlockingRepository(FilePlayerRepository.PathResolver resolver){
            file=new FilePlayerRepository(resolver);
        }

        @Override public Optional<PlayerSnapshot> load(
            String account
        )throws IOException{
            loads.incrementAndGet();
            if(rejectNextLoad){
                rejectNextLoad=false;
                throw new IOException(
                    "G21.26 injected repository load failure"
                );
            }
            return file.load(account);
        }

        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            if(saves.incrementAndGet()==1){
                firstSaveStarted.countDown();
                try{
                    if(!releaseFirstSave.await(
                            8,TimeUnit.SECONDS))
                        throw new IOException(
                            "G21.26 blocked save never released"
                        );
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.26 interrupted writer",interrupted
                    );
                }
            }
            file.save(snapshot);
        }
    }

    private interface Action{
        Object run()throws Exception;
    }

    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException|
                IllegalStateException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
    }

    private G2126MailboxDiskReconciliationIntegrationTest(){}
}
