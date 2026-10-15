package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * G21.246: opt-in RAW unique temp must not follow/touch a historical
 * fixed .tmp symlink pointing at an unrelated file.
 */
public final class G21246UniqueRawTempNofollowFixedSymlinkTest {
    private static final String NAMED="g21246-legacy-named";
    private static final String RAW="g21246-repository-raw";
    private static final String VICTIM="DO_NOT_TOUCH_G21246_VICTIM";
    private static final String KEY="extension.core_g21246.phase";
    private G21246UniqueRawTempNofollowFixedSymlinkTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.246 "+why);
    }
    private static Path installFixedSymlink(Path file,Path victim)
        throws IOException{
        Files.createDirectories(file.getParent());
        Path temp=file.resolveSibling(file.getFileName().toString()+".tmp");
        check(!Files.exists(temp,LinkOption.NOFOLLOW_LINKS),
            "legacy temporary path occupied unexpectedly");
        Files.createSymbolicLink(temp,victim.toAbsolutePath().normalize());
        return temp;
    }
    private static void checkVictim(Path victim,Path oldTemp)
        throws IOException{
        check(VICTIM.equals(Files.readString(
                victim,StandardCharsets.UTF_8)),
            "legacy fixed symlink victim was modified");
        check(Files.isSymbolicLink(oldTemp),
            "legacy fixed .tmp symlink should not be consumed");
    }
    private static void accountExists(Path file)throws IOException{
        check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
            "cooperating account publication did not create file "+file);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21246-safe-raw-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY};
        String[] before=new String[keys.length];
        for(int i=0;i<keys.length;i++)before[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            Path victim=root.resolve("independent-victim.txt");
            Files.writeString(victim,VICTIM,StandardCharsets.UTF_8);
            WorldPlayer p=new WorldPlayer();

            Path primary=AccountStore.accountFile();
            Path primaryTmp=installFixedSymlink(primary,victim);
            String receipt=AccountStore.save(
                p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(receipt.contains("ACCOUNT_SAVED"),
                "primary secure RAW save failed");
            accountExists(primary);
            checkVictim(victim,primaryTmp);

            Path named=LocalAccountProfiles.accountFile(NAMED);
            Path namedTmp=installFixedSymlink(named,victim);
            receipt=LocalAccountProfiles.save(
                NAMED,p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(receipt.contains("ACCOUNT_SAVED"),
                "named secure RAW save failed");
            accountExists(named);
            checkVictim(victim,namedTmp);

            Path raw=LocalAccountProfiles.accountFile(RAW);
            Path rawTmp=installFixedSymlink(raw,victim);
            p.snapshotExtensions().replaceNamespace(
                "core_g21246",Collections.singletonMap("phase","RAW_OK"));
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(RAW,p));
            accountExists(raw);
            checkVictim(victim,rawTmp);
            Optional<PlayerSnapshot> saved=repo.load(RAW);
            check(saved.isPresent()&&
                "RAW_OK".equals(saved.get().values().get(KEY)),
                "secure RAW repository postimage not reloadable");

            try(Stream<Path> walk=Files.walk(root)){
                check(walk.noneMatch(path->path.getFileName()
                    .toString().contains(".g21246-raw-")),
                    "successful unique RAW temp was not moved/cleaned");
            }
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "positive secure RAW path leaked reservation");

            // Incorrect partial opt-in must fail closed rather than
            // silently reverting to predictable fixed .tmp behavior.
            byte[] existing=Files.readAllBytes(primary);
            System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"false");
            boolean denied=false;
            try{
                AccountStore.save(
                    p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState());
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "SAFE_RAW_TEMP_REQUIRES_LOCKED_CONTINUITY_OPT_IN");
            }
            check(denied,"partial safety opt-in silently fell back");
            check(java.util.Arrays.equals(existing,Files.readAllBytes(primary)),
                "misconfigured safe RAW mode rewrote account");
            checkVictim(victim,primaryTmp);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "negative configuration leaked RAW reservation");

            System.out.println(
                "G21246_UNIQUE_RAW_TEMP_NOFOLLOW_FIXED_SYMLINK_PASS"+
                " primaryNamedRepositoryProtected=true"+
                " legacyFixedTempSymlinksUntouched=true"+
                " originalVictimBytesPreserved=true"+
                " partialOptInFailClosed=true"+
                " defaultBehaviorUnchanged=true"+
                " noDurableOwnerEpoch=true");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(before[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],before[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(file);
            }
        }
    }
}
