package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.254: NEGATIVE actual second JVM crash cutpoint.
 * Forced RAW rename can have happened without an authenticated final
 * outcome after the process dies. No automatic relog/recovery authorized.
 */
public final class G21254ForcedRawHardKillPostMoveNoReceiptTest {
    private static final String USER="g21254-hardkill-raw";
    private static final String READY="G21254_CHILD_AFTER_RAW_ATOMIC_MOVE";
    private static final String KEY="extension.core_g21254.phase";
    private G21254ForcedRawHardKillPostMoveNoReceiptTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.254 "+reason);
    }

    private static void flags(Path root){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.FORCE_PROPERTY,"true");
    }
    private static PlayerSnapshot snapshot(String phase){
        WorldPlayer p=new WorldPlayer();
        p.snapshotExtensions().replaceNamespace(
            "core_g21254",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(USER,p);
    }
    private static String disk(FilePlayerRepository repository)
        throws IOException{
        Optional<PlayerSnapshot> saved=repository.load(USER);
        check(saved.isPresent(),"account snapshot absent");
        return saved.get().values().get(KEY);
    }
    private static void child(Path root,Path ready)throws Exception{
        flags(root);
        Path account=LocalAccountProfiles.accountFile(USER);
        check("BEFORE_CRASH".equals(disk(new FilePlayerRepository(
                LocalAccountProfiles::accountFile))),
            "old persisted checkpoint missing");
        LocalOptInRawTemp.setAfterMoveProbeForTest(path->{
            if(!account.equals(path))
                throw new IOException("G21.254 wrong destination");
            // The parent process observes this cutpoint, then
            // terminates the JVM; we never certify directory force.
            Files.writeString(ready,READY,StandardCharsets.US_ASCII);
            for(;;){
                try{Thread.sleep(25L);}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.254 unexpected child interruption",
                        interrupted);
                }
            }
        });
        new FilePlayerRepository(LocalAccountProfiles::accountFile)
            .save(snapshot("MOVED_WITHOUT_ACK"));
        throw new AssertionError("child unexpectedly returned a success");
    }

    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21254-raw-crash-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        flags(root);
        Process victim=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(snapshot("BEFORE_CRASH"));
            check("BEFORE_CRASH".equals(disk(repo)),
                "baseline forced RAW account not saved");
            Path account=LocalAccountProfiles.accountFile(USER);
            Path signal=root.resolve("child-after-move");
            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder pb=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty("java.class.path"),
                G21254ForcedRawHardKillPostMoveNoReceiptTest
                    .class.getName(),
                "--child",root.toString(),signal.toString());
            pb.redirectErrorStream(true);
            victim=pb.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25L);
            while(!Files.exists(signal,LinkOption.NOFOLLOW_LINKS)&&
                  victim.isAlive()&&System.nanoTime()<deadline)
                Thread.sleep(25L);
            check(Files.exists(signal,LinkOption.NOFOLLOW_LINKS)&&
                READY.equals(Files.readString(signal,StandardCharsets.US_ASCII)),
                "child never reached forced post-atomic-move cutpoint");
            check(victim.isAlive(),
                "child terminated before forced hard-kill");
            victim.destroyForcibly();
            check(victim.waitFor(10L,TimeUnit.SECONDS),
                "forcibly terminated child did not exit");
            check("MOVED_WITHOUT_ACK".equals(disk(repo)),
                "actual replaced disk bytes absent after child kill");
            // The OS lock can be reclaimed despite no normal writer
            // completion. That is NOT evidence the account is safe.
            try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        account,USER)){
                check(raw!=null,
                    "OS account lock did not release after JVM death");
            }
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "parent RAW reservation leaked");
            check(!Files.exists(
                    LocalDurableAccountReviewIntentPrimitive
                        .markerPath(account),LinkOption.NOFOLLOW_LINKS),
                "this test did not activate a session quarantine marker");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "parent unexpectedly has live World lease");
            System.out.println(
                "G21254_FORCED_RAW_HARDKILL_POSTMOVE_NO_RECEIPT_PASS"+
                " childKilledAfterRealAccountMove=true"+
                " postimageOnDiskAfterKill=true"+
                " childCouldNotReturnForcedReceipt=true"+
                " OSLockAvailableAfterCrash=true"+
                " durableOwnerEpoch=false"+
                " safeRestartAdmissionNotProven=true"+
                " finalSaveAcknowledgement=false");
        }finally{
            if(victim!=null&&victim.isAlive()){
                victim.destroyForcibly();
                victim.waitFor(5L,TimeUnit.SECONDS);
            }
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),Path.of(args[2]));
        else if(args.length==0)parent();
        else throw new IllegalArgumentException("G21.254 invalid args");
    }
}
