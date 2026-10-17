package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.185: the opt-in OS lock is respected by a different Java process.
 * This narrowly tests cooperating token admission and stable lockfile
 * lifetime, not socket admission or durability after a JVM crash.
 */
public final class G21185CoreCrossJvmSessionFileLockTest {
    private static final String USER="g21185-interprocess";

    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError("G21.185 "+reason);
    }

    private static void child(String expected)throws Exception{
        check("denied".equals(expected)||"admitted".equals(expected),
            "unknown child mode");
        World world=World.isolatedForTest(50L);
        LocalJvmAccountSessionLease.Token token=null;
        try{
            try{
                token=LocalJvmAccountSessionLease.acquire(
                    world.persistence(),USER);
                check("admitted".equals(expected),
                    "another JVM took same account lock concurrently");
                check(token!=null,"child did not take opt-in token");
                System.out.println(
                    "G21185_CHILD_ADMITTED"+
                    " crossProcessLockAcquired=true");
            }catch(IllegalStateException denied){
                check("denied".equals(expected),
                    "unexpected child denial: "+denied);
                check(denied.getMessage()!=null&&
                    denied.getMessage().contains(
                        "G21.185 CROSS_JVM_ACCOUNT_SESSION_DENIED"),
                    "child denial was not the interprocess lock gate");
                System.out.println(
                    "G21185_CHILD_DENIED"+
                    " crossProcessLockBusy=true");
            }
        }finally{
            if(token!=null)
                token.releaseAfterInitializationFailure();
            world.close();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "child JVM token leaked");
    }

    private static String externalJvm(Path accountRoot,Path transcript,
        String expected)throws Exception{
        String javaExecutable=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder process=new ProcessBuilder(
            javaExecutable,
            "-Dspk.local.accountFile="+accountRoot,
            "-D"+LocalJvmAccountSessionLease.PROPERTY+"=true",
            "-D"+LocalCrossJvmAccountSessionLock.PROPERTY+"=true",
            "-cp",
            System.getProperty("java.class.path"),
            G21185CoreCrossJvmSessionFileLockTest.class.getName(),
            "--child",expected
        );
        process.redirectErrorStream(true);
        process.redirectOutput(transcript.toFile());
        Process child=process.start();
        boolean finished=child.waitFor(20L,TimeUnit.SECONDS);
        if(!finished){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.185 child JVM did not exit");
        }
        String output=new String(
            Files.readAllBytes(transcript),
            StandardCharsets.UTF_8
        );
        check(child.exitValue()==0,
            "child JVM exit="+child.exitValue()+" output="+output);
        check(output.contains(
            "denied".equals(expected)
                ?"G21185_CHILD_DENIED crossProcessLockBusy=true"
                :"G21185_CHILD_ADMITTED crossProcessLockAcquired=true"),
            "child JVM did not produce expected outcome: "+output);
        return output;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21185-cross-jvm-");
        Path settings=root.resolve("opensrc.properties");
        String oldAccount=System.getProperty("spk.local.accountFile");
        String oldJvm=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        String oldProcess=System.getProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        System.setProperty("spk.local.accountFile",settings.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        World world=null;
        LocalJvmAccountSessionLease.Token parent=null;
        try{
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "unexpected prior process token");
            world=World.isolatedForTest(50L);
            Path account=LocalAccountProfiles.accountFile(USER)
                .toAbsolutePath().normalize();
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            parent=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(parent!=null&&
                LocalJvmAccountSessionLease.activeCountForTest()==1,
                "parent did not acquire session and process lock");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "cooperative lockfile not regular");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "a lock admission must not synthesize account contents");

            externalJvm(settings,root.resolve("denied-child.log"),
                "denied");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "independent JVM modified parent owner map");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "failed competing process removed lockfile");

            // No actual player was admitted in this low-level test.
            parent.releaseAfterInitializationFailure();
            parent=null;
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "parent did not release its unused token");

            externalJvm(settings,root.resolve("admitted-child.log"),
                "admitted");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "successful new JVM must not unlink lockfile");

            world.close();
            check(world.closed(),"parent World still open");
            boolean publicationClean=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(publicationClean,"publication/watch resources leaked");
            System.out.println(
                "G21185_CROSS_JVM_LOCK_DIAGNOSTICS"+
                " externalJvms=2 concurrentAttemptDenied=true"+
                " postReleaseAttemptAdmitted=true"+
                " stableLockFileRetained=true"+
                " firstTimeAccountNotCreated=true"+
                " zeroJvmLeaseLeaks="+publicationClean);
            System.out.println(
                "G21185_CROSS_JVM_LOCK_PASS"+
                " experimentalOptIn=true defaultOff=true"+
                " cooperatingOsLockExclusion=true"+
                " fullSocketSessionTest=false"+
                " uncooperativeWriterExcluded=false"+
                " crashDurableNegative=false"+
                " unlinkAttackResistant=false"+
                " atomicAccountEpoch=false"+
                " originalClientProven=false"+
                " grants=false replay=false recovery=false");
        }finally{
            if(parent!=null)
                parent.releaseAfterInitializationFailure();
            if(world!=null&&!world.closed())world.close();
            if(oldAccount==null)
                System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",oldAccount);
            if(oldJvm==null)
                System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(LocalJvmAccountSessionLease.PROPERTY,
                oldJvm);
            if(oldProcess==null)
                System.clearProperty(
                    LocalCrossJvmAccountSessionLock.PROPERTY);
            else System.setProperty(
                LocalCrossJvmAccountSessionLock.PROPERTY,oldProcess);
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(args[1]);
            return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}
