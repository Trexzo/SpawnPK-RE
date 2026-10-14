package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.198: evidence that G21.196's UNUSED epoch records are correlations
 * only, NOT authenticated ownership or postimage receipts.
 *
 * A raw test writer can reproduce a structurally valid pair without
 * ever reserving through the helper or registering a World player;
 * independently changing account bytes does not invalidate the pair.
 * No production/admission/recovery/marker-retirement behavior is added.
 */
public final class G21198CoreEpochPrototypeUntrustedRecordNegativeTest {
    private G21198CoreEpochPrototypeUntrustedRecordNegativeTest(){}

    private static void check(boolean truth,String failure){
        if(!truth)throw new AssertionError("G21.198 "+failure);
    }

    private static String sha(byte[] data)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder result=new StringBuilder();
        for(byte b:hash){
            String pair=Integer.toHexString(b&255);
            if(pair.length()==1)result.append('0');
            result.append(pair);
        }
        return result.toString();
    }

    private static byte[] utf(String text){
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void expectInspectFailure(
        Path account,String expected
    )throws Exception{
        boolean rejected=false;
        try{
            LocalDurableOwnerEpochPrototype.inspect(account);
        }catch(IOException e){
            rejected="MISSING_RESERVATION".equals(expected)
                ?e instanceof java.nio.file.NoSuchFileException
                :e.getMessage()!=null&&e.getMessage().contains(expected);
        }
        check(rejected,"unsafe prototype record not rejected: "+expected);
    }

    private static void actualHelperPairDoesNotSealAccount(Path root)
        throws Exception{
        Path account=root.resolve("helper-account.properties");
        LocalDurableOwnerEpochPrototype.Reservation reservation=
            LocalDurableOwnerEpochPrototype
                .reserveBeforeWorldRegistration(account);
        LocalDurableOwnerEpochPrototype
            .bindAfterWorldRegistration(reservation,11L);
        LocalDurableOwnerEpochPrototype.Observation observed=
            LocalDurableOwnerEpochPrototype.inspect(account);
        check(observed.generationBound&&observed.worldGeneration==11L,
            "legitimate prototype pair not readable");

        byte[] originalReservation=Files.readAllBytes(
            LocalDurableOwnerEpochPrototype.reservationPath(account));
        Path binding=LocalDurableOwnerEpochPrototype
            .bindingPath(account);
        byte[] originalBinding=Files.readAllBytes(binding);
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "prototype unexpectedly created account snapshot");

        // Raw test-only mutations bypass the UNUSED record helper entirely.
        // The records describe the path, not the content of this account.
        Files.write(account,utf("untrusted-preimage=v1\n"),
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        byte[] before=Files.readAllBytes(account);
        Files.write(account,utf("untrusted-postimage=v2\n"),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        byte[] after=Files.readAllBytes(account);
        check(!Arrays.equals(before,after),
            "test-only account rewrite had no effect");

        LocalDurableOwnerEpochPrototype.Observation drift=
            LocalDurableOwnerEpochPrototype.inspect(account);
        check(drift.generationBound&&drift.worldGeneration==11L&&
              drift.reservation.reservationSha
                  .equals(reservation.reservationSha),
            "account bytes unexpectedly bind to epoch prototype");
        check(Arrays.equals(originalReservation,Files.readAllBytes(
                LocalDurableOwnerEpochPrototype.reservationPath(account)))&&
              Arrays.equals(originalBinding,Files.readAllBytes(binding)),
            "prototype records changed during uncoordinated account rewrite");

        String corrupted=new String(originalBinding,StandardCharsets.UTF_8)
            .replace("reservationSha256="+reservation.reservationSha,
                     "reservationSha256="+
                     "0000000000000000000000000000000000000000000000000000000000000000");
        check(!Arrays.equals(originalBinding,utf(corrupted)),
            "corrupted digest was not different");
        Files.write(binding,utf(corrupted),
            StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        expectInspectFailure(account,"BINDING_MISMATCH_NO_AUTHORITY");

        // Restore only the disposable fixture. A structurally correct
        // pair becoming parseable again does not establish authentication.
        Files.write(binding,originalBinding,
            StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        check(LocalDurableOwnerEpochPrototype.inspect(account)
                  .generationBound,
            "restored raw record not parseable");
    }

    private static void constructForeignPairWithoutOwner(Path root)
        throws Exception{
        Path account=root.resolve("foreign-account.properties");
        Path reservationPath=LocalDurableOwnerEpochPrototype
            .reservationPath(account);
        Path bindingPath=LocalDurableOwnerEpochPrototype
            .bindingPath(account);
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "foreign fixture account unexpectedly exists");

        // Reproduce the public/unencrypted record format using test code,
        // WITHOUT calling reserveBeforeWorldRegistration or ever registering
        // an actual WorldPlayer.
        String accountSha=sha(utf(account.toAbsolutePath()
            .normalize().toString()));
        String madeUpEpoch="550e8400-e29b-41d4-a716-446655440000";
        String reserveText="G21196_OWNER_RESERVATION_V1\n"+
            "accountSha256="+accountSha+"\n"+
            "prototypeEpoch="+madeUpEpoch+"\n"+
            "state=RESERVED_NO_ADMISSION\n";
        byte[] reservationBytes=utf(reserveText);
        String reservationSha=sha(reservationBytes);
        String bindText="G21196_OWNER_BINDING_V1\n"+
            "reservationSha256="+reservationSha+"\n"+
            "accountSha256="+accountSha+"\n"+
            "worldGeneration=37\n"+
            "state=BOUND_NO_ADMISSION\n";

        Files.write(reservationPath,reservationBytes,
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        Files.write(bindingPath,utf(bindText),
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        // No force and no real owner: inspect checks record structure.
        LocalDurableOwnerEpochPrototype.Observation manufactured=
            LocalDurableOwnerEpochPrototype.inspect(account);
        check(manufactured.generationBound&&
              manufactured.worldGeneration==37L&&
              madeUpEpoch.equals(manufactured.reservation.epoch),
            "untrusted writer could not demonstrate structural-only parsing");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "structural inspection created a World account");

        boolean duplicateDenied=false;
        try{
            LocalDurableOwnerEpochPrototype
                .reserveBeforeWorldRegistration(account);
        }catch(IOException ex){
            duplicateDenied=ex.getMessage()!=null&&
                ex.getMessage().contains(
                    "EPOCH_RECORD_PRESENT_NO_OVERWRITE");
        }
        check(duplicateDenied,
            "existing raw record was silently replaced");

        // The independent G21.188 ACTIVE review marker still controls
        // negative review status; forged epoch structure cannot clear it.
        Path active=LocalDurableAccountReviewIntentPrimitive
            .writeActiveReview(account);
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
        boolean activeVeto=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
        }catch(IOException ex){
            activeVeto=ex.getMessage()!=null&&
                ex.getMessage().contains(
                    "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
        }
        check(activeVeto,"forged records bypassed ACTIVE review negative");
        byte[] activeBefore=Files.readAllBytes(active);
        check(LocalDurableOwnerEpochPrototype.inspect(account)
                  .generationBound,
            "valid structural forgery ceased to be parseable");
        check(Arrays.equals(activeBefore,Files.readAllBytes(active)),
            "prototype inspection altered ACTIVE review");

        // Removal of a *test fixture* reservation leaves the second leaf
        // stranded: a binding by itself cannot be treated as owner proof.
        Files.delete(reservationPath);
        expectInspectFailure(account,"MISSING_RESERVATION");
        check(Files.isRegularFile(bindingPath,
            LinkOption.NOFOLLOW_LINKS),
            "stranded binding unexpectedly cleaned up");
        check(Arrays.equals(activeBefore,Files.readAllBytes(active)),
            "missing reservation retired ACTIVE intent");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21198-epoch-untrusted-");
        try{
            actualHelperPairDoesNotSealAccount(root);
            constructForeignPairWithoutOwner(root);
            System.out.println(
                "G21198_EPOCH_PROTOTYPE_UNTRUSTED_RECORD_PASS"+
                " validPairDoesNotSealAccountPostimage=true"+
                " digestCorruptionRejected=true"+
                " thirdPartyRecordsParseAsBound=true"+
                " fabricatedGenerationNotAuthenticated=true"+
                " rawRecordsDoNotProveForce=true"+
                " duplicateReservationDenied=true"+
                " activeReviewRemainsNegative=true"+
                " missingReservationRejected=true"+
                " noRealWorldPlayerCreated=true"+
                " noRuntimeAdmissionWiring=true"+
                " safeRetirement=false"+
                " recovery=false"+
                " rawWriterExclusion=false"+
                " powerLossProof=false");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
