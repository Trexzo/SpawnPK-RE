package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.188: a fully forced intent sidecar remains a fail-closed negative
 * across a real holder-JVM process kill and a fresh child process.
 *
 * PRIMITIVE ONLY: not wired into real session login/persistence. The
 * test's private temporary marker is deleted only with the fixture.
 */
public final class G21188CoreDurableReviewIntentProcessKillTest {
    private G21188CoreDurableReviewIntentProcessKillTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.188 "+why);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition c,String what)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<until){
            if(c.ready())return;
            Thread.sleep(10L);
        }
        check(c.ready(),"timed out "+what);
    }

    private static void holder(Path account,Path ready)throws Exception{
        Path marker=
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                account);
        check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
            "marker not regular");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "primitive unexpectedly wrote an account snapshot");
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
        Files.write(ready,
            "G21188_HOLDER_FORCED".getBytes(StandardCharsets.UTF_8));
        System.out.println(
            "G21188_HOLDER_FORCED"+
            " fileForced=true directoryForced=true"+
            " testOnly=true admissionIntegration=false");
        System.out.flush();
        Thread.sleep(120_000L);
        throw new AssertionError(
            "G21.188 holder was not killed after forcing marker");
    }

    private static void freshProbe(Path account)throws Exception{
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
        boolean deniedByPresence=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
        }catch(IOException negative){
            check(negative.getMessage()!=null&&
                negative.getMessage().contains(
                    "G21.188 ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY"),
                "incorrect review-negative reason");
            deniedByPresence=true;
        }
        check(deniedByPresence,
            "new child JVM did not see negative intent");

        boolean deniedSecondCreate=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
        }catch(IOException negative){
            check(negative.getMessage()!=null&&
                negative.getMessage().contains(
                    "G21.188 ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY"),
                "incorrect second-marker denial reason");
            deniedSecondCreate=true;
        }
        check(deniedSecondCreate,
            "new child JVM recreated existing active intent");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "primitive unexpectedly created account snapshot");
        System.out.println(
            "G21188_CHILD_PERSISTENT_NEGATIVE_PASS"+
            " existingIntentDetected=true"+
            " secondActiveIntentRefused=true"+
            " noCleanupOrRecovery=true");
    }

    private static Process spawn(
        Path account,Path ready,Path output,String mode
    )throws Exception{
        String executable=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(
            executable,"-cp",System.getProperty("java.class.path"),
            G21188CoreDurableReviewIntentProcessKillTest
                .class.getName(),
            "--child",mode,account.toString(),ready.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        return builder.start();
    }

    private static void requireChild(
        Process process,Path transcript,String marker
    )throws Exception{
        if(!process.waitFor(20L,TimeUnit.SECONDS)){
            process.destroyForcibly();
            process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.188 child process timed out");
        }
        String output=new String(
            Files.readAllBytes(transcript),StandardCharsets.UTF_8);
        check(process.exitValue()==0,
            "child failed exit="+process.exitValue()+" "+output);
        check(output.contains(marker),
            "child marker missing "+output);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21188-intent-");
        Path account=root.resolve("named.properties");
        Path marker=LocalDurableAccountReviewIntentPrimitive
            .markerPath(account);
        Path ready=root.resolve("ready");
        Path holderLog=root.resolve("holder.log");
        Path probeLog=root.resolve("probe.log");
        Process holding=null;
        try{
            check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "marker unexpectedly exists before start");
            holding=spawn(account,ready,holderLog,"holder");
            final Process active=holding;
            await(()->Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
                "holder file and directory force");
            check(active.isAlive(),"holder died before kill");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            byte[] before=Files.readAllBytes(marker);
            check(before.length>0,"marker empty before kill");

            active.destroyForcibly();
            check(active.waitFor(10L,TimeUnit.SECONDS),
                "holder process refused forced termination");
            check(active.exitValue()!=0,
                "holder ended normally");
            holding=null;
            check(Files.isRegularFile(
                marker,LinkOption.NOFOLLOW_LINKS),
                "process kill erased durable marker");
            check(java.util.Arrays.equals(
                before,Files.readAllBytes(marker)),
                "marker bytes changed after process kill");

            Process fresh=spawn(account,ready,probeLog,"probe");
            requireChild(fresh,probeLog,
                "G21188_CHILD_PERSISTENT_NEGATIVE_PASS");
            check(java.util.Arrays.equals(
                before,Files.readAllBytes(marker)),
                "fresh refused process altered the review marker");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "no account was required for marker creation");

            System.out.println(
                "G21188_DURABLE_PRIMITIVE_DIAGNOSTICS"+
                " distinctJvmProcesses=2"+
                " holderMarkerFileAndDirectoryForced=true"+
                " holderKilledBeforeNormalExit=true"+
                " exactMarkerBytesPreserved=true"+
                " newJvmDeniedByExistingMarker=true"+
                " accidentalAccountCreation=false");
            System.out.println(
                "G21188_DURABLE_PRIMITIVE_PASS"+
                " markerSurvivesProcessKill=true"+
                " failClosedExistingMarker=true"+
                " wiredToLogin=false"+
                " durableSaveReceipt=false"+
                " automaticIntentRetirement=false"+
                " powerLossCutpointsProven=false"+
                " hostileUnlinkProtection=false"+
                " rawWriterExclusion=false"+
                " originalClientProven=false"+
                " grants=false replay=false recovery=false");
        }finally{
            if(holding!=null&&holding.isAlive()){
                holding.destroyForcibly();
                holding.waitFor(5L,TimeUnit.SECONDS);
            }
            // Disposable fixture cleanup only. NEVER unlink a real
            // account review marker from this prototype.
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            Path account=Paths.get(args[2]);
            Path ready=Paths.get(args[3]);
            if("holder".equals(args[1]))
                holder(account,ready);
            else if("probe".equals(args[1]))
                freshProbe(account);
            else throw new IllegalArgumentException("unexpected mode");
        }else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
