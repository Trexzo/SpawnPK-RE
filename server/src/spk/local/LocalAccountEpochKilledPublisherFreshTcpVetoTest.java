package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R165: independent publisher JVM halts abruptly at four R154 negative
 * publication boundaries. Another fresh JVM then creates an isolated
 * World and tries the real synthetic LocalSession TCP login. Retained
 * negative HEAD/orphan must deny admission, with no automatic recovery.
 *
 * JVM halt is NOT power failure, multi-file durability certification,
 * original graphical client acceptance or positive owner settlement.
 */
public final class LocalAccountEpochKilledPublisherFreshTcpVetoTest {
    private static final int HALT_CODE=73;
    private static int cases;
    private LocalAccountEpochKilledPublisherFreshTcpVetoTest(){}

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R165 "+reason);
    }
    private static byte[] bytes(Path account){
        return ("R165 immutable forced snapshot before hard halt "+
            account.getFileName()).getBytes(StandardCharsets.US_ASCII);
    }
    private static LocalAccountEpochHeadCodec.Record record(Path account)
            throws IOException{
        return new LocalAccountEpochHeadCodec.Record(
            "opensrc",1,1,165,
            LocalAccountEpochHeadCodec.State.REVIEW,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(bytes(account)));
    }
    private static void killedPublisher(
        Path account,LocalAccountEpochNegativeHeadPublication.Stage target,
        Path cutpoint)throws Exception{
        LocalAccountEpochNegativeHeadPublication.publish(
            account,record(account),bytes(account),stage->{
                if(stage==target){
                    Files.createFile(cutpoint);
                    System.out.println("R165_PUBLISHER_HALTED_AT_"+target);
                    System.out.flush();
                    Runtime.getRuntime().halt(HALT_CODE);
                }
            });
        throw new AssertionError("R165 publisher unexpectedly returned");
    }
    private static String javaBinary(){
        return new File(new File(System.getProperty("java.home"),"bin"),
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").getAbsolutePath();
    }
    private static Process start(Path log,String className,String... args)
            throws IOException{
        String[] command=new String[4+args.length];
        command[0]=javaBinary();
        command[1]="-cp";
        command[2]=System.getProperty("java.class.path");
        command[3]=className;
        System.arraycopy(args,0,command,4,args.length);
        return new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }
    private static int exit(Process p,String which)throws Exception{
        if(!p.waitFor(40L,TimeUnit.SECONDS))
            throw new IOException("R165_PROCESS_TIMEOUT "+which);
        return p.exitValue();
    }
    private static String output(Path log)throws IOException{
        return new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
    }
    private static void cleanup(Process process)throws Exception{
        if(process!=null&&process.isAlive()){
            process.destroyForcibly();
            process.waitFor(10L,TimeUnit.SECONDS);
        }
    }

    private static void oneCase(boolean preexisting,
        LocalAccountEpochNegativeHeadPublication.Stage stage)
            throws Exception{
        Path root=Files.createTempDirectory("r165-killed-to-fresh-tcp-");
        String old=System.getProperty("spk.local.accountFile");
        Path account=root.resolve("opensrc.properties");
        System.setProperty("spk.local.accountFile",account.toString());
        try{
            if(preexisting){
                new FilePlayerRepository(
                    LocalAccountProfiles::accountFile).save(
                        PlayerSnapshotCodec.capture(
                            "opensrc",new WorldPlayer()));
            }
            byte[] oldBytes=preexisting?Files.readAllBytes(account):null;
            Object oldKey=preexisting?
                Files.readAttributes(account,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey():null;
            Path cutpoint=root.resolve("publisher-cutpoint");
            Path publishLog=root.resolve("publisher.log");
            Process publisher=null;
            try{
                publisher=start(publishLog,
                    LocalAccountEpochKilledPublisherFreshTcpVetoTest
                        .class.getName(),
                    "--publisher",account.toString(),stage.name(),
                    cutpoint.toString());
                int status=exit(publisher,"publisher");
                check(status==HALT_CODE&&
                    Files.exists(cutpoint,LinkOption.NOFOLLOW_LINKS)&&
                    output(publishLog).contains(
                        "R165_PUBLISHER_HALTED_AT_"+stage),
                    "missing exact halt: exit="+status+
                    " log="+output(publishLog));
            }finally{cleanup(publisher);}

            Path immutable=LocalAccountEpochSnapshotObjects.objectPath(
                account,record(account).snapshotSha256);
            check(Files.isRegularFile(immutable,LinkOption.NOFOLLOW_LINKS),
                "forced snapshot not retained");
            LocalAccountEpochSnapshotObjects.verify(
                account,record(account).snapshotSha256,null);
            boolean hasHead=stage!=
                LocalAccountEpochNegativeHeadPublication.Stage
                    .OBJECT_FORCED_BEFORE_HEAD;
            Path head=LocalAccountEpochNegativeHeadPublication.headPath(
                account);
            check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==hasHead,
                "wrong head presence after hard halt");
            byte[] exactHead=hasHead?Files.readAllBytes(head):null;
            if(hasHead){
                check(Arrays.equals(exactHead,
                    LocalAccountEpochHeadCodec.encode(record(account))),
                    "partial HEAD has unexpected exact bytes");
            }
            check(preexisting?
                Arrays.equals(oldBytes,Files.readAllBytes(account))&&
                    oldKey.equals(Files.readAttributes(account,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                        .fileKey()):
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "hard-halted publisher changed original account");
            // The R164 fresh-World subprocess executes a genuine
            // LocalSession synthetic-revision317 TCP login attempt.
            // It also checks a read-only negative repository admission.
            String kind=hasHead?"COMPLETE_HEAD":"ORPHAN_OBJECT";
            Path freshLog=root.resolve("fresh-world.log");
            Process fresh=null;
            try{
                fresh=start(freshLog,
                    LocalAccountEpochFreshJvmTcpRestartVetoTest
                        .class.getName(),
                    "--fresh-world",account.toString(),kind);
                int status=exit(fresh,"fresh World");
                check(status==0&&output(freshLog).contains(
                    "R164_FRESH_PROCESS_TCP_NEGATIVE_DENIED evidence="+kind),
                    "fresh World failed closed test: exit="+status+
                    " log="+output(freshLog));
            }finally{cleanup(fresh);}

            check(preexisting?
                Arrays.equals(oldBytes,Files.readAllBytes(account)):
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "fresh JVM mutated account after denied TCP login");
            check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==hasHead&&
                (!hasHead||Arrays.equals(exactHead,Files.readAllBytes(head))),
                "fresh JVM changed retained head evidence");
            LocalAccountEpochSnapshotObjects.verify(
                account,record(account).snapshotSha256,null);
            cases++;
            System.out.println(
                "R165_KILLED_PUBLISHER_NEW_WORLD_TCP_DENIAL_PASS"+
                " case="+cases+" stage="+stage+
                " accountPreexisting="+preexisting+
                " publisherHalted73=true"+
                " newJvmWorldTcpDenied=true"+
                " immutableEvidenceStillPresent=true"+
                " originalLegacyAccountPreserved=true"+
                " noRecoveryOrFinalAck=true");
        }finally{
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            // Only this isolated synthetic test directory is removed.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--publisher".equals(args[0])){
            killedPublisher(Paths.get(args[1]),
                LocalAccountEpochNegativeHeadPublication.Stage
                    .valueOf(args[2]),Paths.get(args[3]));
            return;
        }
        check(args.length==0,"unexpected CLI");
        for(boolean preexisting:new boolean[]{false,true})
            for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                    LocalAccountEpochNegativeHeadPublication.Stage.values())
                oneCase(preexisting,stage);
        check(cases==8,"eight stage/account hard-halt cases required");
        System.out.println("R165_HARD_HALT_TO_NEW_JVM_TCP_MATRIX_PASS"+
            " cases=8 publisherJvms=8 freshWorldJvms=8"+
            " noOriginalClientPowerLossOrEpochRecoveryProof=true");
    }
}
