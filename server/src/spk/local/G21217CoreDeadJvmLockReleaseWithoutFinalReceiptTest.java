package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.217 TEST ONLY: process death releases the G21.185 OS session
 * lock without proving its formerly admitted owner's final postimage.
 *
 * This is deliberately NOT crash-safe recovery or positive admission.
 * It demonstrates the absence of a durable owner/final-save witness.
 */
public final class G21217CoreDeadJvmLockReleaseWithoutFinalReceiptTest {
    private static final String USER="g21217-crashed-owner";
    private static final String NS="core_g21217";
    private static final String KEY="extension.core_g21217.phase";
    private static final String READY="OWNER_UNSAVED_NEWER_VOLATILE_STATE";
    private G21217CoreDeadJvmLockReleaseWithoutFinalReceiptTest(){}

    private static void check(boolean condition,String stage){
        if(!condition)throw new AssertionError("G21.217 "+stage);
    }

    private static String disk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> saved=repository.load(USER);
        check(saved.isPresent(),"account file missing");
        return saved.get().values().get(KEY);
    }

    private static void child(Path root,Path ready)throws Exception{
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");

        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer owner=new WorldPlayer();
        LocalJvmAccountSessionLease.Token token=
            LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
        check(token!=null,"crash fixture did not acquire session token");
        long generation=world.registerPlayer(owner,USER);
        token.bindAdmittedWorldOwner(owner,generation);
        world.start();

        final World realWorld=world;
        final WorldPlayer live=owner;
        final long originalGeneration=generation;
        AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
            new AtomicReference<>();
        world.submitAndWait(live,originalGeneration,()->{
            live.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(
                    "phase","LAST_CONFIRMED_ACCOUNT_POSTIMAGE"));
            ticket.set(realWorld.persistence().captureAndSave(
                USER,live,originalGeneration,0,
                "[G21.217] ","BASELINE_CONFIRMED_SAVE"));
        },5_000L);
        check(ticket.get()!=null,"baseline ticket missing");
        ticket.get().completion.get(10L,TimeUnit.SECONDS);
        check("LAST_CONFIRMED_ACCOUNT_POSTIMAGE".equals(
            disk(repository)),"baseline file not committed");

        // The old owner has further live state that has NEVER been
        // captured for a final save. Deliberately no final ticket,
        // no release, no unregister: the parent forcibly kills us.
        world.submitAndWait(live,originalGeneration,()->{
            live.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(
                    "phase","UNSAVED_NEWER_VOLATILE_STATE"));
        },5_000L);
        check(LocalJvmAccountSessionLease.activeCountForTest()==1,
            "child JVM failed to retain old owner");
        LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
            LocalAccountProfiles.accountFile(USER),live,originalGeneration);
        Files.writeString(ready,READY,StandardCharsets.UTF_8);
        System.out.println(
            "G21217_CHILD_SESSION_HELD_UNSAVED_STATE_READY"+
            " lastCompletedSaveOlder=true"+
            " finalSaveNotIssued=true");
        System.out.flush();

        // Keep token and its OS FileLock open until forcibly killed.
        for(;;)Thread.sleep(1_000L);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21217-crash-lock-");
        String[] properties={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] prior=new String[properties.length];
        for(int i=0;i<properties.length;i++)
            prior[i]=System.getProperty(properties[i]);
        System.setProperty(properties[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(properties[1],"true");
        System.setProperty(properties[2],"true");
        System.setProperty(properties[3],"true");

        Process child=null;
        World nextWorld=null;
        LocalJvmAccountSessionLease.Token nextToken=null;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "test account unexpectedly existed");
            Path ready=root.resolve("child-crash-ready.txt");
            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder process=new ProcessBuilder(
                java.toString(),"-cp",
                System.getProperty("java.class.path"),
                G21217CoreDeadJvmLockReleaseWithoutFinalReceiptTest
                    .class.getName(),
                "--child",root.toString(),ready.toString());
            process.redirectErrorStream(true);
            child=process.start();

            // Bounded wait for the independent JVM's *actual* bound
            // World owner with a completed older disk snapshot and
            // newer unsaved in-memory state.
            boolean signalled=false;
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(20L);
            while(System.nanoTime()<deadline){
                if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)&&
                   READY.equals(Files.readString(
                       ready,StandardCharsets.UTF_8))){
                    signalled=true;
                    break;
                }
                if(!child.isAlive())break;
                Thread.sleep(50L);
            }
            check(signalled,
                "separate JVM never signalled unconfirmed live owner");
            check(child.isAlive(),
                "crash fixture process already exited");
            check("LAST_CONFIRMED_ACCOUNT_POSTIMAGE".equals(
                disk(repository)),"old postimage lost while child alive");

            nextWorld=World.isolatedForTest(60_000L,repository);
            boolean firstDenied=false;
            try{
                LocalJvmAccountSessionLease.Token invalid=
                    LocalJvmAccountSessionLease.acquire(
                        nextWorld.persistence(),USER);
                if(invalid!=null)
                    invalid.releaseAfterInitializationFailure();
            }catch(IllegalStateException expected){
                firstDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "CROSS_JVM_ACCOUNT_SESSION_DENIED");
            }
            check(firstDenied,
                "child OS session lock did not deny a live competitor");

            // HARD PROCESS DEATH: bypass all final-save, close and
            // release handlers. The kernel releases the OS FileLock.
            child.destroyForcibly();
            check(child.waitFor(10L,TimeUnit.SECONDS),
                "hard-killed child JVM did not terminate");
            check(!child.isAlive(),"child JVM still alive");

            // Without any durable owner generation/final receipt
            // evidence, current opt-in admission can reacquire solely
            // because the OS lock vanished after process death.
            nextToken=LocalJvmAccountSessionLease.acquire(
                nextWorld.persistence(),USER);
            check(nextToken!=null,
                "post-death OS session lock remained held");
            check("LAST_CONFIRMED_ACCOUNT_POSTIMAGE".equals(
                disk(repository)),
                "old postimage unexpectedly became newest volatile data");
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "stable G21.185 OS lock file unexpectedly disappeared");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "parent post-death test token not held");

            System.out.println(
                "G21217_DEAD_JVM_OS_LOCK_RELEASE_WITHOUT_FINAL_RECEIPT_PASS"+
                " trueSeparateJvm=true"+
                " originalSessionLockDeniedLiveCompetitor=true"+
                " oldWorldPostimageCommitted=true"+
                " newerVolatileOwnerStateUnconfirmed=true"+
                " actualOldProcessForciblyKilled=true"+
                " osSessionLockAcquirableAfterDeath=true"+
                " recoveredFinalSaveReceipt=false"+
                " durableOwnerEpoch=false"+
                " globalWriterFencing=false"+
                " safeRelogAuthority=false"+
                " activeReviewMarkerRetirement=false");
        }finally{
            try{
                if(nextToken!=null)
                    nextToken.releaseAfterInitializationFailure();
                if(nextWorld!=null)nextWorld.close();
                if(child!=null&&child.isAlive()){
                    child.destroyForcibly();
                    child.waitFor(5L,TimeUnit.SECONDS);
                }
            }finally{
                for(int i=0;i<properties.length;i++){
                    if(prior[i]==null)
                        System.clearProperty(properties[i]);
                    else
                        System.setProperty(properties[i],prior[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path path:entries.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]));
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}
