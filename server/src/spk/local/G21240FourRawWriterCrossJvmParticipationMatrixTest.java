package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.240 — real forked-JVM four-entry cooperative RAW OS-lock matrix.
 *
 * Checks primary and named account routes against the SAME account
 * currently owned by another process: repository.save, primary
 * AccountStore.save, named LocalAccountProfiles.save and standalone
 * StrictDurablePlayerSnapshotWriter.saveStrict.
 *
 * Test only. The system's three experimental flags remain DEFAULT OFF.
 * This proves voluntary writer participation, not durable epoch,
 * arbitrary writer fencing or safe relog/review authority.
 */
public final class G21240FourRawWriterCrossJvmParticipationMatrixTest {
    private static final String PRIMARY=AccountStore.CANONICAL_USERNAME;
    private static final String NAMED="g21240-named-account";
    private static final String NAMESPACE="core_g21240";
    private static final String KEY="extension.core_g21240.phase";

    private G21240FourRawWriterCrossJvmParticipationMatrixTest(){}

    private static void check(boolean value,String stage){
        if(!value)throw new AssertionError("G21.240 "+stage);
    }

    private static String disk(FilePlayerRepository repository,String user)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repository.load(user);
        check(snapshot.isPresent(),"missing persisted "+user);
        return snapshot.get().values().get(KEY);
    }

    private static void flags(Path primaryPath){
        System.setProperty("spk.local.accountFile",
            primaryPath.toAbsolutePath().normalize().toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
    }

    private static void child(Path primaryPath,String user,String kind)
        throws Exception{
        flags(primaryPath);
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "foreign JVM should have empty local session map");

        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        Path account=LocalAccountProfiles.accountFile(user);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "foreign JVM fixture file missing");
        byte[] original=Files.readAllBytes(account);
        Path legacyTmp=account.resolveSibling(
            account.getFileName().toString()+".tmp");

        WorldPlayer old=new WorldPlayer();
        old.snapshotExtensions().replaceNamespace(
            NAMESPACE,Collections.singletonMap(
                "phase","FOREIGN_UNAUTHORIZED_RAW"));
        boolean denied=false;
        try{
            switch(kind){
                case "repository":
                    repository.save(PlayerSnapshotCodec.capture(user,old));
                    break;
                case "primary":
                    check(PRIMARY.equals(user),
                        "primary AccountStore called with named user");
                    AccountStore.save(old.bank(),old.equipment(),
                        old.movement(),old.petState(),old.playerState());
                    break;
                case "named":
                    check(!PRIMARY.equals(user),
                        "named LocalAccountProfiles requires non-primary");
                    LocalAccountProfiles.save(
                        user,old.bank(),old.equipment(),old.movement(),
                        old.petState(),old.playerState());
                    break;
                case "strict":
                    new StrictDurablePlayerSnapshotWriter(
                        LocalAccountProfiles::accountFile).saveStrict(
                            PlayerSnapshotCodec.capture(user,old));
                    break;
                default:throw new AssertionError(
                    "G21.240 unsupported child kind "+kind);
            }
        }catch(IOException expected){
            denied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "G21.216 CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED");
        }
        check(denied,"foreign "+kind+" writer did not honor OS lock");
        check(Arrays.equals(original,Files.readAllBytes(account)),
            "foreign "+kind+" writer replaced original bytes");
        check(!Files.exists(legacyTmp,LinkOption.NOFOLLOW_LINKS),
            "foreign "+kind+" writer left historical temp");
        check("WORLD_OWNED_CURRENT".equals(disk(repository,user)),
            "foreign "+kind+" writer changed World extension");
        check(LocalJvmAccountSessionLease
                .rawWriteReservationsForTest()==0,
            "foreign "+kind+" writer leaked JVM reservation");

        // Standalone strict must also refuse BEFORE its unique G21.23
        // forced-temp creation; there should be no strict temp.
        try(Stream<Path> paths=Files.list(account.getParent())){
            check(paths.noneMatch(p->
                p.getFileName().toString().startsWith(
                    account.getFileName().toString()+".g2123-")),
                "strict writer created forced temp under denied lock");
        }

        System.out.println(
            "G21240_CHILD_COOPERATING_RAW_OS_LOCK_DENIED"+
            " writer="+kind+" user="+user+
            " independentJvm=true bytesUnchanged=true"+
            " noRawTemp=true");
    }

    private static String fork(Path primaryPath,String user,String kind)
        throws Exception{
        String exe=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        Path java=Path.of(System.getProperty("java.home"),"bin",exe);
        ProcessBuilder process=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21240FourRawWriterCrossJvmParticipationMatrixTest
                .class.getName(),"--child",primaryPath.toString(),
            user,kind);
        process.redirectErrorStream(true);
        Process child=process.start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.240 foreign "+kind+" JVM timed out");
        }
        String output=new String(child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0,
            "foreign JVM "+kind+" exit="+child.exitValue()+
            " stdout="+output);
        check(output.contains(
            "G21240_CHILD_COOPERATING_RAW_OS_LOCK_DENIED")&&
            output.contains("writer="+kind)&&
            output.contains("user="+user),
            "foreign JVM did not confirm "+kind+" proof "+output);
        return output;
    }

    private static void oneAccount(Path primaryPath,String user,
        String[] modes)throws Exception{
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer owner=new WorldPlayer();
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(user);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "unexpected preexisting account "+user);
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),user);
            check(token!=null,"opt-in session token absent "+user);
            generation=world.registerPlayer(owner,user);
            token.bindAdmittedWorldOwner(owner,generation);
            world.start();

            final World activeWorld=world;
            final WorldPlayer admitted=owner;
            final long admittedGeneration=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            int guardBefore=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            world.submitAndWait(admitted,admittedGeneration,()->{
                admitted.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","WORLD_OWNED_CURRENT"));
                ticket.set(activeWorld.persistence().captureAndSave(
                    user,admitted,admittedGeneration,0,
                    "[G21.240] ","GUARDED_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"World ticket absent "+user);
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("WORLD_OWNED_CURRENT".equals(disk(repository,user)),
                "World snapshot not persisted "+user);
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>guardBefore,
                "World queued owner witness missing "+user);

            byte[] expected=Files.readAllBytes(account);
            for(String mode:modes){
                String stdout=fork(primaryPath,user,mode);
                check(stdout.contains("bytesUnchanged=true"),
                    "foreign "+mode+" missing negative proof");
                check(Arrays.equals(expected,Files.readAllBytes(account)),
                    "foreign "+mode+" changed account bytes");
                check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "foreign "+mode+" changed parent's live owner");
                LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                    account,admitted,admittedGeneration);
            }
            System.out.println("G21240_ACCOUNT_WRITER_MATRIX"+
                " user="+user+" foreignModes="+modes.length+
                " originalOwnerStillBound=true");
        }finally{
            if(generation>0L)
                world.unregisterPlayer(owner,generation);
            if(token!=null)
                token.releaseAfterInitializationFailure();
            world.close();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21240-four-raw-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] before=new String[props.length];
        for(int i=0;i<props.length;i++)
            before[i]=System.getProperty(props[i]);
        Path primaryPath=root.resolve("opensrc.properties");
        flags(primaryPath);
        try{
            oneAccount(primaryPath,PRIMARY,
                new String[]{"repository","primary","strict"});
            oneAccount(primaryPath,NAMED,
                new String[]{"repository","named","strict"});
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "after matrix parent JVM holds leaked account tokens");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "after matrix parent holds leaked RAW reservation");
            System.out.println(
                "G21240_FOUR_RAW_WRITERS_TWO_JVM_MATRIX_PASS"+
                " testedWriterKinds=4"+
                " totalSeparateJvmChildAttempts=6"+
                " realWorldOwnerGenerations=2"+
                " allForeignWritesDeniedBeforeTemp=true"+
                " allWorldPostimagesPreserved=true"+
                " java11Compatible=true"+
                " cooperativeOnly=true"+
                " safeCrashRecovery=false"+
                " durableOwnerEpoch=false"+
                " arbitraryExternalWriterFence=false");
        }finally{
            for(int i=0;i<props.length;i++){
                if(before[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],before[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Path.of(args[1]),args[2],args[3]);
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}
