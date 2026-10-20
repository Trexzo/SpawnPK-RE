package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R38: a surviving one-shot final World NEGATIVE witness is an
 * independent RAW publication veto even when ACTIVE was removed.
 * Witness presence, including a malformed body, never authorizes
 * retry, repair, deletion or positive recovery.
 */
public final class G21366ForcedRawFinalNegativeWitnessFenceRegression {
    private static final String NS="r38_forced_witness";
    private G21366ForcedRawFinalNegativeWitnessFenceRegression(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("R38_FINAL_WITNESS_RAW "+reason);
    }
    @FunctionalInterface interface Save {void run()throws IOException;}

    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            NS,Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static Path witness(Path selected)throws IOException{
        return LocalFinalSaveNegativeWitness.witnessPath(selected);
    }

    private static void assertNoOwnedTemp(Path account)throws IOException{
        try(Stream<Path> all=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(all.noneMatch(file->file.getFileName().toString()
                .startsWith(prefix)),
                "refused RAW writer leaked owned temp "+account);
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "cooperating RAW reservation leaked");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "G21.39 publication lease leaked");
    }

    private static void seedUntrustedWitness(Path account)
        throws IOException{
        // Synthetic malformed witness: presence alone is negative.
        // Real forced World final witnesses require many further
        // independent gates and must never be manufactured by RAW.
        Files.write(witness(account),"UNTRUSTED_NEGATIVE".getBytes(
            StandardCharsets.US_ASCII));
    }

    private static void existingWitnessDenied(Path account,Save attempt)
        throws Exception{
        byte[] baseline=Files.readAllBytes(account);
        check(!Files.exists(witness(account),LinkOption.NOFOLLOW_LINKS),
            "negative witness already present");
        seedUntrustedWitness(account);
        boolean refused=false;
        try{
            attempt.run();
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
        }
        check(refused,"preexisting negative witness was bypassed");
        check(Arrays.equals(baseline,Files.readAllBytes(account)),
            "preexisting witness RAW modified account bytes");
        check(Files.isRegularFile(witness(account),
                LinkOption.NOFOLLOW_LINKS),
            "publisher removed independent final witness");
        assertNoOwnedTemp(account);
        // Fixture-only manual deletion; no production cleanup contract.
        Files.delete(witness(account));
    }

    private static void lateWitnessDenied(Path account,Save attempt)
        throws Exception{
        byte[] baseline=Files.readAllBytes(account);
        AtomicBoolean inserted=new AtomicBoolean();
        LocalOptInRawTemp.setBeforeMoveProbeForTest(selected->{
            check(account.equals(selected),
                "late witness injected into unrelated account");
            seedUntrustedWitness(account);
            inserted.set(true);
        });
        boolean refused=false;
        try{
            attempt.run();
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
        }
        check(inserted.get()&&refused,
            "late independent witness was not a pre-move veto");
        check(Arrays.equals(baseline,Files.readAllBytes(account)),
            "late witness RAW replaced old account");
        check(Files.isRegularFile(witness(account),
                LinkOption.NOFOLLOW_LINKS),
            "late negative witness was lost");
        assertNoOwnedTemp(account);
        // Fixture-only manual removal; production must retain evidence.
        Files.delete(witness(account));
    }

    private static void postMoveMustBeUncertain(
        Path account,Save attempt
    )throws Exception{
        byte[] baseline=Files.readAllBytes(account);
        AtomicBoolean inserted=new AtomicBoolean();
        LocalOptInRawTemp.setAfterMoveProbeForTest(selected->{
            check(account.equals(selected),
                "postmove hook observed another RAW account");
            seedUntrustedWitness(account);
            inserted.set(true);
        });
        boolean uncertain=false;
        try{
            attempt.run();
        }catch(IOException expected){
            uncertain=String.valueOf(expected.getMessage()).contains(
                "RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                expected.getCause()!=null&&
                String.valueOf(expected.getCause().getMessage()).contains(
                    "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
        }
        check(inserted.get()&&uncertain,
            "postmove negative witness was acknowledged as success");
        check(!Arrays.equals(baseline,Files.readAllBytes(account)),
            "postmove fixture failed to replace exact account bytes");
        check(Files.isRegularFile(witness(account),
                LinkOption.NOFOLLOW_LINKS),
            "postmove witness was erased");
        assertNoOwnedTemp(account);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r38-forced-final-negative-");
        String[] properties={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalFinalSaveNegativeWitness.PROPERTY
        };
        String[] previous=new String[properties.length];
        for(int i=0;i<properties.length;i++)
            previous[i]=System.getProperty(properties[i]);
        System.setProperty(properties[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<properties.length-1;i++)
            System.setProperty(properties[i],"true");
        // Witness admission is intentionally OFF; an old surviving
        // witness must remain a negative RAW write fence.
        System.setProperty(properties[properties.length-1],"false");
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);

            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path primary=AccountStore.accountFile();
            Save savePrimary=()->AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            existingWitnessDenied(primary,savePrimary);
            lateWitnessDenied(primary,savePrimary);
            savePrimary.run();

            String namedUser="r38-named";
            LocalAccountProfiles.save(namedUser,
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(namedUser);
            Save saveNamed=()->LocalAccountProfiles.save(
                namedUser,player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            existingWitnessDenied(named,saveNamed);
            lateWitnessDenied(named,saveNamed);
            saveNamed.run();

            String rawUser="r38-repo";
            repo.save(snapshot(rawUser,"BASE"));
            Path raw=LocalAccountProfiles.accountFile(rawUser);
            Save saveRaw=()->repo.save(snapshot(rawUser,"NEW"));
            existingWitnessDenied(raw,saveRaw);
            lateWitnessDenied(raw,saveRaw);
            postMoveMustBeUncertain(raw,saveRaw);

            // An unrelated account's final witness is not a global
            // rejection condition for a separate selected account.
            String otherUser="r38-foreign";
            repo.save(snapshot(otherUser,"OTHER"));
            Path other=LocalAccountProfiles.accountFile(otherUser);
            seedUntrustedWitness(other);
            String cleanUser="r38-clean";
            repo.save(snapshot(cleanUser,"SUCCESS"));
            check(repo.load(cleanUser).isPresent(),
                "foreign witness blocked distinct successful RAW write");
            check(Files.isRegularFile(witness(other),
                    LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(witness(raw),
                    LinkOption.NOFOLLOW_LINKS),
                "unrelated or uncertain negative evidence removed");
            assertNoOwnedTemp(LocalAccountProfiles.accountFile(cleanUser));
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test retained admitted World owner");

            System.out.println(
                "R38_FORCED_RAW_FINAL_NEGATIVE_WITNESS_FENCE_PASS"+
                " witnessFeatureDisabledButOldEvidenceProtected=true"+
                " primaryNamedRepositoryPreexistingDenied=true"+
                " primaryNamedRepositoryLateDenied=true"+
                " accountPreimagesPreserved=true"+
                " latePostmoveUncertainNotAck=true"+
                " postmoveBytesNotRolledBack=true"+
                " separateAccountPositiveControl=true"+
                " witnessesPreserved=true"+
                " cleanReservationsAndTemps=true"+
                " globalOwnerEpoch=false"+
                " crashDurableFinalAck=false"+
                " noAutoWitnessRetirement=true");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            for(int i=0;i<properties.length;i++){
                if(previous[i]==null)
                    System.clearProperty(properties[i]);
                else System.setProperty(properties[i],previous[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
