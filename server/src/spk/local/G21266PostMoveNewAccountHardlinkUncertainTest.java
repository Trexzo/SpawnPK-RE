package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.266: the NEW account inode can acquire a foreign hardlink after
 * Files.move. With the opt-in nlink gate, an observed alias means
 * uncertain postmove, not rollback or authenticated owner success.
 */
public final class G21266PostMoveNewAccountHardlinkUncertainTest {
    private G21266PostMoveNewAccountHardlinkUncertainTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.266 "+why);
    }
    @FunctionalInterface
    private interface Save {void run()throws IOException;}

    private static PlayerSnapshot snapshot(String account,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21266",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(account,player);
    }

    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> entries=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(entries.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)),"owned RAW temp leaked");
        }
    }

    private static void exercise(
        Path account,String aliasLabel,Save write
    )throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "baseline account missing");
        Path alias=account.resolveSibling(
            account.getFileName().toString()+"."+aliasLabel+"-external-link");
        check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
            "test alias unexpectedly exists");
        AtomicBoolean inserted=new AtomicBoolean();
        byte[][] moved={null};
        LocalOptInRawTemp.setAfterMoveProbeForTest(path->{
            check(account.equals(path),"postmove hook on wrong account");
            moved[0]=Files.readAllBytes(account);
            Files.createLink(alias,account);
            inserted.set(true);
        });
        boolean uncertain=false;
        try{
            write.run();
        }catch(IOException expected){
            uncertain=String.valueOf(expected.getMessage())
                .contains("RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                expected.getCause()!=null&&
                String.valueOf(expected.getCause().getMessage())
                .contains("MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
        }
        check(inserted.get()&&uncertain,
            "new-inode postmove hardlink did not cause uncertain result");
        check(moved[0]!=null&&
            Arrays.equals(moved[0],Files.readAllBytes(account))&&
            Arrays.equals(moved[0],Files.readAllBytes(alias)),
            "postmove account/alias bytes diverged or were rolled back");
        check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
            "writer deleted foreign hardlink alias");
        noTemp(account);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "publication lock leaked");
        // Only the fixture owns its alias, so only the fixture deletes it.
        Files.delete(alias);
        write.run();
        noTemp(account);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0&&
              MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "positive post-alias-removal save leaked lock state");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21266-new-inode-nlink-");
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
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            exercise(AccountStore.accountFile(),"primary",
                ()->AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState()));

            String namedUser="g21266-named";
            LocalAccountProfiles.save(namedUser,
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            exercise(LocalAccountProfiles.accountFile(namedUser),"named",
                ()->LocalAccountProfiles.save(namedUser,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()));

            String directUser="g21266-direct";
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(snapshot(directUser,"BASE"));
            exercise(LocalAccountProfiles.accountFile(directUser),"direct",
                ()->repo.save(snapshot(directUser,"AFTER")));

            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "unexpected World-session lease");
            System.out.println(
                "G21266_POSTMOVE_NEW_ACCOUNT_HARDLINK_UNCERTAIN_PASS"+
                " primaryNamedRepository=true"+
                " foreignAliasInsertedAfterAtomicMove=true"+
                " observedNewInodeNlinkVeto=true"+
                " uncertainNotRollback=true"+
                " accountAndAliasPostimagePreserved=true"+
                " foreignAliasNotAutoDeleted=true"+
                " healthyAfterTestAliasRemoved=true"+
                " optInGateOnly=true"+
                " externalAtomicFence=false"+
                " durableOwnerAck=false");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
