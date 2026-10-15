package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.248: tested opt-in RAW filesystem publication only.
 * Not a final-disconnect ACK, crash-safe epoch or account recovery.
 */
public final class G21248RawForcedVerifiedPostimageTest {
    private static final String NAMED="g21248-named";
    private static final String RAW="g21248-repository";
    private static final String KEY="extension.core_g21248.phase";
    private G21248RawForcedVerifiedPostimageTest(){}

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G21.248 "+reason);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer p=new WorldPlayer();
        p.snapshotExtensions().replaceNamespace(
            "core_g21248",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,p);
    }
    private static String disk(FilePlayerRepository repo,String username)
        throws IOException{
        Optional<PlayerSnapshot> read=repo.load(username);
        check(read.isPresent(),"account missing "+username);
        return read.get().values().get(KEY);
    }
    private static void noUniqueTemp(Path account)throws IOException{
        try(Stream<Path> dir=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(dir.noneMatch(path->path.getFileName().toString()
                .startsWith(prefix)),
                "unique RAW temp was not removed or renamed");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21248-forced-raw-");
        String[] names={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] original=new String[names.length];
        for(int i=0;i<names.length;i++)original[i]=System.getProperty(names[i]);
        System.setProperty(names[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<names.length;i++)System.setProperty(names[i],"true");
        try{
            WorldPlayer p=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);

            String outcome=AccountStore.save(
                p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(outcome.contains("ACCOUNT_SAVED"),
                "primary forced RAW did not return completion");
            Path primary=AccountStore.accountFile();
            check(Files.isRegularFile(
                    primary,LinkOption.NOFOLLOW_LINKS),
                "primary forced postimage missing");
            noUniqueTemp(primary);

            outcome=LocalAccountProfiles.save(
                NAMED,p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(outcome.contains("ACCOUNT_SAVED"),
                "named forced RAW did not return completion");
            Path named=LocalAccountProfiles.accountFile(NAMED);
            check(Files.isRegularFile(
                    named,LinkOption.NOFOLLOW_LINKS),
                "named forced postimage missing");
            noUniqueTemp(named);

            repo.save(snapshot(RAW,"BEFORE"));
            check("BEFORE".equals(disk(repo,RAW)),
                "repository forced RAW initial postimage differs");
            Path raw=LocalAccountProfiles.accountFile(RAW);
            noUniqueTemp(raw);

            // Fault fires strictly AFTER successful atomic rename.
            // The saved account can already contain the new bytes, so
            // throwing MUST say UNCONFIRMED, not claim rollback.
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalOptInRawTemp.setAfterMoveProbeForTest(account->{
                if(raw.equals(account)){
                    injected.set(true);
                    throw new IOException("TEST_AFTER_RAW_ATOMIC_RENAME");
                }
            });
            boolean unconfirmed=false;
            try{repo.save(snapshot(RAW,"MOVED_BUT_UNCONFIRMED"));}
            catch(IOException expected){
                unconfirmed=String.valueOf(expected.getMessage()).contains(
                    "RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW");
            }finally{
                LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            }
            check(injected.get()&&unconfirmed,
                "post-move failure was falsely certified as success");
            check("MOVED_BUT_UNCONFIRMED".equals(disk(repo,RAW)),
                "fault test did not demonstrate real post-move state");
            noUniqueTemp(raw);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "post-move fault leaked RAW reservation");

            byte[] before=Files.readAllBytes(primary);
            System.setProperty(LocalOptInRawTemp.PROPERTY,"false");
            boolean denied=false;
            try{
                AccountStore.save(
                    p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState());
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "FORCE_RAW_POSTIMAGE_REQUIRES_SAFE_TEMP");
            }
            check(denied,
                "forced mode without safe temp did not fail closed");
            check(Arrays.equals(before,Files.readAllBytes(primary)),
                "misconfiguration modified previous account");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "misconfiguration leaked RAW reservation");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test left admitted JVM owner");
            System.out.println(
                "G21248_FORCED_VERIFIED_RAW_POSTIMAGE_PASS"+
                " primaryNamedRepositoryPositive=true"+
                " uniqueTempForcedBeforeAtomicMove=true"+
                " directoryForcedAndBytesVerified=true"+
                " postMoveFaultExplicitlyUnconfirmed=true"+
                " partialOptInDenied=true"+
                " noRawReservationsLeaked=true"+
                " durableFinalSessionAck=false"+
                " crashSafeEpoch=false");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            for(int i=0;i<names.length;i++){
                if(original[i]==null)System.clearProperty(names[i]);
                else System.setProperty(names[i],original[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
