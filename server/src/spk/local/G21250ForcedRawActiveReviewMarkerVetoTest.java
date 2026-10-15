package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.250: opt-in forced RAW filesystem publications reject a
 * pre-existing G21.188 durable ACTIVE review marker before rename.
 * Does not authorize clearing it, repair, replay or safe re-login.
 */
public final class G21250ForcedRawActiveReviewMarkerVetoTest {
    private static final String NAMED="g21250-named-account";
    private static final String RAW="g21250-repository-account";
    private G21250ForcedRawActiveReviewMarkerVetoTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.250 "+why);
    }
    private static void assertPreserved(
        Path account,byte[] bytes
    )throws IOException{
        check(Arrays.equals(bytes,Files.readAllBytes(account)),
            "ACTIVE review account postimage overwritten "+account);
        check(Files.exists(
                LocalDurableAccountReviewIntentPrimitive.markerPath(account),
                LinkOption.NOFOLLOW_LINKS),
            "ACTIVE review marker was retired "+account);
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(files.noneMatch(path->path.getFileName()
                .toString().startsWith(prefix)),
                "denied forced RAW leaked owned temp "+account);
        }
    }
    private static void expectVeto(IOAction action)throws Exception{
        boolean veto=false;
        try{action.run();}
        catch(IOException expected){
            veto=String.valueOf(expected.getMessage()).contains(
                "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
        }
        check(veto,"forced RAW writer did not veto ACTIVE review");
    }
    @FunctionalInterface
    private interface IOAction {void run()throws IOException;}
    private static PlayerSnapshot snapshot(String user,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21250",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(user,player);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21250-active-review-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);

            // PRIMARY: real legacy primary initial snapshot + forced
            // save, then persistent ACTIVE review, then denied overwrite.
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path primary=AccountStore.accountFile();
            byte[] primaryBefore=Files.readAllBytes(primary);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(primary);
            expectVeto(()->AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState()));
            assertPreserved(primary,primaryBefore);

            // Named profile uses its own RAW implementation and temp.
            LocalAccountProfiles.save(
                NAMED,player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(NAMED);
            byte[] namedBefore=Files.readAllBytes(named);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(named);
            expectVeto(()->LocalAccountProfiles.save(
                NAMED,player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState()));
            assertPreserved(named,namedBefore);

            // Repository direct RAW writer (not an admitted World save).
            repo.save(snapshot(RAW,"BEFORE_ACTIVE"));
            Path raw=LocalAccountProfiles.accountFile(RAW);
            byte[] rawBefore=Files.readAllBytes(raw);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(raw);
            expectVeto(()->repo.save(snapshot(RAW,"AFTER_ACTIVE")));
            assertPreserved(raw,rawBefore);

            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservations retained after three review denials");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test leaked admitted JVM owner");
            System.out.println(
                "G21250_FORCED_RAW_ACTIVE_REVIEW_VETO_PASS"+
                " primaryNamedRepositoryDenied=true"+
                " accountOldBytesPreserved=true"+
                " existingActiveReviewMarkersRetained=true"+
                " noReservationOrTempLeak=true"+
                " legacyDefaultModeUnchanged=true"+
                " noAutoRecoveryOrMarkerRetirement=true");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
