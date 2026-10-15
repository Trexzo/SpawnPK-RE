package spk.local;

import java.io.File;
import java.io.IOException;
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
 * G21.215 historical negative preserved in exact parent #2676.
 * Descendant G21.216: the second JVM explicitly opts OUT of
 * cross-JVM RAW lock during its write. Unsupported legacy bypass
 * remains, never a valid live runtime toggle or recovery authority.
 */
public final class G21215CoreSecondJvmRawWriterBypassesSessionLockTest {
    private static final String USER="g21215-second-jvm-raw";
    private static final String NS="core_g21215";
    private static final String KEY="extension.core_g21215.phase";

    private G21215CoreSecondJvmRawWriterBypassesSessionLockTest(){}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.215 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(USER);
        check(snapshot.isPresent(),"account file not found");
        return snapshot.get().values().get(KEY);
    }

    private static void child(Path primary)throws Exception{
        System.setProperty("spk.local.accountFile",
            primary.toAbsolutePath().normalize().toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "child unexpectedly shares the parent JVM lease registry");

        // Separate process, same exact account path. An ordinary NEW
        // cooperating session cannot join the existing OS session lock.
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World isolated=World.isolatedForTest(60_000L,repository);
        boolean newSessionDenied=false;
        try{
            LocalJvmAccountSessionLease.acquire(
                isolated.persistence(),USER);
        }catch(IllegalStateException blocked){
            newSessionDenied=blocked.getMessage()!=null&&
                blocked.getMessage().contains(
                    "CROSS_JVM_ACCOUNT_SESSION_DENIED");
        }finally{
            isolated.close();
        }
        check(newSessionDenied,
            "cross-JVM session lock failed to deny second session");

        // G21.215 parent #2676 retains original ALL-flags-ON
        // second-JVM RAW bypass evidence. After G21.216, this test
        // explicitly disables the cross-JVM RAW opt-in in the child
        // ONLY to characterize the unsupported opt-out hazard.
        // Never toggle protection while sessions are live.
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"false");
        WorldPlayer stale=new WorldPlayer();
        stale.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap("phase","FOREIGN_STALE_RAW"));
        try{
            repository.save(PlayerSnapshotCodec.capture(USER,stale));
        }finally{
            System.setProperty(
                LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        }
        check("FOREIGN_STALE_RAW".equals(onDisk(repository)),
            "second-JVM RAW save did not replace account postimage");
        System.out.println(
            "G21215_CHILD_CROSS_JVM_SESSION_DENIED_RAW_OPT_OUT_ALLOWED"+
            " independentJvm=true processSessionDenied=true"+
            " rawOsLockOptOutSucceeded=true");
    }

    private static String runChild(Path primary)throws Exception{
        Path java=Path.of(System.getProperty("java.home"),
            "bin","java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder builder=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21215CoreSecondJvmRawWriterBypassesSessionLockTest
                .class.getName(),"--child",primary.toString());
        builder.redirectErrorStream(true);
        Process child=builder.start();
        boolean finished=child.waitFor(20L,TimeUnit.SECONDS);
        if(!finished){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.215 child timed out");
        }
        String stdout=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0,
            "independent JVM failed exit="+child.exitValue()+
            " stdout="+stdout);
        check(stdout.contains(
            "G21215_CHILD_CROSS_JVM_SESSION_DENIED_RAW_OPT_OUT_ALLOWED"),
            "missing child process proof stdout="+stdout);
        return stdout;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21215-jvm-raw-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        Path primary=root.resolve("opensrc.properties");
        System.setProperty(keys[0],primary.toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");

        World world=null;
        WorldPlayer owner=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "unexpected preexisting named account");
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            owner=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"opt-in JVM account session token missing");
            generation=world.registerPlayer(owner,USER);
            token.bindAdmittedWorldOwner(owner,generation);
            world.start();
            final World live=world;
            final WorldPlayer admitted=owner;
            final long admittedGeneration=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket>
                ticket=new AtomicReference<>();
            int before=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            world.submitAndWait(admitted,admittedGeneration,()->{
                admitted.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","CURRENT_WORLD_POSTIMAGE"));
                ticket.set(live.persistence().captureAndSave(
                    USER,admitted,admittedGeneration,0,
                    "[G21.215] ","REAL_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"actual World save capture missing");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("CURRENT_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "World postimage not committed");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>before,
                "World worker did not check original token");

            // Child proves real separate-process session exclusion first,
            // then deliberately opts OUT of RAW OS coordination.
            // The exact parent #2676 records the original all-ON gap.
            String output=runChild(primary);
            check(output.contains("rawOsLockOptOutSucceeded=true"),
                "second JVM did not confirm RAW overwrite");
            check("FOREIGN_STALE_RAW".equals(onDisk(repository)),
                "parent did not observe second-JVM stale RAW postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "foreign RAW writer unexpectedly released parent token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,admitted,admittedGeneration);
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "parent leaked raw reservation");

            System.out.println(
                "G21215_SECOND_JVM_RAW_OPT_OUT_BYPASS_PASS"+
                " realTwoJvmProcessBoundary=true"+
                " crossJvmSessionFileLockHeld=true"+
                " secondarySessionDenied=true"+
                " secondJvmRawOverwriteSucceeded=true"+
                " originalParentWorldLeaseStillHeld=true"+
                " java11Compatible=true"+
                " childRawCrossJvmOptOut=true"+
                " crashDurableOwnerEpoch=false"+
                " activeMarkerRetirement=false");
        }finally{
            try{
                if(world!=null&&owner!=null&&generation>0L)
                    world.unregisterPlayer(owner,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(old[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],old[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path path:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(Path.of(args[1]));
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}
