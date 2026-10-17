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
 * G21.214 opt-in standalone non-World strict writer RAW gate.
 * A positive, scoped same-JVM same-file World lease exclusion proof,
 * NOT global writer coverage, cross-process epoch or crash recovery.
 */
public final class G21214CoreStandaloneStrictJvmReservationRegression {
    private static final String USER=AccountStore.CANONICAL_USERNAME;
    private static final String NAMESPACE="core_g21214";
    private static final String KEY="extension.core_g21214.phase";

    private G21214CoreStandaloneStrictJvmReservationRegression(){}

    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.214 "+stage);
    }

    private static String value(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> current=repository.load(USER);
        check(current.isPresent(),"missing persisted account");
        return current.get().values().get(KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21214-profile-guard-");
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
        LocalJvmAccountSessionLease.Token lease=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "test account unexpectedly existed");

            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            player=new WorldPlayer();
            lease=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(lease!=null,"opt-in session lease absent");
            generation=world.registerPlayer(player,USER);
            lease.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World live=world;
            final WorldPlayer owner=player;
            final long ownedGeneration=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket>
                ticket=new AtomicReference<>();
            int ownerGuardBefore=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            world.submitAndWait(owner,ownedGeneration,()->{
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(
                        "phase","NEWER_WORLD_SNAPSHOT"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,ownedGeneration,0,
                    "[G21.214] ","REAL_GUARDED_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"World-owned save not captured");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("NEWER_WORLD_SNAPSHOT".equals(value(repository)),
                "healthy World save did not reach disk");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()>ownerGuardBefore,
                "World queued save omitted original captured Token");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownedGeneration);

            byte[] before=Files.readAllBytes(account);
            Path legacyTmp=account.resolveSibling(
                account.getFileName().toString()+".tmp");
            check(!Files.exists(legacyTmp,LinkOption.NOFOLLOW_LINKS),
                "unexpected legacy temp before denial");

            // All standalone saveStrict* APIs share the G21.214
            // entry boundary. The reserve check occurs BEFORE strict
            // G21.100 lifecycle and forced-temp creation.
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    LocalAccountProfiles::accountFile);
            PlayerSnapshot oldCaptured=
                PlayerSnapshotCodec.capture(USER,new WorldPlayer());
            long strictTempCountBefore;
            try(Stream<Path> leaves=Files.list(account.getParent())){
                strictTempCountBefore=leaves.filter(p->
                    p.getFileName().toString().startsWith(
                        account.getFileName().toString()+".g2123-"))
                    .count();
            }
            boolean denied=false;
            try{
                strict.saveStrict(oldCaptured);
            }catch(IOException expected){
                denied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "RAW_WRITE_DENIED_ACTIVE_JVM_LEASE");
            }
            check(denied,"standalone strict writer bypassed live lease");
            long strictTempCountAfter;
            try(Stream<Path> leaves=Files.list(account.getParent())){
                strictTempCountAfter=leaves.filter(p->
                    p.getFileName().toString().startsWith(
                        account.getFileName().toString()+".g2123-"))
                    .count();
            }
            check(strictTempCountAfter==strictTempCountBefore,
                "denied strict writer created forced temp");
            check(!Files.exists(legacyTmp,LinkOption.NOFOLLOW_LINKS),
                "denied standalone strict allocated fixed legacy temp");
            check(Arrays.equals(before,Files.readAllBytes(account)),
                "denied standalone strict replaced account postimage");
            check("NEWER_WORLD_SNAPSHOT".equals(value(repository)),
                "denied standalone strict lost World extension");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "profile denial released the World lease");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "profile denial leaked a RAW reservation");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,owner,ownedGeneration);

            // A different exact account path remains independent.
            String unrelated=USER+"-other";
            WorldPlayer other=new WorldPlayer();
            String otherSave=LocalAccountProfiles.save(
                unrelated,other.bank(),other.equipment(),
                other.movement(),other.petState(),other.playerState());
            check(otherSave!=null&&otherSave.contains("ACCOUNT_SAVED"),
                "unrelated profile adapter should remain available");
            check(repository.load(unrelated).isPresent(),
                "unrelated profile account file missing");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "unrelated writer affected current owner's token");

            check(world.unregisterPlayer(player,generation),
                "test World owner could not unregister");
            // Test cleanup only. An actual session must require a
            // confirmed final save before releasing its lease.
            lease.releaseAfterInitializationFailure();
            lease=null;

            StrictDurablePlayerSnapshotWriter.Receipt after=
                strict.saveStrict(oldCaptured);
            check(after!=null&&after.matchesSnapshot(oldCaptured),
                "standalone strict could not operate after cleanup");
            check(value(repository)==null,
                "legacy core-only profile serializer changed semantics");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "post-cleanup legacy save retained stale lease");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "post-cleanup legacy save retained RAW reservation");

            System.out.println(
                "G21214_STANDALONE_STRICT_JVM_RESERVATION_PASS"+
                " realWorldOwnerBound=true"+
                " realWorldSaveWorkerGuarded=true"+
                " standaloneStrictDeniedBeforeTemp=true"+
                " diskPostimagePreserved=true"+
                " originalSessionLeaseHeld=true"+
                " otherNamedAccountAllowed=true"+
                " strictAfterOwnerCleanupAllowed=true"+
                " strictSerializerUnchanged=true"+
                " flagDefaultOff=true"+
                " primaryAccountStoreCoveredByG21212=true"+
                " standaloneStrictWriterCovered=true"+
                " crossJvmDurableEpoch=false"+
                " powerLossRecovery=false"+
                " activeMarkerRetirement=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(lease!=null)lease.releaseAfterInitializationFailure();
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
