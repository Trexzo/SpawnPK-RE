package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R68 true TCP/World capture publishes a forced R50 PERMANENT NEGATIVE
 * intent under the already-held real World Token's cross-JVM OS lock.
 * No positive final-save ACK or owner release is claimed.
 */
public final class G21396RealCapturedNegativeFinalIntentRegressionTest {
    private G21396RealCapturedNegativeFinalIntentRegressionTest(){}
    private static final String USER="r68-original-world";
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R68_CAPTURE_NEGATIVE "+why);
    }
    @FunctionalInterface interface Condition{boolean ready()throws Exception;}
    private static void until(Condition c,String why)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<deadline){
            if(c.ready())return;
            Thread.sleep(10L);
        }
        check(c.ready(),"timeout "+why);
    }
    private static boolean has(Throwable t,String fragment){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(fragment))return true;
        return false;
    }
    private static WorldPlayerPersistence.CapturedSave captured(
        World world,WorldPlayer owner,long generation
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.CapturedSave> ref=
            new AtomicReference<>();
        world.submit(owner,generation,()->
            ref.set(world.persistence().captureDeferredSave(
                USER,owner,generation,0,"[r68] ","R68_FINAL_CANDIDATE"))
        ).get(5,TimeUnit.SECONDS);
        WorldPlayerPersistence.CapturedSave c=ref.get();
        check(c!=null&&c.ownerWitness!=null&&
            c.ticket.snapshot.username().equals(USER),
            "not original immutable World capture");
        return c;
    }
    private static void refuse(Path path,String code)throws Exception{
        boolean rejected=false;
        try{
            new FilePlayerRepository(LocalAccountProfiles::accountFile)
                .loadForWorldSession(USER);
        }catch(IOException failed){rejected=has(failed,code);}
        check(rejected,"World hydration did not reject "+code);
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r68-genuine-final-intent-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease
                .CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        World world=null;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repository.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] original=Files.readAllBytes(account);
            // Gated feature stays OFF, even with a real capture.
            for(int i=1;i<4;i++)System.setProperty(keys[i],"true");
            world=World.isolatedForTest(50L,repository);
            World current=world;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=server.login(USER)){
                    until(()->current.players().byName(USER)!=null,
                        "real TCP World registration");
                    WorldPlayer owner=world.players().byName(USER);
                    long gen=owner.generation();
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "real session lease absent");
                    WorldPlayerPersistence.CapturedSave cap=
                        captured(world,owner,gen);
                    check(LocalCapturedWorldSnapshotFingerprintLab
                        .fingerprint(account,cap.ticket.snapshot,gen)
                        .canonicalSha256.matches("[0-9a-f]{64}"),
                        "R67 canonical digest unavailable");
                    boolean flagDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                                account,owner,gen,cap.ticket.snapshot,
                                cap.ownerWitness);
                    }catch(IOException failure){
                        flagDenied=has(failure,
                            "R68_CAPTURED_NEGATIVE_INTENT_LAB_DEFAULT_OFF");
                    }
                    check(flagDenied&&!Files.exists(
                        LocalNegativeAccountHeadPrototype.headPath(account),
                        LinkOption.NOFOLLOW_LINKS),
                        "default-off unexpectedly wrote intent");
                    System.setProperty(keys[4],"true");
                    WorldPlayer impostor=new WorldPlayer();
                    impostor.markRegistered(USER);
                    boolean forgedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                                account,impostor,gen,cap.ticket.snapshot,
                                cap.ownerWitness);
                    }catch(IOException failure){
                        forgedDenied=has(failure,
                            "R68_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
                    }
                    check(forgedDenied,"forged World owner wrote intent");
                    LocalNegativeAccountHeadPrototype.NegativeObservation
                        negative=LocalJvmAccountSessionLease
                            .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                                account,owner,gen,cap.ticket.snapshot,
                                cap.ownerWitness);
                    String canonical=LocalCapturedWorldSnapshotFingerprintLab
                        .fingerprint(account,cap.ticket.snapshot,gen)
                        .canonicalSha256;
                    check(canonical.equals(negative.snapshotSha256)&&
                        !negative.admissionAuthorized&&
                        !negative.releaseAuthorized,
                        "negative HEAD was not bound to actual capture");
                    check(Arrays.equals(original,Files.readAllBytes(account)),
                        "negative intent replaced old account snapshot");
                    refuse(account,
                        "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY");
                    boolean repeatedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                                account,owner,gen,cap.ticket.snapshot,
                                cap.ownerWitness);
                    }catch(IOException failed){
                        repeatedDenied=has(failed,
                            "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY");
                    }
                    check(repeatedDenied,
                        "negative intent was auto-overwritten or retired");
                }
                until(()->current.players().size()==0,
                    "session disconnect following permanent intent");
                server.waitCompleted();
            }
            check(world.persistence().failedCount()>0,
                "pre-final negative intent did not block legacy final write");
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "failed final write changed legacy postimage");
            check(Files.exists(
                LocalNegativeAccountHeadPrototype.headPath(account),
                LinkOption.NOFOLLOW_LINKS),
                "durable negative record unexpectedly retired");
            // A stranded snapshot object WITHOUT its negative HEAD
            // must be rejected in fresh legacy reads and World loads.
            Path orphan=root.resolve("orphan.properties");
            Files.write(orphan,new byte[]{10},
                StandardOpenOption.CREATE_NEW);
            Path lonely=orphan.resolveSibling(orphan.getFileName()+
                ".g21378-object-deadbeef.snapshot");
            Files.write(lonely,new byte[]{11},
                StandardOpenOption.CREATE_NEW);
            boolean orphanRejected=false;
            try{LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(orphan);}
            catch(IOException failure){
                orphanRejected=has(failure,
                    "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
            }
            check(orphanRejected&&Files.exists(lonely,
                LinkOption.NOFOLLOW_LINKS),
                "orphan forced R50 object did not cause veto");
            world.close();
            check(world.closed(),"World failed to close");
            System.out.println("R68_REAL_CAPTURED_WORLD_NEGATIVE_INTENT_PASS"+
                " actualTcpWorldAndCaptureWitness=true"+
                " originalSessionOsLockHeld=true"+
                " deterministicCanonicalImageForced=true"+
                " snapshotShaMatchesR67Capture=true"+
                " normalFinalSaveBlocked=true"+
                " staleWorldHydrationDenied=true"+
                " orphanObjectWithoutHeadDenied=true"+
                " oldAccountBytesUnmodified=true"+
                " noFinalSaveAckOrOwnerRelease=true"+
                " optInOnly=true"+
                " noPositiveRecoveryOrReplay=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path file:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        run();
    }
}
