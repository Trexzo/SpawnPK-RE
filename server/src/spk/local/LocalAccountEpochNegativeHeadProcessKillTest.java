package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R155: abrupt JVM halt cutpoints on the detached, NEGATIVE-only R154
 * prototype. This does not simulate power loss, real World final saves
 * or non-cooperating writers; none of the markers grant login authority.
 */
public final class LocalAccountEpochNegativeHeadProcessKillTest {
    private static final int KILL_EXIT=73;
    private static final byte[] SNAPSHOT=
        "R155 ownerless immutable negative snapshot".getBytes(
            StandardCharsets.US_ASCII);

    private LocalAccountEpochNegativeHeadProcessKillTest(){}

    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError("R155 "+what);
    }

    private static LocalAccountEpochHeadCodec.Record record(
        Path account,LocalAccountEpochHeadCodec.State state)throws IOException{
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"test account leaf");
        String username=leaf.substring(0,leaf.length()-11);
        return new LocalAccountEpochHeadCodec.Record(
            username,1,1,7,state,LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(SNAPSHOT));
    }

    private static void holder(Path account,String stageText)throws Exception{
        LocalAccountEpochNegativeHeadPublication.Stage selected=
            LocalAccountEpochNegativeHeadPublication.Stage.valueOf(stageText);
        LocalAccountEpochHeadCodec.Record negative=record(account,
            LocalAccountEpochHeadCodec.State.ACTIVE);
        LocalAccountEpochNegativeHeadPublication.publish(
            account,negative,SNAPSHOT,reached->{
                if(reached==selected){
                    System.out.println("R155_CHILD_CUTPOINT_REACHED stage="+
                        selected.name());
                    System.out.flush();
                    Runtime.getRuntime().halt(KILL_EXIT);
                }
            });
        throw new AssertionError("R155 holder incorrectly returned");
    }

    /**
     * Read-only independent JVM restart decision. Both a missing HEAD
     * and an intact but permanently negative HEAD deny any authority.
     * No actual login or account writer is invoked.
     */
    private static void observer(Path account,String stageText)throws Exception{
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        String classification;
        try{
            LocalAccountEpochNegativeHeadPublication.Observation observation=
                LocalAccountEpochNegativeHeadPublication.inspectNegative(account);
            check(observation.untrusted.state==
                    LocalAccountEpochHeadCodec.State.ACTIVE &&
                !observation.admissionAuthorized &&
                !observation.releaseAuthorized &&
                !observation.recoveryAuthorized,
                "negative inspection claimed positive authority");
            classification="PRESENT_UNTRUSTED_NEGATIVE";
        }catch(NoSuchFileException absent){
            check(!Files.exists(head,LinkOption.NOFOLLOW_LINKS),
                "HEAD unexpectedly absent only during inspection");
            classification="ABSENT_UNKNOWN_DENY";
        }catch(IOException unsafe){
            check(Files.exists(head,LinkOption.NOFOLLOW_LINKS),
                "uncertain error while head absent");
            classification="UNREADABLE_OR_PARTIAL_DENY";
        }
        System.out.println("R155_INDEPENDENT_JVM_RESTART_DENIED_PASS"+
            " stage="+stageText+
            " classification="+classification+
            " noAdmissionReleaseRecoveryOrFinalACK=true");
    }

    private static String javaBinary(){
        String name=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        return new File(new File(System.getProperty("java.home"),"bin"),
            name).getAbsolutePath();
    }

    private static int child(String mode,Path account,String stage,
                             Path log)throws Exception{
        ProcessBuilder command=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            LocalAccountEpochNegativeHeadProcessKillTest.class.getName(),
            mode,account.toString(),stage);
        command.redirectErrorStream(true);
        command.redirectOutput(log.toFile());
        Process process=command.start();
        try{
            if(!process.waitFor(30L,TimeUnit.SECONDS)){
                process.destroyForcibly();
                process.waitFor(10L,TimeUnit.SECONDS);
                throw new IOException("R155_CHILD_TIMEOUT mode="+mode+
                    " stage="+stage);
            }
            return process.exitValue();
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(10L,TimeUnit.SECONDS);
            }
        }
    }

    private static Object fileKey(Path account)throws IOException{
        return Files.readAttributes(account,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r155-negative-head-halt-");
        int checks=0;
        try{
            for(boolean initialAccountExists:new boolean[]{false,true}){
                for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                        LocalAccountEpochNegativeHeadPublication.Stage.values()){
                    String label="halt"+stage.ordinal()+
                        (initialAccountExists?"existing":"absent");
                    Path account=root.resolve(label+".properties");
                    byte[] old="unchanged healthy old account".getBytes(
                        StandardCharsets.US_ASCII);
                    Object oldKey=null;
                    if(initialAccountExists){
                        Files.write(account,old);
                        oldKey=fileKey(account);
                    }
                    Path log=root.resolve(label+".holder.log");
                    int exit=child("--holder",account,stage.name(),log);
                    String trace=new String(Files.readAllBytes(log),
                        StandardCharsets.UTF_8);
                    check(exit==KILL_EXIT && trace.contains(
                        "R155_CHILD_CUTPOINT_REACHED stage="+stage.name()),
                        "did not reach exact abrupt halt: "+trace);
                    String sha=LocalAccountEpochHeadCodec
                        .digestSnapshot(SNAPSHOT);
                    Path immutable=LocalAccountEpochSnapshotObjects
                        .objectPath(account,sha);
                    LocalAccountEpochSnapshotObjects.verify(
                        account,sha,null);
                    check(Files.isRegularFile(immutable,
                        LinkOption.NOFOLLOW_LINKS),
                        "halt lost immutable object in live filesystem");
                    Path head=LocalAccountEpochNegativeHeadPublication
                        .headPath(account);
                    boolean shouldHaveHead=
                        stage!=LocalAccountEpochNegativeHeadPublication.Stage
                            .OBJECT_FORCED_BEFORE_HEAD;
                    check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==
                        shouldHaveHead,
                        "unexpected HEAD leaf after abrupt halt "+stage);
                    if(initialAccountExists){
                        check(oldKey.equals(fileKey(account))&&
                            Arrays.equals(old,Files.readAllBytes(account)),
                            "halt modified existing legacy account");
                    }else{
                        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                            "halt bootstrapped account unexpectedly");
                    }
                    Path observationLog=root.resolve(label+".observer.log");
                    int observed=child("--observer",account,stage.name(),
                        observationLog);
                    String observation=new String(
                        Files.readAllBytes(observationLog),
                        StandardCharsets.UTF_8);
                    check(observed==0 && observation.contains(
                        "R155_INDEPENDENT_JVM_RESTART_DENIED_PASS stage="+
                        stage.name())&&
                        observation.contains(
                            "noAdmissionReleaseRecoveryOrFinalACK=true"),
                        "independent process did not deny: "+observation);
                    // An abandoned object and/or HEAD must not be
                    // silently reused as a new trusted publication.
                    try{
                        LocalAccountEpochNegativeHeadPublication.publish(
                            account,record(account,
                                LocalAccountEpochHeadCodec.State.ACTIVE),
                            SNAPSHOT);
                        throw new AssertionError(
                            "R155 resumed orphan as successful HEAD");
                    }catch(IOException rejected){
                        // Intentional negative-only retry refusal.
                    }
                    check(Files.isRegularFile(immutable,
                        LinkOption.NOFOLLOW_LINKS)&&
                        Files.exists(head,LinkOption.NOFOLLOW_LINKS)==
                            shouldHaveHead,
                        "retry removed or changed negative evidence");
                    System.out.println(
                        "R155_ABRUPT_JVM_NEGATIVE_HEAD_CUTPOINT_PASS"+
                        " stage="+stage.name()+
                        " initialAccountExists="+initialAccountExists+
                        " exactChildExit=73"+
                        " nextJvmDenied=true"+
                        " orphanObjectRetained=true"+
                        " negativeHeadLeafPresent="+shouldHaveHead+
                        " noLegacyAccountMutation=true"+
                        " noPowerLossProofOrOwnerHandoff=true");
                    checks++;
                }
            }
            check(checks==8,"expected eight exact cutpoints");
            System.out.println(
                "R155_ABRUPT_KILL_MATRIX_PASS cases=8"+
                " independentJvmRestartChecks=8"+
                " noLoginOrWorldIntegration=true"+
                " noDurablePositiveEpochClaim=true"+
                " noPowerLossProof=true");
        }finally{
            // Remove ONLY this disposable regression fixture tree.
            // No API for cleaning up real negative evidence exists.
            try(Stream<Path> entries=Files.walk(root)){
                for(Path item:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(item);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3 && "--holder".equals(args[0])){
            holder(Paths.get(args[1]),args[2]);
            return;
        }
        if(args.length==3 && "--observer".equals(args[0])){
            observer(Paths.get(args[1]),args[2]);
            return;
        }
        check(args.length==0,"unexpected CLI arguments");
        run();
    }
}
