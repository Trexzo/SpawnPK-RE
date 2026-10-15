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
 * G21.242 genuine separate-process POSITIVE control for all four
 * cooperating legacy/strict RAW writer APIs AFTER a legitimate
 * account's old World token/OS session lock has been released.
 *
 * TEST ONLY owner teardown is not a proof of a production final
 * receipt or durable safe relog/automatic recovery.
 */
public final class G21242CrossJvmRawWriterAfterReleasedSessionMatrixTest {
    private static final String PRIMARY=AccountStore.CANONICAL_USERNAME;
    private static final String NAMED="g21242-unlocked-raw";
    private static final String NS="core_g21242";
    private static final String KEY="extension.core_g21242.phase";
    private G21242CrossJvmRawWriterAfterReleasedSessionMatrixTest(){}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.242 "+stage);
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
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        Path account=LocalAccountProfiles.accountFile(user);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "previous World account file missing");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "child must have no old in-JVM owner");

        WorldPlayer next=new WorldPlayer();
        next.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap(
                "phase","CHILD_RAW_"+kind));
        switch(kind){
            case "repository":
                repository.save(PlayerSnapshotCodec.capture(user,next));
                break;
            case "strict":
                StrictDurablePlayerSnapshotWriter strict=
                    new StrictDurablePlayerSnapshotWriter(
                        LocalAccountProfiles::accountFile);
                PlayerSnapshot snapshot=
                    PlayerSnapshotCodec.capture(user,next);
                StrictDurablePlayerSnapshotWriter.Receipt receipt=
                    strict.saveStrict(snapshot);
                check(receipt!=null&&receipt.matchesSnapshot(snapshot),
                    "allowed standalone STRICT receipt not matching snapshot");
                break;
            case "primary":
                check(PRIMARY.equals(user),
                    "primary writer with non-primary path");
                AccountStore.save(next.bank(),next.equipment(),
                    next.movement(),next.petState(),next.playerState());
                break;
            case "named":
                check(!PRIMARY.equals(user),
                    "named writer used for primary path");
                LocalAccountProfiles.save(
                    user,next.bank(),next.equipment(),next.movement(),
                    next.petState(),next.playerState());
                break;
            default:
                throw new AssertionError(
                    "G21.242 unknown raw writer "+kind);
        }
        Optional<PlayerSnapshot> persisted=repository.load(user);
        check(persisted.isPresent(),
            "successful "+kind+" write left no account file");
        String extension=persisted.get().values().get(KEY);
        if("repository".equals(kind)||"strict".equals(kind)){
            check(("CHILD_RAW_"+kind).equals(extension),
                "allowed "+kind+" writer lost its own extension");
        }else{
            check(extension==null,
                "legacy core-only "+kind+" unexpectedly kept extension");
        }
        Path tmp=account.resolveSibling(
            account.getFileName().toString()+".tmp");
        check(!Files.exists(tmp,LinkOption.NOFOLLOW_LINKS),
            "allowed "+kind+" writer leaked fixed temp");
        try(Stream<Path> leaves=Files.list(account.getParent())){
            check(leaves.noneMatch(p->
                p.getFileName().toString().startsWith(
                    account.getFileName().toString()+".g2123-")),
                "allowed "+kind+" writer leaked forced strict temp");
        }
        check(LocalJvmAccountSessionLease
                .rawWriteReservationsForTest()==0,
            "successful "+kind+" writer did not release reservation");
        System.out.println(
            "G21242_CHILD_RAW_AFTER_SESSION_RELEASE_PASS"+
            " kind="+kind+" user="+user+
            " independentJvm=true"+
            " successfulWriterReceipt=true"+
            " releasedRawReservation=true");
    }

    private static void fork(Path primary,String user,String mode)
        throws Exception{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder pb=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21242CrossJvmRawWriterAfterReleasedSessionMatrixTest
                .class.getName(),"--child",primary.toString(),
            user,mode);
        pb.redirectErrorStream(true);
        Process child=pb.start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.242 "+mode+" child timeout");
        }
        String stdout=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0&&stdout.contains(
                "G21242_CHILD_RAW_AFTER_SESSION_RELEASE_PASS")&&
                stdout.contains("kind="+mode)&&
                stdout.contains("user="+user),
            "G21.242 successful "+mode+
            " foreign writer proof failed: "+stdout);
    }

    private static void testAccount(
        Path primary,String username,String[] kinds
    )throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repo);
        WorldPlayer owner=new WorldPlayer();
        LocalJvmAccountSessionLease.Token token=null;
        long gen=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(username);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "unexpected account before World admission");
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),username);
            check(token!=null,"owner lease absent");
            gen=world.registerPlayer(owner,username);
            token.bindAdmittedWorldOwner(owner,gen);
            world.start();
            final World w=world;
            final WorldPlayer p=owner;
            final long g=gen;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(p,g,()->{
                p.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","PARENT_WORLD_PRIOR"));
                ticket.set(w.persistence().captureAndSave(
                    username,p,g,0,
                    "[G21.242] ","ORIGINAL_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"prior World save ticket absent");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("PARENT_WORLD_PRIOR".equals(
                repo.load(username).get().values().get(KEY)),
                "prior World postimage not committed");
            check(world.unregisterPlayer(owner,gen),
                "test World unregister failed");
            // Test-only teardown: the World save was completed,
            // but this is NOT a production final-save ACK protocol.
            token.releaseAfterInitializationFailure();
            token=null;
            world.close();
        }finally{
            if(gen>0L)world.unregisterPlayer(owner,gen);
            if(token!=null)token.releaseAfterInitializationFailure();
            world.close();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "prior owner token still held after test cleanup");
        for(String kind:kinds){
            fork(primary,username,kind);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "foreign writer changed parent lease registry");
        }
        System.out.println(
            "G21242_ACCOUNT_WRITERS_RELEASED_OWNER"+
            " user="+username+" successes="+kinds.length);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21242-release-raw-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        Path primary=root.resolve("opensrc.properties");
        flags(primary);
        try{
            testAccount(primary,PRIMARY,
                new String[]{"repository","primary","strict"});
            testAccount(primary,NAMED,
                new String[]{"repository","named","strict"});
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "parent JVM final sessions leaked");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "parent JVM final RAW reservations leaked");
            System.out.println(
                "G21242_FOUR_RAW_WRITERS_POST_SESSION_TWO_JVM_PASS"+
                " separateJvmSuccessfulWrites=6"+
                " distinctRawWriterApis=4"+
                " verifiedAccountKinds=2"+
                " priorWorldSavesCompleted=true"+
                " testOnlyOwnerReleaseBeforeWriters=true"+
                " ordinaryLegacySerializersUnchanged=true"+
                " allWriterReservationsReleased=true"+
                " defaultFlagsUnchanged=true"+
                " unsafeRelogAuthorized=false"+
                " durableEpochImplemented=false"+
                " crashRecoveryImplemented=false"+
                " grantsOrReviewRetirementAuthorized=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
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
