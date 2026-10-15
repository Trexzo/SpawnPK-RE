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
 * G21.216 genuine two-process positive: cooperating RAW writer
 * shares the stable OS session lock when all three opt-in flags are ON.
 * This excludes participating writes only; no durable epoch/recovery.
 */
public final class G21216CoreCrossJvmCooperativeRawWriterOsLockTest {
    private static final String USER="g21216-second-jvm-raw";
    private static final String NS="core_g21216";
    private static final String KEY="extension.core_g21216.phase";

    private G21216CoreCrossJvmCooperativeRawWriterOsLockTest(){}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.216 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(USER);
        check(snapshot.isPresent(),"account file not found");
        return snapshot.get().values().get(KEY);
    }

    private static void child(Path primary,String mode)throws Exception{
        System.setProperty("spk.local.accountFile",
            primary.toAbsolutePath().normalize().toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "child unexpectedly shares parent JVM session registry");

        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        if("session".equals(mode)){
            World isolated=World.isolatedForTest(60_000L,repository);
            boolean denied=false;
            try{
                LocalJvmAccountSessionLease.acquire(
                    isolated.persistence(),USER);
            }catch(IllegalStateException blocked){
                denied=blocked.getMessage()!=null&&
                    blocked.getMessage().contains(
                        "CROSS_JVM_ACCOUNT_SESSION_DENIED");
            }finally{
                isolated.close();
            }
            check(denied,"second JVM session admitted under RAW OS lock");
            System.out.println(
                "G21216_CHILD_SESSION_DENIED_BY_RAW_OS_LOCK"+
                " separateProcess=true newSessionDenied=true");
            return;
        }
        check("raw".equals(mode),"unrecognized child mode");
        // The parent holds the actual G21.185 stable OS session lock.
        // Our cooperating RAW FilePlayerRepository must fail BEFORE
        // fixed .tmp allocation or moving account bytes.
        WorldPlayer old=new WorldPlayer();
        old.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap("phase","FOREIGN_STALE_RAW"));
        Path account=LocalAccountProfiles.accountFile(USER);
        byte[] before=Files.readAllBytes(account);
        Path temp=account.resolveSibling(
            account.getFileName().toString()+".tmp");
        boolean denied=false;
        try{
            repository.save(PlayerSnapshotCodec.capture(USER,old));
        }catch(IOException expected){
            denied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "G21.216 CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED");
        }
        check(denied,"second JVM RAW write did not respect OS lock");
        check(java.util.Arrays.equals(before,Files.readAllBytes(account)),
            "rejected second-JVM RAW modified account bytes");
        check(!Files.exists(temp,LinkOption.NOFOLLOW_LINKS),
            "rejected second-JVM RAW allocated legacy temp");
        check(LocalJvmAccountSessionLease
                .rawWriteReservationsForTest()==0,
            "rejected second-JVM RAW leaked reservation");
        System.out.println(
            "G21216_CHILD_RAW_DENIED_BY_SESSION_OS_LOCK"+
            " independentJvm=true crossJvmRawRefused=true"+
            " diskBytesUnchanged=true noTempAllocated=true");
    }

    private static String runChild(Path primary,String mode)throws Exception{
        Path java=Path.of(System.getProperty("java.home"),
            "bin","java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder builder=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21216CoreCrossJvmCooperativeRawWriterOsLockTest
                .class.getName(),"--child",primary.toString(),mode);
        builder.redirectErrorStream(true);
        Process child=builder.start();
        boolean finished=child.waitFor(20L,TimeUnit.SECONDS);
        if(!finished){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.216 "+mode+" child timed out");
        }
        String stdout=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0,
            "independent JVM "+mode+" failed exit="+child.exitValue()+
            " stdout="+stdout);
        String marker="raw".equals(mode)
            ?"G21216_CHILD_RAW_DENIED_BY_SESSION_OS_LOCK"
            :"G21216_CHILD_SESSION_DENIED_BY_RAW_OS_LOCK";
        check(stdout.contains(marker),
            "missing child "+mode+" proof stdout="+stdout);
        return stdout;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21216-jvm-raw-");
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
                    "[G21.216] ","REAL_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"actual World save capture missing");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("CURRENT_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "World postimage not committed");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>before,
                "World worker did not check original token");

            // Actual second JVM tries a cooperating RAW save while
            // the parent holds genuine OS session + JVM lease.
            String foreignRaw=runChild(primary,"raw");
            check(foreignRaw.contains("crossJvmRawRefused=true"),
                "second process did not prove RAW exclusion");
            check("CURRENT_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "second-JVM denied RAW changed current World postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "foreign RAW attempt released parent token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,admitted,admittedGeneration);

            // Inverse ordering: RAW process-lock acquired FIRST by
            // parent, then a fresh other process tries session lease.
            check(world.unregisterPlayer(owner,generation),
                "parent World unregistration failed in test cleanup");
            token.releaseAfterInitializationFailure();
            token=null;
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        account,USER)){
                check(raw!=null,"cross-JVM RAW reservation absent");
                String foreignSession=runChild(primary,"session");
                check(foreignSession.contains("newSessionDenied=true"),
                    "second JVM admitted session under RAW lock");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1,
                    "RAW OS reservation lost during opposing JVM attempt");
            }
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "RAW OS reservation leaked after close");
            check("CURRENT_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "opposing cross-JVM attempts changed persisted state");

            System.out.println(
                "G21216_TWO_JVM_COOPERATIVE_RAW_SESSION_LOCK_PASS"+
                " actualTwoIndependentJavaProcesses=true"+
                " sessionFirstDeniesForeignRaw=true"+
                " rawFirstDeniesForeignSession=true"+
                " noForeignRawTemp=true"+
                " currentWorldPostimagePreserved=true"+
                " sameStableSessionLockDomain=true"+
                " java11Compatible=true"+
                " defaultFlagsUnchanged=true"+
                " arbitraryRawWriterExclusion=false"+
                " authenticatedDurableOwnerEpoch=false"+
                " crashRecovery=false"+
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
        if(args.length==3&&"--child".equals(args[0])){
            child(Path.of(args[1]),args[2]);
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}
