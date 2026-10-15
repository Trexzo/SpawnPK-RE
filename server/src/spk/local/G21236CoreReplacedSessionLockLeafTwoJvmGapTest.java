package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.236 TEST ONLY: unlink/recreate a stable OS .session.lck leaf
 * while a true World session still holds the original inode FileLock;
 * another JVM can acquire a lock on the replacement inode.
 *
 * POSIX fixture. Not a normal TCP action nor a safe recovery design.
 */
public final class G21236CoreReplacedSessionLockLeafTwoJvmGapTest {
    private G21236CoreReplacedSessionLockLeafTwoJvmGapTest(){}
    private static final String USER="g21236-lock-leaf";
    private static final String READY="G21236_CHILD_NEW_LOCK_HELD";
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.236 "+reason);
    }
    private static void enable(){
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,"true");
    }
    private static void child(Path root,Path ready,Path finish)
        throws Exception{
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        enable();
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repo);
        LocalJvmAccountSessionLease.Token token=null;
        WorldPlayer player=null;
        long generation=-1;
        try{
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,
                "replacement OS lock object did not admit child");
            player=new WorldPlayer();
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                LocalAccountProfiles.accountFile(USER),player,generation);
            Files.writeString(ready,READY,StandardCharsets.UTF_8);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20L);
            while(!Files.exists(finish,LinkOption.NOFOLLOW_LINKS)){
                check(System.nanoTime()<deadline,
                    "child replacement lock wait timed out");
                Thread.sleep(25L);
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "child lost replacement lock owner");
            System.out.println(
                "G21236_CHILD_NEW_LOCK_OBJECT_SESSION_HELD"+
                " independentJavaProcess=true"+
                " validNewWorldGeneration=true");
        }finally{
            if(player!=null&&generation>0)
                world.unregisterPlayer(player,generation);
            if(token!=null)token.releaseAfterInitializationFailure();
            world.close();
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            return;
        }
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21236-lock-swap-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        enable();
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        Process process=null;
        long generation=-1L;
        Path ready=root.resolve("new-owner-ready.txt");
        Path finish=root.resolve("new-owner-release.txt");
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            world=World.isolatedForTest(60_000L,repo);
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"parent OS lock owner missing");
            player=new WorldPlayer();
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "original stable OS lock file missing");
            Object originalKey=Files.readAttributes(
                lock,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            check(originalKey!=null,"lock fileKey unavailable");

            // TEST-ONLY filesystem adversary with direct write access
            // to lock directory, not an ordinary account/session API.
            Files.delete(lock);
            Files.createFile(lock);
            Object replacementKey=Files.readAttributes(
                lock,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            check(replacementKey!=null&&
                    !originalKey.equals(replacementKey),
                "OS lock inode not substituted");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,player,generation);

            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder builder=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty("java.class.path"),
                G21236CoreReplacedSessionLockLeafTwoJvmGapTest
                    .class.getName(),
                "--child",root.toString(),ready.toString(),
                finish.toString());
            builder.redirectErrorStream(true);
            process=builder.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
            // Existence alone is NOT a complete ready signal:
            // writeString first creates the file and only then writes
            // its payload. Poll for the COMPLETE exact readiness text.
            boolean readyVerified=false;
            while(System.nanoTime()<deadline){
                if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)){
                    String text=Files.readString(
                        ready,StandardCharsets.UTF_8);
                    if(READY.equals(text)){
                        readyVerified=true;
                        break;
                    }
                }
                check(process.isAlive(),
                    "replacement child died before complete readiness");
                Thread.sleep(25L);
            }
            check(readyVerified,
                "replacement child never finished readiness marker");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "parent current token unexpectedly changed");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,player,generation);
            Files.writeString(finish,"release",StandardCharsets.UTF_8);
            check(process.waitFor(10L,TimeUnit.SECONDS),
                "replacement child did not exit");
            String output=new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(process.exitValue()==0&&output.contains(
                    "G21236_CHILD_NEW_LOCK_OBJECT_SESSION_HELD"),
                "replacement child proof failed: "+output);

            System.out.println(
                "G21236_TWO_JVM_LOCK_LEAF_SUBSTITUTION_GAP_PASS"+
                " originalJvmSessionTokenStillHeld=true"+
                " originalOsFileLockInodeStillOpen=true"+
                " lockFileDeletedAndRecreatedTestOnly=true"+
                " replacementFileKeyDifferent=true"+
                " secondJvmAdmittedOnNewOsLockObject=true"+
                " bothWorldGenerationsSimultaneouslyValid=true"+
                " stableLockPathIdentityFenced=false"+
                " uncooperativeFileSystemMutationRequired=true"+
                " durableOwnerEpoch=false"+
                " crashRecovery=false");
        }finally{
            try{
                Files.writeString(finish,"release",StandardCharsets.UTF_8);
                if(process!=null&&process.isAlive()){
                    process.destroyForcibly();
                    process.waitFor(5L,TimeUnit.SECONDS);
                }
                if(world!=null&&player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(old[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],old[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path p:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }
}
