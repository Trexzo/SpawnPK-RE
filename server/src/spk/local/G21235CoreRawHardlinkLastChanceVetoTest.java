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
 * G21.235 positive opt-in last-chance NOFOLLOW RAW link-count veto
 * after the existing test hook creates a hardlink post-reservation.
 * Not an authenticated durable file-object epoch or external fence.
 */
public final class G21235CoreRawHardlinkLastChanceVetoTest {
    private G21235CoreRawHardlinkLastChanceVetoTest(){}
    private static final String USER="g21235-raw-hardlink";
    private static final String NS="core_g21235";
    private static final String KEY="extension.core_g21235.phase";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.235 "+why);
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
        Path root=Files.createTempDirectory("g21235-raw-late-");
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
            Path alias=file.resolveSibling("g21235-raw-alias.properties");
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

            // The last-chance opt-in check is AFTER the existing
            // inert-by-default RAW race hook, BEFORE the actual move.
            armed.set(true);
            boolean denied=false;
            try{
                repo.save(snapshot("LATER_RAW"));
            }catch(java.io.IOException expected){
                denied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "G21.230 MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }
            check(linked.get(),
                "RAW test hook never introduced post-reservation alias");
            check(denied,
                "RAW writer replaced file despite newly added hardlink");
            check("EARLIER_RAW".equals(phase(repo))&&
                    "EARLIER_RAW".equals(phase(aliasRepo)),
                "denied RAW write changed existing hardlink postimage");
            check(Files.isSameFile(file,alias),
                "denied RAW publication split one file object");
            Path temp=file.resolveSibling(
                file.getFileName().toString()+".tmp");
            check(!Files.exists(temp,LinkOption.NOFOLLOW_LINKS),
                "denied RAW saved an orphan fixed temp");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "denied RAW publication leaked in-JVM reservation");

            System.out.println(
                "G21235_RAW_HARDLINK_LAST_CHANCE_VETO_PASS"+
                " rawInJvmAndCrossJvmReservationEnabled=true"+
                " hardlinkCountGateOnThroughout=true"+
                " aliasCreatedAfterInitialRawReservation=true"+
                " finalNofollowHardlinkCheckDenied=true"+
                " originalAndAliasPostimageIdentical=true"+
                " fixedTempCleaned=true"+
                " existingRawReservationsReleased=true"+
                " atomicExternalWriterExclusion=false"+
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
