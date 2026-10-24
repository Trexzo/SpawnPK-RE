package spk.local;

import java.io.IOException;
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
 * G21.211 was an historical unsafe primary-account-store opt-out characterization. R40 must now refuse live opt-out under a World lease; the default-off legacy writer still works after test-only owner release.
 * This is a regression of the new fail-closed boundary, not a
 * positive grant, crash recovery, or authorization to toggle live flags.
 */
public final class G21211CorePrimaryAccountStoreBypassTest {
    private static final String USER=AccountStore.CANONICAL_USERNAME;
    private static final String NAMESPACE="core_g21211";
    private static final String KEY="extension.core_g21211.phase";

    private G21211CorePrimaryAccountStoreBypassTest(){}

    private static void check(boolean condition,String stage){
        if(!condition)throw new AssertionError("G21.211 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> loaded=repository.load(USER);
        check(loaded.isPresent(),"named account missing");
        return loaded.get().values().get(KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21211-profile-bypass-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "account unexpectedly existed before fixture");
            check(LocalAccountProfiles.PRIMARY.equals(USER),
                "fixture must use primary opensrc account");

            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            player=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"opt-in account token absent");
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World liveWorld=world;
            final WorldPlayer owner=player;
            final long ownerGeneration=generation;
            final AtomicReference<WorldPlayerPersistence.SaveTicket>
                healthy=new AtomicReference<>();
            final AtomicReference<PlayerSnapshot>
                rawAttempt=new AtomicReference<>();
            int before=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();

            world.submitAndWait(owner,ownerGeneration,()->{
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","LATEST_WORLD_EXTENSION"));
                rawAttempt.set(PlayerSnapshotCodec.capture(USER,owner));
                healthy.set(liveWorld.persistence().captureAndSave(
                    USER,owner,ownerGeneration,0,
                    "[G21.211] ","REAL_WORLD_ACCOUNT_SAVE"));
            },5_000L);
            check(healthy.get()!=null&&rawAttempt.get()!=null,
                "real World capture did not finish");
            healthy.get().completion.get(10L,TimeUnit.SECONDS);
            check("LATEST_WORLD_EXTENSION".equals(onDisk(repository)),
                "World saved extension postimage missing");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>before,
                "real guarded worker did not validate original Token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownerGeneration);
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "live World Token unexpectedly released");

            byte[] beforeLegacy=Files.readAllBytes(account);

            // Control: the G21.208 participating RAW repository path
            // is actually denied while the same World lease is held.
            boolean participatingRawDenied=false;
            try{
                repository.save(rawAttempt.get());
            }catch(IOException expected){
                participatingRawDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "RAW_WRITE_DENIED_ACTIVE_JVM_LEASE");
            }
            check(participatingRawDenied,
                "cooperating RAW repository path unexpectedly accepted");
            check(Arrays.equals(beforeLegacy,Files.readAllBytes(account)),
                "denied participating RAW changed bytes");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "participating RAW denial leaked reservation");

            // Historical G21.211 parent #2671 is the certified
            // gate-ON PRIMARY bypass evidence. Following G21.212,
            // default-OFF compatibility must remain demonstrably unsafe.
            // This explicit flag flip is TEST ONLY, never live practice.
            // R40 correction: G21.211 was an historical unsafe primary-account-store opt-out characterization. R40 must now refuse live opt-out under a World lease; the default-off legacy writer still works after test-only owner release.
            System.setProperty(keys[2],"false");
            String legacy=null;
            boolean refused=false;
            try{
                legacy=AccountStore.save(
                    owner.bank(),owner.equipment(),owner.movement(),
                    owner.petState(),owner.playerState());
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "R40_RAW_OPT_OUT_DURING_ACCOUNT_OWNERSHIP_DENIED");
            }finally{
                System.setProperty(keys[2],"true");
            }
            check(refused&&legacy==null,
                "late primary RAW opt-out bypassed World lease");
            check(Arrays.equals(beforeLegacy,Files.readAllBytes(account)),
                "denied primary writer replaced newer World bytes");
            check("LATEST_WORLD_EXTENSION".equals(onDisk(repository)),
                "denied primary RAW erased World extension");
            check(!Files.exists(account.resolveSibling(
                    account.getFileName().toString()+".tmp"),
                    LinkOption.NOFOLLOW_LINKS),
                "denied primary allocated fixed temp");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1&&
                LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "denied primary writer changed held owner accounting");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownerGeneration);
            check(world.unregisterPlayer(player,generation),
                "fixture-only World unregister failed");
            token.releaseAfterInitializationFailure();
            token=null;
            System.setProperty(keys[2],"false");
            try{
                legacy=AccountStore.save(
                    owner.bank(),owner.equipment(),owner.movement(),
                    owner.petState(),owner.playerState());
            }finally{
                System.setProperty(keys[2],"true");
            }
            check(legacy!=null&&legacy.contains("ACCOUNT_SAVED"),
                "primary default-off writer after release was denied");
            check(!Arrays.equals(beforeLegacy,Files.readAllBytes(account))&&
                onDisk(repository)==null,
                "historical unleased primary account semantics changed");

            System.out.println(
                "G21211_PRIMARY_LIVE_RAW_OPT_OUT_DENIED_PASS"+
                " liveWorldLeaseRetainedUntilFixtureRelease=true"+
                " latePrimaryFlagOffRejectedBeforeTemp=true"+
                " oldWorldBytesPreservedUnderLease=true"+
                " legacyUnleasedPrimarySaveAfterRelease=true"+
                " crossJvmEpoch=false crashDurableRecovery=false");

        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(previous[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],previous[i]);
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
        check(args.length==0,"unexpected arguments");
        run();
    }
}
