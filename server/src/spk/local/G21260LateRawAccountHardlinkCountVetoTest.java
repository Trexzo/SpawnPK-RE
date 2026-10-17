package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.260: late exact old account hardlink creation is denied under
 * the existing optional G21.230 nlink gate, not an atomic inode fence.
 */
public final class G21260LateRawAccountHardlinkCountVetoTest {
    private G21260LateRawAccountHardlinkCountVetoTest(){}
    private static void check(boolean pass,String what){
        if(!pass)throw new AssertionError("G21.260 "+what);
    }
    private static void noTemp(Path file)throws IOException{
        try(Stream<Path> entries=Files.list(file.getParent())){
            String prefix=file.getFileName().toString()+".g21246-raw-";
            check(entries.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)), "owned forced RAW temp leaked");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21260-late-nlink-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve(
            "opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path account=AccountStore.accountFile();
            byte[] first=Files.readAllBytes(account);
            Path alias=account.resolveSibling(
                account.getFileName().toString()+".g21260-test-alias");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "test alias already exists");
            AtomicBoolean added=new AtomicBoolean();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(current->{
                check(account.equals(current),
                    "wrong account in late hardlink hook");
                Files.createLink(alias,account);
                added.set(true);
            });
            boolean refused=false;
            try{
                AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }finally{
                LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            }
            check(added.get()&&refused,
                "late G21.230 nlink mutation was not denied");
            check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "hardlink alias was silently removed");
            check(Arrays.equals(first,Files.readAllBytes(account))&&
                Arrays.equals(first,Files.readAllBytes(alias)),
                "forced RAW changed hardlinked account bytes");
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation retained after hardlink veto");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 marker lease retained after hardlink veto");
            Files.delete(alias);
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0&&
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "positive forced save leaked account locks");
            System.out.println(
                "G21260_LATE_RAW_ACCOUNT_HARDLINK_COUNT_VETO_PASS"+
                " hardlinkInsertedInsideHeldPublicationLock=true"+
                " finalNlinkRecheckDeniedBeforeAtomicMove=true"+
                " oldAccountAndAliasBytesPreserved=true"+
                " aliasRemovedOnlyByTestAfterWriter=true"+
                " subsequentSingleLinkSaveSucceeds=true"+
                " negativeOnlyNoAtomicExternalFence=true"+
                " durableOwnerEpoch=false");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
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
