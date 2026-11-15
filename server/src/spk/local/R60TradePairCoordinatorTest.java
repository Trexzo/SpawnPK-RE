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
import java.util.concurrent.TimeUnit;

/**
 * R60: actual independent JVM holds an OS FileLock while the test JVM
 * tries to read and register. Also inject crash-after-manifest-before-index.
 */
public final class R60TradePairCoordinatorTest {
    private static final UUID PRIMARY=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );
    private static final UUID GAP=UUID.fromString(
        "77777777-8888-4999-aaaa-bbbbbbbbbbbb"
    );
    private static final UUID STALE=UUID.fromString(
        "99999999-aaaa-4bbb-8ccc-dddddddddddd"
    );

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r60-cross-jvm-lock-");
        try{
            Path journal=scratch.resolve("pair-journal");
            Files.createDirectory(journal);

            deny(journal,"opensrc");
            R60TradePairCoordinator.initializeEmpty(journal);
            R59TradePairIntentIndex.Result clear=
                R60TradePairCoordinator.inspect(journal,"opensrc");
            require(clear.status==
                R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR &&
                clear.revision==1L&&!clear.loginAuthorized(),
                "fresh offline index not clear at revision 1");

            Snapshots first=fixture("opensrc","src");
            // Process #2 takes a REAL OS FileLock before process #1 tries
            // to register or admit either participant.
            runWithWorker(scratch,journal,"hold",(process)->{
                deny(journal,"opensrc");
                deny(journal,"outsider");
                expectIOException(()->R60TradePairCoordinator.registerPrepared(
                    journal,1L,Collections.emptyList(),PRIMARY,
                    first.beforeA,first.afterA,
                    first.beforeB,first.afterB),
                    "R60_LOCK_BUSY_OR_OVERLAPPING");
                require(!Files.exists(journal.resolve(name(PRIMARY))),
                    "locked registration created a partial intent");
            });

            long rev=R60TradePairCoordinator.registerPrepared(
                journal,1L,Collections.emptyList(),PRIMARY,
                first.beforeA,first.afterA,
                first.beforeB,first.afterB
            );
            require(rev==2L,"pair not indexed after lock release");
            require(indexed(journal,"opensrc",PRIMARY,2L),
                "first participant lookup missing");
            require(indexed(journal,"src",PRIMARY,2L),
                "counterparty lookup missing");
            require(R60TradePairCoordinator.inspect(
                journal,"outsider").status==
                    R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR,
                "unrelated offline index lookup unexpectedly blocked");

            Snapshots stale=fixture("third_a","third_b");
            expectIOException(()->R60TradePairCoordinator.registerPrepared(
                journal,1L,Arrays.asList(PRIMARY),STALE,
                stale.beforeA,stale.afterA,stale.beforeB,stale.afterB
            ),"stale index revision");
            require(!Files.exists(journal.resolve(name(STALE))),
                "stale register attempt published a manifest");

            // Worker creates a real R57 PREPARED manifest while holding
            // exclusive OS lock, then pauses BEFORE R59 index replacement.
            // Reader fails CLOSED while writer is active. When the child
            // exits without indexing, a fresh review also fails CLOSED.
            runWithWorker(scratch,journal,"manifest-gap",(process)->{
                deny(journal,"opensrc");
                deny(journal,"outsider");
                require(Files.exists(journal.resolve(name(GAP))),
                    "other JVM did not publish its prepared intent");
            });

            R59TradePairIntentIndex.Result afterGap=
                R60TradePairCoordinator.inspect(journal,"outsider");
            require(afterGap.status==
                R59TradePairIntentIndex.Status.BLOCK_INDETERMINATE_INDEX,
                "unindexed completed writer gap looked safe");
            require(Files.isRegularFile(journal.resolve(name(GAP))),
                "unindexed crash evidence was removed");

            // No implicit recovery: even after writer exits, a new pair
            // registration must not overwrite or heal the abandoned gap.
            expectIOException(()->R60TradePairCoordinator.registerPrepared(
                journal,2L,Arrays.asList(PRIMARY),STALE,
                stale.beforeA,stale.afterA,stale.beforeB,stale.afterB
            ),"existing/ambiguous participant");
            require(!Files.exists(journal.resolve(name(STALE))),
                "failed incomplete registry admitted new trade");
            require(Files.exists(journal.resolve(name(PRIMARY))) &&
                    Files.exists(journal.resolve(name(GAP))),
                "failure deleted pending journal evidence");

            System.out.println(
                "R60_COOPERATIVE_CROSS_JVM_PAIR_LOCK_PASS "+
                "otherJvmExclusiveLockObserved=true "+
                "busyLoginAndRegistrationFailClosed=true "+
                "noPartialIntentOnBusyLock=true "+
                "staleRevisionRejectedBeforeManifest=true "+
                "bothPlayerLookupsUnderLock=true "+
                "midRegistrationGapBlocksAllAfterWorkerExit=true "+
                "orphanEvidenceNeverAutoDeleted=true "+
                "noLiveLoginTradeSaveHook=true "+
                "noncooperativeWriterAndPowerLossNotProven=true");
        }finally{
            try(java.util.stream.Stream<Path> all=Files.walk(scratch)){
                all.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
            }
        }
    }

    private static void runWithWorker(
        Path scratch,Path journal,String mode,
        WorkerAction action
    )throws Exception{
        Path ready=scratch.resolve("worker-"+mode+".ready");
        Path release=scratch.resolve("worker-"+mode+".release");
        String os=System.getProperty("os.name").toLowerCase();
        String binary=os.contains("win")?"java.exe":"java";
        Path java=Paths.get(System.getProperty("java.home"),
            "bin",binary);
        Process worker=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R60TradePairLockWorker",
            journal.toString(),ready.toString(),release.toString(),mode
        ).redirectErrorStream(true).start();
        try{
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12);
            while(!Files.exists(ready)){
                if(!worker.isAlive())
                    throw new AssertionError("worker exited before lock: "+
                        new String(readAll(worker),StandardCharsets.UTF_8));
                if(System.nanoTime()>deadline)
                    throw new AssertionError(
                        "worker never acquired lock mode="+mode);
                Thread.sleep(20L);
            }
            action.run(worker);
        }finally{
            Files.write(release,new byte[]{1});
            if(!worker.waitFor(12,TimeUnit.SECONDS)){
                worker.destroyForcibly();
                worker.waitFor(5,TimeUnit.SECONDS);
                throw new AssertionError("only test-owned worker did not exit");
            }
            require(worker.exitValue()==0,
                "worker failure mode="+mode+" output="+
                new String(readAll(worker),StandardCharsets.UTF_8));
        }
    }

    private static byte[] readAll(Process process)throws IOException{
        java.io.ByteArrayOutputStream out=
            new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[2048];
        try(java.io.InputStream input=process.getInputStream()){
            int n;
            while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
        }
        return out.toByteArray();
    }

    @FunctionalInterface
    private interface WorkerAction{
        void run(Process worker)throws Exception;
    }

    private static Snapshots fixture(String a,String b)throws Exception{
        WorldPlayer first=new WorldPlayer(),second=new WorldPlayer();
        PlayerSnapshot oldA=PlayerSnapshotCodec.capture(a,first);
        PlayerSnapshot oldB=PlayerSnapshotCodec.capture(b,second);
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        first.bank().spawnItem(995,1,writer);
        second.bank().spawnItem(385,1,writer);
        return new Snapshots(
            oldA,PlayerSnapshotCodec.capture(a,first),
            oldB,PlayerSnapshotCodec.capture(b,second)
        );
    }

    private static final class Snapshots{
        final PlayerSnapshot beforeA,afterA,beforeB,afterB;
        Snapshots(PlayerSnapshot oldA,PlayerSnapshot newA,
            PlayerSnapshot oldB,PlayerSnapshot newB){
            beforeA=oldA;afterA=newA;
            beforeB=oldB;afterB=newB;
        }
    }

    private static void deny(Path root,String account){
        R59TradePairIntentIndex.Result status=
            R60TradePairCoordinator.inspect(root,account);
        require(status.status==
            R59TradePairIntentIndex.Status.BLOCK_INDETERMINATE_INDEX &&
            !status.canProceedToOtherLoginChecks() &&
            !status.loginAuthorized(),
            "coordination gap admitted account="+account+" "+status.reason);
    }

    private static boolean indexed(
        Path root,String account,UUID id,long revision){
        R59TradePairIntentIndex.Result r=
            R60TradePairCoordinator.inspect(root,account);
        return r.status==R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR &&
            r.revision==revision&&r.transactions.size()==1 &&
            id.equals(r.transactions.get(0));
    }

    private static String name(UUID id){
        return "trade-r57-"+id.toString()+".properties";
    }

    @FunctionalInterface
    interface CheckedAction {void run()throws Exception;}
    private static void expectIOException(
        CheckedAction action,String reason
    )throws Exception{
        try{
            action.run();
            throw new AssertionError("expected IOException "+reason);
        }catch(IOException expected){
            require(expected.getMessage().contains(reason),
                "wrong refusal: "+expected+" wanted "+reason);
        }
    }
    private static void require(boolean ok,String failure){
        if(!ok)throw new AssertionError(failure);
    }
    private R60TradePairCoordinatorTest(){}
}

/**
 * A separate REAL JVM. Creates no network sockets or account files. It
 * holds only the test-owned R60 lock until the parent emits release signal.
 */
final class R60TradePairLockWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("args");
        Path root=Paths.get(args[0]),ready=Paths.get(args[1]);
        Path release=Paths.get(args[2]);
        String mode=args[3];
        R60TradePairCoordinator.withExclusive(root,false,()->{
            if("manifest-gap".equals(mode)){
                WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
                PlayerSnapshot oldA=PlayerSnapshotCodec.capture("othera",a);
                PlayerSnapshot oldB=PlayerSnapshotCodec.capture("otherb",b);
                ServerPacketWriter writer=new ServerPacketWriter(
                    new OutboundPacketQueue(),
                    new IsaacCipher(new int[]{7,6,5,4})
                );
                a.bank().spawnItem(995,1,writer);
                b.bank().spawnItem(385,1,writer);
                R57TradePairJournal.writePrepared(
                    root.resolve("trade-r57-"+
                        "77777777-8888-4999-aaaa-bbbbbbbbbbbb.properties"),
                    UUID.fromString(
                        "77777777-8888-4999-aaaa-bbbbbbbbbbbb"),
                    oldA,PlayerSnapshotCodec.capture("othera",a),
                    oldB,PlayerSnapshotCodec.capture("otherb",b)
                );
            }else if(!"hold".equals(mode)){
                throw new IOException("unknown fixture mode");
            }
            Files.write(ready,new byte[]{1});
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12);
            try{
                while(!Files.exists(release)){
                    if(System.nanoTime()>deadline)
                        throw new IOException("test worker release timed out");
                    Thread.sleep(20L);
                }
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException("worker interrupted",interrupted);
            }
            return null;
        });
        System.out.println("R60_LOCK_WORKER_COMPLETED");
    }
}
