package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R71 real WorldPlayerPersistence regression. All snapshots originate from
 * real World actor capture; all successes/refusals execute through its
 * dedicated IO worker and the real coordinated R64 FilePlayerRepository.
 * No live sessions, game sockets or production files.
 */
public final class R71OfflineSnapshotCaptureOrderTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r71-order-");
        try{
            oldDeferredAfterNewCheckpoint(root.resolve("deferred"));
            oldFinalAfterNewSave(root.resolve("final"));
            coalescedCheckpointOrdinal(root.resolve("coalesced"));
            System.out.println(
                "R71_REAL_CAPTURE_ORDER_FENCE_PASS "+
                "oldDeferredAfterCheckpointDenied=true "+
                "identicalNewerNoopAdvancesWatermark=true "+
                "replayedOrdinalDenied=true "+
                "newOrdinaryAndFinalSavesCommit=true "+
                "crossJvmOwnerTakeoverRefusesNewerOrdinal=true "+
                "oldFinalAfterNewSaveDenied=true "+
                "checkpointCoalescingCarriesCaptureOrdinal=true "+
                "noRawRepositoryBypass=true "+
                "canonicalRecordsPreservedOnRefusal=true "+
                "offlineOnlyNoLiveHooks=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new IllegalStateException(
                        "R71 temporary cleanup "+p,e
                    );}
                });
            }
        }
    }

    private static void oldDeferredAfterNewCheckpoint(Path root)
            throws Exception{
        Paths4 p=new Paths4(root);
        R64OfflineSessionOwnership.Token first=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R71OfflineOrderedPersistenceAdmission gate=
                p.install(world,first);
            world.start();
            ServerPacketWriter packets=packets();
            AtomicReference<WorldPlayerPersistence.CapturedSave> older=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket> recent=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packets);
                older.set(world.persistence().captureDeferredSave(
                    "opensrc",player,generation,0,
                    "[r71] ","OLD_DEFERRED"
                ));
            },10_000L);
            require(older.get()!=null &&
                    older.get().ticket.captureOrdinal==1L,
                "old snapshot did not capture ordinal one");

            world.submitAndWait(player,generation,()->{
                addItem(player,385,packets);
                world.persistence().checkpointDue(100L);
            },10_000L);
            awaitCheckpoints(world.persistence(),1L);
            require(gate.highestAcceptedOrdinal()==2L,
                "checkpoint did not retain World-thread capture order");
            checkCanonical(p,first,2L);

            SnapshotBytes before=p.snapshotBytes();
            WorldPlayerPersistence.SaveTicket oldTicket=
                world.persistence().submitCapturedWithBackpressure(
                    older.get(),5_000L
                );
            expectFailure(oldTicket,
                "R71_SAVE_DENIED stale capture ordinal");
            require(before.equalsBytes(p.snapshotBytes()),
                "older deferred save overwrote newer checkpoint");
            require(gate.rejectedOldWrites()==1,
                "stale deferred snapshot not counted");

            // Identical snapshots are valid no-ops and still advance
            // the capture-order high-water mark.
            world.submitAndWait(player,generation,()->{
                recent.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r71] ","UNCHANGED_NEWER"
                ));
            },10_000L);
            awaitSuccess(recent.get());
            require(recent.get().captureOrdinal==3L &&
                    gate.highestAcceptedOrdinal()==3L &&
                    gate.identicalWrites()==1,
                "identical capture did not advance its ordering fence");
            checkCanonical(p,first,2L);

            // A direct retry of a previously accepted ordinal is refused
            // even if its snapshot bytes are identical.
            try{
                gate.withOrderedAdmission(
                    recent.get().captureOrdinal,
                    recent.get().snapshot,
                    ()->{throw new IOException("RAW_WRITER_BYPASS");}
                );
                throw new AssertionError("duplicate ordinal accepted");
            }catch(IOException expected){
                require(expected.getMessage().contains(
                    "R71_SAVE_DENIED stale capture ordinal"),
                    "duplicate ordinal gave wrong refusal "+expected);
            }

            world.submitAndWait(player,generation,()->{
                addItem(player,560,packets);
                recent.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r71] ","NEWER_ORDINARY"
                ));
            },10_000L);
            awaitSuccess(recent.get());
            require(recent.get().captureOrdinal==4L,
                "new ordinary capture ordinal incorrect");
            checkCanonical(p,first,3L);

            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,565,packets);
                finalSave.set(
                    world.persistence().captureDeferredFinalSave(
                        "opensrc",player,generation,0,
                        "[r71] ","VALID_FINAL_SAVE"
                    )
                );
                world.persistence().checkpointDue(200L);
            },10_000L);
            require(world.persistence().checkpointCapturedCount()==1L,
                "final capture did not suppress later checkpoint");
            WorldPlayerPersistence.SaveTicket finalTicket=
                world.persistence().submitCapturedWithBackpressure(
                    finalSave.get(),5_000L
                );
            awaitSuccess(finalTicket);
            require(finalTicket.captureOrdinal==5L &&
                    gate.highestAcceptedOrdinal()==5L,
                "final save lost its original capture ordinal");
            checkCanonical(p,first,4L);

            // Same process/World actor is still alive, but a different
            // JVM owns the account. Fresh numerical ordinals are NOT
            // sufficient without the persisted R64 owner check.
            takeover(p);
            SnapshotBytes transferred=p.snapshotBytes();
            world.submitAndWait(player,generation,()->{
                addItem(player,1334,packets);
                recent.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r71] ","STALE_OWNER_AFTER_TAKEOVER"
                ));
            },10_000L);
            expectFailure(recent.get(),
                "R64_SESSION_DENIED stale owner");
            require(transferred.equalsBytes(p.snapshotBytes()),
                "old owner mutated canonical files after takeover");
            require(gate.committedWrites()==3 &&
                    gate.identicalWrites()==1 &&
                    gate.highestAcceptedOrdinal()==5L,
                "stale owner advanced capture watermark");
        }
    }

    private static void oldFinalAfterNewSave(Path root)throws Exception{
        Paths4 p=new Paths4(root);
        R64OfflineSessionOwnership.Token first=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R71OfflineOrderedPersistenceAdmission gate=
                p.install(world,first);
            world.start();
            ServerPacketWriter packets=packets();
            AtomicReference<WorldPlayerPersistence.CapturedSave> oldFinal=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket> newSave=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packets);
                oldFinal.set(
                    world.persistence().captureDeferredFinalSave(
                        "opensrc",player,generation,0,
                        "[r71-final] ","HELD_OLD_FINAL"
                    )
                );
            },10_000L);

            // Explicit save captured later than the final save, but
            // submitted immediately; the old final is held by caller.
            world.submitAndWait(player,generation,()->{
                addItem(player,385,packets);
                newSave.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r71-final] ","NEWER_SAVE_FIRST"
                ));
            },10_000L);
            awaitSuccess(newSave.get());
            require(oldFinal.get().ticket.captureOrdinal==1L &&
                    newSave.get().captureOrdinal==2L &&
                    gate.highestAcceptedOrdinal()==2L,
                "final-save inversion setup invalid");
            checkCanonical(p,first,2L);
            SnapshotBytes saved=p.snapshotBytes();

            WorldPlayerPersistence.SaveTicket older=
                world.persistence().submitCapturedWithBackpressure(
                    oldFinal.get(),5_000L
                );
            expectFailure(older,"R71_SAVE_DENIED stale capture ordinal");
            require(saved.equalsBytes(p.snapshotBytes()) &&
                    world.persistence().checkpointSuppressed(
                        player.id(),generation),
                "old final save bypassed freshness or suppression");
            require(gate.committedWrites()==1&&
                    gate.rejectedOldWrites()==1,
                "final save rollback was not rejected");
        }
    }

    private static void coalescedCheckpointOrdinal(Path root)
            throws Exception{
        Paths4 p=new Paths4(root);
        R64OfflineSessionOwnership.Token first=p.initialize();
        CountDownLatch firstEntered=new CountDownLatch(1);
        CountDownLatch releaseFirst=new CountDownLatch(1);
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R71OfflineOrderedPersistenceAdmission ordered=
                new R71OfflineOrderedPersistenceAdmission(
                    p.journal,p.accounts,p.generations,p.sessions,first
                );
            AtomicInteger calls=new AtomicInteger();
            world.persistence().installOfflineSaveAdmissionForTest(
                new WorldPlayerPersistence.OfflineOrderedSaveAdmission(){
                    @Override public void withAdmission(
                        PlayerSnapshot snapshot,
                        WorldPlayerPersistence.CheckedSaveWriter writer
                    )throws IOException{
                        throw new IOException(
                            "R71_SAVE_DENIED missing capture ordinal"
                        );
                    }

                    @Override public void withOrderedAdmission(
                        long ordinal,PlayerSnapshot snapshot,
                        WorldPlayerPersistence.CheckedSaveWriter writer
                    )throws IOException{
                        if(calls.incrementAndGet()==1){
                            firstEntered.countDown();
                            try{
                                if(!releaseFirst.await(
                                        15L,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "R71 checkpoint test hold timed out"
                                    );
                            }catch(InterruptedException e){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R71 checkpoint hold interrupted",e
                                );
                            }
                        }
                        ordered.withOrderedAdmission(
                            ordinal,snapshot,writer
                        );
                    }
                }
            );
            world.start();
            ServerPacketWriter packets=packets();
            try{
                world.submitAndWait(player,generation,()->{
                    addItem(player,995,packets);
                    world.persistence().checkpointDue(100L);
                },10_000L);
                require(firstEntered.await(10L,TimeUnit.SECONDS),
                    "first checkpoint never reached the real IO worker");

                world.submitAndWait(player,generation,()->{
                    addItem(player,385,packets);
                    world.persistence().checkpointDue(200L);
                    addItem(player,560,packets);
                    world.persistence().checkpointDue(300L);
                },10_000L);
                require(world.persistence().checkpointCapturedCount()==3L&&
                        world.persistence().checkpointCoalescedCount()==1L,
                    "two queued checkpoints did not coalesce");
            }finally{
                releaseFirst.countDown();
            }
            awaitCheckpoints(world.persistence(),2L);
            require(calls.get()==2 &&
                    ordered.highestAcceptedOrdinal()==3L &&
                    ordered.committedWrites()==2,
                "coalesced checkpoint used drain-time ordinal");
            checkCanonical(p,first,3L);
        }finally{
            releaseFirst.countDown();
        }
    }

    private static void checkCanonical(
        Paths4 p,R64OfflineSessionOwnership.Token original,
        long generation
    )throws Exception{
        R64OfflineSessionOwnership.Review review=p.review();
        require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null &&
                review.token.owner.equals(original.owner) &&
                review.token.epoch==original.epoch &&
                review.token.account.generation==generation,
            "canonical account/R63/R64 generation not coordinated");
        PlayerSnapshot disk=p.files().load("opensrc").orElseThrow(
            ()->new IOException("missing canonical account")
        );
        require(R57TradePairJournal.digest(disk).equals(
                review.token.account.snapshotDigest),
            "R63 ledger digest inconsistent with canonical data");
    }

    private static void awaitCheckpoints(
        WorldPlayerPersistence persistence,long written
    )throws Exception{
        long deadline=System.nanoTime()+
            TimeUnit.SECONDS.toNanos(15L);
        while(persistence.checkpointWrittenCount()<written){
            if(persistence.failedCount()>0)
                throw new AssertionError(
                    "unexpected checkpoint failure "+
                    persistence.metrics()
                );
            if(System.nanoTime()>deadline)
                throw new AssertionError(
                    "checkpoint write timeout "+
                    persistence.metrics()
                );
            Thread.sleep(10L);
        }
    }

    private static void awaitSuccess(
        WorldPlayerPersistence.SaveTicket ticket
    )throws Exception{
        require(ticket!=null,"save ticket missing");
        ticket.completion.get(15L,TimeUnit.SECONDS);
    }

    private static void expectFailure(
        WorldPlayerPersistence.SaveTicket ticket,
        String expected
    )throws Exception{
        require(ticket!=null,"missing refusal ticket");
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("stale write was accepted");
        }catch(ExecutionException denied){
            require(denied.getCause() instanceof IOException &&
                    denied.getCause().getMessage().contains(expected),
                "wrong write denial "+denied.getCause()+
                ", expected "+expected);
        }
    }

    private static PlayerRepository rawWriterMustFail(
        FilePlayerRepository file
    ){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            )throws IOException{
                return file.load(username);
            }

            @Override public void save(PlayerSnapshot snapshot)
                    throws IOException{
                throw new IOException(
                    "R71_RAW_REPOSITORY_BYPASS_DETECTED"
                );
            }
        };
    }

    private static void addItem(
        WorldPlayer player,int id,ServerPacketWriter packet
    )throws IOException{
        String result=player.bank().spawnItem(id,1,packet);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R71 test inventory mutation refused "+result);
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static void takeover(Paths4 p)throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker","takeover",
            p.journal.toString(),p.accounts.toString(),
            p.generations.toString(),p.sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("R71 takeover JVM timeout");
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0 &&
                    output.contains("R64_SESSION_WORKER_PASS"),
            "R71 separate JVM takeover failed "+output);
    }

    private static String javaExecutable(){
        return Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java"
        ).toString();
    }

    private static void require(boolean ok,String error){
        if(!ok)throw new AssertionError(error);
    }

    private static final class SnapshotBytes {
        final byte[] account;
        final byte[] ledger;
        final byte[] owner;
        SnapshotBytes(byte[] account,byte[] ledger,byte[] owner){
            this.account=account;
            this.ledger=ledger;
            this.owner=owner;
        }
        boolean equalsBytes(SnapshotBytes other){
            return Arrays.equals(account,other.account) &&
                Arrays.equals(ledger,other.ledger) &&
                Arrays.equals(owner,other.owner);
        }
    }

    private static final class Paths4 {
        final Path journal;
        final Path accounts;
        final Path generations;
        final Path sessions;
        Paths4(Path root)throws IOException{
            Files.createDirectory(root);
            journal=Files.createDirectory(root.resolve("journal"));
            accounts=Files.createDirectory(root.resolve("accounts"));
            generations=Files.createDirectory(root.resolve("generations"));
            sessions=Files.createDirectory(root.resolve("sessions"));
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                name->accounts.resolve(name+".properties")
            );
        }
        SnapshotBytes snapshotBytes()throws IOException{
            return new SnapshotBytes(
                Files.readAllBytes(accounts.resolve(
                    "opensrc.properties"
                )),
                Files.readAllBytes(generations.resolve(
                    "opensrc.r63-generation.properties"
                )),
                Files.readAllBytes(sessions.resolve(
                    "opensrc.r64-session.properties"
                ))
            );
        }
        R64OfflineSessionOwnership.Token initialize()
                throws IOException{
            files().save(PlayerSnapshotCodec.capture(
                "opensrc",new WorldPlayer()
            ));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            return R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,"opensrc"
            );
        }
        R71OfflineOrderedPersistenceAdmission install(
            World world,R64OfflineSessionOwnership.Token first
        ){
            R71OfflineOrderedPersistenceAdmission hook=
                new R71OfflineOrderedPersistenceAdmission(
                    journal,accounts,generations,sessions,first
                );
            world.persistence().installOfflineSaveAdmissionForTest(hook);
            return hook;
        }
        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        }
    }

    private R71OfflineSnapshotCaptureOrderTest(){}
}
