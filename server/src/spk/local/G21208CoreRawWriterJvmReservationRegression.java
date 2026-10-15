package spk.local;

import java.io.IOException;
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
 * G21.208 opt-in cooperative FilePlayerRepository RAW writer guard.
 * Same JVM / same normalized account path only. NOT durable recovery,
 * RAW writer universal coverage, crash fencing or cross-process epoch.
 */
public final class G21208CoreRawWriterJvmReservationRegression {
    private static final String USER="g21208-reserved-raw-writer";
    private static final String NAMESPACE="core_g21208";
    private static final String KEY="extension.core_g21208.phase";

    private G21208CoreRawWriterJvmReservationRegression(){}

    private static void check(boolean good,String stage){
        if(!good)throw new AssertionError("G21.208 "+stage);
    }

    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> loaded=repository.load(USER);
        check(loaded.isPresent(),"missing account file");
        return loaded.get().values().get(KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21208-raw-jvm-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token held=null;
        long generation=-1L;
        try{
            Path file=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(file.getParent());
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "fixture account already existed");
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);

            // RAW starts first: a co-operating raw reservation prevents
            // any later opt-in lease admission on the SAME account.
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        file,USER)){
                check(raw!=null,"opt-in RAW reservation missing");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1,
                    "RAW reservation not counted");
                boolean secondRawDenied=false;
                try(LocalJvmAccountSessionLease.RawWriteReservation other=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            file,USER)){
                    check(other==null,
                        "second RAW should not gain same-path permit");
                }catch(IOException expected){
                    secondRawDenied=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "RAW_WRITE_DENIED_ANOTHER_RAW_WRITER");
                }
                check(secondRawDenied,
                    "concurrent same-account RAW writer was admitted");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1,
                    "second RAW attempt changed reservation ownership");

                boolean blocked=false;
                try{
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                }catch(IllegalStateException expected){
                    blocked=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "JVM_LEASE_DENIED_RAW_WRITE_IN_FLIGHT");
                }
                check(blocked,"RAW reservation admitted competing lease");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "competing lease acquired despite RAW reservation");
            }
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "RAW reservation did not clear after close");

            // Session starts first: its held token denies same-path RAW
            // BEFORE the historical .tmp file could be created.
            player=new WorldPlayer();
            held=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(held!=null,"opt-in JVM lease not acquired");
            generation=world.registerPlayer(player,USER);
            held.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World worldRef=world;
            final WorldPlayer owner=player;
            final long ownedGeneration=generation;
            AtomicReference<PlayerSnapshot> stale=new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket>
                current=new AtomicReference<>();
            world.submitAndWait(owner,ownedGeneration,()->{
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","OLDER_CAPTURE"));
                stale.set(PlayerSnapshotCodec.capture(USER,owner));
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","LATEST_WORLD"));
                current.set(worldRef.persistence().captureAndSave(
                    USER,owner,ownedGeneration,0,
                    "[G21.208] ","GUARDED_SAVE"));
            },5_000L);
            check(stale.get()!=null&&current.get()!=null,
                "World capture did not return snapshots");
            current.get().completion.get(10L,TimeUnit.SECONDS);
            check("LATEST_WORLD".equals(onDisk(repository)),
                "healthy guarded World worker failed");

            Path legacyTemp=file.resolveSibling(
                file.getFileName().toString()+".tmp");
            boolean rejected=false;
            try{
                repository.save(stale.get());
            }catch(IOException expected){
                rejected=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "RAW_WRITE_DENIED_ACTIVE_JVM_LEASE");
            }
            check(rejected,"RAW writer accepted active same-path lease");
            check(!Files.exists(legacyTemp,LinkOption.NOFOLLOW_LINKS),
                "rejected RAW operation created temp bytes");
            check("LATEST_WORLD".equals(onDisk(repository)),
                "rejected RAW operation modified guarded postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "rejected RAW operation revoked World token");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "denied RAW operation left a leaked reservation");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                file,owner,ownedGeneration);

            // Unrelated accounts retain their independent RAW path.
            String unrelated=USER+"-other";
            repository.save(PlayerSnapshotCodec.capture(
                unrelated,new WorldPlayer()));
            check(repository.load(unrelated).isPresent(),
                "separate account RAW unexpectedly denied");

            check(world.unregisterPlayer(player,generation),
                "fixture World owner removal failed");
            // Test cleanup only: no application-level final-save or
            // recovery permission is implied by this release.
            held.releaseAfterInitializationFailure();
            held=null;

            // Same RAW API still retains prior standalone semantics
            // once no same-JVM session owns this account.
            repository.save(stale.get());
            check("OLDER_CAPTURE".equals(onDisk(repository)),
                "post-session RAW writer no longer works");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "post-session raw path leaked held leases");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "post-session raw path leaked writer reservations");

            System.out.println(
                "G21208_COOPERATIVE_RAW_JVM_RESERVATION_PASS"+
                " rawFirstBlocksJvmLease=true"+
                " secondSameAccountRawDenied=true"+
                " worldLeaseFirstBlocksRaw=true"+
                " rejectBeforeRawTemp=true"+
                " existingWorldCaptureWriterHealthy=true"+
                " existingDiskPostimagePreserved=true"+
                " unrelatedAccountAllowed=true"+
                " unleasedRawAfterCleanupAllowed=true"+
                " defaultFlagsUnchanged=true"+
                " globalRawWriterFence=false"+
                " crossJvmOwnerEpoch=false"+
                " durableCrashRecovery=false"+
                " activeReviewRetirement=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(held!=null)held.releaseAfterInitializationFailure();
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
        check(args.length==0,"unexpected arguments");
        run();
    }
}
