package spk.local;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** R66: real TCP/World owner plus the exact account file object while
 * original cooperating OS FileLock is held; strictly observational. */
public final class G21394RealWorldAccountImageBindingRegressionTest {
    private G21394RealWorldAccountImageBindingRegressionTest(){}
    private static final String USER="r66-actual-world-image";
    private static void check(boolean value,String why){
        if(!value)throw new AssertionError("R66_WORLD_IMAGE "+why);
    }
    private static boolean contains(Throwable error,String expected){
        for(Throwable t=error;t!=null;t=t.getCause())
            if(String.valueOf(t.getMessage()).contains(expected))
                return true;
        return false;
    }
    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }
    private static void until(Condition condition,String why)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<deadline){
            if(condition.ready())return;
            Thread.sleep(10);
        }
        check(condition.ready(),"timeout "+why);
    }
    private static String sha(byte[] bytes)throws Exception{
        StringBuilder output=new StringBuilder(64);
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)){
            String hex=Integer.toHexString(b&255);
            if(hex.length()==1)output.append('0');
            output.append(hex);
        }
        return output.toString();
    }
    private static boolean rejected(Path account,WorldPlayer owner,
                                    long generation,String reason){
        try{
            LocalJvmAccountSessionLease
                .observeRealWorldAccountImageUnderOsLockForLab(
                    account,owner,generation);
            return false;
        }catch(IOException denied){
            return contains(denied,reason);
        }
    }
    private static void exercise()throws Exception{
        Path root=Files.createTempDirectory("r66-world-image-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        World world=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            // A positive immutable fingerprint requires an actual
            // existing account byte image. Bootstrap deterministically
            // BEFORE taking any real World/session lock.
            repo.save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            byte[] baseline=Files.readAllBytes(account);
            check(baseline.length>0,"baseline absent");
            for(int i=1;i<flags.length;i++)
                System.setProperty(flags[i],"true");
            world=World.isolatedForTest(50L,repo);
            final World live=world;
            WorldPlayer owner;
            long gen;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=listener.login(USER)){
                    until(()->live.players().byName(USER)!=null,
                        "real TCP World registration");
                    owner=live.players().byName(USER);
                    gen=owner.generation();
                    check(gen>0&&
                        LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "original JVM Token not live");
                    LocalJvmAccountSessionLease.WorldAccountImageLabObservation
                        image=LocalJvmAccountSessionLease
                            .observeRealWorldAccountImageUnderOsLockForLab(
                                account,owner,gen);
                    check(account.equals(image.accountFile)&&
                        USER.equals(image.username)&&
                        gen==image.observedWorldGeneration&&
                        image.imageLength==baseline.length&&
                        sha(baseline).equals(image.imageSha256)&&
                        !image.admissionAuthorized&&!image.releaseAuthorized,
                        "actual original account image was not bound");
                    WorldPlayer forged=new WorldPlayer();
                    forged.markRegistered(USER);
                    check(forged.generation()==gen,
                        "forged identity negative fixture invalid");
                    check(rejected(account,forged,gen,
                        "R65_REAL_WORLD_IDENTITY_OR_GENERATION_REJECTED"),
                        "same-generation forged World owner accepted");
                    check(rejected(account,owner,gen+1,
                        "R65_REAL_WORLD_IDENTITY_OR_GENERATION_REJECTED"),
                        "foreign generation was accepted");
                    // Same-size mutation between independent readbacks
                    // must invalidate fingerprint, even with matching
                    // filename and original held session FileLock.
                    byte[] changed=baseline.clone();
                    changed[changed.length-1]^=1;
                    boolean changedDenied=false;
                    try{
                        LocalJvmAccountSessionLease
                            .observeRealWorldAccountImageUnderOsLockForLab(
                                account,owner,gen,file->{
                                    Files.write(file,changed,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.TRUNCATE_EXISTING);
                                });
                    }catch(IOException negative){
                        changedDenied=contains(negative,
                            "R66_ACCOUNT_IMAGE_CHANGED_DURING_REAL_OWNER_OBSERVATION");
                    }finally{
                        Files.write(account,baseline,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    }
                    check(changedDenied,"same-size in-place rewrite accepted");
                    check(sha(Files.readAllBytes(account)).equals(
                        image.imageSha256),
                        "test-only mutation was not restored");
                    // Missing account is never interpreted as proof
                    // of a fresh or safely recoverable account.
                    Path hold=root.resolve("held-baseline.tmp");
                    Files.move(account,hold);
                    try{
                        check(rejected(account,owner,gen,
                            "R66_ACCOUNT_IMAGE_MISSING_NO_POSITIVE_PROOF"),
                            "missing image accepted as positive evidence");
                    }finally{
                        Files.move(hold,account);
                    }
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==1,
                        "observation released owner token");
                    check(!Files.exists(
                        account.resolveSibling(
                            account.getFileName()+".g21385-ledger-head"),
                        LinkOption.NOFOLLOW_LINKS),
                        "real-account inspection wrote LAB HEAD");
                }
                until(()->live.players().size()==0,
                    "real World unregister");
                listener.waitCompleted();
            }
            until(()->LocalJvmAccountSessionLease.activeCountForTest()==0,
                "session lease retirement");
            check(rejected(account,owner,gen,
                "R65_NO_CURRENT_REAL_WORLD_SESSION_TOKEN"),
                "released World owner still obtained image evidence");
            check(world.persistence().failedCount()==0,
                "real healthy session final save failed");
            world.close();
            check(world.closed(),"World not closed");
            System.out.println("R66_REAL_WORLD_ACCOUNT_IMAGE_BINDING_PASS"+
                " genuineTcpSession=true"+
                " exactWorldObjectAndGeneration=true"+
                " originalHeldOsLock=true"+
                " boundedDoubleSha256=true"+
                " sameSizeInPlaceRewriteDenied=true"+
                " missingAccountNoGrant=true"+
                " postReleaseObservationDenied=true"+
                " noRealAccountAutoRecovery=true"+
                " noDurableWorldEpochOrFinalAck=true"+
                " allAuthorityFlagsFalse=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path f:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        exercise();
    }
}
