package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * R37: strict opt-in forced RAW writer must not publish a new image
 * over an existing different-account profile selected by its resolver.
 * Only explicitly stored existing usernames prove a mismatch:
 * historical schema-v1 images without username remain compatible.
 *
 * This is a per-account publication negative, not an authenticated
 * global owner epoch, login authorization or crash-safe recovery.
 */
public final class G21365ForcedRawExistingProfileOwnershipRegression {
    private static final String ACTUAL="r37-file-owner";
    private static final String WRONG="r37-foreign-writer";
    private static final String NAMED="r37-named";
    private G21365ForcedRawExistingProfileOwnershipRegression(){}

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("R37_RAW_PROFILE "+reason);
    }

    private static PlayerSnapshot snapshot(String username){
        return PlayerSnapshotCodec.capture(
            username,new WorldPlayer());
    }

    private static void seedExisting(Path path,String username)
        throws IOException{
        Files.createDirectories(path.getParent());
        Properties bytes=snapshot(username).toLegacyProperties();
        try(OutputStream stream=Files.newOutputStream(path)){
            bytes.store(stream,"R37 test-only foreign profile");
        }
    }

    @FunctionalInterface
    private interface SaveAttempt {
        void run()throws IOException;
    }

    private static void noOwnedRawTemps(Path account)
        throws IOException{
        String prefix=account.getFileName().toString()+
            ".g21246-raw-";
        try(Stream<Path> children=Files.list(account.getParent())){
            check(children.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),
                "RAW temp was not cleaned after refusal "+account);
        }
    }

    private static void deniedForeignOverwrite(
        Path account,SaveAttempt action
    )throws Exception{
        byte[] old=Files.readAllBytes(account);
        boolean refused=false;
        try{
            action.run();
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "R37_FORCED_RAW_EXISTING_PROFILE_UNTRUSTED_NO_MOVE");
        }
        check(refused,
            "wrong-account forced RAW postimage was accepted "+account);
        check(Arrays.equals(old,Files.readAllBytes(account)),
            "foreign-account refusal overwrote original bytes "+account);
        noOwnedRawTemps(account);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "rejected foreign-account RAW reservation leaked");
    }

    private static void confirmAccount(
        Path account,String username
    )throws IOException{
        FilePlayerRepository selected=new FilePlayerRepository(
            ignored->account);
        Optional<PlayerSnapshot> persisted=selected.load(username);
        check(persisted.isPresent()&&
            username.equals(persisted.get().username()),
            "correct same-profile RAW postimage missing "+account);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r37-raw-profile-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY
        };
        String[] old=new String[props.length];
        for(int i=0;i<props.length;i++)
            old[i]=System.getProperty(props[i]);
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<props.length;i++)
            System.setProperty(props[i],"true");
        try{
            FilePlayerRepository defaultRepo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            WorldPlayer state=new WorldPlayer();

            // RAW FilePlayerRepository may be configured with a custom
            // resolver mapping two usernames to one existing file.
            Path raw=LocalAccountProfiles.accountFile(ACTUAL);
            seedExisting(raw,ACTUAL);
            FilePlayerRepository foreignResolver=
                new FilePlayerRepository(ignored->raw);
            deniedForeignOverwrite(raw,
                ()->foreignResolver.save(snapshot(WRONG)));
            defaultRepo.save(snapshot(ACTUAL));
            confirmAccount(raw,ACTUAL);
            noOwnedRawTemps(raw);

            // The legacy primary account filename may be reused by a
            // misconfigured external initializer with foreign content.
            Path primary=AccountStore.accountFile();
            seedExisting(primary,WRONG);
            deniedForeignOverwrite(primary,
                ()->AccountStore.save(
                    state.bank(),state.equipment(),state.movement(),
                    state.petState(),state.playerState()));
            Files.delete(primary); // fixture-only manual correction
            AccountStore.save(
                state.bank(),state.equipment(),state.movement(),
                state.petState(),state.playerState());
            confirmAccount(primary,AccountStore.CANONICAL_USERNAME);
            noOwnedRawTemps(primary);

            // Named legacy writer follows the same locked strict RAW
            // boundary; no second interpretation of profile ownership.
            Path named=LocalAccountProfiles.accountFile(NAMED);
            seedExisting(named,WRONG);
            deniedForeignOverwrite(named,
                ()->LocalAccountProfiles.save(
                    NAMED,state.bank(),state.equipment(),
                    state.movement(),state.petState(),
                    state.playerState()));
            Files.delete(named); // fixture-only manual correction
            LocalAccountProfiles.save(
                NAMED,state.bank(),state.equipment(),state.movement(),
                state.petState(),state.playerState());
            confirmAccount(named,NAMED);
            noOwnedRawTemps(named);

            // An existing correctly-owned account is allowed to
            // undergo a subsequent legitimate forced RAW publication.
            defaultRepo.save(snapshot(ACTUAL));
            AccountStore.save(
                state.bank(),state.equipment(),state.movement(),
                state.petState(),state.playerState());
            LocalAccountProfiles.save(
                NAMED,state.bank(),state.equipment(),
                state.movement(),state.petState(),state.playerState());
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0&&
                LocalJvmAccountSessionLease.activeCountForTest()==0,
                "positive RAW retries leaked reservations");
            confirmAccount(raw,ACTUAL);
            confirmAccount(primary,AccountStore.CANONICAL_USERNAME);
            confirmAccount(named,NAMED);

            System.out.println(
                "R37_FORCED_RAW_EXISTING_PROFILE_OWNERSHIP_PASS"+
                " foreignRepositoryResolverDenied=true"+
                " foreignPrimaryLegacyOwnerDenied=true"+
                " foreignNamedLegacyOwnerDenied=true"+
                " oldAccountBytesPreserved=true"+
                " realForcedRawPositiveRetries=true"+
                " ownedTempsAndReservationsClean=true"+
                " defaultModeUnchanged=true"+
                " globalAuthenticatedOwnerEpoch=false"+
                " crashDurableFinalAck=false");
        }finally{
            for(int i=0;i<props.length;i++){
                if(old[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],old[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
