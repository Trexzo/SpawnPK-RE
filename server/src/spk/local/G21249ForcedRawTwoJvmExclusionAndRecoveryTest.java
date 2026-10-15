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
 * G21.249: independent JVM forced RAW writer excludes another held
 * cooperating account RAW lock and succeeds after release.
 * No crash owner epoch or final-disconnect acknowledgement.
 */
public final class G21249ForcedRawTwoJvmExclusionAndRecoveryTest {
    private static final String USER="g21249-forced-twojvm";
    private static final String NS="core_g21249";
    private static final String KEY="extension.core_g21249.phase";
    private G21249ForcedRawTwoJvmExclusionAndRecoveryTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.249 "+why);
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
    private static PlayerSnapshot snapshot(String value){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap("phase",value));
        return PlayerSnapshotCodec.capture(USER,player);
    }
    private static String disk(FilePlayerRepository repo)throws IOException{
        Optional<PlayerSnapshot> current=repo.load(USER);
        check(current.isPresent(),"named account absent");
        return current.get().values().get(KEY);
    }
    private static void child(Path root,String phase)throws Exception{
        flags(root);
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        check("PARENT_BASE".equals(disk(repo)),
            "parent account unexpectedly missing before child");
        if("DENY".equals(phase)){
            boolean rejected=false;
            try{repo.save(snapshot("SHOULD_NEVER_PUBLISH"));}
            catch(IOException expected){
                rejected=String.valueOf(expected.getMessage()).contains(
                    "CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED");
            }
            check(rejected,"held RAW OS reservation did not exclude child");
            check("PARENT_BASE".equals(disk(repo)),
                "denied child changed account bytes");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "child leaked local pending RAW reservation");
            System.out.println(
                "G21249_CHILD_HELD_RAW_LOCK_DENIED_PASS");
        }else if("ALLOW".equals(phase)){
            repo.save(snapshot("AFTER_RELEASE_FORCED"));
            check("AFTER_RELEASE_FORCED".equals(disk(repo)),
                "released session still blocked forced child");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "successful forced child leaked RAW reservation");
            System.out.println(
                "G21249_CHILD_RELEASED_FORCED_WRITER_PASS");
        }else throw new AssertionError("unknown child phase");
    }
    private static String spawn(Path root,String phase)throws Exception{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder command=new ProcessBuilder(
            java.toString(),"-cp",
            System.getProperty("java.class.path"),
            G21249ForcedRawTwoJvmExclusionAndRecoveryTest
                .class.getName(),
            "--child",root.toString(),phase);
        command.redirectErrorStream(true);
        Process process=command.start();
        if(!process.waitFor(25L,TimeUnit.SECONDS)){
            process.destroyForcibly();
            process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.249 foreign JVM timed out");
        }
        String output=new String(
            process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,
            "foreign JVM failed phase="+phase+" output="+output);
        return output;
    }
    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21249-forced-twojvm-");
        String[] props={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] original=new String[props.length];
        for(int i=0;i<props.length;i++)
            original[i]=System.getProperty(props[i]);
        flags(root);
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(snapshot("PARENT_BASE"));
            check("PARENT_BASE".equals(disk(repo)),
                "forced parent baseline failed");
            Path account=LocalAccountProfiles.accountFile(USER);
            check(Files.isRegularFile(
                    account,LinkOption.NOFOLLOW_LINKS),
                "account file missing");
            try(LocalJvmAccountSessionLease.RawWriteReservation held=
                    LocalJvmAccountSessionLease.reserveRawWrite(
                        account,USER)){
                check(held!=null,"parent RAW reservation missing");
                String rejected=spawn(root,"DENY");
                check(rejected.contains(
                    "G21249_CHILD_HELD_RAW_LOCK_DENIED_PASS"),
                    "child denial marker missing");
                check("PARENT_BASE".equals(disk(repo)),
                    "held parent bytes changed");
            }
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "parent RAW reservation leaked after release");
            String accepted=spawn(root,"ALLOW");
            check(accepted.contains(
                "G21249_CHILD_RELEASED_FORCED_WRITER_PASS"),
                "child released marker missing");
            check("AFTER_RELEASE_FORCED".equals(disk(repo)),
                "parent did not observe forced foreign postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test leaked account sessions");
            System.out.println(
                "G21249_FORCED_RAW_TWO_JVM_EXCLUSION_RECOVERY_PASS"+
                " heldParentRawDeniesForeignForcedWriter=true"+
                " foreignForcedWriterSucceedsAfterRelease=true"+
                " actualSeparateJavaProcesses=true"+
                " oneAccountPostimageConfirmed=true"+
                " durableAccountEpoch=false"+
                " finalSaveAck=false");
        }finally{
            for(int i=0;i<props.length;i++){
                if(original[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],original[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),args[2]);
        else if(args.length==0)parent();
        else throw new IllegalArgumentException("invalid G21.249 args");
    }
}
