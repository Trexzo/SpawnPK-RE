package spk.local;

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
 * G21.207 TEST-ONLY characterisation: legacy raw repository.save() can
 * replace a newer guarded World postimage while its original opt-in JVM
 * account lease is STILL bound to the registered player/generation.
 *
 * A PASS proves the intentionally uncoordinated RAW writer gap.
 * It does NOT authorize direct saves, grant recovery, or fix the gap.
 */
public final class G21207CoreRawWriterBypassesHeldWorldLeaseTest {
    private static final String USER = "g21207-raw-writer-bypass";
    private static final String NAMESPACE = "core_g21207";
    private static final String KEY = "extension.core_g21207.phase";
    private G21207CoreRawWriterBypassesHeldWorldLeaseTest() {}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.207 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> loaded = repository.load(USER);
        check(loaded.isPresent(),"account file missing");
        return loaded.get().values().get(KEY);
    }

    private static void run()throws Exception{
        Path root = Files.createTempDirectory("g21207-raw-bypass-");
        String accountProperty = "spk.local.accountFile";
        String jvmLeaseProperty = LocalJvmAccountSessionLease.PROPERTY;
        String crossJvmProperty = LocalCrossJvmAccountSessionLock.PROPERTY;
        String oldAccount = System.getProperty(accountProperty);
        String oldLease = System.getProperty(jvmLeaseProperty);
        String oldCrossJvm = System.getProperty(crossJvmProperty);
        System.setProperty(accountProperty,
            root.resolve("opensrc.properties").toString());
        System.setProperty(jvmLeaseProperty,"true");
        System.setProperty(crossJvmProperty,"false");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token held=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "unexpected pre-existing test account");

            FilePlayerRepository repository = new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            player=new WorldPlayer();
            held=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(held!=null,"expected opt-in session token");
            generation=world.registerPlayer(player,USER);
            held.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World worldRef=world;
            final WorldPlayer owner=player;
            final long ownedGeneration=generation;
            final AtomicReference<PlayerSnapshot> stale=new AtomicReference<>();

            // A legitimate World-owned old snapshot is captured but
            // NOT published. Later the same World owner advances state.
            world.submitAndWait(owner,ownedGeneration,()->{
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","OLDER_CAPTURE"));
                stale.set(PlayerSnapshotCodec.capture(USER,owner));
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","NEWER_WORLD_POSTIMAGE"));
            },5_000L);
            check(stale.get()!=null,"old immutable snapshot absent");

            // Actual queued guarded World save: proves current token
            // works through the capture-bound worker path.
            final AtomicReference<WorldPlayerPersistence.SaveTicket>
                current=new AtomicReference<>();
            int workerChecksBefore=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            world.submitAndWait(owner,ownedGeneration,()->{
                current.set(worldRef.persistence().captureAndSave(
                    USER,owner,ownedGeneration,0,
                    "[G21.207] ","CURRENT_WORLD_SNAPSHOT"));
            },5_000L);
            check(current.get()!=null,"World SaveTicket absent");
            current.get().completion.get(10L,TimeUnit.SECONDS);
            check("NEWER_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "guarded World postimage did not commit first");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>workerChecksBefore,
                "real guarded World worker omitted original lease check");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "World save unexpectedly released held JVM lease");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownedGeneration);

            // The raw/forensic API intentionally skips the guarded
            // World admission and in-JVM captured-token check.
            // Its stale snapshot can replace the current account file
            // even while the legitimate World owner has a held lease.
            repository.save(stale.get());
            check("OLDER_CAPTURE".equals(onDisk(repository)),
                "raw writer did not demonstrate stale postimage override");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "raw writer unexpectedly revoked live JVM lease");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownedGeneration);

            System.out.println(
                "G21207_RAW_WRITER_HELD_SESSION_LEASE_BYPASS_PASS"+
                " realWorldOwnerRegistered=true"+
                " originalJVMTokenHeldThroughout=true"+
                " guardedWorkerCaptureCheckObserved=true"+
                " guardedNewerPostimageWrittenFirst=true"+
                " rawWriterStalePostimageReplacedIt=true"+
                " rawWriterCoordinatedWithJvmLease=false"+
                " productionRawWriterFence=false"+
                " crossProcessOwnerEpoch=false"+
                " durableRecovery=false"+
                " positiveAdmissionAuthority=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                // Fixture teardown ONLY, not a model for production
                // release without a confirmed final-save receipt.
                if(held!=null)held.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                if(oldAccount==null)System.clearProperty(accountProperty);
                else System.setProperty(accountProperty,oldAccount);
                if(oldLease==null)System.clearProperty(jvmLeaseProperty);
                else System.setProperty(jvmLeaseProperty,oldLease);
                if(oldCrossJvm==null)System.clearProperty(crossJvmProperty);
                else System.setProperty(crossJvmProperty,oldCrossJvm);
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path path:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
