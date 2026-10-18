package spk.local;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.381: inherited negative ACTIVE review marker vetoes a fresh JVM.
 * Child performs a real isolated-World lease/admission boundary, without
 * granting, deleting markers or claiming that marker creation survived a crash.
 */
public final class G21381CrossJvmReviewMarkerAdmissionTest {
    private static void check(boolean b,String why){
        if(!b)throw new AssertionError("G21.381 "+why);
    }
    private static void child(Path root)throws Exception{
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        FilePlayerRepository repo=new FilePlayerRepository(
            user->root.resolve(user+".properties"));
        try(World world=World.isolatedForTest(60000L,repo)){
            LocalJvmAccountSessionLease.Token token=
                LocalJvmAccountSessionLease.acquire(world.persistence(),"same-user");
            try{
                check(token!=null,"child token missing");
                boolean refused=false;
                try{
                    LocalReviewIntentAdmissionVeto.requireAdmissible(
                        true,true,token);
                }catch(IllegalStateException e){
                    refused=String.valueOf(e.getMessage()).contains(
                        "G21.189 REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
                }
                check(refused,"independent process admitted review-marked account");
                check(world.players().size()==0,"child registered a player");
                System.out.println("G21381_CHILD_REVIEW_DENIED");
            }finally{
                if(token!=null)token.releaseAfterInitializationFailure();
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(Path.of(args[1]));return;
        }
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21381-crossjvm-review-");
        try{
            Path account=root.resolve("same-user.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive.markerPath(account);
            byte[] evidence=new byte[]{71,21,3,81};
            Files.write(marker,evidence,StandardOpenOption.CREATE_NEW);
            Path transcript=root.resolve("child.log");
            String java=Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                    ?"java.exe":"java").toString();
            Process process=new ProcessBuilder(java,"-cp",
                System.getProperty("java.class.path"),
                G21381CrossJvmReviewMarkerAdmissionTest.class.getName(),
                "--child",root.toString()).redirectErrorStream(true)
                .redirectOutput(transcript.toFile()).start();
            if(!process.waitFor(30L,TimeUnit.SECONDS)){
                process.destroyForcibly();
                process.waitFor(5L,TimeUnit.SECONDS);
                throw new AssertionError("G21.381 child timed out");
            }
            String output=Files.readString(transcript,StandardCharsets.UTF_8);
            check(process.exitValue()==0&&
                output.contains("G21381_CHILD_REVIEW_DENIED"),
                "child process denied incorrectly: "+output);
            check(java.util.Arrays.equals(evidence,Files.readAllBytes(marker)),
                "cross-JVM review changed original negative evidence");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "independent JVM published account file");
            System.out.println("G21381_CROSS_JVM_REVIEW_NEGATIVE_PASS"+
                " independentJvm=true sidecarRetained=true"+
                " noAccountPublication=true durableCrashCreationProven=false");
        }finally{
            try(Stream<Path> tree=Files.walk(root)){
                for(Path p:tree.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    private G21381CrossJvmReviewMarkerAdmissionTest(){}
}
