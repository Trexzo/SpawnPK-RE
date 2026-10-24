package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R43: standalone non-World StrictDurablePlayerSnapshotWriter uses
 * the shared opt-in RAW reservation. It must refuse a retained
 * final-save negative witness at admission AND pre-move, and report
 * UNKNOWN if a final witness first appears AFTER atomic replacement.
 *
 * This proves a cooperating strict file API boundary, not final
 * logout ACK, power-loss durability or global World owner epoch.
 */
public final class G21371StandaloneStrictFinalWitnessPublicationRegression {
    private static final String USER="r43-strict-selected";
    private static final String OTHER="r43-unrelated";
    private G21371StandaloneStrictFinalWitnessPublicationRegression(){}

    private static void check(boolean good,String why){
        if(!good)throw new AssertionError("R43_STRICT_FINAL_WITNESS "+why);
    }
    private static PlayerSnapshot snapshot(String user,String value){
        WorldPlayer state=new WorldPlayer();
        state.snapshotExtensions().replaceNamespace(
            "r43_strict",Collections.singletonMap("phase",value));
        return PlayerSnapshotCodec.capture(user,state);
    }
    private static void injectWitness(Path account)throws IOException{
        Files.write(
            LocalFinalSaveNegativeWitness.witnessPath(account),
            "test-only untrusted retained final witness".getBytes(
                StandardCharsets.US_ASCII));
    }
    private static void noOwnedTemp(Path account)throws IOException{
        String name=account.getFileName().toString()+".g2123-";
        try(Stream<Path> all=Files.list(account.getParent())){
            check(all.noneMatch(path->path.getFileName().toString()
                .startsWith(name)),
                "strict owned temp leaked after negative result");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "strict RAW reservation leaked");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r43-strict-final-negative-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalFinalSaveNegativeWitness.PROPERTY
        };
        String[] old=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            old[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],"true");
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"false");
        // Witness creation is OFF, but a surviving witness remains
        // a negative exact-account publication fence.
        System.setProperty(flags[3],"false");
        try{
            Path selected=root.resolve("selected.properties");
            Path independent=root.resolve("other.properties");
            FilePlayerRepository.PathResolver resolver=user->
                OTHER.equals(user)?independent:selected;
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            check(strict.saveStrict(snapshot(USER,"BASE"))
                    .matchesSnapshot(snapshot(USER,"BASE")),
                "healthy initial strict save failed");
            byte[] baseline=Files.readAllBytes(selected);
            check(Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS),
                "strict account file missing");

            // Admission-time witness refuses before temp allocation.
            injectWitness(selected);
            boolean denied=false;
            try{
                strict.saveStrict(snapshot(USER,"DENIED_EXISTING"));
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            check(denied,"admission ignored retained final witness");
            check(Arrays.equals(baseline,Files.readAllBytes(selected)),
                "admission negative changed existing strict account");
            check(Files.isRegularFile(
                    LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "admission removed independent final witness");
            noOwnedTemp(selected);
            Files.delete(LocalFinalSaveNegativeWitness
                .witnessPath(selected)); // fixture-only manual correction

            // After RAW admission, but before account move: use the
            // real strict file publisher BEFORE_ATOMIC_REPLACE cutpoint.
            AtomicBoolean injectedBefore=new AtomicBoolean();
            StrictDurablePlayerSnapshotWriter beforeMove=
                new StrictDurablePlayerSnapshotWriter(
                    resolver,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            injectWitness(selected);
                            injectedBefore.set(true);
                        }
                    });
            denied=false;
            try{
                beforeMove.saveStrict(snapshot(USER,"DENIED_LATE"));
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            check(injectedBefore.get()&&denied,
                "late final witness was not vetoed pre-move");
            check(Arrays.equals(baseline,Files.readAllBytes(selected)),
                "late pre-move witness changed strict account bytes");
            check(Files.isRegularFile(
                    LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "late negative evidence removed");
            noOwnedTemp(selected);
            Files.delete(LocalFinalSaveNegativeWitness
                .witnessPath(selected)); // fixture-only manual correction

            // After successful move + directory force the same
            // negative evidence cannot undo the already changed file.
            // A strict Receipt would misrepresent the outcome.
            AtomicBoolean injectedAfter=new AtomicBoolean();
            LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
                path->{
                    check(selected.equals(path),
                        "postmove negative seam observed other account");
                    injectWitness(selected);
                    injectedAfter.set(true);
                });
            boolean uncertain=false;
            try{
                strict.saveStrict(snapshot(USER,"MOVED_UNCONFIRMED"));
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                uncertain=String.valueOf(expected.getMessage()).contains(
                    "R43 STRICT_RAW_POST_MOVE_FINAL_WITNESS_UNCONFIRMED_MANUAL_REVIEW")&&
                    expected.getCause()!=null&&
                    String.valueOf(expected.getCause().getMessage()).contains(
                    "R39_RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW");
            }finally{
                LocalJvmAccountSessionLease
                    .setAfterRawPublicationProbeForTest(null);
            }
            check(injectedAfter.get()&&uncertain,
                "postmove witness issued strict receipt or false rollback");
            check(!Arrays.equals(baseline,Files.readAllBytes(selected)),
                "postmove fixture did not demonstrate replaced bytes");
            check(Files.isRegularFile(
                    LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "postmove witness lost");
            noOwnedTemp(selected);

            // A witness scoped to selected does not globally block
            // a distinct account's healthy strict file publication.
            PlayerSnapshot other=snapshot(OTHER,"HEALTHY");
            check(strict.saveStrict(other).matchesSnapshot(other),
                "foreign account's witness blocked independent strict");
            check(Files.isRegularFile(independent,
                LinkOption.NOFOLLOW_LINKS),
                "healthy unrelated strict file not published");
            noOwnedTemp(independent);

            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "strict negative test leaked World owner");
            System.out.println(
                "R43_STRICT_FINAL_WITNESS_PUBLICATION_PASS"+
                " witnessProducerOptInOff=true"+
                " preexistingAdmissionDenied=true"+
                " beforeAtomicMoveWitnessDenied=true"+
                " originalAccountBytesPreservedOnPreMoveVeto=true"+
                " postAtomicMoveUncertainNotReceipt=true"+
                " changedBytesNeverClaimedRolledBack=true"+
                " foreignAccountStillReceivesHealthyStrictReceipt=true"+
                " negativeWitnessPreserved=true"+
                " strictTempsAndRawReservationsClean=true"+
                " durableFinalWorldAck=false"+
                " authenticatedGlobalOwnerEpoch=false"+
                " originalV308Runtime=false");
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
            for(int i=0;i<flags.length;i++){
                if(old[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],old[i]);
            }
            try(Stream<Path> walked=Files.walk(root)){
                for(Path file:walked.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
