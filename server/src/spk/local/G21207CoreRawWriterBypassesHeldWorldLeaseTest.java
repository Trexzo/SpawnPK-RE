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
 * G21.207 historical TEST-ONLY characterisation updated for R40.
 *
 * The original PASS demonstrated a RAW writer bypassing an already
 * held opt-in World lease merely because RAW participation was OFF.
 * R40 intentionally closes that SAME-JVM live-flag bypass. Preserve
 * both sides of the evidence: deny the stale RAW overwrite while the
 * original live World owner still holds its token, then demonstrate
 * legacy unleased RAW behavior after explicit fixture-only release.
 *
 * This does NOT authorize release after an uncertain final save,
 * authenticated cross-JVM ownership or durable account recovery.
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
        String rawGateProperty=
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY;
        String oldRawGate=System.getProperty(rawGateProperty);
        System.setProperty(rawGateProperty,"false");
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

            // R40 changes this historical gap: toggling RAW writer
            // participation OFF must not defeat an already-held World
            // lease for this EXACT account. A failed RAW must preserve
            // both the last good account bytes and the rightful owner.
            byte[] guardedBytes=Files.readAllBytes(account);
            boolean refused=false;
            try{
                repository.save(stale.get());
            }catch(java.io.IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "R40_RAW_OPT_OUT_DURING_ACCOUNT_OWNERSHIP_DENIED");
            }
            check(refused,
                "R40 admitted historical stale RAW live-owner bypass");
            check(java.util.Arrays.equals(
                    guardedBytes,Files.readAllBytes(account)),
                "denied RAW modified guarded account bytes");
            check(!Files.exists(account.resolveSibling(
                    account.getFileName().toString()+".tmp"),
                    LinkOption.NOFOLLOW_LINKS),
                "denied RAW allocated legacy fixed temp");
            check("NEWER_WORLD_POSTIMAGE".equals(onDisk(repository)),
                "failed RAW replaced newer World postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "denied RAW revoked live World lease");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownedGeneration);

            // Fixture-only lifecycle completion: this does NOT model
            // recovery after an uncertain final logout or disk fault.
            check(world.unregisterPlayer(player,generation),
                "test World owner unregister failed");
            held.releaseAfterInitializationFailure();
            held=null;
            repository.save(stale.get());
            check("OLDER_CAPTURE".equals(onDisk(repository)),
                "historical default-off RAW after explicit release changed");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test left an account owner after fixture release");

            System.out.println(
                "G21207_HISTORIC_RAW_LIVE_BYPASS_NOW_DENIED_PASS"+
                " realWorldOwnerRegistered=true"+
                " originalJVMTokenHeldForDeniedRaw=true"+
                " guardedWorkerCaptureCheckObserved=true"+
                " guardedNewerPostimageWrittenFirst=true"+
                " staleRawWriterDeniedBeforeTemp=true"+
                " latestWorldBytesPreservedWhileOwned=true"+
                " rightfulOwnerRetainedUntilFixtureRelease=true"+
                " legacyRawAfterManualFixtureReleaseStillUncoordinated=true"+
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
                if(oldRawGate==null)System.clearProperty(rawGateProperty);
                else System.setProperty(rawGateProperty,oldRawGate);
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
