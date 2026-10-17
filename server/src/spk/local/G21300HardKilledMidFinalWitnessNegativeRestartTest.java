package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.300 actual foreign JVM killed after the untrusted negative
 * final-witness bytes were written, BEFORE FileChannel.force.
 * Never treat a visible partial witness as durable confirmation.
 */
public final class G21300HardKilledMidFinalWitnessNegativeRestartTest {
    private G21300HardKilledMidFinalWitnessNegativeRestartTest(){}
    private static void check(boolean okay,String why){
        if(!okay)throw new AssertionError("G21.300 "+why);
    }
    private static void child(Path root,Path ready)throws Exception{
        Path account=root.resolve("selected.properties");
        Files.write(account,"stable-account".getBytes(
            StandardCharsets.US_ASCII));
        LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
            account);
        LocalFinalSaveNegativeWitness.setAfterWriteBeforeForceForTest(
            leaf->{
                Files.writeString(ready,"READY_BEFORE_FILE_FORCE",
                    StandardCharsets.US_ASCII);
                for(;;){
                    try{
                        Thread.sleep(1_000L);
                    }catch(InterruptedException ignored){}
                }
            });
        LocalFinalSaveNegativeWitness.publishOnce(
            account,"selected",1L,1L);
        throw new AssertionError(
            "G21.300 test child continued after pre-force barrier");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]));
            return;
        }
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21300-hardkill-witness-");
        Path account=root.resolve("selected.properties");
        Path ready=root.resolve("ready");
        Process child=null;
        try{
            Path executable=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder pb=new ProcessBuilder(
                executable.toString(),"-cp",
                System.getProperty("java.class.path"),
                G21300HardKilledMidFinalWitnessNegativeRestartTest
                    .class.getName(),
                "--child",root.toString(),ready.toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(root.resolve("child.log").toFile());
            child=pb.start();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<until&&child.isAlive()){
                if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)&&
                    "READY_BEFORE_FILE_FORCE".equals(Files.readString(
                        ready,StandardCharsets.US_ASCII)))
                    break;
                Thread.sleep(10L);
            }
            check(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS),
                "child did not reach actual pre-force cutpoint");
            child.destroyForcibly();
            check(child.waitFor(8L,TimeUnit.SECONDS),
                "hard-killed child did not terminate");
            byte[] accountBytes=Files.readAllBytes(account);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            Path witness=LocalFinalSaveNegativeWitness
                .witnessPath(account);
            check(Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
                "crash witness/ACTIVE not visible; negative evidence lost");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            boolean restartDenied=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
            }catch(IOException expected){
                restartDenied=true;
            }
            check(restartDenied,
                "hard-killed pre-force witness incorrectly admits restart");
            boolean replayDenied=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,2L);
            }catch(IOException expected){
                replayDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
            }
            check(replayDenied&&
                Arrays.equals(accountBytes,Files.readAllBytes(account)),
                "repeated witness publication rewrote crash evidence");
            System.out.println(
                "G21300_HARDKILLED_FINAL_WITNESS_PRE_FORCE_NEGATIVE_PASS"+
                " realChildProcessKilled=true"+
                " crashCutBeforeFileForce=true"+
                " ACTIVEReviewPersisted=true"+
                " witnessVisibleButNotTrusted=true"+
                " restartAdmissionDenied=true"+
                " noAutomaticOverwriteOrRecovery=true");
        }finally{
            if(child!=null&&child.isAlive()){
                child.destroyForcibly();
                child.waitFor(8L,TimeUnit.SECONDS);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
