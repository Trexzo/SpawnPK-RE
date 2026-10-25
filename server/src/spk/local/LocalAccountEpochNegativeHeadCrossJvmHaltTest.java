package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R158: an abruptly halted JVM and an already-alive separate contender.
 * Both force distinct immutable objects before the victim is released.
 * Victim Runtime.halt(73) occurs at one selected negative HEAD cutpoint.
 * A third JVM observes the resulting negative-only state after the
 * survivor completes or is denied. No World, login, real power failure,
 * durable owner, recovery, release, or final-save ACK is involved.
 */
public final class LocalAccountEpochNegativeHeadCrossJvmHaltTest {
    private static final int HALT_EXIT=73;
    private static final int DENIED_EXIT=17;
    private static int checks;
    private LocalAccountEpochNegativeHeadCrossJvmHaltTest(){}

    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("R158 "+reason);
    }

    private static String name(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"account leaf");
        return leaf.substring(0,leaf.length()-".properties".length());
    }

    private static byte[] snapshot(Path account,int role){
        return ("R158 forced negative immutable snapshot: "+
            name(account)+" role="+role).getBytes(StandardCharsets.US_ASCII);
    }

    private static LocalAccountEpochHeadCodec.Record record(Path account,
            int role)throws IOException{
        LocalAccountEpochHeadCodec.State state=role==0?
            LocalAccountEpochHeadCodec.State.ACTIVE:
            LocalAccountEpochHeadCodec.State.REVIEW;
        return new LocalAccountEpochHeadCodec.Record(
            name(account),1,1,201+role,state,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(
                snapshot(account,role)));
    }

    private static void awaitGate(Path gate)throws IOException{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(!Files.exists(gate,LinkOption.NOFOLLOW_LINKS)){
            if(System.nanoTime()>until)
                throw new IOException("R158_GATE_TIMEOUT "+gate);
            try{Thread.sleep(10);}
            catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException("R158_GATE_INTERRUPTED",interrupted);
            }
        }
    }

    private static void victim(Path account,
            LocalAccountEpochNegativeHeadPublication.Stage target,
            Path ready,Path go,Path cutpoint)throws Exception{
        LocalAccountEpochNegativeHeadPublication.publish(
            account,record(account,0),snapshot(account,0),stage->{
                if(stage==LocalAccountEpochNegativeHeadPublication.Stage
                        .OBJECT_FORCED_BEFORE_HEAD){
                    Files.createFile(ready);
                    awaitGate(go);
                }
                if(stage==target){
                    Files.createFile(cutpoint);
                    System.out.println("R158_VICTIM_CUTPOINT "+
                        target.name()+" exit=73");
                    System.out.flush();
                    Runtime.getRuntime().halt(HALT_EXIT);
                }
            });
        throw new AssertionError("R158 victim returned without halt");
    }

    private static void contender(Path account,Path ready,Path go)
            throws Exception{
        try{
            LocalAccountEpochNegativeHeadPublication.publish(
                account,record(account,1),snapshot(account,1),stage->{
                    if(stage==LocalAccountEpochNegativeHeadPublication.Stage
                            .OBJECT_FORCED_BEFORE_HEAD){
                        Files.createFile(ready);
                        awaitGate(go);
                    }
                });
            System.out.println("R158_CONTENDER_PUBLISHED "+
                "negativeOnly=true noAuthority=true");
        }catch(FileAlreadyExistsException expected){
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS),
                "contender rejected before ready barrier");
            System.out.println("R158_CONTENDER_CREATE_NEW_DENIED "+
                "orphanRetained=true noAuthority=true");
            System.out.flush();
            System.exit(DENIED_EXIT);
        }
    }

    private static void observer(Path account,int expectedRole)
            throws Exception{
        LocalAccountEpochHeadCodec.Record expected=
            record(account,expectedRole);
        LocalAccountEpochNegativeHeadPublication.Observation view=
            LocalAccountEpochNegativeHeadPublication.inspectNegative(account);
        check(view.untrusted.snapshotSha256.equals(
                  expected.snapshotSha256)&&
              view.untrusted.state==expected.state&&
              view.untrusted.worldGeneration==expected.worldGeneration&&
              !view.admissionAuthorized&&!view.releaseAuthorized&&
              !view.recoveryAuthorized,
              "independent restart accepted untrusted head");
        System.out.println("R158_INDEPENDENT_JVM_NEGATIVE_DENY_PASS"+
            " role="+expectedRole+
            " noAdmissionReleaseRecoveryOrFinalAck=true");
    }

    private static String javaBinary(){
        String exe=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        return new File(new File(System.getProperty("java.home"),"bin"),
            exe).getAbsolutePath();
    }

    private static Process launch(Path log,String... args)throws IOException{
        String[] command=new String[4+args.length];
        command[0]=javaBinary();
        command[1]="-cp";
        command[2]=System.getProperty("java.class.path");
        command[3]=LocalAccountEpochNegativeHeadCrossJvmHaltTest.class
            .getName();
        System.arraycopy(args,0,command,4,args.length);
        return new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }

    private static String readLog(Path path)throws IOException{
        return new String(Files.readAllBytes(path),
            StandardCharsets.UTF_8);
    }

    private static void awaitReady(Path first,Path second,
            Process a,Process b)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(!Files.exists(first,LinkOption.NOFOLLOW_LINKS)||
              !Files.exists(second,LinkOption.NOFOLLOW_LINKS)){
            check(a.isAlive()&&b.isAlive(),
                "process exited before both objects were forced");
            if(System.nanoTime()>until)
                throw new IOException("R158_DUAL_READY_TIMEOUT");
            Thread.sleep(10L);
        }
    }

    private static int awaitExit(Process process,String who)
            throws Exception{
        if(!process.waitFor(35,TimeUnit.SECONDS))
            throw new IOException("R158_PROCESS_TIMEOUT "+who);
        return process.exitValue();
    }

    private static void dispose(Process p)throws Exception{
        if(p!=null&&p.isAlive()){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
        }
    }

    private static void runCase(Path root,boolean existing,
            LocalAccountEpochNegativeHeadPublication.Stage stage)
            throws Exception{
        String id="r158case"+checks;
        Path account=root.resolve(id+".properties");
        byte[] old=("R158 old account byte witness "+id)
            .getBytes(StandardCharsets.US_ASCII);
        Object oldKey=null;
        if(existing){
            Files.write(account,old);
            oldKey=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                .fileKey();
        }
        Path victimReady=root.resolve(id+".victim.ready");
        Path contenderReady=root.resolve(id+".contender.ready");
        Path victimGo=root.resolve(id+".victim.go");
        Path contenderGo=root.resolve(id+".contender.go");
        Path cutpoint=root.resolve(id+".cutpoint");
        Path victimLog=root.resolve(id+".victim.log");
        Path contenderLog=root.resolve(id+".contender.log");
        Process victim=null,contender=null;
        int contenderExit;
        try{
            victim=launch(victimLog,"--victim",account.toString(),
                stage.name(),victimReady.toString(),
                victimGo.toString(),cutpoint.toString());
            contender=launch(contenderLog,"--contender",account.toString(),
                contenderReady.toString(),contenderGo.toString());
            awaitReady(victimReady,contenderReady,victim,contender);
            // Both distinct immutable objects are already forced. Release
            // the victim alone so the chosen cutpoint is deterministic.
            Files.createFile(victimGo);
            int victimExit=awaitExit(victim,"victim");
            check(victimExit==HALT_EXIT&&
                Files.exists(cutpoint,LinkOption.NOFOLLOW_LINKS)&&
                readLog(victimLog).contains(
                    "R158_VICTIM_CUTPOINT "+stage.name()+" exit=73"),
                "victim did not hard-halt at exact cutpoint: "+
                readLog(victimLog));
            // The contender was already alive BEFORE the victim died.
            // Release it only after the abrupt exit to isolate the
            // forensic postimage of each unsafe publishing stage.
            Files.createFile(contenderGo);
            contenderExit=awaitExit(contender,"contender");
        }finally{
            dispose(victim);
            dispose(contender);
        }
        boolean headCreatedByVictim=stage!=
            LocalAccountEpochNegativeHeadPublication.Stage
                .OBJECT_FORCED_BEFORE_HEAD;
        check(contenderExit==(headCreatedByVictim?DENIED_EXIT:0),
            "wrong contender result at "+stage+" exit="+contenderExit+
            " log="+readLog(contenderLog));
        check(readLog(contenderLog).contains(headCreatedByVictim?
                "R158_CONTENDER_CREATE_NEW_DENIED":
                "R158_CONTENDER_PUBLISHED"),
            "unexpected contender outcome marker");
        int winningRole=headCreatedByVictim?0:1;
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        LocalAccountEpochHeadCodec.Record winner=
            record(account,winningRole);
        check(Arrays.equals(Files.readAllBytes(head),
                LocalAccountEpochHeadCodec.encode(winner)),
            "canonical negative HEAD bytes changed");
        LocalAccountEpochNegativeHeadPublication.Observation view=
            LocalAccountEpochNegativeHeadPublication.inspectNegative(account);
        check(!view.admissionAuthorized&&!view.releaseAuthorized&&
            !view.recoveryAuthorized&&
            view.untrusted.snapshotSha256.equals(winner.snapshotSha256),
            "negative HEAD incorrectly authorized release");
        for(int role=0;role<=1;role++){
            String digest=record(account,role).snapshotSha256;
            LocalAccountEpochSnapshotObjects.verify(account,digest,null);
            check(Files.isRegularFile(
                LocalAccountEpochSnapshotObjects.objectPath(account,digest),
                LinkOption.NOFOLLOW_LINKS),
                "immutable object missing role="+role);
        }
        if(existing){
            check(Arrays.equals(old,Files.readAllBytes(account))&&
                oldKey.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()),"legacy account changed");
        }else{
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "new account unexpectedly bootstrapped");
        }

        Path observerLog=root.resolve(id+".observer.log");
        Process fresh=null;
        try{
            fresh=launch(observerLog,"--observer",account.toString(),
                Integer.toString(winningRole));
            check(awaitExit(fresh,"observer")==0&&
                readLog(observerLog).contains(
                    "R158_INDEPENDENT_JVM_NEGATIVE_DENY_PASS role="+
                    winningRole),
                "independent restart failed to deny: "+
                readLog(observerLog));
        }finally{dispose(fresh);}

        LocalAccountEpochHeadCodec.Record unrelated=record(account,2);
        Path unrelatedObject=LocalAccountEpochSnapshotObjects.objectPath(
            account,unrelated.snapshotSha256);
        check(!Files.exists(unrelatedObject,LinkOption.NOFOLLOW_LINKS),
            "new retry object already existed");
        try{
            LocalAccountEpochNegativeHeadPublication.publish(account,
                unrelated,snapshot(account,2));
            throw new AssertionError("R158 replay reused negative HEAD");
        }catch(IOException denied){
            check(!Files.exists(unrelatedObject,LinkOption.NOFOLLOW_LINKS),
                "denied retry wrote another immutable object");
        }
        check(Arrays.equals(Files.readAllBytes(head),
                LocalAccountEpochHeadCodec.encode(winner)),
            "replay mutated exact negative HEAD");
        checks++;
        System.out.println("R158_TWO_JVM_HALT_NEGATIVE_HEAD_PASS"+
            " case="+checks+" stage="+stage+
            " existingLegacyAccount="+existing+
            " victimHalt73=true"+
            " contenderWon="+!headCreatedByVictim+
            " bothObjectsRetained=true"+
            " independentJVMRestartDenied=true"+
            " noAccountMutationOrPositiveAuthority=true");
    }

    public static void main(String[] args)throws Exception{
        if(args.length==6&&"--victim".equals(args[0])){
            victim(Paths.get(args[1]),
                LocalAccountEpochNegativeHeadPublication.Stage
                    .valueOf(args[2]),
                Paths.get(args[3]),Paths.get(args[4]),
                Paths.get(args[5]));
            return;
        }
        if(args.length==4&&"--contender".equals(args[0])){
            contender(Paths.get(args[1]),Paths.get(args[2]),
                Paths.get(args[3]));
            return;
        }
        if(args.length==3&&"--observer".equals(args[0])){
            observer(Paths.get(args[1]),Integer.parseInt(args[2]));
            return;
        }
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r158-negative-jvm-halt-");
        try{
            for(boolean existing:new boolean[]{false,true})
                for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                        LocalAccountEpochNegativeHeadPublication.Stage.values())
                    runCase(root,existing,stage);
            check(checks==8,"expected eight staged two-JVM cutpoints");
            System.out.println("R158_TWO_JVM_HALT_MATRIX_PASS"+
                " cases=8 forcedHaltJvms=8 contenderJvms=8"+
                " independentObserverJvms=8"+
                " noDurableEpochWorldLoginOrPowerLossClaim=true");
        }finally{
            // Clean ONLY this isolated temporary regression directory.
            // No production evidence removal/replay path is added.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}
