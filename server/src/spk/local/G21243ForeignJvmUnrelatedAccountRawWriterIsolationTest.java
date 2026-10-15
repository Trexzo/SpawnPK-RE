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
 * G21.243 — actual second-JVM unrelated-account RAW writers remain
 * functional while first JVM holds genuine other-account session/OS
 * ownership. Source-bound positive non-interference only.
 */
public final class G21243ForeignJvmUnrelatedAccountRawWriterIsolationTest {
    private static final String OWNER=AccountStore.CANONICAL_USERNAME;
    private static final String OTHER="g21243-different-file";
    private static final String NS="core_g21243";
    private static final String KEY="extension.core_g21243.phase";
    private G21243ForeignJvmUnrelatedAccountRawWriterIsolationTest(){}

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.243 "+reason);
    }

    private static void flags(Path primary){
        System.setProperty("spk.local.accountFile",
            primary.toAbsolutePath().normalize().toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
    }

    private static String disk(FilePlayerRepository repo,String user)
        throws Exception{
        Optional<PlayerSnapshot> saved=repo.load(user);
        check(saved.isPresent(),"missing file "+user);
        return saved.get().values().get(KEY);
    }

    private static void child(Path primary)throws Exception{
        flags(primary);
        Path first=LocalAccountProfiles.accountFile(OWNER);
        Path second=LocalAccountProfiles.accountFile(OTHER);
        check(!first.equals(second),"unrelated accounts alias same path");
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        check("PARENT_WORLD_CURRENT".equals(disk(repo,OWNER)),
            "parent account not ready");

        WorldPlayer next=new WorldPlayer();
        next.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap(
                "phase","FOREIGN_OTHER_RAW"));
        PlayerSnapshot raw=PlayerSnapshotCodec.capture(OTHER,next);
        repo.save(raw);
        check("FOREIGN_OTHER_RAW".equals(disk(repo,OTHER)),
            "other-account repository RAW write blocked/lost");

        next.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap(
                "phase","FOREIGN_OTHER_STRICT"));
        PlayerSnapshot strictSnapshot=
            PlayerSnapshotCodec.capture(OTHER,next);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            new StrictDurablePlayerSnapshotWriter(
                LocalAccountProfiles::accountFile).saveStrict(
                    strictSnapshot);
        check(receipt!=null&&receipt.matchesSnapshot(strictSnapshot),
            "other-account strict receipt missing");
        check("FOREIGN_OTHER_STRICT".equals(disk(repo,OTHER)),
            "other-account strict write lost");
        check("PARENT_WORLD_CURRENT".equals(disk(repo,OWNER)),
            "unrelated RAW writer changed parent account file");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "child shares parent in-JVM session state");
        check(LocalJvmAccountSessionLease
                .rawWriteReservationsForTest()==0,
            "child retains RAW reservation");
        System.out.println(
            "G21243_CHILD_UNRELATED_RAW_AND_STRICT_ALLOWED"+
            " realSeparateJvm=true"+
            " differentAccountOsLockPaths=true"+
            " allowedRepositoryAndStrict=true"+
            " parentAccountUnchanged=true");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21243-unrelated-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[props.length];
        for(int i=0;i<props.length;i++)
            old[i]=System.getProperty(props[i]);
        Path primary=root.resolve("opensrc.properties");
        flags(primary);
        World world=null;
        WorldPlayer owner=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repo);
            owner=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),OWNER);
            check(token!=null,"primary owner token missing");
            generation=world.registerPlayer(owner,OWNER);
            token.bindAdmittedWorldOwner(owner,generation);
            world.start();

            final World w=world;
            final WorldPlayer p=owner;
            final long g=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(p,g,()->{
                p.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","PARENT_WORLD_CURRENT"));
                ticket.set(w.persistence().captureAndSave(
                    OWNER,p,g,0,
                    "[G21.243] ","PARENT_OWNED_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"actual World save absent");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("PARENT_WORLD_CURRENT".equals(disk(repo,OWNER)),
                "parent World save not committed");
            Path first=LocalAccountProfiles.accountFile(OWNER);
            Path other=LocalAccountProfiles.accountFile(OTHER);
            check(!first.equals(other),
                "cross-account paths unexpectedly alias");

            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder cmd=new ProcessBuilder(
                java.toString(),"-cp",
                System.getProperty("java.class.path"),
                G21243ForeignJvmUnrelatedAccountRawWriterIsolationTest
                    .class.getName(),"--child",primary.toString());
            cmd.redirectErrorStream(true);
            Process child=cmd.start();
            if(!child.waitFor(20L,TimeUnit.SECONDS)){
                child.destroyForcibly();
                child.waitFor(5L,TimeUnit.SECONDS);
                throw new AssertionError("G21.243 child timed out");
            }
            String out=new String(
                child.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(child.exitValue()==0&&out.contains(
                    "G21243_CHILD_UNRELATED_RAW_AND_STRICT_ALLOWED"),
                "foreign independent RAW control failed: "+out);
            check("PARENT_WORLD_CURRENT".equals(disk(repo,OWNER)),
                "first World postimage changed after foreign child");
            check("FOREIGN_OTHER_STRICT".equals(disk(repo,OTHER)),
                "independent other account postimage missing");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "foreign other account writer revoked original token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                first,owner,generation);

            System.out.println(
                "G21243_FOREIGN_JVM_UNRELATED_RAW_WRITERS_PASS"+
                " independentProcesses=true"+
                " originalWorldOwnerRetained=true"+
                " separateAccountFilesAndOsLocks=true"+
                " foreignRepositoryRawAllowed=true"+
                " foreignStandaloneStrictAllowed=true"+
                " originalWorldPostimagePreserved=true"+
                " sameAccountExclusionStillTestedByG21240=true"+
                " unrelatedAccountBlocking=false"+
                " durableEpoch=false"+
                " automaticRecovery=false"+
                " ACTIVEReviewRetirement=false");
        }finally{
            try{
                if(world!=null&&owner!=null&&generation>0L)
                    world.unregisterPlayer(owner,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<props.length;i++){
                    if(old[i]==null)System.clearProperty(props[i]);
                    else System.setProperty(props[i],old[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path p:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
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
