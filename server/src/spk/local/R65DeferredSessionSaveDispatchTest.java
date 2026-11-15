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
 * R65 offline queued callback race: two already-captured callbacks are held
 * in actual executor threads; another JVM takes over the account; the
 * delayed autosave and final-logout callbacks run afterward and must refuse
 * without touching any canonical profile, generation or owner record.
 */
public final class R65DeferredSessionSaveDispatchTest {
    private static final UUID PENDING=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r65-deferred-save-");
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
            PlayerSnapshot initial=snapshot("opensrc",995,100);
            repository(accounts).save(initial);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            R64OfflineSessionOwnership.Token first=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );

            R65DeferredSessionSaveDispatch.CapturedCallback staleAutosave=
                R65DeferredSessionSaveDispatch.capture(
                    R65DeferredSessionSaveDispatch.Reason.AUTOSAVE,
                    first,bump(initial,385)
                );
            R65DeferredSessionSaveDispatch.CapturedCallback staleLogout=
                R65DeferredSessionSaveDispatch.capture(
                    R65DeferredSessionSaveDispatch.Reason.LOGOUT_FINAL_SAVE,
                    first,bump(initial,560)
                );

            // Real asynchronous callbacks are queued BEFORE the successor
            // enters. Latches make order deterministic, not a timer guess.
            ExecutorService executor=Executors.newFixedThreadPool(2);
            CountDownLatch callbacksCaptured=new CountDownLatch(2);
            CountDownLatch releaseAutosave=new CountDownLatch(1);
            CountDownLatch releaseLogout=new CountDownLatch(1);
            try{
                Future<String> pendingSave=executor.submit(()->{
                    callbacksCaptured.countDown();
                    if(!releaseAutosave.await(15,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "old autosave callback never released"
                        );
                    return expectStaleDispatch(
                        journal,accounts,generations,sessions,
                        staleAutosave
                    );
                });
                Future<String> pendingLogout=executor.submit(()->{
                    callbacksCaptured.countDown();
                    if(!releaseLogout.await(15,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "old logout callback never released"
                        );
                    return expectStaleDispatch(
                        journal,accounts,generations,sessions,
                        staleLogout
                    );
                });
                require(callbacksCaptured.await(10,TimeUnit.SECONDS),
                    "old async callbacks were not queued");
                require(!pendingSave.isDone()&&!pendingLogout.isDone(),
                    "queued old callbacks ran before owner takeover");

                // A separate JVM publishes epoch 2 under the shared R60
                // lock, while the old callback threads remain suspended.
                runTakeoverWorker(
                    journal,accounts,generations,sessions
                );
                R64OfflineSessionOwnership.Token second=
                    ready(journal,accounts,generations,sessions,2L);
                require(!first.owner.equals(second.owner),
                    "replacement JVM did not change owner identity");

                byte[] accountAtTakeover=Files.readAllBytes(
                    accounts.resolve("opensrc.properties")
                );
                byte[] ledgerAtTakeover=Files.readAllBytes(
                    generations.resolve(
                        "opensrc.r63-generation.properties"
                    )
                );
                byte[] ownerAtTakeover=Files.readAllBytes(
                    sessions.resolve(
                        "opensrc.r64-session.properties"
                    )
                );

                // Both callbacks were queued before the new owner.
                // Release one at a time so each actually reaches the
                // stale-owner fence, rather than racing for the OS lock
                // and merely returning an earlier LOCK_BUSY refusal.
                releaseAutosave.countDown();
                require("REJECTED_STALE_OWNER".equals(
                        pendingSave.get(15,TimeUnit.SECONDS)),
                    "late autosave did not fail stale ownership");
                releaseLogout.countDown();
                require("REJECTED_STALE_OWNER".equals(
                        pendingLogout.get(15,TimeUnit.SECONDS)),
                    "late logout did not fail stale ownership");

                require(Arrays.equals(accountAtTakeover,
                        Files.readAllBytes(accounts.resolve(
                            "opensrc.properties"
                        ))) &&
                    Arrays.equals(ledgerAtTakeover,
                        Files.readAllBytes(generations.resolve(
                            "opensrc.r63-generation.properties"
                        ))) &&
                    Arrays.equals(ownerAtTakeover,
                        Files.readAllBytes(sessions.resolve(
                            "opensrc.r64-session.properties"
                        ))),
                    "late queued callback changed any of the three files");

                // The new legitimate owner progresses immediately AFTER
                // old callback denial. Dispatch is the only write path
                // exercised by this R65 fixture, not a live save callback.
                R65DeferredSessionSaveDispatch.CapturedCallback activeSave=
                    R65DeferredSessionSaveDispatch.capture(
                        R65DeferredSessionSaveDispatch.Reason.AUTOSAVE,
                        second,bump(initial,385)
                    );
                R65DeferredSessionSaveDispatch.Outcome advanced=
                    R65DeferredSessionSaveDispatch.dispatch(
                        journal,accounts,generations,sessions,
                        activeSave
                    );
                require(!advanced.ownerReleased &&
                        advanced.latestOwner.epoch==2L &&
                        advanced.latestOwner.account.generation==2L &&
                        !advanced.loginAuthorized(),
                    "legitimate successor save failed its generation fence");

                byte[] savedAccount=Files.readAllBytes(
                    accounts.resolve("opensrc.properties")
                );
                byte[] savedLedger=Files.readAllBytes(
                    generations.resolve(
                        "opensrc.r63-generation.properties"
                    )
                );
                byte[] savedOwner=Files.readAllBytes(
                    sessions.resolve(
                        "opensrc.r64-session.properties"
                    )
                );
                expectDenied(()->R65DeferredSessionSaveDispatch.dispatch(
                    journal,accounts,generations,sessions,
                    activeSave
                ),"R64_SESSION_DENIED stale generation");
                require(Arrays.equals(savedAccount,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(savedLedger,Files.readAllBytes(
                        generations.resolve(
                            "opensrc.r63-generation.properties"
                        ))) &&
                    Arrays.equals(savedOwner,Files.readAllBytes(
                        sessions.resolve(
                            "opensrc.r64-session.properties"
                        ))),
                    "duplicate queued autosave wrote a second generation");

                // A current owner's final queued save AND logout release
                // are committed under ONE cooperative OS FileLock.
                PlayerSnapshot later=bump(bump(initial,385),560);
                R65DeferredSessionSaveDispatch.CapturedCallback finalLogout=
                    R65DeferredSessionSaveDispatch.capture(
                        R65DeferredSessionSaveDispatch.Reason.LOGOUT_FINAL_SAVE,
                        advanced.latestOwner,later
                    );
                R65DeferredSessionSaveDispatch.Outcome loggedOut=
                    R65DeferredSessionSaveDispatch.dispatch(
                        journal,accounts,generations,sessions,
                        finalLogout
                    );
                require(loggedOut.ownerReleased &&
                        loggedOut.latestOwner.phase==
                            R64OfflineSessionOwnership.Phase.RELEASED &&
                        loggedOut.latestOwner.account.generation==3L,
                    "final-save+logout did not publish RELEASED epoch");
                R64OfflineSessionOwnership.Review released=
                    R64OfflineSessionOwnership.inspect(
                        journal,accounts,generations,sessions,"opensrc"
                    );
                require(released.status==
                        R64OfflineSessionOwnership.Status.BLOCK_RELEASED &&
                        !released.loginAuthorized(),
                    "logged-out account admitted again");
                expectDenied(()->R65DeferredSessionSaveDispatch.dispatch(
                    journal,accounts,generations,sessions,
                    finalLogout
                ),"R64_SESSION_DENIED stale owner");

                // Explicit new owner after the released epoch is allowed.
                R64OfflineSessionOwnership.Token third=
                    R64OfflineSessionOwnership.replaceOwner(
                        journal,accounts,generations,sessions,
                        loggedOut.latestOwner
                    );
                require(third.epoch==3L &&
                        third.account.generation==3L,
                    "new epoch after logout did not advance");

                R65DeferredSessionSaveDispatch.CapturedCallback newCallback=
                    R65DeferredSessionSaveDispatch.capture(
                        R65DeferredSessionSaveDispatch.Reason.AUTOSAVE,
                        third,bump(later,565)
                    );
                // Actual second JVM holding R60 prevents any deferred
                // callback from bypassing the transaction authority.
                holdSecondJvmLock(scratch,journal,()->{
                    byte[] before=Files.readAllBytes(
                        accounts.resolve("opensrc.properties")
                    );
                    expectDenied(()->R65DeferredSessionSaveDispatch.dispatch(
                        journal,accounts,generations,sessions,newCallback
                    ),"R60_LOCK_BUSY_OR_OVERLAPPING");
                    require(Arrays.equals(before,Files.readAllBytes(
                        accounts.resolve("opensrc.properties")
                    )),"locked callback modified an account");
                });
                R65DeferredSessionSaveDispatch.Outcome finalSave=
                    R65DeferredSessionSaveDispatch.dispatch(
                        journal,accounts,generations,sessions,newCallback
                    );
                require(finalSave.latestOwner.account.generation==4L,
                    "new owner did not progress after lock released");

                // Pending paired trade supersedes a captured callback
                // before any canonical write. This models a late autosave
                // racing with the coordinated R57/R59 trade intent.
                PlayerSnapshot post=pullSnapshot(accounts,"opensrc");
                R65DeferredSessionSaveDispatch.CapturedCallback queued=
                    R65DeferredSessionSaveDispatch.capture(
                        R65DeferredSessionSaveDispatch.Reason.AUTOSAVE,
                        finalSave.latestOwner,bump(post,561)
                    );
                PlayerSnapshot pendingA=snapshot("opensrc",995,1);
                PlayerSnapshot pendingB=snapshot("zpeer",385,1);
                long rev=R60TradePairCoordinator.registerPrepared(
                    journal,1L,Collections.emptyList(),PENDING,
                    pendingA,bump(pendingA,560),
                    pendingB,bump(pendingB,565)
                );
                require(rev==2L,"pending pair not registered");
                byte[] beforePending=Files.readAllBytes(
                    accounts.resolve("opensrc.properties")
                );
                expectDenied(()->R65DeferredSessionSaveDispatch.dispatch(
                    journal,accounts,generations,sessions,queued
                ),"R64_SESSION_DENIED account/pair not clear");
                require(Arrays.equals(beforePending,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))),
                    "trade-pending callback wrote account after quarantine");
                require(R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,"opensrc"
                ).status==R64OfflineSessionOwnership.Status.BLOCK_PENDING_PAIR,
                    "pending pair did not block offline session inspection");

                System.out.println(
                    "R65_DEFERRED_CALLBACK_FENCE_PASS "+
                    "twoOldCallbacksQueuedBeforeCrossJvmTakeover=true "+
                    "lateAutosaveAndLogoutBothDeniedAfterTakeover=true "+
                    "canonicalLedgerAndOwnerBytesPreserved=true "+
                    "newOwnerSaveAfterStaleRefusalPass=true "+
                    "replayedCallbackDoesNotAdvanceGeneration=true "+
                    "finalSaveAndLogoutSingleLock=true "+
                    "secondJvmLockBlocksDeferredDispatch=true "+
                    "pendingTradeBlocksCapturedCallback=true "+
                    "noLiveCallbackHooksOrLoginAuthorization=true "+
                    "hardwarePowerLossDurabilityNotProven=true"
                );
            }finally{
                releaseAutosave.countDown();
                releaseLogout.countDown();
                executor.shutdownNow();
                require(executor.awaitTermination(10,TimeUnit.SECONDS),
                    "test-only queued callback threads did not terminate");
            }
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(
                            "test-only cleanup "+path,failure
                        );
                    }
                });
            }
        }
    }

    private static String expectStaleDispatch(
        Path journal,Path accounts,Path generations,Path sessions,
        R65DeferredSessionSaveDispatch.CapturedCallback callback
    )throws Exception{
        try{
            R65DeferredSessionSaveDispatch.dispatch(
                journal,accounts,generations,sessions,callback
            );
            throw new AssertionError("old callback saved after takeover");
        }catch(IOException expected){
            require(expected.getMessage().contains(
                "R64_SESSION_DENIED stale owner"),
                "wrong callback refusal "+expected);
            return "REJECTED_STALE_OWNER";
        }
    }

    private static FilePlayerRepository repository(Path accounts){
        return new FilePlayerRepository(
            username->accounts.resolve(username+".properties")
        );
    }

    private static PlayerSnapshot pullSnapshot(
        Path accounts,String username)throws IOException{
        return repository(accounts).load(username).orElseThrow(
            ()->new IOException("test canonical missing "+username)
        );
    }

    private static PlayerSnapshot snapshot(
        String name,int item,int count)throws IOException{
        WorldPlayer player=new WorldPlayer();
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        player.bank().spawnItem(item,count,writer);
        return PlayerSnapshotCodec.capture(name,player);
    }

    private static PlayerSnapshot bump(
        PlayerSnapshot previous,int item)throws IOException{
        WorldPlayer player=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(previous,player);
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{4,3,2,1})
        );
        player.bank().spawnItem(item,1,writer);
        return PlayerSnapshotCodec.capture(previous.username(),player);
    }

    private static R64OfflineSessionOwnership.Token ready(
        Path journal,Path accounts,Path generations,Path sessions,
        long expectedEpoch
    ){
        R64OfflineSessionOwnership.Review review=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        require(review.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null&&
                review.token.epoch==expectedEpoch &&
                !review.loginAuthorized(),
            "offline owner not ready "+review.status+" "+review.reason);
        return review.token;
    }

    private static void runTakeoverWorker(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-owned takeover JVM timed out");
        }
        String output=new String(child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        require(child.exitValue()==0 &&
                output.contains("R64_SESSION_WORKER_PASS"),
            "takeover JVM failed: "+child.exitValue()+" "+output);
    }

    @FunctionalInterface
    private interface Action{void run()throws Exception;}

    private static void holdSecondJvmLock(
        Path scratch,Path journal,Action action
    )throws Exception{
        Path ready=scratch.resolve("r65-lock-ready");
        Path release=scratch.resolve("r65-lock-release");
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R60TradePairLockWorker",
            journal.toString(),ready.toString(),release.toString(),"hold"
        ).redirectErrorStream(true).start();
        try{
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12);
            while(!Files.exists(ready)){
                if(!child.isAlive()||System.nanoTime()>deadline)
                    throw new AssertionError("lock child failed to acquire");
                Thread.sleep(20L);
            }
            action.run();
        }finally{
            Files.write(release,new byte[]{1});
            if(!child.waitFor(15,TimeUnit.SECONDS)){
                child.destroyForcibly();
                child.waitFor(5,TimeUnit.SECONDS);
                throw new AssertionError("test-owned lock holder timed out");
            }
            require(child.exitValue()==0,"test lock child failed");
        }
    }

    @FunctionalInterface
    private interface DeniedAction{void run()throws Exception;}
    private static void expectDenied(
        DeniedAction action,String reason
    )throws Exception{
        try{
            action.run();
            throw new AssertionError("expected refusal "+reason);
        }catch(IOException expected){
            require(expected.getMessage().contains(reason),
                "incorrect refusal "+expected+" wanted "+reason);
        }
    }

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")?
                "java.exe":"java").toString();
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private R65DeferredSessionSaveDispatchTest(){}
}
