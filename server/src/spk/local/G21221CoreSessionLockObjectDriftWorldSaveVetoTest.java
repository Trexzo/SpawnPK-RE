package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.221 TEST-ONLY opt-in diagnostic: if the lock NAME no longer
 * resolves to its originally observed fileKey, original World owner
 * checks and queued saved snapshot publication must fail closed.
 * This does NOT fence a foreign JVM already using a substituted inode.
 */
public final class G21221CoreSessionLockObjectDriftWorldSaveVetoTest {
    private static final String USER="g21221-stale-lock-inode";
    private static final String NS="core_g21221";
    private static final String KEY="extension.core_g21221.phase";
    private G21221CoreSessionLockObjectDriftWorldSaveVetoTest(){}
    private static void check(boolean ok,String stage){
        if(!ok)throw new AssertionError("G21.221 "+stage);
    }
    private static String onDisk(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> stored=repository.load(USER);
        check(stored.isPresent(),"persisted account missing");
        return stored.get().values().get(KEY);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21221-object-drift-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)
            System.setProperty(keys[i],"true");
        World world=null;
        WorldPlayer owner=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"original JVM owner token missing");
            owner=new WorldPlayer();
            generation=world.registerPlayer(owner,USER);
            token.bindAdmittedWorldOwner(owner,generation);
            world.start();
            final World live=world;
            final WorldPlayer livePlayer=owner;
            final long admittedGeneration=generation;
            Path account=LocalAccountProfiles.accountFile(USER);

            AtomicReference<WorldPlayerPersistence.SaveTicket> priorSave=
                new AtomicReference<>();
            world.submitAndWait(livePlayer,admittedGeneration,()->{
                livePlayer.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","CONFIRMED_ORIGINAL_WORLD"));
                priorSave.set(live.persistence().captureAndSave(
                    USER,livePlayer,admittedGeneration,0,
                    "[G21.221] ","BASELINE_CONFIRMED"));
            },5_000L);
            check(priorSave.get()!=null,
                "baseline genuine World SaveTicket missing");
            priorSave.get().completion.get(10L,TimeUnit.SECONDS);
            check("CONFIRMED_ORIGINAL_WORLD".equals(onDisk(repository)),
                "original verified postimage absent");

            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "original OS lock name missing");
            if(System.getProperty("os.name").toLowerCase()
                    .contains("win")){
                System.out.println(
                    "G21221_LOCK_DRIFT_PLATFORM_UNSUPPORTED"+
                    " windowsRenameLockedFileUnsupported=true"+
                    " oldWorldPrepublicationVetoProven=false");
                return;
            }
            Path orphan=lock.resolveSibling(
                lock.getFileName().toString()+".held-by-original-jvm");
            Files.move(lock,orphan,StandardCopyOption.ATOMIC_MOVE);
            Files.createFile(lock);
            BasicFileAttributes a=Files.readAttributes(
                orphan,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes b=Files.readAttributes(
                lock,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            check(a.fileKey()!=null&&b.fileKey()!=null&&
                    !a.fileKey().equals(b.fileKey()),
                "locked object identity did not actually change");

            boolean currentRejected=false;
            try{
                LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                    account,livePlayer,admittedGeneration);
            }catch(IllegalStateException expected){
                currentRejected=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "G21.221 SESSION_LOCK_OBJECT_CHANGED_NO_PUBLICATION");
            }
            check(currentRejected,
                "original World owner check missed observed lock drift");

            final AtomicReference<WorldPlayerPersistence.SaveTicket>
                rejected=new AtomicReference<>();
            long failedBefore=world.persistence().failedCount();
            world.submitAndWait(livePlayer,admittedGeneration,()->{
                livePlayer.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","SHOULD_NOT_OVERWRITE_DISK"));
                rejected.set(live.persistence().captureAndSave(
                    USER,livePlayer,admittedGeneration,0,
                    "[G21.221] ","STALE_OBJECT_SAVE"));
            },5_000L);
            check(rejected.get()!=null,"rejected save had no ticket");
            boolean workerDenied=false;
            try{
                rejected.get().completion.get(10L,TimeUnit.SECONDS);
            }catch(ExecutionException expected){
                Throwable why=expected.getCause();
                workerDenied=why instanceof IllegalStateException&&
                    why.getMessage()!=null&&
                    why.getMessage().contains(
                        "G21.221 SESSION_LOCK_OBJECT_CHANGED_NO_PUBLICATION");
            }
            check(workerDenied,
                "queued captured-owner World worker ignored changed object");
            check(world.persistence().failedCount()==failedBefore+1,
                "failed World worker not counted exactly once");
            check("CONFIRMED_ORIGINAL_WORLD".equals(onDisk(repository)),
                "stale World snapshot replaced healthy disk state");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "lock identity failure released original JVM token");

            Files.delete(lock);
            boolean missingDenied=false;
            try{
                LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                    account,livePlayer,admittedGeneration);
            }catch(IllegalStateException expected){
                missingDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "G21.221 SESSION_LOCK_OBJECT_CHANGED_NO_PUBLICATION");
            }
            check(missingDenied,
                "missing original lock name did not veto owner use");

            System.out.println(
                "G21221_LOCK_OBJECT_DRIFT_WORLD_SAVE_VETO_PASS"+
                " actualJVMTokenAndWorldGeneration=true"+
                " originalWorldPostimageCommitted=true"+
                " lockedOriginalInodeRenamed=true"+
                " newLockPathFileKeyDistinct=true"+
                " currentOwnerWitnessDenied=true"+
                " captureBoundWorldSaveWorkerDenied=true"+
                " oldDiskPostimagePreserved=true"+
                " vanishedLockNameAlsoDenied=true"+
                " tokenHeldForConservativeCleanup=true"+
                " externalWriterGloballyFenced=false"+
                " authenticatedEpoch=false"+
                " crashRecovery=false");
        }finally{
            try{
                if(world!=null&&owner!=null&&generation>0L)
                    world.unregisterPlayer(owner,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(prior[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],prior[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path p:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }
}
