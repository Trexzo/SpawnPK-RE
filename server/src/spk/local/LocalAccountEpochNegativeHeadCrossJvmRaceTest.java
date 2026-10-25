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
 * R157: two independent JVMs compete for the same first NEGATIVE HEAD.
 * Both publish distinct forced immutable objects and block at the same
 * cutpoint before either creates HEAD. Exactly one CREATE_NEW may win.
 * This is not a World session, trusted epoch, cross-JVM account lock,
 * power-loss proof or permission for account admission/final-save ACK.
 */
public final class LocalAccountEpochNegativeHeadCrossJvmRaceTest {
    private static final int LOSER_EXIT=17;
    private static int cases;
    private LocalAccountEpochNegativeHeadCrossJvmRaceTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R157 "+why);
    }

    private static String username(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"account leaf");
        return leaf.substring(0,leaf.length()-".properties".length());
    }

    private static byte[] snapshot(Path account,int contestant,int flavor){
        return ("R157 independent process immutable negative snapshot "+
            username(account)+" contestant="+contestant+" flavor="+flavor)
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static LocalAccountEpochHeadCodec.Record record(
            Path account,int contestant,int flavor)throws IOException{
        LocalAccountEpochHeadCodec.State state=
            (contestant+flavor)%2==0?
                LocalAccountEpochHeadCodec.State.ACTIVE:
                LocalAccountEpochHeadCodec.State.REVIEW;
        return new LocalAccountEpochHeadCodec.Record(
            username(account),1L,1L,31L+contestant,state,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(
                snapshot(account,contestant,flavor)));
    }

    private static void publisher(Path account,int contestant,int flavor,
                                  Path ready,Path gate)throws Exception{
        check(contestant==0||contestant==1,"invalid contestant");
        LocalAccountEpochHeadCodec.Record marker=
            record(account,contestant,flavor);
        byte[] bytes=snapshot(account,contestant,flavor);
        try{
            LocalAccountEpochNegativeHeadPublication.Observation result=
                LocalAccountEpochNegativeHeadPublication.publish(
                    account,marker,bytes,stage->{
                        if(stage!=LocalAccountEpochNegativeHeadPublication
                            .Stage.OBJECT_FORCED_BEFORE_HEAD)return;
                        Files.createFile(ready);
                        long deadline=System.nanoTime()+
                            TimeUnit.SECONDS.toNanos(25);
                        while(!Files.exists(gate,LinkOption.NOFOLLOW_LINKS)){
                            if(System.nanoTime()>deadline)
                                throw new IOException(
                                    "R157_GATE_TIMEOUT_NO_PUBLICATION");
                            try{Thread.sleep(10L);}
                            catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R157_GATE_INTERRUPTED",interrupted);
                            }
                        }
                    });
            check(result.untrusted.snapshotSha256.equals(
                    marker.snapshotSha256)&&
                !result.admissionAuthorized&&!result.releaseAuthorized&&
                !result.recoveryAuthorized,
                "winner returned unexpected authority");
            System.out.println("R157_PUBLISH_SUCCESS contestant="+contestant+
                " noAdmissionOrFinalAck=true");
        }catch(FileAlreadyExistsException expected){
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS),
                "loser failed before publication barrier");
            System.out.println(
                "R157_PUBLISH_CREATE_NEW_DENIED contestant="+contestant+
                " immutableObjectRetained=true");
            System.out.flush();
            System.exit(LOSER_EXIT);
        }
    }

    private static void observer(Path account,int winner,int flavor)
            throws Exception{
        LocalAccountEpochHeadCodec.Record marker=
            record(account,winner,flavor);
        LocalAccountEpochNegativeHeadPublication.Observation view=
            LocalAccountEpochNegativeHeadPublication.inspectNegative(account);
        check(view.untrusted.snapshotSha256.equals(
                  marker.snapshotSha256)&&
              view.untrusted.state==marker.state&&
              view.untrusted.worldGeneration==marker.worldGeneration&&
              !view.admissionAuthorized&&!view.releaseAuthorized&&
              !view.recoveryAuthorized,
            "independent restart granted or changed negative authority");
        System.out.println("R157_INDEPENDENT_RESTART_DENIED_PASS"+
            " winner="+winner+" noAdmissionReleaseRecoveryOrFinalAck=true");
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
        command[3]=LocalAccountEpochNegativeHeadCrossJvmRaceTest.class
            .getName();
        System.arraycopy(args,0,command,4,args.length);
        return new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }

    private static void waitForBoth(Path first,Path second,
            Process a,Process b)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(!(Files.exists(first,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(second,LinkOption.NOFOLLOW_LINKS))){
            check(a.isAlive()&&b.isAlive(),
                "publisher exited before dual barrier");
            if(System.nanoTime()>deadline)
                throw new IOException("R157_DUAL_BARRIER_TIMEOUT");
            Thread.sleep(10L);
        }
    }

    private static int await(Process process,String label)throws Exception{
        if(!process.waitFor(30,TimeUnit.SECONDS))
            throw new IOException("R157_PROCESS_TIMEOUT "+label);
        return process.exitValue();
    }

    private static String log(Path path)throws IOException{
        return new String(Files.readAllBytes(path),
            StandardCharsets.UTF_8);
    }

    private static void destroy(Process p)throws Exception{
        if(p!=null&&p.isAlive()){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
        }
    }

    private static void oneCase(Path root,boolean existing,int flavor,
            int round)throws Exception{
        String name="r157"+cases;
        Path account=root.resolve(name+".properties");
        byte[] old=("R157 untouched legacy account "+name).getBytes(
            StandardCharsets.US_ASCII);
        Object previousKey=null;
        if(existing){
            Files.write(account,old);
            previousKey=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        }
        Path first=root.resolve(name+".ready0");
        Path second=root.resolve(name+".ready1");
        Path gate=root.resolve(name+".release");
        Path log0=root.resolve(name+".child0.log");
        Path log1=root.resolve(name+".child1.log");
        Process a=null,b=null;
        int exit0,exit1;
        try{
            a=launch(log0,"--publish",account.toString(),"0",
                Integer.toString(flavor),first.toString(),gate.toString());
            b=launch(log1,"--publish",account.toString(),"1",
                Integer.toString(flavor),second.toString(),gate.toString());
            waitForBoth(first,second,a,b);
            // Only after BOTH distinct immutable objects have been forced
            // can either process attempt CREATE_NEW on the same HEAD leaf.
            Files.createFile(gate);
            exit0=await(a,"contender 0");
            exit1=await(b,"contender 1");
        }finally{
            destroy(a);
            destroy(b);
        }
        String trace0=log(log0),trace1=log(log1);
        check((exit0==0&&exit1==LOSER_EXIT)||
              (exit1==0&&exit0==LOSER_EXIT),
            "expected exactly one winner: "+exit0+"/"+exit1+
            "\nchild0="+trace0+"\nchild1="+trace1);
        int winner=exit0==0?0:1,loser=1-winner;
        check((winner==0?trace0:trace1).contains(
                "R157_PUBLISH_SUCCESS contestant="+winner)&&
              (loser==0?trace0:trace1).contains(
                "R157_PUBLISH_CREATE_NEW_DENIED contestant="+loser),
            "publisher exits lacked exact result markers");

        LocalAccountEpochHeadCodec.Record selected=
            record(account,winner,flavor);
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        check(Arrays.equals(Files.readAllBytes(head),
                LocalAccountEpochHeadCodec.encode(selected)),
            "the final HEAD did not match its sole CREATE_NEW winner");
        LocalAccountEpochNegativeHeadPublication.Observation observed=
            LocalAccountEpochNegativeHeadPublication.inspectNegative(account);
        check(observed.untrusted.snapshotSha256.equals(
                  selected.snapshotSha256)&&
              !observed.admissionAuthorized&&!observed.releaseAuthorized&&
              !observed.recoveryAuthorized,
            "local inspection granted negative head authority");

        for(int contestant=0;contestant<2;contestant++){
            String digest=record(account,contestant,flavor).snapshotSha256;
            LocalAccountEpochSnapshotObjects.verify(account,digest,null);
            check(Files.isRegularFile(
                LocalAccountEpochSnapshotObjects.objectPath(account,digest),
                LinkOption.NOFOLLOW_LINKS),
                "contender's immutable object missing after race");
        }
        if(existing){
            check(Arrays.equals(old,Files.readAllBytes(account))&&
                previousKey.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()),"existing account changed");
        }else{
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "initially missing account was created");
        }

        Path observerLog=root.resolve(name+".observer.log");
        Process reader=null;
        try{
            reader=launch(observerLog,"--observe",account.toString(),
                Integer.toString(winner),Integer.toString(flavor));
            int result=await(reader,"independent restart");
            check(result==0&&log(observerLog).contains(
                "R157_INDEPENDENT_RESTART_DENIED_PASS winner="+winner),
                "independent observer did not deny: "+log(observerLog));
        }finally{destroy(reader);}

        byte[] untrustedRetry=snapshot(account,2,flavor);
        Path retryObject=LocalAccountEpochSnapshotObjects.objectPath(
            account,LocalAccountEpochHeadCodec.digestSnapshot(untrustedRetry));
        check(!Files.exists(retryObject,LinkOption.NOFOLLOW_LINKS),
            "retry object unexpectedly present");
        try{
            LocalAccountEpochNegativeHeadPublication.publish(account,
                record(account,2,flavor),untrustedRetry);
            throw new AssertionError("R157 reused raced HEAD");
        }catch(IOException refused){
            check(!Files.exists(retryObject,LinkOption.NOFOLLOW_LINKS),
                "refused retry wrote a new object");
        }
        check(Arrays.equals(Files.readAllBytes(head),
                LocalAccountEpochHeadCodec.encode(selected)),
            "retry changed canonical negative HEAD");
        cases++;
        System.out.println("R157_TWO_JVM_NEGATIVE_HEAD_RACE_PASS"+
            " case="+cases+" existing="+existing+" flavor="+flavor+
            " round="+round+" winner="+winner+" loser="+loser+
            " separateImmutableObjects=2"+
            " oneCreateNewWinner=true"+
            " independentRestartDenied=true"+
            " noLegacyAccountMutation=true"+
            " noPowerLossProofOrPositiveAuthority=true");
    }

    public static void main(String[] args)throws Exception{
        if(args.length==6&&"--publish".equals(args[0])){
            publisher(Paths.get(args[1]),Integer.parseInt(args[2]),
                Integer.parseInt(args[3]),Paths.get(args[4]),
                Paths.get(args[5]));
            return;
        }
        if(args.length==4&&"--observe".equals(args[0])){
            observer(Paths.get(args[1]),Integer.parseInt(args[2]),
                Integer.parseInt(args[3]));
            return;
        }
        check(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("r157-head-crossjvm-");
        try{
            for(boolean existing:new boolean[]{false,true})
                for(int flavor=0;flavor<2;flavor++)
                    for(int round=0;round<2;round++)
                        oneCase(root,existing,flavor,round);
            check(cases==8,"expected eight two-JVM contests");
            System.out.println("R157_TWO_JVM_NEGATIVE_HEAD_MATRIX_PASS"+
                " cases=8 publisherJvms=16 observerJvms=8"+
                " oneWinnerPerCase=true noRecoveryOrFinalAck=true"+
                " noWorldIntegrationOrPowerLossProof=true");
        }finally{
            // Clean only the freshly created disposable regression tree.
            // There is no API for deleting real quarantine evidence.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path item:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}
