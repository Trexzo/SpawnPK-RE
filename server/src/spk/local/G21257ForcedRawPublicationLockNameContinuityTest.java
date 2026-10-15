package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.257: opt-in forced RAW must not publish after deterministic
 * replacement of the very G21.39 publication-lock *name* whose
 * original file identity was observed under its OS lock.
 */
public final class G21257ForcedRawPublicationLockNameContinuityTest {
    private G21257ForcedRawPublicationLockNameContinuityTest(){}
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError("G21.257 "+message);
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(files.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),"orphaned forced RAW temp");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21257-publication-lock-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve(
            "opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        Path lock=null;
        Path orphan=null;
        boolean nameReplaced=false;
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path account=AccountStore.accountFile();
            byte[] original=Files.readAllBytes(account);
            lock=MailboxAccountPublicationCoordinator.lockPath(account);
            orphan=lock.resolveSibling(
                lock.getFileName().toString()+".g21257-held-original");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "G21.39 lock name not created");
            final Path targetLock=lock;
            final Path displaced=orphan;
            AtomicBoolean swapped=new AtomicBoolean();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(file->{
                check(account.equals(file),"wrong account intercepted");
                Files.move(targetLock,displaced,
                    StandardCopyOption.ATOMIC_MOVE);
                Files.createFile(targetLock);
                swapped.set(true);
            });
            boolean refused=false;
            try{
                AccountStore.save(
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState());
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "RAW_PUBLICATION_LOCK_NAME_CHANGED_NO_MOVE");
            }finally{
                LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            }
            nameReplaced=swapped.get();
            check(nameReplaced&&refused,
                "replaced lock name was not vetoed before account move");
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "observed publication lock swap changed account bytes");
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked on swapped publication lock");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 JVM lease leaked on lock-name swap");
            // Restore original object only after G21.39 FileLock closes.
            Files.delete(targetLock);
            Files.move(displaced,targetLock,
                StandardCopyOption.ATOMIC_MOVE);
            AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            noTemp(account);
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "normal continuation left G21.39 lease");
            System.out.println(
                "G21257_FORCED_RAW_PUBLICATION_LOCK_NAME_CONTINUITY_PASS"+
                " actualHeldG2139LockNameReplaced=true"+
                " forcedAccountMoveDenied=true"+
                " originalAccountBytesPreserved=true"+
                " originalLockObjectRestored=true"+
                " subsequentForcedWriteSucceeds=true"+
                " noRawOrG2139LeaseLeak=true"+
                " noAtomicDescriptorBindingClaim=true"+
                " durableOwnerEpoch=false");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            // Only test-owned files are under the isolated temp root.
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:(Iterable<Path>)files.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
