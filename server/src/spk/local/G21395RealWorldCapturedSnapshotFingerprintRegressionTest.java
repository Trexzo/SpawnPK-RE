package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R67: real TCP + registered World command capturing actual immutable
 * PlayerSnapshot with original G21.202 SaveCaptureWitness. The bounded
 * LAB fingerprint is never published as a receipt or account epoch.
 */
public final class G21395RealWorldCapturedSnapshotFingerprintRegressionTest {
    private G21395RealWorldCapturedSnapshotFingerprintRegressionTest(){}
    private static final String USER="r67-real-captured-snapshot";
    private static final String FIELD="extension.r67_capture.phase";
    private static void check(boolean pass,String reason){
        if(!pass)throw new AssertionError("R67_REAL_CAPTURE "+reason);
    }
    @FunctionalInterface interface Condition{
        boolean test()throws Exception;
    }
    private static void until(Condition condition,String label)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<deadline){
            if(condition.test())return;
            Thread.sleep(10);
        }
        check(condition.test(),"timeout "+label);
    }
    private static boolean has(Throwable error,String code){
        for(Throwable t=error;t!=null;t=t.getCause())
            if(String.valueOf(t.getMessage()).contains(code))return true;
        return false;
    }
    private static boolean denied(
        Path file,WorldPlayer owner,long gen,
        WorldPlayerPersistence.CapturedSave capture,String marker
    ){
        try{
            LocalJvmAccountSessionLease
                .observeCapturedWorldSnapshotUnderOsLockForLab(
                    file,owner,gen,capture.ticket.snapshot,
                    capture.ownerWitness);
            return false;
        }catch(IOException rejection){
            return has(rejection,marker);
        }
    }
    private static WorldPlayerPersistence.CapturedSave captureInWorld(
        World world,WorldPlayer owner,long gen,String reason
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.CapturedSave> observed=
            new AtomicReference<>();
        world.submit(owner,gen,()->{
            observed.set(world.persistence().captureDeferredSave(
                USER,owner,gen,0,"[r67] ",reason));
        }).get(5,TimeUnit.SECONDS);
        WorldPlayerPersistence.CapturedSave result=observed.get();
        check(result!=null&&result.owner==owner&&
            result.expectedGeneration==gen&&
            !result.finalDisconnect&&result.ownerWitness!=null,
            "World command did not capture immutable witness");
        return result;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r67-world-capture-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] original=new String[props.length];
        for(int i=0;i<props.length;i++)
            original[i]=System.getProperty(props[i]);
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        World world=null;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            // Seed before enabling session flags; this is a disposable
            // test fixture, not recovery of an existing player.
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] diskPreimage=Files.readAllBytes(account);
            for(int i=1;i<props.length;i++)
                System.setProperty(props[i],"true");
            world=World.isolatedForTest(50L,repository);
            World registered=world;
            WorldPlayer owner;
            long generation;
            WorldPlayerPersistence.CapturedSave originalCapture;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket client=server.login(USER)){
                    until(()->registered.players().byName(USER)!=null,
                        "real authenticated World registration");
                    owner=world.players().byName(USER);
                    generation=owner.generation();
                    check(generation>0&&
                        LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "genuine World Token not held");
                    originalCapture=captureInWorld(
                        world,owner,generation,"R67_FIRST");
                    LocalCapturedWorldSnapshotFingerprintLab.Observation a=
                        LocalJvmAccountSessionLease
                            .observeCapturedWorldSnapshotUnderOsLockForLab(
                                account,owner,generation,
                                originalCapture.ticket.snapshot,
                                originalCapture.ownerWitness);
                    check(USER.equals(a.username)&&
                        account.equals(a.exactAccount)&&
                        a.worldGeneration==generation&&
                        a.snapshotVersion==PlayerSnapshot.CURRENT_VERSION&&
                        a.fieldCount>0&&a.canonicalBytes>0&&
                        a.canonicalSha256.matches("[0-9a-f]{64}")&&
                        !a.admissionAuthorized&&
                        !a.finalSaveAcknowledged&&
                        !a.releaseAuthorized,
                        "real captured fingerprint gained authority");
                    LocalCapturedWorldSnapshotFingerprintLab.Observation again=
                        LocalJvmAccountSessionLease
                            .observeCapturedWorldSnapshotUnderOsLockForLab(
                                account,owner,generation,
                                originalCapture.ticket.snapshot,
                                originalCapture.ownerWitness);
                    check(a.canonicalSha256.equals(again.canonicalSha256)&&
                        a.canonicalBytes==again.canonicalBytes,
                        "identical immutable capture digest drifted");
                    world.submit(owner,generation,()->{
                        owner.snapshotExtensions().replaceNamespace(
                            "r67_capture",Collections.singletonMap(
                                "phase","CHANGED_AFTER_FIRST_CAPTURE"));
                    }).get(5,TimeUnit.SECONDS);
                    WorldPlayerPersistence.CapturedSave newer=captureInWorld(
                        world,owner,generation,"R67_AFTER_MUTATION");
                    check("CHANGED_AFTER_FIRST_CAPTURE".equals(
                            newer.ticket.snapshot.value(FIELD)),
                        "World mutation was not captured");
                    LocalCapturedWorldSnapshotFingerprintLab.Observation b=
                        LocalJvmAccountSessionLease
                            .observeCapturedWorldSnapshotUnderOsLockForLab(
                                account,owner,generation,
                                newer.ticket.snapshot,newer.ownerWitness);
                    check(!a.canonicalSha256.equals(b.canonicalSha256),
                        "newer World snapshot not separated from old capture");
                    check(a.canonicalSha256.equals(
                        LocalJvmAccountSessionLease
                            .observeCapturedWorldSnapshotUnderOsLockForLab(
                                account,owner,generation,
                                originalCapture.ticket.snapshot,
                                originalCapture.ownerWitness).canonicalSha256),
                        "immutable older capture mutated after World change");
                    WorldPlayer forged=new WorldPlayer();
                    forged.markRegistered(USER);
                    check(forged.generation()==generation,
                        "forged identical generation fixture invalid");
                    check(denied(account,forged,generation,originalCapture,
                        "R67_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER"),
                        "forged same-generation capture owner accepted");
                    check(denied(account,owner,generation+1,originalCapture,
                        "R67_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER"),
                        "wrong World generation accepted");
                    Path foreign=LocalAccountProfiles.accountFile(
                        "r67-other-account");
                    check(denied(foreign,owner,generation,originalCapture,
                        "R67_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER"),
                        "foreign file borrowed captured owner witness");
                    check(java.util.Arrays.equals(
                        diskPreimage,Files.readAllBytes(account)),
                        "LAB fingerprint wrote account bytes");
                    check(!Files.exists(account.resolveSibling(
                        account.getFileName()+".g21385-ledger-head"),
                        LinkOption.NOFOLLOW_LINKS),
                        "read-only capture wrote LAB HEAD");
                }
                until(()->registered.players().size()==0,
                    "World unregister after actual TCP logout");
                server.waitCompleted();
            }
            until(()->LocalJvmAccountSessionLease.activeCountForTest()==0,
                "healthy final World release");
            check(denied(account,owner,generation,originalCapture,
                "R67_CAPTURE_ORIGINAL_LEASE_RELEASED_OR_REPLACED"),
                "released capture still allowed to observe");
            check(world.persistence().failedCount()==0,
                "ordinary World final save unexpectedly failed");
            world.close();
            check(world.closed(),"World failed to close");
            System.out.println("R67_REAL_WORLD_CAPTURED_SNAPSHOT_FINGERPRINT_PASS"+
                " genuineTcpWorldOwner=true"+
                " originalCaptureTimeLeaseWitness=true"+
                " originalCrossJvmSessionLock=true"+
                " stableImmutableContentDigest=true"+
                " newerWorldMutationDifferentDigest=true"+
                " forgedOwnerAndGenerationDenied=true"+
                " foreignAccountDenied=true"+
                " releasedLeaseDenied=true"+
                " noAccountFileOrLabMutation=true"+
                " noDurableFinalIntentOrReceipt=true"+
                " noPositiveAdmissionOrRecovery=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            for(int i=0;i<props.length;i++){
                if(original[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],original[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        run();
    }
}
