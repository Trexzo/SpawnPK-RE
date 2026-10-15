package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.245: a real replaced lock file at legacy primary/named RAW
 * pre-move seam must deny account publication without changing bytes.
 */
public final class G21245LegacyPrimaryAndNamedRawLockContinuityTest {
    private static final String NAMED="g21245-named-account";
    private G21245LegacyPrimaryAndNamedRawLockContinuityTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.245 "+reason);
    }
    private static void swap(Path account)throws IOException{
        Path lock=account.resolveSibling(
            account.getFileName().toString()+".session.lck");
        BasicFileAttributes before=Files.readAttributes(
            lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(before.fileKey()!=null,"old OS lock key missing");
        Files.delete(lock);
        Files.createFile(lock);
        BasicFileAttributes after=Files.readAttributes(
            lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(after.fileKey()!=null&&
            !after.fileKey().equals(before.fileKey()),
            "OS lock inode substitution not effective");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21245-primary-named-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] before=new String[keys.length];
        for(int i=0;i<keys.length;i++)before[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer p=new WorldPlayer();
            Path primary=AccountStore.accountFile();
            String saved=AccountStore.save(
                p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(saved.contains("ACCOUNT_SAVED"),
                "primary positive control failed");
            byte[] primaryBytes=Files.readAllBytes(primary);
            boolean denied=false;
            try{
                AccountStore.saveWithPreMoveHookForTest(
                    p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState(),account->swap(primary));
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage())
                    .contains("SESSION_LOCK_FILE_OBJECT_CHANGED");
            }
            check(denied,"primary did not veto swapped lock object");
            check(Arrays.equals(primaryBytes,Files.readAllBytes(primary)),
                "primary replaced account bytes despite veto");
            check(!Files.exists(primary.resolveSibling(
                    primary.getFileName().toString()+".tmp"),
                    LinkOption.NOFOLLOW_LINKS),
                "primary temporary file not cleaned after veto");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "primary RAW reservation leaked");
            saved=AccountStore.save(
                p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(saved.contains("ACCOUNT_SAVED"),
                "primary could not recover after old reservation release");

            Path named=LocalAccountProfiles.accountFile(NAMED);
            saved=LocalAccountProfiles.save(
                NAMED,p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(saved.contains("ACCOUNT_SAVED"),
                "named positive control failed");
            byte[] namedBytes=Files.readAllBytes(named);
            denied=false;
            try{
                LocalAccountProfiles.saveWithPreMoveHookForTest(
                    NAMED,p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState(),account->swap(named));
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage())
                    .contains("SESSION_LOCK_FILE_OBJECT_CHANGED");
            }
            check(denied,"named profile did not veto swapped lock object");
            check(Arrays.equals(namedBytes,Files.readAllBytes(named)),
                "named profile replaced account bytes despite veto");
            check(!Files.exists(named.resolveSibling(
                    named.getFileName().toString()+".tmp"),
                    LinkOption.NOFOLLOW_LINKS),
                "named temporary file not cleaned after veto");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "named RAW reservation leaked");
            saved=LocalAccountProfiles.save(
                NAMED,p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            check(saved.contains("ACCOUNT_SAVED"),
                "named writer could not recover after reservation release");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "legacy writer test leaked JVM sessions");
            System.out.println(
                "G21245_LEGACY_PRIMARY_NAMED_LOCK_OBJECT_VETO_PASS"+
                " primaryAndNamedDeniedBeforeMove=true"+
                " oldPostimagesPreserved=true"+
                " legacyTempCleanup=true"+
                " reservationRecovery=true"+
                " externalHostileSwapAtomicity=false"+
                " durableOwnerEpoch=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(before[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],before[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:(Iterable<Path>)paths.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
