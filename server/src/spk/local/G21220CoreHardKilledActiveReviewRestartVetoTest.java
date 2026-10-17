package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.220: real hard-killed World JVM with a durably forced,
 * never-automatically-retired G21.188 ACTIVE intent. A new JVM can
 * acquire the released OS lock but MUST fail G21.189 admission veto.
 * This test makes no positive crash recovery/epoch/grant claim.
 */
public final class G21220CoreHardKilledActiveReviewRestartVetoTest {
    private static final String USER="g21220-crashed-review";
    private static final String READY="G21220_DURABLE_ACTIVE_WORLD_READY";
    private G21220CoreHardKilledActiveReviewRestartVetoTest(){}
    private static void check(boolean ok,String stage){
        if(!ok)throw new AssertionError("G21.220 "+stage);
    }

    private static void enable(Path root){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalReviewIntentAdmissionVeto.PROPERTY,"true");
        System.setProperty(LocalOneShotActiveReviewOnAdmission.PROPERTY,"true");
    }

    private static void child(Path root,Path ready)throws Exception{
        enable(root);
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(60_000L,repository);
        LocalJvmAccountSessionLease.Token token=
            LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
        check(token!=null,"child did not acquire session lease");
        LocalReviewIntentAdmissionVeto.requireConfiguration(true,true);
        LocalOneShotActiveReviewOnAdmission.requireConfiguration(
            true,true,true);
        // BEFORE World registration, force actual G21.188 negative
        // file+directory evidence and NEVER authorize its retirement.
        LocalReviewIntentAdmissionVeto.requireAdmissible(true,true,token);
        LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration(
            true,true,token);
        Path account=LocalAccountProfiles.accountFile(USER);
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,USER);
        token.bindAdmittedWorldOwner(player,generation);
        world.start();
        check(LocalJvmAccountSessionLease.activeCountForTest()==1,
            "child lost in-memory token before crash");
        Files.writeString(ready,READY,StandardCharsets.UTF_8);
        System.out.println(
            "G21220_CHILD_DURABLE_INTENT_AND_SESSION_HELD_READY"+
            " activeMarkerFileAndDirectoryForced=true"+
            " registeredWorldGeneration=true"+
            " finalSaveReceipt=false");
        System.out.flush();
        // This JVM must NEVER reach unregister/final-save/lock close:
        // the parent kills the exact process after readiness proof.
        for(;;)Thread.sleep(1_000L);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21220-active-kill-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalReviewIntentAdmissionVeto.PROPERTY,
            LocalOneShotActiveReviewOnAdmission.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        enable(root);
        Path primary=root.resolve("opensrc.properties");
        Path account=LocalAccountProfiles.accountFile(USER);
        Path marker=LocalDurableAccountReviewIntentPrimitive
            .markerPath(account);
        Path ready=root.resolve("ready.txt");
        Process child=null;
        World candidate=null;
        LocalJvmAccountSessionLease.Token acquired=null;
        try{
            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder launch=new ProcessBuilder(
                java.toString(),"-cp",
                System.getProperty("java.class.path"),
                G21220CoreHardKilledActiveReviewRestartVetoTest
                    .class.getName(),
                "--child",root.toString(),ready.toString());
            launch.redirectErrorStream(true);
            child=launch.start();
            boolean readyObserved=false;
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(20L);
            while(System.nanoTime()<deadline){
                if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)&&
                   READY.equals(Files.readString(
                       ready,StandardCharsets.UTF_8))){
                    readyObserved=true;
                    break;
                }
                if(!child.isAlive())break;
                Thread.sleep(50L);
            }
            check(readyObserved&&child.isAlive(),
                "child never owned World and durable review marker");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "forced review marker missing");

            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            candidate=World.isolatedForTest(60_000L,repository);
            boolean deniedWhileAlive=false;
            try{
                LocalJvmAccountSessionLease.Token unexpected=
                    LocalJvmAccountSessionLease.acquire(
                        candidate.persistence(),USER);
                if(unexpected!=null)
                    unexpected.releaseAfterInitializationFailure();
            }catch(IllegalStateException expected){
                deniedWhileAlive=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "CROSS_JVM_ACCOUNT_SESSION_DENIED");
            }
            check(deniedWhileAlive,
                "other JVM acquired live child's OS owner lock");

            child.destroyForcibly();
            check(child.waitFor(10L,TimeUnit.SECONDS),
                "hard-killed owner JVM did not terminate");
            check(!child.isAlive(),"old session JVM still alive");

            // G21.217 demonstrated exactly this OS lock reopens
            // after death. G21.220 verifies a separate and stronger
            // negative: forced ACTIVE sidecar is still present and
            // must veto hydration/World membership in next JVM.
            acquired=LocalJvmAccountSessionLease.acquire(
                candidate.persistence(),USER);
            check(acquired!=null,
                "new JVM OS lock did not become acquireable");
            boolean reviewDenied=false;
            try{
                LocalReviewIntentAdmissionVeto.requireAdmissible(
                    true,true,acquired);
            }catch(IllegalStateException blocked){
                reviewDenied=blocked.getMessage()!=null&&
                    blocked.getMessage().contains(
                        "REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
            }
            check(reviewDenied,
                "crashed owner's forced ACTIVE marker did not veto");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "restart veto erroneously retired marker");
            check(candidate.players().size()==0,
                "vetoed candidate unexpectedly admitted World member");

            System.out.println(
                "G21220_KILLED_JVM_DURABLE_ACTIVE_REVIEW_RESTART_VETO_PASS"+
                " originalWorldOwnerRegistered=true"+
                " activeMarkerForcedBeforeRegistration=true"+
                " liveOtherJVMBlockedByOsLock=true"+
                " realChildProcessHardKilled=true"+
                " osSessionLockReacquiredAfterDeath=true"+
                " durableMarkerStillPresent=true"+
                " reviewVetoDeniedBeforeHydration=true"+
                " autoMarkerRetirement=false"+
                " recoveredOldSnapshot=false"+
                " positiveCrashRecovery=false"+
                " durableOwnerEpoch=false"+
                " rewardReplay=false");
        }finally{
            try{
                if(acquired!=null)
                    acquired.releaseAfterInitializationFailure();
                if(candidate!=null)candidate.close();
                if(child!=null&&child.isAlive()){
                    child.destroyForcibly();
                    child.waitFor(5L,TimeUnit.SECONDS);
                }
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(old[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],old[i]);
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
        if(args.length==3&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]));
            return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}
