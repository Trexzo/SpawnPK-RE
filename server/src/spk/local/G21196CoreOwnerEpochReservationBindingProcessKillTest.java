package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.196 process-cutpoint evidence for an UNUSED owner-epoch prototype.
 * A forced reservation is possible before World generation is known.
 * A separately forced binding can be added after generation is known.
 * Both states are non-admitting and have NO release/retirement behavior.
 */
public final class G21196CoreOwnerEpochReservationBindingProcessKillTest {
    private G21196CoreOwnerEpochReservationBindingProcessKillTest(){}

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G21.196 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String stage)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+stage);
    }

    private static void holder(String stage,Path account,Path ready)
        throws Exception{
        LocalDurableOwnerEpochPrototype.Reservation reservation=
            LocalDurableOwnerEpochPrototype
                .reserveBeforeWorldRegistration(account);
        check(reservation.account.equals(account.toAbsolutePath().normalize()),
            "reservation changed account path");
        LocalDurableOwnerEpochPrototype.Observation first=
            LocalDurableOwnerEpochPrototype.inspect(account);
        check(!first.generationBound&&first.worldGeneration==-1L,
            "pre-registration reservation unexpectedly bound");

        if("after-bind".equals(stage)){
            LocalDurableOwnerEpochPrototype
                .bindAfterWorldRegistration(reservation,7L);
            LocalDurableOwnerEpochPrototype.Observation bound=
                LocalDurableOwnerEpochPrototype.inspect(account);
            check(bound.generationBound&&bound.worldGeneration==7L,
                "post-registration generation binding not visible");
        }else{
            check("before-bind".equals(stage),"invalid holder stage");
            boolean invalidRejected=false;
            try{
                LocalDurableOwnerEpochPrototype
                    .bindAfterWorldRegistration(reservation,0L);
            }catch(IOException expected){
                invalidRejected=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "INVALID_WORLD_GENERATION");
            }
            check(invalidRejected,
                "invalid World generation was bound");
            check(!Files.exists(
                LocalDurableOwnerEpochPrototype.bindingPath(account),
                LinkOption.NOFOLLOW_LINKS),
                "unbound stage wrote a binding");
        }

        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "owner epoch prototype created account snapshot");
        Files.write(ready,
            ("G21196_HOLDER_READY_"+stage)
                .getBytes(StandardCharsets.UTF_8));
        System.out.println("G21196_HOLDER_READY stage="+stage+
            " forcedReservation=true"+
            " forcedBinding="+("after-bind".equals(stage))+
            " loginWiring=false");
        System.out.flush();
        Thread.sleep(120_000L);
        throw new AssertionError("G21.196 holder never killed");
    }

    private static void probe(String stage,Path account)throws Exception{
        LocalDurableOwnerEpochPrototype.Observation current=
            LocalDurableOwnerEpochPrototype.inspect(account);
        boolean bound="after-bind".equals(stage);
        check(current.generationBound==bound,
            "new JVM observed unexpected owner bind state");
        check(current.worldGeneration==(bound?7L:-1L),
            "new JVM observed incorrect generation");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "prototype unexpectedly created account");

        boolean duplicateDenied=false;
        try{
            LocalDurableOwnerEpochPrototype
                .reserveBeforeWorldRegistration(account);
        }catch(IOException expected){
            duplicateDenied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "EPOCH_RECORD_PRESENT_NO_OVERWRITE");
        }
        check(duplicateDenied,"fresh JVM replaced the reservation");

        if(bound){
            boolean reboundDenied=false;
            try{
                LocalDurableOwnerEpochPrototype
                    .bindAfterWorldRegistration(current.reservation,8L);
            }catch(IOException expected){
                reboundDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "EPOCH_RECORD_PRESENT_NO_OVERWRITE");
            }
            check(reboundDenied,
                "bound generation overwritten by a second generation");
        }else{
            check(!Files.exists(
                    LocalDurableOwnerEpochPrototype.bindingPath(account),
                    LinkOption.NOFOLLOW_LINKS),
                "fresh process auto-recovered missing binding");
        }
        System.out.println(
            "G21196_FRESH_JVM_NEGATIVE_PASS"+
            " stage="+stage+
            " bound="+bound+
            " generation="+current.worldGeneration+
            " duplicateReservationDenied=true"+
            " automaticRepair=false");
    }

    private static Process spawn(
        String role,String stage,Path account,Path ready,Path log
    )throws Exception{
        String exe=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder pb=new ProcessBuilder(
            exe,"-cp",System.getProperty("java.class.path"),
            G21196CoreOwnerEpochReservationBindingProcessKillTest
                .class.getName(),"--child",role,stage,
            account.toString(),ready.toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        return pb.start();
    }

    private static void finished(
        Process process,Path log,String expected
    )throws Exception{
        if(!process.waitFor(25L,TimeUnit.SECONDS)){
            process.destroyForcibly();
            process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.196 probe child timeout");
        }
        String message=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,
            "probe process failed exit="+process.exitValue()+" "+message);
        check(message.contains(expected),
            "probe marker absent "+message);
    }

    private static void stage(String stage,Path root)throws Exception{
        Path account=root.resolve("account-"+stage+".properties");
        Path ready=root.resolve(stage+".ready");
        Path holderLog=root.resolve(stage+".holder.log");
        Path probeLog=root.resolve(stage+".probe.log");
        Path reservation=LocalDurableOwnerEpochPrototype
            .reservationPath(account);
        Path binding=LocalDurableOwnerEpochPrototype.bindingPath(account);
        check(!Files.exists(reservation,LinkOption.NOFOLLOW_LINKS),
            "reservation preexisted");
        check(!Files.exists(binding,LinkOption.NOFOLLOW_LINKS),
            "binding preexisted");

        Process holder=spawn("holder",stage,account,ready,holderLog);
        try{
            await(()->Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
                "holder "+stage);
            check(holder.isAlive(),"holder exited before forced kill");
            byte[] reserveBefore=Files.readAllBytes(reservation);
            byte[] bindingBefore=Files.exists(binding,
                LinkOption.NOFOLLOW_LINKS)
                    ?Files.readAllBytes(binding):null;
            check(reserveBefore.length>0,"reservation empty");
            check(bindingBefore!=null==
                "after-bind".equals(stage),
                "binding presence differs from expected stage");

            holder.destroyForcibly();
            check(holder.waitFor(10L,TimeUnit.SECONDS),
                "holder failed to terminate");
            check(holder.exitValue()!=0,"holder exited gracefully");
            check(Arrays.equals(reserveBefore,
                    Files.readAllBytes(reservation)),
                "reservation changed across process death");
            if(bindingBefore!=null)
                check(Arrays.equals(bindingBefore,
                    Files.readAllBytes(binding)),
                    "generation binding changed across process death");
            else check(!Files.exists(binding,LinkOption.NOFOLLOW_LINKS),
                    "pre-binding crash manufactured binding");

            Process probe=spawn(
                "probe",stage,account,ready,probeLog);
            finished(probe,probeLog,"G21196_FRESH_JVM_NEGATIVE_PASS");
            check(Arrays.equals(reserveBefore,
                Files.readAllBytes(reservation)),
                "probing JVM changed reservation bytes");
            if(bindingBefore!=null)
                check(Arrays.equals(bindingBefore,
                    Files.readAllBytes(binding)),
                    "probing JVM changed binding bytes");
            System.out.println("G21196_STAGE_PASS"+
                " stage="+stage+
                " processKilled=true"+
                " freshJvmObserved=true"+
                " noAdmissionOrCleanup=true");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(5L,TimeUnit.SECONDS);
            }
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21196-owner-epoch-");
        try{
            stage("before-bind",root);
            stage("after-bind",root);
            check(!Files.exists(root.resolve("account-before-bind.properties"),
                    LinkOption.NOFOLLOW_LINKS)&&
                !Files.exists(root.resolve("account-after-bind.properties"),
                    LinkOption.NOFOLLOW_LINKS),
                "standalone primitive wrote account data");
            System.out.println(
                "G21196_OWNER_EPOCH_CUTPOINT_PASS"+
                " forcedPreRegistrationReservation=true"+
                " forcedPostRegistrationGenerationBinding=true"+
                " externalJvmKillCutpoints=2"+
                " newJvmExactWitnessReadback=true"+
                " duplicateOrRebindDenied=true"+
                " noAdmissionWiring=true"+
                " accountFileUntouched=true"+
                " markerRetirement=false"+
                " durableOwnerAuthority=false"+
                " crashRecovery=false"+
                " machinePowerLossProven=false"+
                " rawWriterExclusion=false");
        }finally{
            // Disposable child-process fixture only; no real marker release.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            Path account=Paths.get(args[3]);
            Path ready=Paths.get(args[4]);
            if("holder".equals(args[1]))
                holder(args[2],account,ready);
            else if("probe".equals(args[1]))
                probe(args[2],account);
            else throw new IllegalArgumentException(
                "invalid child role");
        }else{
            check(args.length==0,"unexpected arguments");
            run();
        }
    }
}
