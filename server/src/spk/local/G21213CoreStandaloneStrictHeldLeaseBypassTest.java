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
 * G21.213 after G21.214: default-OFF compatibility negative.
 * Parent #2674 retains the historical gate-ON standalone strict
 * bypass evidence. This explicitly test-only flag flip is not safe
 * for a live session.
 */
public final class G21213CoreStandaloneStrictHeldLeaseBypassTest {
    private static final String USER=AccountStore.CANONICAL_USERNAME;
    private static final String NAMESPACE="core_g21213";
    private static final String KEY="extension.core_g21213.phase";

    private G21213CoreStandaloneStrictHeldLeaseBypassTest(){}

    private static void check(boolean condition,String stage){
        if(!condition)throw new AssertionError("G21.213 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> loaded=repository.load(USER);
        check(loaded.isPresent(),"named account missing");
        return loaded.get().values().get(KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21213-profile-bypass-");
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
                        "phase","OLDER_STRICT_CAPTURE"));
                rawAttempt.set(PlayerSnapshotCodec.capture(USER,owner));
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","LATEST_WORLD_EXTENSION"));
                healthy.set(liveWorld.persistence().captureAndSave(
                    USER,owner,ownerGeneration,0,
                    "[G21.213] ","REAL_WORLD_ACCOUNT_SAVE"));
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

            // Standalone STRICT file writer is a distinct opt-in RAW
            // primitive, not the World PREPARED/TERMINAL strict path.
            // Both JVM flags remain ON. No malicious files or crash:
            // this is one legal direct isolated test-only call.
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    LocalAccountProfiles::accountFile);
            // G21.213 on parent #2674 retains the exact gate-ON
            // strict bypass evidence; with G21.214 this descendant
            // preserves only test-only default-OFF compatibility.
            System.setProperty(keys[2],"false");
            StrictDurablePlayerSnapshotWriter.Receipt receipt;
            try{
                receipt=strict.saveStrict(rawAttempt.get());
            }finally{
                System.setProperty(keys[2],"true");
            }
            check(receipt!=null&&
                    receipt.matchesSnapshot(rawAttempt.get()),
                "standalone STRICT default-OFF receipt missing");
            check(!Arrays.equals(beforeLegacy,Files.readAllBytes(account)),
                "strict file writer did not replace account postimage");
            check("OLDER_STRICT_CAPTURE".equals(onDisk(repository)),
                "strict standalone postimage did not restore older captured state");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "strict writer unexpectedly participated in RAW reservations");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "strict writer changed held JVM session Token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownerGeneration);

            System.out.println(
                "G21213_STANDALONE_STRICT_HELD_JVM_LEASE_BYPASS_PASS"+
                " actualWorldSaveFirst=true"+
                " originalWorldCaptureLeaseChecked=true"+
                " participatingRepositoryRawDenied=true"+
                " standaloneStrictWriteSucceededWithGateOff=true"+
                " newerWorldExtensionLostFromDisk=true"+
                " originalWorldSessionLeaseStillHeld=true"+
                " defaultOffFlagsUntouched=true"+
                " standaloneStrictOptOutStillUnsafe=true"+
                " globalWriterExclusion=false"+
                " authenticatedDurableEpoch=false"+
                " crossJvmOrCrashRecovery=false"+
                " positiveGrantOrReviewRetirement=false");
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
