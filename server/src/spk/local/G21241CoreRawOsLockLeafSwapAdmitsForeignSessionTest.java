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
 * G21.241 TEST ONLY: a participating RAW writer holds the OS lock
 * object, but replacing the lock filename permits an independent JVM
 * session to lock a DIFFERENT file object. This is an explicit hostile
 * filesystem mutation, not an ordinary login or save workflow.
 */
public final class G21241CoreRawOsLockLeafSwapAdmitsForeignSessionTest {
    private static final String USER="g21241-raw-os-lock";
    private static final String READY="G21241_CHILD_FOREIGN_SESSION_HELD";
    private G21241CoreRawOsLockLeafSwapAdmitsForeignSessionTest(){}

    private static void check(boolean ok,String stage){
        if(!ok)throw new AssertionError("G21.241 "+stage);
    }

    private static void flags(Path root){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,"true");
    }

    private static void child(Path root,Path ready,Path release)
        throws Exception{
        flags(root);
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repo);
        LocalJvmAccountSessionLease.Token session=null;
        try{
            session=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(session!=null,
                "foreign session did not acquire replaced lock object");
            Files.writeString(ready,READY,StandardCharsets.UTF_8);
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(20L);
            while(!Files.exists(release,LinkOption.NOFOLLOW_LINKS)){
                check(System.nanoTime()<deadline,
                    "foreign session release timeout");
                Thread.sleep(25L);
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "foreign session token not current");
            System.out.println(
                "G21241_CHILD_FOREIGN_SESSION_ON_REPLACED_RAW_LOCK_PASS"+
                " independentJvm=true"+
                " newOsLockObject=true"+
                " oldRawLockStillHeldElsewhere=true");
        }finally{
            if(session!=null)
                session.releaseAfterInitializationFailure();
            world.close();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21241-raw-leaf-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY
        };
        String[] old=new String[props.length];
        for(int i=0;i<props.length;i++)
            old[i]=System.getProperty(props[i]);
        flags(root);

        Path ready=root.resolve("foreign-session-ready");
        Path release=root.resolve("foreign-session-release");
        Process foreign=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            Path lockFile=account.resolveSibling(
                account.getFileName().toString()+".session.lck");

            // Opted-in cooperating RAW writer starts FIRST, holding
            // the same stable OS lock domain as a regular session.
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        account,USER)){
                check(raw!=null,"RAW OS reservation absent");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1,
                    "parent RAW map missing held owner");
                check(Files.isRegularFile(
                    lockFile,LinkOption.NOFOLLOW_LINKS),
                    "stable OS lock file not created");
                Object first=Files.readAttributes(
                    lockFile,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey();
                check(first!=null,"original OS lock fileKey missing");

                // TEST-ONLY adversary who already controls the
                // filesystem parent directory. Existing OS FileLock
                // still binds the old inode even after unlink.
                Files.delete(lockFile);
                Files.createFile(lockFile);
                Object second=Files.readAttributes(
                    lockFile,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey();
                check(second!=null&&!first.equals(second),
                    "actual lock object did not change");

                Path java=Path.of(System.getProperty("java.home"),"bin",
                    "java"+(System.getProperty("os.name").toLowerCase()
                        .contains("win")?".exe":""));
                ProcessBuilder cmd=new ProcessBuilder(
                    java.toString(),"-cp",
                    System.getProperty("java.class.path"),
                    G21241CoreRawOsLockLeafSwapAdmitsForeignSessionTest
                        .class.getName(),
                    "--child",root.toString(),ready.toString(),
                    release.toString());
                cmd.redirectErrorStream(true);
                foreign=cmd.start();
                long deadline=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(20L);
                boolean signalled=false;
                while(System.nanoTime()<deadline){
                    if(Files.isRegularFile(
                        ready,LinkOption.NOFOLLOW_LINKS)&&
                        READY.equals(Files.readString(
                            ready,StandardCharsets.UTF_8))){
                        signalled=true;
                        break;
                    }
                    if(!foreign.isAlive())break;
                    Thread.sleep(25L);
                }
                check(signalled,
                    "independent JVM did not acquire replacement lock");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1,
                    "parent RAW lock lost despite new foreign session");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "parent unexpectedly admitted an in-JVM session");
                // Unlike World SaveTask owner admission, no snapshot
                // is published here: this characterizes coexistence.
                Files.writeString(release,"release",
                    StandardCharsets.UTF_8);
                check(foreign.waitFor(10L,TimeUnit.SECONDS),
                    "foreign JVM did not exit");
                String output=new String(
                    foreign.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
                check(foreign.exitValue()==0&&output.contains(
                    "G21241_CHILD_FOREIGN_SESSION_ON_REPLACED_RAW_LOCK_PASS"),
                    "independent session proof failed "+output);
            }
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "RAW reservation failed to release");
            System.out.println(
                "G21241_RAW_OS_LOCK_LEAF_SWAP_SECOND_JVM_SESSION_GAP_PASS"+
                " actualSeparateJavaProcesses=true"+
                " parentCooperatingRawHeldOriginalOsFileLock=true"+
                " stablePathReplacedWithDifferentFileKey=true"+
                " foreignSessionAdmittedOnNewOsLockObject=true"+
                " parentRawReservationStillHeld=true"+
                " hostileFilesystemMutationRequired=true"+
                " rawAdmissionCannotAuthenticateInodeContinuity=true"+
                " durableCrossJvmEpoch=false"+
                " automaticRecoveryAuthority=false");
        }finally{
            try{
                Files.writeString(release,"release",
                    StandardCharsets.UTF_8);
                if(foreign!=null&&foreign.isAlive()){
                    foreign.destroyForcibly();
                    foreign.waitFor(5L,TimeUnit.SECONDS);
                }
            }finally{
                for(int i=0;i<props.length;i++){
                    if(old[i]==null)System.clearProperty(props[i]);
                    else System.setProperty(props[i],old[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path p:paths.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}
