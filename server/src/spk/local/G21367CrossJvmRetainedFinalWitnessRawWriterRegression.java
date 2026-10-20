package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R39: a real, retained G21.298 negative final World witness must
 * prevent ALL cooperating opt-in RAW writers in a fresh JVM, even
 * when ACTIVE was removed and final-witness creation is disabled.
 *
 * This exercises real separate JVMs, but not power-loss durability,
 * unauthenticated filesystem tampering, uncooperative default RAW,
 * a cross-process owner epoch or original-v308 client receipt.
 */
public final class G21367CrossJvmRetainedFinalWitnessRawWriterRegression {
    private static final String NAMED="r39-legacy-named";
    private static final String REPO="r39-file-repo";
    private static final String LATE="r39-late-raw";
    private static final String OTHER="r39-unrelated";
    private G21367CrossJvmRetainedFinalWitnessRawWriterRegression(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("R39_CROSS_JVM_FINAL_RAW "+reason);
    }

    private static void flags(Path root,boolean strict){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,
            Boolean.toString(strict));
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            Boolean.toString(strict));
        System.setProperty(LocalOptInRawTemp.PROPERTY,
            Boolean.toString(strict));
        System.setProperty(LocalOptInRawTemp.FORCE_PROPERTY,
            Boolean.toString(strict));
        System.setProperty(LocalFinalSaveNegativeWitness.PROPERTY,
            "false");
    }

    private static PlayerSnapshot snapshot(String username){
        return PlayerSnapshotCodec.capture(
            username,new WorldPlayer());
    }

    private static FilePlayerRepository repository(){
        return new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
    }

    private static Path[] paths(){
        return new Path[]{
            AccountStore.accountFile(),
            LocalAccountProfiles.accountFile(NAMED),
            LocalAccountProfiles.accountFile(REPO)
        };
    }

    private static void saveAll()throws IOException{
        WorldPlayer state=new WorldPlayer();
        AccountStore.save(state.bank(),state.equipment(),
            state.movement(),state.petState(),state.playerState());
        LocalAccountProfiles.save(NAMED,
            state.bank(),state.equipment(),state.movement(),
            state.petState(),state.playerState());
        repository().save(snapshot(REPO));
    }

    @FunctionalInterface interface Save {void run()throws IOException;}

    private static void assertNoTemps(Path selected,boolean strict)
        throws IOException{
        String name=selected.getFileName().toString();
        try(Stream<Path> entries=Files.list(selected.getParent())){
            check(entries.noneMatch(file->file.getFileName().toString()
                .startsWith(name+".g21246-raw-")),
                "unique RAW temp leaked");
        }
        check(!Files.exists(selected.resolveSibling(name+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "fixed RAW temp leaked");
    }

    private static void deny(Path selected,Save action,boolean strict)
        throws Exception{
        byte[] before=Files.readAllBytes(selected);
        Path witness=LocalFinalSaveNegativeWitness
            .witnessPath(selected);
        byte[] witnessBefore=Files.readAllBytes(witness);
        boolean denied=false;
        try{
            action.run();
        }catch(IOException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
        }
        check(denied,
            "retained FINAL witness permitted cooperating RAW writer");
        check(Arrays.equals(before,Files.readAllBytes(selected)),
            "denied cross-JVM RAW changed account bytes");
        check(Arrays.equals(witnessBefore,Files.readAllBytes(witness)),
            "denied RAW modified final witness");
        assertNoTemps(selected,strict);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "failed RAW writer retained JVM reservation");
    }

    private static void deniedWriters(boolean strict)
        throws Exception{
        WorldPlayer state=new WorldPlayer();
        Path[] files=paths();
        deny(files[0],()->AccountStore.save(
            state.bank(),state.equipment(),state.movement(),
            state.petState(),state.playerState()),strict);
        deny(files[1],()->LocalAccountProfiles.save(
            NAMED,state.bank(),state.equipment(),state.movement(),
            state.petState(),state.playerState()),strict);
        deny(files[2],()->repository().save(snapshot(REPO)),strict);
        check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
            MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "rejected RAW writer leaked account or publication owner");
    }

    private static void child(Path root,String phase)throws Exception{
        boolean strict="DENY_FORCED".equals(phase);
        flags(root,strict);
        if("DENY_PLAIN".equals(phase)||strict){
            deniedWriters(strict);
            System.out.println(
                "R39_CHILD_NEGATIVE_CROSS_JVM_RAW_DENIED_PASS mode="+
                (strict?"STRICT_FORCED":"COOPERATIVE_NONFORCED"));
        }else if("UNRELATED".equals(phase)){
            repository().save(snapshot(OTHER));
            check(repository().load(OTHER).isPresent(),
                "other account RAW success was wrongly denied");
            System.out.println(
                "R39_CHILD_UNRELATED_RAW_ISOLATION_PASS");
        }else if("ALLOW".equals(phase)){
            saveAll();
            for(Path account:paths())
                check(Files.isRegularFile(account,
                    LinkOption.NOFOLLOW_LINKS),
                    "manually restored account RAW retry failed");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "positive RAW child leaked reservation");
            System.out.println(
                "R39_CHILD_MANUALLY_RESTORED_RAW_SUCCESS_PASS");
        }else throw new AssertionError("unknown child phase "+phase);
    }

    private static String spawn(Path root,String phase)
        throws Exception{
        Path java=Path.of(System.getProperty("java.home"),
            "bin","java"+(System.getProperty("os.name")
                .toLowerCase().contains("win")?".exe":""));
        ProcessBuilder request=new ProcessBuilder(java.toString(),
            "-cp",System.getProperty("java.class.path"),
            G21367CrossJvmRetainedFinalWitnessRawWriterRegression
                .class.getName(),
            "--child",root.toString(),phase);
        request.redirectErrorStream(true);
        Process foreign=request.start();
        if(!foreign.waitFor(30L,TimeUnit.SECONDS)){
            foreign.destroyForcibly();
            foreign.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R39 independent JVM timed out "+phase);
        }
        String text=new String(
            foreign.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(foreign.exitValue()==0,
            "independent JVM failed phase="+phase+" output="+text);
        return text;
    }

    private static void requireWitness(Path account,String user)
        throws Exception{
        Path active=LocalDurableAccountReviewIntentPrimitive
            .writeActiveReview(account);
        LocalFinalSaveNegativeWitness.publishOnce(account,user,1L,1L);
        Path witness=LocalFinalSaveNegativeWitness
            .witnessPath(account);
        check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
            "real final witness was not created");
        // Fixture-only simulation of out-of-band ACTIVE loss. A real
        // operator must never infer authority to retire the witness.
        Files.delete(active);
        check(!Files.exists(active,LinkOption.NOFOLLOW_LINKS),
            "ACTIVE missing precondition not established");
    }

    private static void lateNonForcedWitness(Path account)
        throws Exception{
        FilePlayerRepository initial=repository();
        initial.save(snapshot(LATE));
        byte[] before=Files.readAllBytes(account);
        AtomicBoolean inserted=new AtomicBoolean();
        FilePlayerRepository late=new FilePlayerRepository(
            LocalAccountProfiles::accountFile,
            unused->{},unused->{},unused->{},unused->{},
            actual->{
                check(LATE.equals(actual),
                    "pre-move RAW hook targeted foreign username");
                Files.write(
                    LocalFinalSaveNegativeWitness.witnessPath(account),
                    "test-only late negative".getBytes(
                        StandardCharsets.US_ASCII));
                inserted.set(true);
            });
        boolean veto=false;
        try{
            late.save(snapshot(LATE));
        }catch(IOException expected){
            veto=String.valueOf(expected.getMessage()).contains(
                "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
        }
        check(inserted.get()&&veto,
            "non-forced RAW late witness was not rejected");
        check(Arrays.equals(before,Files.readAllBytes(account)),
            "late witness allowed nonforced RAW overwrite");
        assertNoTemps(account,false);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "late negative RAW kept reservation");
    }

    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("r39-cross-jvm-final-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalFinalSaveNegativeWitness.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        flags(root,false);
        try{
            saveAll();
            for(Path file:paths())
                check(Files.isRegularFile(file,
                    LinkOption.NOFOLLOW_LINKS),
                    "initial source account was not committed");

            requireWitness(paths()[0],
                AccountStore.CANONICAL_USERNAME);
            requireWitness(paths()[1],NAMED);
            requireWitness(paths()[2],REPO);

            String plain=spawn(root,"DENY_PLAIN");
            check(plain.contains(
                "R39_CHILD_NEGATIVE_CROSS_JVM_RAW_DENIED_PASS"),
                "fresh JVM non-forced RAW did not respect witness");
            String forced=spawn(root,"DENY_FORCED");
            check(forced.contains(
                "R39_CHILD_NEGATIVE_CROSS_JVM_RAW_DENIED_PASS"),
                "fresh JVM forced RAW did not respect witness");
            String unrelated=spawn(root,"UNRELATED");
            check(unrelated.contains(
                "R39_CHILD_UNRELATED_RAW_ISOLATION_PASS"),
                "witness became a global block on other accounts");
            Path late=LocalAccountProfiles.accountFile(LATE);
            lateNonForcedWitness(late);

            // Test fixture ONLY: simulate verified operator manual
            // correction and preserve normal healthy retry semantics.
            for(Path file:paths()){
                Path negative=LocalFinalSaveNegativeWitness
                    .witnessPath(file);
                check(Files.isRegularFile(negative,
                    LinkOption.NOFOLLOW_LINKS),
                    "witness missing before manual fixture correction");
                Files.delete(negative);
            }
            String restored=spawn(root,"ALLOW");
            check(restored.contains(
                "R39_CHILD_MANUALLY_RESTORED_RAW_SUCCESS_PASS"),
                "explicit fixture recovery did not permit healthy RAW");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "parent leak after multiple JVM phases");
            System.out.println(
                "R39_CROSS_JVM_RETAINED_FINAL_WITNESS_RAW_PASS"+
                " genuineG21298FinalWitnessCreated=true"+
                " missingActiveStillDenied=true"+
                " witnessProducerFeatureDisabled=true"+
                " freshJvmNonforcedRawDeniedAllThree=true"+
                " freshJvmForcedRawDeniedAllThree=true"+
                " lateNonforcedRawWitnessDenied=true"+
                " accountsAndNegativeWitnessBytesPreserved=true"+
                " unrelatedAccountRawStillAllowed=true"+
                " manualFixtureOnlyWitnessRemoval=true"+
                " legitimateRetryAfterFixtureCorrection=true"+
                " actualSeparateJvmProcesses=true"+
                " originalV308ClientAck=false"+
                " realCrashPowerLossProof=false"+
                " crossProcessOwnerEpoch=false"+
                " automaticRecovery=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> walked=Files.walk(root)){
                for(Path file:walked.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),args[2]);
        else if(args.length==0)parent();
        else throw new IllegalArgumentException("R39 invalid args");
    }
}
