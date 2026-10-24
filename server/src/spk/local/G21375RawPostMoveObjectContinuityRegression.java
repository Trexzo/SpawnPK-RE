package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R47: all THREE ordinary RAW account writers must withhold normal
 * success when an uncooperative actor swaps the newly moved account
 * inode for an identical-byte different inode or deletes that inode.
 * These are source/filesystem cutpoints, not a cross-process epoch.
 */
public final class G21375RawPostMoveObjectContinuityRegression {
    private G21375RawPostMoveObjectContinuityRegression(){}
    @FunctionalInterface interface Save{void run()throws IOException;}
    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("R47_RAW_POSTMOVE "+reason);
    }
    private static Object inode(Path path)throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "fixture inode unavailable");
        return a.fileKey();
    }
    private static boolean unconfirmed(Save writer){
        try{
            writer.run();
            return false;
        }catch(IOException failure){
            return String.valueOf(failure.getMessage()).contains(
                "R47_RAW_POST_MOVE_ACCOUNT_OBJECT_UNCONFIRMED_MANUAL_REVIEW")&&
                failure.getCause()!=null&&
                String.valueOf(failure.getCause().getMessage()).contains(
                    "R47_RAW_POST_MOVE_ACCOUNT_OBJECT_CHANGED");
        }
    }
    private static void noLeaks(Path selected)throws IOException{
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked");
        // Without safe unique-temp opt-in, each ordinary writer
        // retains its legacy fixed .tmp name, which must be consumed.
        check(!Files.exists(
                selected.resolveSibling(selected.getFileName()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "legacy temporary account file leaked");
    }
    private static void caseFor(
        String label,Path account,Save save
    )throws Exception{
        save.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            label+" baseline account absent");
        byte[] base=Files.readAllBytes(account);
        Path impostor=account.resolveSibling(
            account.getFileName()+".r47-impostor");
        AtomicBoolean swapped=new AtomicBoolean();
        LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
            actual->{
                check(account.equals(actual),label+" swap hit wrong account");
                Object published=inode(actual);
                Files.copy(actual,impostor);
                check(!published.equals(inode(impostor))&&
                        Arrays.equals(Files.readAllBytes(actual),
                            Files.readAllBytes(impostor)),
                    label+" postmove impersonator is not exact-byte copy");
                Files.move(impostor,actual,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
                swapped.set(true);
            });
        boolean swapUnconfirmed;
        try{
            swapUnconfirmed=unconfirmed(save);
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
        }
        check(swapped.get()&&swapUnconfirmed,
            label+" identical-byte inode replacement was ACKed");
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            label+" swap did not leave account leaf");
        noLeaks(account);

        AtomicBoolean deleted=new AtomicBoolean();
        LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
            actual->{
                check(account.equals(actual),label+" delete hit wrong account");
                Files.delete(actual);
                deleted.set(true);
            });
        boolean deletionUnconfirmed;
        try{
            deletionUnconfirmed=unconfirmed(save);
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
        }
        check(deleted.get()&&deletionUnconfirmed&&
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            label+" postmove missing inode was ACKed");
        noLeaks(account);

        // Test-only fixture restoration after an UNKNOWN result.
        // No production automatic recovery, reconciliation or grants.
        save.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            label+" clean retry failed after fixture correction");
        noLeaks(account);
        check(!Files.exists(impostor,LinkOption.NOFOLLOW_LINKS),
            label+" impostor remained");
        check(base.length>0,"empty baseline account fixture");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r47-raw-postmove-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");
        System.setProperty(keys[4],"false");
        System.setProperty(keys[5],"false");
        System.setProperty(keys[6],"false");
        System.setProperty(keys[7],"false");
        try{
            WorldPlayer player=new WorldPlayer();
            caseFor("primary",AccountStore.accountFile(),()->
                AccountStore.save(
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()));
            String username="r47-profile";
            caseFor("named",LocalAccountProfiles.accountFile(username),()->
                LocalAccountProfiles.save(username,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()));
            String repoUser="r47-repository";
            FilePlayerRepository repository=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            caseFor("repository",root.resolve(repoUser+".properties"),()->
                repository.save(PlayerSnapshotCodec.capture(
                    repoUser,new WorldPlayer())));
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "session lease leaked");
            System.out.println("R47_ORDINARY_RAW_POSTMOVE_OBJECT_PASS"+
                " paths=repository,primary,named"+
                " byteIdenticalInodeReplacementUnconfirmed=3"+
                " missingNewAccountPostimageUnconfirmed=3"+
                " fixtureOnlyHealthyRetry=3"+
                " noRawReservationsOrTemps=true"+
                " optInInodeGuardOnly=true"+
                " externallyAtomicCAS=false"+
                " crashDurableWorldEpoch=false"+
                " originalV308Runtime=false");
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}
