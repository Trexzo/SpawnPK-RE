package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.228 opt-in same-JVM observed account inode alias admission.
 * Covers both pending and committed session Token ownership.
 * Does NOT claim cross-JVM or post-atomic-replace inode fencing.
 */
public final class G21228CoreHardlinkAliasPendingAndLiveSessionGateTest {
    private static final String USER="g21228-hardlink";
    private G21228CoreHardlinkAliasPendingAndLiveSessionGateTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.228 "+stage);
    }
    private static boolean aliasDenied(World world){
        try{
            LocalJvmAccountSessionLease.Token unexpected=
                LocalJvmAccountSessionLease.acquire(
                    world.persistence(),USER);
            if(unexpected!=null)
                unexpected.releaseAfterInitializationFailure();
            return false;
        }catch(IllegalStateException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "SAME_FILE_OBJECT_ALIAS_SESSION_DENIED");
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21228-hardlink-gate-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY
        };
        String[] prior=new String[flags.length];
        for(int i=0;i<flags.length;i++){
            prior[i]=System.getProperty(flags[i]);
            System.setProperty(flags[i],"true");
        }
        World a=null,b=null,c=null;
        LocalJvmAccountSessionLease.Token tokenA=null,tokenC=null;
        WorldPlayer admittedA=null;
        long generationA=-1L;
        CountDownLatch pendingReached=new CountDownLatch(1);
        CountDownLatch allowOriginal=new CountDownLatch(1);
        AtomicReference<LocalJvmAccountSessionLease.Token> result=
            new AtomicReference<>();
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread worker=null;
        try{
            Path first=root.resolve("first.properties");
            Path alias=root.resolve("alias.properties");
            Path unrelated=root.resolve("unrelated.properties");
            Files.writeString(first,"test-only-hardlink-identity");
            Files.createLink(alias,first);
            Files.writeString(unrelated,"unrelated-file-object");
            check(Files.isSameFile(first,alias)&&
                    !Files.isSameFile(first,unrelated),
                "invalid account hardlink fixture");

            a=World.isolatedForTest(60_000L,
                new FilePlayerRepository(name->first));
            b=World.isolatedForTest(60_000L,
                new FilePlayerRepository(name->alias));
            c=World.isolatedForTest(60_000L,
                new FilePlayerRepository(name->unrelated));
            final World ownerA=a;
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
                (path,account)->{
                    if(Thread.currentThread().getName().equals(
                            "g21228-original-pending")){
                        pendingReached.countDown();
                        try{
                            if(!allowOriginal.await(
                                    10L,TimeUnit.SECONDS))
                                throw new AssertionError(
                                    "pending alias fixture timed out");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new AssertionError(
                                "pending alias fixture interrupted");
                        }
                    }
                });
            worker=new Thread(()->{
                try{
                    result.set(LocalJvmAccountSessionLease.acquire(
                        ownerA.persistence(),USER));
                }catch(Throwable failure){error.set(failure);}
            },"g21228-original-pending");
            worker.start();
            check(pendingReached.await(5L,TimeUnit.SECONDS),
                "first exact inode owner did not reach pending claim");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "pending reservation did not protect first file alias");

            check(aliasDenied(b),
                "hardlinked alias entered during pending original owner");
            tokenC=LocalJvmAccountSessionLease.acquire(
                c.persistence(),"g21228-other");
            check(tokenC!=null,
                "independent file object was denied by alias gate");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "unrelated account admission did not complete");

            allowOriginal.countDown();
            worker.join(10_000L);
            check(!worker.isAlive()&&error.get()==null,
                "original token did not finish "+error.get());
            tokenA=result.get();
            check(tokenA!=null,
                "original pending owner was not admitted");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "pending original inode reservation leaked");

            admittedA=new WorldPlayer();
            generationA=a.registerPlayer(admittedA,USER);
            tokenA.bindAdmittedWorldOwner(admittedA,generationA);
            a.start();
            check(aliasDenied(b),
                "hardlinked alias entered after original was committed");
            check(LocalJvmAccountSessionLease.activeCountForTest()==2,
                "two intended independent tokens not present");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                first,admittedA,generationA);
            check(Files.isSameFile(first,alias),
                "test lost inode alias relationship");
            check(!Files.exists(alias.resolveSibling(
                    "alias.properties.session.lck"),
                    LinkOption.NOFOLLOW_LINKS),
                "refused alias admission created second OS lock leaf");

            System.out.println(
                "G21228_HARDLINK_ALIAS_PENDING_AND_LIVE_GATE_PASS"+
                " genuineExistingHardlinkInode=true"+
                " pendingFileKeyAliasDenied=true"+
                " liveTokenFileKeyAliasDenied=true"+
                " unrelatedFileObjectAllowed=true"+
                " originalBoundWorldOwnerPreserved=true"+
                " secondAliasOsLockNotCreated=true"+
                " defaultFeatureOff=true"+
                " rawAliasWriterCovered=false"+
                " afterAtomicReplaceAliasStability=false"+
                " crossJvmAuthenticatedEpoch=false"+
                " crashRecovery=false");
        }finally{
            allowOriginal.countDown();
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(
                null);
            if(worker!=null&&worker.isAlive())worker.join(5_000L);
            try{
                if(a!=null&&admittedA!=null&&generationA>0L)
                    a.unregisterPlayer(admittedA,generationA);
                if(tokenA==null)tokenA=result.get();
                if(tokenA!=null)tokenA.releaseAfterInitializationFailure();
                if(tokenC!=null)tokenC.releaseAfterInitializationFailure();
                if(a!=null)a.close();
                if(b!=null)b.close();
                if(c!=null)c.close();
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(prior[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],prior[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path item:paths.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(item);
                }
            }
        }
    }
}
