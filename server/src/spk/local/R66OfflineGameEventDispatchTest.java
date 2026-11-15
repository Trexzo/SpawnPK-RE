package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * R66 offline in-memory event ownership: a test-owned WorldPlayer is
 * mutated only after a queued event rechecks its session-owner UUID,
 * epoch, R63 canonical account generation and R59 journal revision
 * under R60's OS lock. No real game traffic or live player is used.
 */
public final class R66OfflineGameEventDispatchTest {
    private static final UUID PENDING=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r66-offline-game-event-");
        try{
            Path journal=scratch.resolve("journal");
            Path accounts=scratch.resolve("accounts");
            Path generations=scratch.resolve("generations");
            Path sessions=scratch.resolve("sessions");
            Files.createDirectory(journal);
            Files.createDirectory(accounts);
            Files.createDirectory(generations);
            Files.createDirectory(sessions);
            R60TradePairCoordinator.initializeEmpty(journal);

            WorldPlayer oldActor=new WorldPlayer();
            ServerPacketWriter oldPackets=packets();
            oldActor.bank().spawnItem(995,100,oldPackets);
            PlayerSnapshot original=PlayerSnapshotCodec.capture(
                "opensrc",oldActor
            );
            FilePlayerRepository accountRepository=repository(accounts);
            accountRepository.save(original);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            R64OfflineSessionOwnership.Token firstOwner=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );
            R66OfflineGameEventDispatch gate=
                new R66OfflineGameEventDispatch(
                    journal,accounts,generations,sessions
                );
            R66OfflineGameEventDispatch.Actor firstActor=
                gate.bindCurrent(firstOwner,oldActor,oldPackets);

            // Two *real executor threads* hold queued synthetic gameplay
            // actions before a different JVM publishes the successor
            // owner epoch. Capture by itself does not authorize mutation.
            R66OfflineGameEventDispatch.QueuedEvent staleA=
                gate.capture(firstActor,385);
            R66OfflineGameEventDispatch.QueuedEvent staleB=
                gate.capture(firstActor,560);
            ExecutorService callbacks=Executors.newFixedThreadPool(2);
            CountDownLatch waiting=new CountDownLatch(2);
            CountDownLatch allowA=new CountDownLatch(1);
            CountDownLatch allowB=new CountDownLatch(1);
            try{
                Future<String> oldEventA=callbacks.submit(()->{
                    waiting.countDown();
                    if(!allowA.await(15,TimeUnit.SECONDS))
                        throw new AssertionError("first event never released");
                    return expectStaleEvent(gate,staleA);
                });
                Future<String> oldEventB=callbacks.submit(()->{
                    waiting.countDown();
                    if(!allowB.await(15,TimeUnit.SECONDS))
                        throw new AssertionError("second event never released");
                    return expectStaleEvent(gate,staleB);
                });
                require(waiting.await(10,TimeUnit.SECONDS),
                    "two old events not queued");
                require(!oldEventA.isDone()&&!oldEventB.isDone(),
                    "event executed before owner takeover");

                runTakeover(journal,accounts,generations,sessions);
                R64OfflineSessionOwnership.Token secondOwner=
                    requireReadyOwner(
                        journal,accounts,generations,sessions,2L
                    );
                require(!firstOwner.owner.equals(secondOwner.owner),
                    "separate JVM did not change owner UUID");

                byte[] originalAccount=Files.readAllBytes(
                    accounts.resolve("opensrc.properties")
                );
                byte[] originalLedger=Files.readAllBytes(
                    generations.resolve("opensrc.r63-generation.properties")
                );
                byte[] newOwnerBytes=Files.readAllBytes(
                    sessions.resolve("opensrc.r64-session.properties")
                );
                int old385=oldActor.bank().inventoryCount(385);
                int old560=oldActor.bank().inventoryCount(560);

                // Test both old events separately so neither can merely
                // fail on a concurrent tryLock and skip stale-owner logic.
                allowA.countDown();
                require("STALE_OWNER_REJECTED".equals(
                    oldEventA.get(15,TimeUnit.SECONDS)),
                    "first old event not refused at ownership fence");

                // Bind the new independent in-memory actor while the
                // second old event remains queued and its slot superseded.
                WorldPlayer freshActor=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(original,freshActor);
                R66OfflineGameEventDispatch.Actor boundSecond=
                    gate.bindCurrent(
                        secondOwner,freshActor,packets()
                    );

                allowB.countDown();
                require("STALE_OWNER_REJECTED".equals(
                    oldEventB.get(15,TimeUnit.SECONDS)),
                    "second old event not refused at ownership fence");

                require(oldActor.bank().inventoryCount(385)==old385 &&
                    oldActor.bank().inventoryCount(560)==old560 &&
                    freshActor.bank().inventoryCount(385)==0 &&
                    freshActor.bank().inventoryCount(560)==0,
                    "old queued events mutated the prior or new actor");
                require(Arrays.equals(originalAccount,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(originalLedger,Files.readAllBytes(
                        generations.resolve(
                            "opensrc.r63-generation.properties"
                        ))) &&
                    Arrays.equals(newOwnerBytes,Files.readAllBytes(
                        sessions.resolve(
                            "opensrc.r64-session.properties"
                        ))),
                    "stale events changed canonical owner/ledger bytes");

                // New legitimate owner can apply exactly one synthetic
                // in-memory event. The exact same callback UUID cannot
                // be replayed in this dispatcher instance.
                R66OfflineGameEventDispatch.QueuedEvent valid=
                    gate.capture(boundSecond,385);
                R66OfflineGameEventDispatch.Outcome applied=
                    gate.dispatch(valid);
                require(applied.countBefore==0&&
                        applied.countAfter==1&&
                        !applied.accountSaved()&&
                        !applied.loginAuthorized()&&
                        !applied.productionGameplayDispatched(),
                    "new-owner synthetic event did not apply exactly once");
                expectDenied(()->gate.dispatch(valid),
                    "R66_EVENT_DENIED duplicate queued event");
                require(freshActor.bank().inventoryCount(385)==1,
                    "duplicate event reapplied the in-memory mutation");

                // A save advancing R63 generation makes a queued event
                // with the old generation invalid even when owner UUID
                // and epoch have NOT changed.
                R66OfflineGameEventDispatch.QueuedEvent staleGeneration=
                    gate.capture(boundSecond,561);
                R64OfflineSessionOwnership.Token secondSaved=
                    R64OfflineSessionOwnership.saveOwned(
                        journal,accounts,generations,sessions,secondOwner,
                        bump(original,560),false
                    );
                require(secondSaved.account.generation==2L &&
                        secondSaved.epoch==2L,
                    "test-owned generation advancement failed");
                expectDenied(()->gate.dispatch(staleGeneration),
                    "R64_SESSION_DENIED stale generation");
                require(freshActor.bank().inventoryCount(561)==0,
                    "stale-generation event changed in-memory actor");

                WorldPlayer currentActor=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    accountRepository.load("opensrc").orElseThrow(
                        ()->new IOException("canonical vanished")
                    ),currentActor
                );
                R66OfflineGameEventDispatch.Actor active2=
                    gate.bindCurrent(secondSaved,currentActor,packets());
                R66OfflineGameEventDispatch.QueuedEvent oldSlot=
                    gate.capture(active2,562);
                WorldPlayer replacementActor=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    accountRepository.load("opensrc").orElseThrow(
                        ()->new IOException("canonical vanished")
                    ),replacementActor
                );
                R66OfflineGameEventDispatch.Actor active3=
                    gate.bindCurrent(secondSaved,replacementActor,packets());
                expectDenied(()->gate.dispatch(oldSlot),
                    "R66_EVENT_DENIED stale actor incarnation");
                expectDenied(()->gate.capture(active2,563),
                    "R66_CAPTURE_DENIED inactive or superseded actor");
                require(currentActor.bank().inventoryCount(562)==0 &&
                        replacementActor.bank().inventoryCount(562)==0,
                    "superseded actor event changed in-memory state");

                R66OfflineGameEventDispatch.QueuedEvent beforeLogout=
                    gate.capture(active3,563);
                R64OfflineSessionOwnership.Token released=
                    R64OfflineSessionOwnership.releaseOwner(
                        journal,accounts,generations,sessions,secondSaved
                    );
                expectDenied(()->gate.dispatch(beforeLogout),
                    "R64_SESSION_DENIED stale owner");
                require(replacementActor.bank().inventoryCount(563)==0,
                    "logout did not fence pending in-memory action");
                R64OfflineSessionOwnership.Token thirdOwner=
                    R64OfflineSessionOwnership.replaceOwner(
                        journal,accounts,generations,sessions,released
                    );
                require(thirdOwner.epoch==3L,
                    "new test owner failed after RELEASED session");

                WorldPlayer thirdPlayer=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    accountRepository.load("opensrc").orElseThrow(
                        ()->new IOException("canonical missing")
                    ),thirdPlayer
                );
                R66OfflineGameEventDispatch.Actor thirdActor=
                    gate.bindCurrent(thirdOwner,thirdPlayer,packets());

                // Hold R60 lock *after* owner verification but before
                // mutation. A separate test-owned JVM's real takeover
                // attempt must fail while the event owns that lock.
                R66OfflineGameEventDispatch.QueuedEvent lockedEvent=
                    gate.capture(thirdActor,565);
                ExecutorService single=Executors.newSingleThreadExecutor();
                CountDownLatch insideLock=new CountDownLatch(1);
                CountDownLatch permitMutation=new CountDownLatch(1);
                try{
                    Future<R66OfflineGameEventDispatch.Outcome> inFlight=
                        single.submit(()->gate.dispatch(lockedEvent,()->{
                            insideLock.countDown();
                            try{
                                if(!permitMutation.await(
                                        15,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "R66_TEST_MUTATION_GATE_TIMEOUT"
                                    );
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R66_TEST_MUTATION_GATE_INTERRUPTED",
                                    interrupted
                                );
                            }
                        }));
                    require(insideLock.await(10,TimeUnit.SECONDS),
                        "event did not acquire owner lock before mutation");
                    runBusyTakeoverProbe(
                        journal,accounts,generations,sessions,thirdOwner
                    );
                    require(thirdPlayer.bank().inventoryCount(565)==0,
                        "in-memory mutation ran before gate released");
                    permitMutation.countDown();
                    R66OfflineGameEventDispatch.Outcome outcome=
                        inFlight.get(15,TimeUnit.SECONDS);
                    require(outcome.countAfter==outcome.countBefore+1 &&
                            thirdPlayer.bank().inventoryCount(565)==1,
                        "legitimate in-flight event could not complete");
                }finally{
                    permitMutation.countDown();
                    single.shutdownNow();
                    require(single.awaitTermination(10,TimeUnit.SECONDS),
                        "test event-dispatch worker did not terminate");
                }

                // A real PREPARED journal on the same participant must
                // invalidate already-captured synthetic gameplay events.
                R66OfflineGameEventDispatch.QueuedEvent pendingTrade=
                    gate.capture(thirdActor,567);
                PlayerSnapshot beforeA=snapshot("opensrc",995,1);
                PlayerSnapshot beforeB=snapshot("zpeer",385,1);
                long revision=R60TradePairCoordinator.registerPrepared(
                    journal,1L,Collections.emptyList(),PENDING,
                    beforeA,bump(beforeA,560),
                    beforeB,bump(beforeB,561)
                );
                require(revision==2L,"test pending pair not published");
                expectDenied(()->gate.dispatch(pendingTrade),
                    "R64_SESSION_DENIED account/pair not clear");
                require(thirdPlayer.bank().inventoryCount(567)==0,
                    "pending trade failed to quarantine gameplay event");

                System.out.println(
                    "R66_OFFLINE_GAMEPLAY_EVENT_FENCE_PASS "+
                    "twoStaleEventsQueuedBeforeCrossJvmTakeover=true "+
                    "oldActorInMemoryInventoryUnchanged=true "+
                    "successorActorInMemoryInventoryUnchangedByOldEvents=true "+
                    "canonicalLedgerAndOwnerFilesPreserved=true "+
                    "newOwnerEventAppliedOnceAndDuplicateDenied=true "+
                    "staleAccountGenerationEventDenied=true "+
                    "supersededActorIncarnationDenied=true "+
                    "logoutDeniesLateInMemoryMutation=true "+
                    "midMutationLockBlocksCrossJvmTakeover=true "+
                    "pendingTradeBlocksCapturedGameEvent=true "+
                    "noLivePacketsOrProductionSessionHooks=true "+
                    "processCrashDurabilityNotProven=true"
                );
            }finally{
                allowA.countDown();
                allowB.countDown();
                callbacks.shutdownNow();
                require(callbacks.awaitTermination(10,TimeUnit.SECONDS),
                    "queued test callback threads did not terminate");
            }
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException fail){
                        throw new IllegalStateException(
                            "test-owned cleanup "+path,fail
                        );
                    }
                });
            }
        }
    }

    private static String expectStaleEvent(
        R66OfflineGameEventDispatch dispatcher,
        R66OfflineGameEventDispatch.QueuedEvent event
    )throws IOException{
        try{
            dispatcher.dispatch(event);
            throw new AssertionError(
                "superseded session event mutated a WorldPlayer"
            );
        }catch(IOException expected){
            if(!expected.getMessage().contains(
                    "R64_SESSION_DENIED stale owner"))
                throw expected;
            return "STALE_OWNER_REJECTED";
        }
    }

    private static FilePlayerRepository repository(Path accounts){
        return new FilePlayerRepository(
            account->accounts.resolve(account+".properties")
        );
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static PlayerSnapshot snapshot(
        String username,int item,int count
    )throws IOException{
        WorldPlayer player=new WorldPlayer();
        player.bank().spawnItem(item,count,packets());
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static PlayerSnapshot bump(
        PlayerSnapshot original,int item
    )throws IOException{
        WorldPlayer player=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(original,player);
        player.bank().spawnItem(item,1,packets());
        return PlayerSnapshotCodec.capture(original.username(),player);
    }

    private static R64OfflineSessionOwnership.Token requireReadyOwner(
        Path journal,Path accounts,Path generations,Path sessions,
        long expectedEpoch
    ){
        R64OfflineSessionOwnership.Review result=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        require(result.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                result.token!=null &&
                result.token.epoch==expectedEpoch &&
                !result.loginAuthorized(),
                "offline owner not ready "+result.status+" "+result.reason);
        return result.token;
    }

    private static void runTakeover(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        waitChild(child,0,"R64_SESSION_WORKER_PASS");
    }

    private static void runBusyTakeoverProbe(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token owner
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R66BlockedTakeoverWorker",
            journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString(),
            owner.username,owner.owner.toString(),
            Long.toString(owner.epoch),owner.phase.name(),
            Long.toString(owner.account.generation),
            Long.toString(owner.account.journalRevision),
            owner.account.snapshotDigest
        ).redirectErrorStream(true).start();
        waitChild(child,0,"R66_CROSS_JVM_TAKEOVER_BLOCKED_BY_EVENT_LOCK");
    }

    private static void waitChild(
        Process child,int expectedExit,String marker
    )throws Exception{
        if(!child.waitFor(20,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-owned process timed out");
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==expectedExit&&
                output.contains(marker),
            "test-owned process error code="+child.exitValue()+
            " output="+output);
    }

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")?
                "java.exe":"java").toString();
    }

    @FunctionalInterface
    private interface Action{void run()throws Exception;}
    private static void expectDenied(Action action,String reason)
            throws Exception{
        try{
            action.run();
            throw new AssertionError("expected refusal "+reason);
        }catch(IOException expected){
            require(expected.getMessage().contains(reason),
                "incorrect refusal "+expected+" expected "+reason);
        }
    }

    private static void require(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    private R66OfflineGameEventDispatchTest(){}
}

/** Independent JVM attempts a real owner replacement while R66 holds R60. */
final class R66BlockedTakeoverWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=11)throw new IllegalArgumentException("worker args");
        Path journal=Paths.get(args[0]);
        Path accounts=Paths.get(args[1]);
        Path generations=Paths.get(args[2]);
        Path sessions=Paths.get(args[3]);
        String user=args[4];
        R64OfflineSessionOwnership.Token old=
            new R64OfflineSessionOwnership.Token(
                user,UUID.fromString(args[5]),Long.parseLong(args[6]),
                R64OfflineSessionOwnership.Phase.valueOf(args[7]),
                new R63AccountSaveAdmission.Token(
                    user,Long.parseLong(args[8]),
                    Long.parseLong(args[9]),args[10]
                )
            );
        try{
            R64OfflineSessionOwnership.replaceOwner(
                journal,accounts,generations,sessions,old
            );
            throw new AssertionError(
                "concurrent process took owner while event mutating"
            );
        }catch(IOException expected){
            if(!expected.getMessage().contains(
                "R60_LOCK_BUSY_OR_OVERLAPPING"))
                throw expected;
        }
        System.out.println(
            "R66_CROSS_JVM_TAKEOVER_BLOCKED_BY_EVENT_LOCK"
        );
    }
}
