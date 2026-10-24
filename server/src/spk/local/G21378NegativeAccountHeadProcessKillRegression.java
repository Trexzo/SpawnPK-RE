package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R50: REAL child-JVM forced-kill cutpoints around an unwired,
 * always-nonadmitting immutable snapshot + negative HEAD publisher.
 * No power-loss durability, gameplay admission or release claim.
 */
public final class G21378NegativeAccountHeadProcessKillRegression {
    private G21378NegativeAccountHeadProcessKillRegression(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R50_NEGATIVE_HEAD "+reason);
    }
    private static final byte[] SNAPSHOT=
        "spk-local-test-only-account-state-48".getBytes(
            StandardCharsets.US_ASCII);
    private static void negative(
        Path account,boolean headExpected
    )throws IOException{
        try{
            LocalNegativeAccountHeadPrototype.NegativeObservation found=
                LocalNegativeAccountHeadPrototype.inspectNegative(account);
            check(headExpected,"unexpected positive-format head");
            check(!found.admissionAuthorized&&!found.releaseAuthorized,
                "inspected head authorized login/release");
            check("r50-account".equals(found.username)&&
                found.untrustedWorldGeneration==17L&&
                found.snapshotLength==SNAPSHOT.length,
                "head claims inconsistent values");
        }catch(IOException unsafe){
            if(headExpected)throw unsafe;
            check(String.valueOf(unsafe.getMessage()).contains(
                "R50_HEAD_MISSING_NEGATIVE_NO_ADMISSION"),
                "missing HEAD not a negative observation");
        }
    }

    private static void holder(
        Path account,Path ready,String stage
    )throws Exception{
        LocalNegativeAccountHeadPrototype.Stage target=
            LocalNegativeAccountHeadPrototype.Stage.valueOf(stage);
        LocalNegativeAccountHeadPrototype.publishNegative(
            account,"r50-account",17L,SNAPSHOT,reached->{
                if(reached==target){
                    Files.write(ready,"R50_READY".getBytes(
                        StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                    System.out.println("R50_HOLDER_READY "+reached);
                    System.out.flush();
                    try{
                        Thread.sleep(120_000L);
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "R50_HOLDER_INTERRUPTED_BEFORE_KILL",
                            interrupted);
                    }
                    throw new AssertionError("fixture was never killed");
                }
            });
        throw new AssertionError("target cutpoint never reached");
    }
    private static Process child(
        String mode,Path account,Path ready,String stage,Path log)
        throws IOException{
        String java=System.getProperty("java.home")+
            File.separator+"bin"+File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder b=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21378NegativeAccountHeadProcessKillRegression.class.getName(),
            "--child",mode,account.toString(),ready.toString(),stage);
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static void stage(
        Path root,LocalNegativeAccountHeadPrototype.Stage cut
    )throws Exception{
        String name=cut.name().toLowerCase();
        Path account=root.resolve(name+".properties");
        Path ready=root.resolve(name+".ready");
        Path log=root.resolve(name+".holder.log");
        Process holder=child("holder",account,ready,cut.name(),log);
        try{
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<until&&
                    !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)&&
                    holder.isAlive())
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(
                    ready,LinkOption.NOFOLLOW_LINKS),
                "holder cutpoint unavailable "+cut+" "+
                (Files.exists(log)?new String(Files.readAllBytes(log),
                    StandardCharsets.UTF_8):""));
            holder.destroyForcibly();
            check(holder.waitFor(12L,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder was not force-killed");

            // Restart inspection is a different JVM. Even an entirely
            // valid byte-identical head is still NONADMITTING evidence.
            Path probeLog=root.resolve(name+".probe.log");
            Process probe=child(
                "probe",account,ready,cut.name(),probeLog);
            check(probe.waitFor(20L,TimeUnit.SECONDS),
                "restarted probe timed out");
            String output=new String(Files.readAllBytes(probeLog),
                StandardCharsets.UTF_8);
            check(probe.exitValue()==0&&
                output.contains("R50_RESTART_NEGATIVE_PASS"),
                "new JVM did not inspect negatively: "+output);

            boolean hasHead=cut==
                LocalNegativeAccountHeadPrototype.Stage
                    .HEAD_FILE_FORCED_BEFORE_DIRECTORY||
                cut==LocalNegativeAccountHeadPrototype.Stage
                    .HEAD_DIRECTORY_FORCED_BEFORE_READBACK;
            negative(account,hasHead);
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "standalone prototype mutated legacy account");
            System.out.println("R50_CUTPOINT_PASS stage="+cut+
                " processKilled=true freshJvmObserved=true"+
                " activeHeadStillNonAdmitting="+hasHead);
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(5L,TimeUnit.SECONDS);
            }
        }
    }
    private static void healthyAndTampering(Path root)throws Exception{
        Path account=root.resolve("healthy.properties");
        LocalNegativeAccountHeadPrototype.NegativeObservation head=
            LocalNegativeAccountHeadPrototype.publishNegative(
                account,"r50-account",17L,SNAPSHOT);
        check(!head.admissionAuthorized&&!head.releaseAuthorized,
            "healthy exact head granted authority");
        check(head.snapshotSha256.matches("[0-9a-f]{64}"),
            "invalid object digest");
        negative(account,true);
        byte[] retained=Files.readAllBytes(
            LocalNegativeAccountHeadPrototype.headPath(account));
        boolean refused=false;
        try{
            LocalNegativeAccountHeadPrototype.publishNegative(
                account,"r50-account",18L,SNAPSHOT);
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "R50_HEAD_ALREADY_PRESENT_NO_OVERWRITE");
        }
        check(refused&&Arrays.equals(retained,Files.readAllBytes(
                LocalNegativeAccountHeadPrototype.headPath(account))),
            "duplicate writer overwrote permanent negative head");

        byte[] corrupted=Files.readAllBytes(head.immutableObject);
        corrupted[0]=(byte)(corrupted[0]^1);
        Files.write(head.immutableObject,corrupted,
            StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
        refused=false;
        try{
            LocalNegativeAccountHeadPrototype.inspectNegative(account);
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "R50_REFERENCED_OBJECT_DIGEST_NEGATIVE");
        }
        check(refused,"same-inode changed object bytes escaped integrity check");
        Path second=root.resolve("foreign.properties");
        LocalNegativeAccountHeadPrototype.NegativeObservation unrelated=
            LocalNegativeAccountHeadPrototype.publishNegative(
                second,"r50-account",17L,SNAPSHOT);
        negative(second,true);
        check(!unrelated.admissionAuthorized,
            "foreign control authenticated login");

        Path malformed=root.resolve("malformed.properties");
        Path malformedHead=LocalNegativeAccountHeadPrototype.headPath(malformed);
        Files.write(malformedHead,
            "G21378_NEGATIVE_ACCOUNT_HEAD_V1\n".getBytes(
                StandardCharsets.US_ASCII),
            StandardOpenOption.CREATE_NEW);
        refused=false;
        try{
            LocalNegativeAccountHeadPrototype.inspectNegative(malformed);
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "R50_MALFORMED_HEAD_NEGATIVE");
        }
        check(refused,"torn HEAD was treated as safe");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "prototype created legacy profile");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            Path account=Paths.get(args[2]);
            Path ready=Paths.get(args[3]);
            if("holder".equals(args[1]))
                holder(account,ready,args[4]);
            else if("probe".equals(args[1])){
                LocalNegativeAccountHeadPrototype.Stage which=
                    LocalNegativeAccountHeadPrototype.Stage.valueOf(args[4]);
                boolean shouldHaveHead=which==
                    LocalNegativeAccountHeadPrototype.Stage
                        .HEAD_FILE_FORCED_BEFORE_DIRECTORY||
                    which==LocalNegativeAccountHeadPrototype.Stage
                        .HEAD_DIRECTORY_FORCED_BEFORE_READBACK;
                negative(account,shouldHaveHead);
                System.out.println("R50_RESTART_NEGATIVE_PASS");
            }else throw new IllegalArgumentException("unknown child mode");
            return;
        }
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r50-negative-head-");
        try{
            for(LocalNegativeAccountHeadPrototype.Stage s:
                    LocalNegativeAccountHeadPrototype.Stage.values())
                stage(root,s);
            healthyAndTampering(root);
            System.out.println("R50_NEGATIVE_ACCOUNT_HEAD_CUTPOINT_PASS"+
                " externalJvmForcedKills=4"+
                " forcedImmutableObjectBeforeHead=true"+
                " boundedNofollowRecords=true"+
                " createNewHeadNoReplacement=true"+
                " tamperedObjectDenied=true"+
                " duplicateHeadDenied=true"+
                " malformedHeadDenied=true"+
                " allHeadsNonAdmitting=true"+
                " localSessionWiring=false"+
                " crossWorldOwnerEpochProven=false"+
                " powerLossDurabilityProven=false"+
                " recoveryAuthorized=false");
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
