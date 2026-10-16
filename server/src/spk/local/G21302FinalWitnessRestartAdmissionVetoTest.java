package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.302: a retained final review witness is always negative for
 * a REAL admitted FilePlayerRepository session load, even if ACTIVE
 * vanished and witness creation is disabled in a later JVM.
 */
public final class G21302FinalWitnessRestartAdmissionVetoTest {
    private G21302FinalWitnessRestartAdmissionVetoTest(){}
    private static void check(boolean good,String why){
        if(!good)throw new AssertionError("G21.302 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21302-restart-veto-");
        final String flag=LocalFinalSaveNegativeWitness.PROPERTY;
        String beforeFlag=System.getProperty(flag);
        try{
            System.clearProperty(flag);
            FilePlayerRepository repository=new FilePlayerRepository(
                user->root.resolve(user+".properties"));
            Path account=root.resolve("selected.properties");
            PlayerSnapshot saved=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            repository.save(saved);
            byte[] accountBytes=Files.readAllBytes(account);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",1L,1L);
            byte[] witnessBytes=Files.readAllBytes(witness);
            check(Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS),
                "ACTIVE review source missing");
            // Model a manual/uncooperative actor removing ACTIVE after
            // a hard restart; the witness must still veto admission.
            Files.delete(active);
            check(!Files.exists(active,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
                "missing-ACTIVE retained-witness precondition");
            boolean denied=false;
            try{
                repository.loadForWorldSession("selected");
            }catch(IOException expected){
                denied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            check(denied,
                "genuine file-backed session hydration ignored retained witness");
            check(repository.load("selected").isPresent(),
                "raw non-admitting forensic load was accidentally disabled");
            check(Arrays.equals(accountBytes,Files.readAllBytes(account))&&
                Arrays.equals(witnessBytes,Files.readAllBytes(witness)),
                "veto rewrote account or untrusted negative witness");
            System.out.println(
                "G21302_NEGATIVE_FINAL_WITNESS_RESTART_ADMISSION_VETO_PASS"+
                " exactRealSessionLoader=true"+
                " missingACTIVEStillVetoed=true"+
                " writerOptInDisabledStillVetoed=true"+
                " noPositiveReceiptClaim=true"+
                " rawForensicReadPreserved=true"+
                " accountAndWitnessUnchanged=true");
        }finally{
            if(beforeFlag==null)System.clearProperty(flag);
            else System.setProperty(flag,beforeFlag);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
