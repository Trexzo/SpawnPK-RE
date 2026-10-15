package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.234 TEST ONLY: opting into admission-time RAW hardlink count
 * does not protect its later fixed-temp replace after a new hardlink
 * appears in the existing deterministic post-reservation test hook.
 */
public final class G21234CoreRawLateHardlinkPublicationGapTest {
    private G21234CoreRawLateHardlinkPublicationGapTest(){}
    private static final String USER="g21234-raw-hardlink";
    private static final String NS="core_g21234";
    private static final String KEY="extension.core_g21234.phase";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.234 "+why);
    }
    private static PlayerSnapshot snapshot(String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(NS,
            Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(USER,player);
    }
    private static String phase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> saved=repository.load(USER);
        check(saved.isPresent(),"saved state missing");
        return saved.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21234-raw-late-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] original=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            original[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)
            System.setProperty(keys[i],"true");
        try{
            Path file=LocalAccountProfiles.accountFile(USER);
            Path alias=file.resolveSibling("g21234-raw-alias.properties");
            AtomicBoolean armed=new AtomicBoolean(false);
            AtomicBoolean linked=new AtomicBoolean(false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                name->{},name->{},name->{},name->{},
                name->{
                    if(armed.compareAndSet(true,false)){
                        check(Files.isRegularFile(
                            file,LinkOption.NOFOLLOW_LINKS),
                            "real account leaf missing before RAW replace");
                        Files.createLink(alias,file);
                        linked.set(true);
                    }
                });
            FilePlayerRepository aliasRepo=new FilePlayerRepository(
                name->alias);
            repo.save(snapshot("EARLIER_RAW"));
            check("EARLIER_RAW".equals(phase(repo)),
                "initial RAW account file not saved");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "alias incorrectly existed before second write");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "initial RAW reservation leaked");

            // Original parent #2717 retains the exact all-ON
            // RAW publication gap. This positive-fix descendant
            // preserves only TEST-ONLY explicit opt-out compatibility.
            armed.set(true);
            System.setProperty(
                LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
                "false");
            try{
                repo.save(snapshot("LATER_RAW"));
            }finally{
                System.setProperty(
                    LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
                    "true");
            }
            check(linked.get(),
                "post-reservation RAW hook did not create real hardlink");
            check("LATER_RAW".equals(phase(repo)),
                "canonical RAW pathname not updated");
            check("EARLIER_RAW".equals(phase(aliasRepo)),
                "new hardlink did not preserve older postimage");
            check(!Files.isSameFile(file,alias),
                "RAW replacement did not create distinct file objects");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "RAW publisher leaked in-JVM reservation");

            System.out.println(
                "G21234_RAW_POST_RESERVATION_HARDLINK_SPLIT_PASS"+
                " hardlinkGateOffDuringSecondRawReplaceOnly=true"+
                " rawReservationAcquiredBeforeAlias=true"+
                " actualAliasCreatedImmediatelyBeforeRawReplace=true"+
                " legacyRawTempReplacedCanonical=true"+
                " aliasRetainedEarlierSnapshot=true"+
                " historicRawOptOutStillUnsafe=true"+
                " noOriginalServerPolicyClaim=true"+
                " globalExternalWriterFence=false"+
                " durableOwnerEpoch=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(original[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],original[i]);
            }
            try(Stream<Path> entries=Files.walk(root)){
                for(Path item:entries.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(item);
            }
        }
    }
}
