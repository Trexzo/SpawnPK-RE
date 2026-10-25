package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * R162: an independent JVM publishes untrusted negative HEAD/object
 * evidence AFTER the selected account is decoded for World admission.
 * The real FilePlayerRepository final read-window veto must reject it.
 * There is no positive owner, World login, account replay or final ACK.
 */
public final class LocalAccountEpochCrossJvmLateReadVetoTest {
    private enum Evidence { HEAD_AND_OBJECT, ORPHAN_OBJECT_ONLY }
    private static int cases;
    private LocalAccountEpochCrossJvmLateReadVetoTest(){}

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R162 "+reason);
    }

    private static String accountName(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"invalid account filename");
        return leaf.substring(0,leaf.length()-11);
    }

    private static byte[] snapshot(Path account){
        return ("R162 independently forced negative object "+
            accountName(account)).getBytes(StandardCharsets.US_ASCII);
    }

    private static LocalAccountEpochHeadCodec.Record head(Path account)
            throws IOException{
        return new LocalAccountEpochHeadCodec.Record(
            accountName(account),1L,1L,162L,
            LocalAccountEpochHeadCodec.State.REVIEW,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(snapshot(account)));
    }

    private static void publish(Path account,Evidence evidence)
            throws IOException{
        byte[] image=snapshot(account);
        if(evidence==Evidence.HEAD_AND_OBJECT){
            LocalAccountEpochNegativeHeadPublication.publish(
                account,head(account),image);
        }else{
            LocalAccountEpochSnapshotObjects.publish(account,image);
            check(!Files.exists(
                LocalAccountEpochNegativeHeadPublication.headPath(account),
                LinkOption.NOFOLLOW_LINKS),
                "orphan publication unexpectedly created HEAD");
        }
        System.out.println("R162_INDEPENDENT_JVM_NEGATIVE_PUBLISHED"+
            " evidence="+evidence+" immutableObject=true");
        System.out.flush();
    }

    private static boolean contains(Throwable error,String token){
        for(Throwable e=error;e!=null;e=e.getCause()){
            if(String.valueOf(e.getMessage()).contains(token))return true;
        }
        return false;
    }

    private static String javaExecutable(){
        String executable=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        return new File(new File(System.getProperty("java.home"),"bin"),
            executable).getAbsolutePath();
    }

    private static void injectFromIndependentJvm(Path account,
            Evidence evidence,Path log)throws IOException{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",System.getProperty("java.class.path"),
            LocalAccountEpochCrossJvmLateReadVetoTest.class.getName(),
            "--publish",account.toString(),evidence.name())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            if(!child.waitFor(35L,TimeUnit.SECONDS)){
                child.destroyForcibly();
                child.waitFor(10L,TimeUnit.SECONDS);
                throw new IOException("R162_FOREIGN_PROCESS_TIMED_OUT");
            }
            String output=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            if(child.exitValue()!=0||
               !output.contains("R162_INDEPENDENT_JVM_NEGATIVE_PUBLISHED"+
                   " evidence="+evidence))
                throw new IOException(
                    "R162_FOREIGN_PROCESS_PUBLISH_FAILED exit="+
                    child.exitValue()+" log="+output);
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("R162_FOREIGN_PROCESS_INTERRUPTED",
                interrupted);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                try{child.waitFor(10L,TimeUnit.SECONDS);}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void runCase(Path root,boolean exists,
            Evidence evidence)throws Exception{
        String accountName="r162case"+cases;
        Path account=root.resolve(accountName+".properties");
        FilePlayerRepository seed=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        byte[] oldBytes=null;
        Object oldKey=null;
        if(exists){
            seed.save(PlayerSnapshotCodec.capture(
                accountName,new WorldPlayer()));
            oldBytes=Files.readAllBytes(account);
            oldKey=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        }

        final AtomicInteger callbackCalls=new AtomicInteger();
        Path foreignLog=root.resolve(accountName+".foreign.log");
        FilePlayerRepository guarded=new FilePlayerRepository(
            name->root.resolve(name+".properties"),
            unused->{},unused->{},
            name->{
                check(accountName.equals(name),
                    "read hook observed wrong account");
                callbackCalls.incrementAndGet();
                injectFromIndependentJvm(account,evidence,foreignLog);
            });
        String negative=evidence==Evidence.HEAD_AND_OBJECT?
            "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY":
            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
        boolean denied=false;
        try{
            guarded.loadForWorldSession(accountName);
        }catch(IOException failure){
            denied=contains(failure,negative);
        }
        check(callbackCalls.get()==1&&denied,
            "foreign JVM evidence missed read-window recheck");

        Path negativeHead=LocalAccountEpochNegativeHeadPublication
            .headPath(account);
        check(Files.exists(negativeHead,LinkOption.NOFOLLOW_LINKS)==
            (evidence==Evidence.HEAD_AND_OBJECT),
            "unexpected HEAD after foreign publisher");
        String digest=head(account).snapshotSha256;
        LocalAccountEpochSnapshotObjects.verify(account,digest,null);
        if(exists){
            check(Arrays.equals(oldBytes,Files.readAllBytes(account))&&
                oldKey.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()),"old account was changed by foreign JVM");
        }else{
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "missing account was unexpectedly bootstrapped");
        }
        boolean repeatDenied=false;
        try{
            seed.loadForWorldSession(accountName);
        }catch(IOException failure){
            repeatDenied=contains(failure,negative);
        }
        check(repeatDenied,"new repository accepted foreign evidence");

        cases++;
        System.out.println("R162_FOREIGN_JVM_READ_WINDOW_VETO_PASS"+
            " case="+cases+" existing="+exists+
            " evidence="+evidence+
            " separatePublisherProcess=true"+
            " secondAdmissionCheckDenied=true"+
            " unchangedAccountBytesAndObject=true"+
            " noPositiveRecoveryOrFinalAck=true");
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--publish".equals(args[0])){
            publish(Paths.get(args[1]),Evidence.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("r162-cross-jvm-read-");
        try{
            for(boolean exists:new boolean[]{false,true})
                for(Evidence evidence:Evidence.values())
                    runCase(root,exists,evidence);
            FilePlayerRepository normal=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            String other="r162-clean";
            normal.save(PlayerSnapshotCodec.capture(
                other,new WorldPlayer()));
            check(normal.loadForWorldSession(other).isPresent(),
                "foreign negative evidence affected an unrelated account");
            check(cases==4,"four cross-JVM read-window cases");
            System.out.println("R162_CROSS_JVM_READ_WINDOW_MATRIX_PASS"+
                " cases=4 foreignJvmPublishers=4"+
                " cleanAccountIsolated=true noOriginalClientTcp=true"+
                " noPowerLossOrDurableOwnershipProof=true");
        }finally{
            // Only remove disposable test fixture files; never touch
            // production negative forensic evidence.
            try(Stream<Path> entries=Files.walk(root)){
                for(Path entry:entries.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(entry);
            }
        }
    }
}
