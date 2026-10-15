package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.219 TEST ONLY: inode/file identity substitution of the G21.185
 * stable lock name can permit TWO opt-in sessions in different JVMs.
 * NEVER a valid production cleanup, unlock, relog or recovery action.
 */
public final class G21219CoreCrossJvmSessionLockPathReplacementTest {
    private static final String USER="g21219-rename-lock";
    private G21219CoreCrossJvmSessionLockPathReplacementTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.219 "+stage);
    }

    private static void child(Path primary)throws Exception{
        System.setProperty("spk.local.accountFile",primary.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        try(World other=World.isolatedForTest(60_000L,repository)){
            LocalJvmAccountSessionLease.Token second=
                LocalJvmAccountSessionLease.acquire(
                    other.persistence(),USER);
            check(second!=null,
                "child could not lock replacement lock-file object");
            WorldPlayer player=new WorldPlayer();
            long generation=other.registerPlayer(player,USER);
            second.bindAdmittedWorldOwner(player,generation);
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                LocalAccountProfiles.accountFile(USER),
                player,generation);
            check(other.unregisterPlayer(player,generation),
                "child could not detach registered owner");
            second.releaseAfterInitializationFailure();
        }
        System.out.println(
            "G21219_CHILD_REPLACEMENT_LOCK_OBJECT_ALLOWED_NEW_SESSION"+
            " secondRealJvm=true"+
            " secondRegisteredWorldGeneration=true"+
            " oldParentLockWasStillHeld=true");
    }

    private static String fork(Path primary)throws Exception{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder builder=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21219CoreCrossJvmSessionLockPathReplacementTest
                .class.getName(),"--child",primary.toString());
        builder.redirectErrorStream(true);
        Process process=builder.start();
        boolean finished=process.waitFor(20L,TimeUnit.SECONDS);
        if(!finished){
            process.destroyForcibly();
            process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.219 fork timed out");
        }
        String output=new String(
            process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,
            "replacement child failed exit="+process.exitValue()+
            " log="+output);
        check(output.contains(
            "G21219_CHILD_REPLACEMENT_LOCK_OBJECT_ALLOWED_NEW_SESSION"),
            "child lacked independent lock replacement proof");
        return output;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21219-inode-drift-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        Path primary=root.resolve("opensrc.properties");
        System.setProperty(keys[0],primary.toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token held=null;
        long generation=-1L;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            held=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(held!=null,"parent opt-in session token missing");
            player=new WorldPlayer();
            generation=world.registerPlayer(player,USER);
            held.bindAdmittedWorldOwner(player,generation);
            Path account=LocalAccountProfiles.accountFile(USER);
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,player,generation);
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "original stable OS lock file absent");

            // Windows may prohibit renaming an OPEN locked file.
            // Do not advertise inode substitution evidence on such
            // a platform. Hosted Linux must execute full proof.
            if(System.getProperty("os.name").toLowerCase()
                    .contains("win")){
                System.out.println(
                    "G21219_LOCK_NAME_REPLACEMENT_PLATFORM_UNSUPPORTED"+
                    " windowsLockedRenameProhibited=true"+
                    " dualJvmBypassProven=false");
                return;
            }

            BasicFileAttributes before=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(before.fileKey()!=null,
                "filesystem has no inode identity for test");
            Path orphan=lock.resolveSibling(
                lock.getFileName().toString()+".original-still-locked");
            Files.move(lock,orphan,StandardCopyOption.ATOMIC_MOVE);
            check(!Files.exists(lock,LinkOption.NOFOLLOW_LINKS),
                "old lock leaf still at original name");
            Files.createFile(lock);
            BasicFileAttributes oldObject=Files.readAttributes(
                orphan,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes replacement=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(oldObject.fileKey()!=null&&
                    replacement.fileKey()!=null&&
                    !oldObject.fileKey().equals(replacement.fileKey()),
                "lock name replacement did not change file identity");

            // Critical: original OS lock stays HELD on orphaned inode
            // and original JVM token still owns World generation.
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "original owner token lost during name substitution");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,player,generation);
            String output=fork(primary);
            check(output.contains("secondRealJvm=true"),
                "second JVM was not independently admitted");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "foreign session modified original JVM active map");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                account,player,generation);
            check(Files.isRegularFile(orphan,LinkOption.NOFOLLOW_LINKS),
                "original locked inode disappeared");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "replacement lock inode disappeared");

            System.out.println(
                "G21219_TWO_JVM_LOCK_PATH_REPLACEMENT_BYPASS_PASS"+
                " trueIndependentJvms=true"+
                " parentOriginalOsLockStillHeld=true"+
                " lockFileKeyChanged=true"+
                " separateFileObjectAcquired=true"+
                " duplicateWorldGenerationAdmitted=true"+
                " originalParentTokenStillValid=true"+
                " accountSnapshotOrRewardModified=false"+
                " adversarialPathReplacementNotExcluded=true"+
                " authenticatedDurableEpoch=false"+
                " safeRecoveryAuthority=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(held!=null)held.releaseAfterInitializationFailure();
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
        if(args.length==2&&"--child".equals(args[0])){
            child(Path.of(args[1]));
            return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}
