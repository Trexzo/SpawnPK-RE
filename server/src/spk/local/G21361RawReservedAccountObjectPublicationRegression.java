package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R33: a cooperating RAW writer must not replace a new account file
 * object that appeared after it reserved the old inode (or an absent
 * bootstrap file). Covers the primary legacy, named legacy and
 * PlayerRepository RAW publisher entry points.
 *
 * Observation before move, not atomic compare-and-swap against
 * noncooperating writers and not crash-durable ownership.
 */
public final class G21361RawReservedAccountObjectPublicationRegression {
    private G21361RawReservedAccountObjectPublicationRegression(){}
    private static final String NAMED="r33-named-raw";
    private static final String REPO="r33-repository-raw";
    private static final String MISSING="r33-repository-missing";

    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("R33_RAW_ACCOUNT_OBJECT "+reason);
    }

    private static Object fileKey(Path file)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attrs.isRegularFile()&&attrs.fileKey()!=null,
            "fixture inode identity unavailable "+file);
        return attrs.fileKey();
    }

    /** Mutate inode identity but preserve every old account byte. */
    private static void swapSameBytes(Path file)throws IOException{
        Object original=fileKey(file);
        byte[] previous=Files.readAllBytes(file);
        Path other=Files.createTempFile(
            file.getParent(),"r33-replacement-",".properties");
        Files.write(other,previous);
        Files.move(other,file,StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        check(!original.equals(fileKey(file))&&
            Arrays.equals(previous,Files.readAllBytes(file)),
            "replacement was not an identical-byte different inode");
    }

    private static void requireDenied(
        Path file,SaveAction action,byte[] original
    )throws Exception{
        boolean denied=false;
        try{
            action.save();
        }catch(IOException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "R33_RAW_ACCOUNT_OBJECT_CHANGED_BEFORE_PUBLICATION");
        }
        check(denied,"RAW writer was allowed to overwrite new inode "+file);
        check(Arrays.equals(original,Files.readAllBytes(file)),
            "rejected RAW publisher changed competing account bytes");
        check(!Files.exists(file.resolveSibling(
                file.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "denied RAW writer leaked fixed temporary account file");
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "denied RAW writer leaked its reservation");
    }

    @FunctionalInterface
    private interface SaveAction {
        void save()throws Exception;
    }

    private static void primaryAndNamed(WorldPlayer player)
        throws Exception {
        AccountStore.save(
            player.bank(),player.equipment(),player.movement(),
            player.petState(),player.playerState());
        Path primary=AccountStore.accountFile();
        byte[] original=Files.readAllBytes(primary);
        requireDenied(primary,
            ()->AccountStore.saveWithPreMoveHookForTest(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState(),
                account->swapSameBytes(primary)),
            original);
        AccountStore.save(
            player.bank(),player.equipment(),player.movement(),
            player.petState(),player.playerState());
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "primary retry retained RAW reservation");

        LocalAccountProfiles.save(
            NAMED,player.bank(),player.equipment(),player.movement(),
            player.petState(),player.playerState());
        Path named=LocalAccountProfiles.accountFile(NAMED);
        original=Files.readAllBytes(named);
        requireDenied(named,
            ()->LocalAccountProfiles.saveWithPreMoveHookForTest(
                NAMED,player.bank(),player.equipment(),
                player.movement(),player.petState(),
                player.playerState(),account->swapSameBytes(named)),
            original);
        LocalAccountProfiles.save(
            NAMED,player.bank(),player.equipment(),player.movement(),
            player.petState(),player.playerState());
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "named retry retained RAW reservation");
    }

    private static PlayerSnapshot image(String account){
        return PlayerSnapshotCodec.capture(account,new WorldPlayer());
    }

    private static void repository()throws Exception{
        FilePlayerRepository normal=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        normal.save(image(REPO));
        Path account=LocalAccountProfiles.accountFile(REPO);
        byte[] original=Files.readAllBytes(account);
        FilePlayerRepository swapping=new FilePlayerRepository(
            LocalAccountProfiles::accountFile,
            ignored->{},ignored->{},ignored->{},ignored->{},
            ignored->swapSameBytes(account));
        requireDenied(account,()->swapping.save(image(REPO)),original);
        normal.save(image(REPO));
        check(normal.load(REPO).isPresent(),
            "repository retry lost readable profile");

        Path missing=LocalAccountProfiles.accountFile(MISSING);
        check(!Files.exists(missing,LinkOption.NOFOLLOW_LINKS),
            "missing profile pre-existed");
        byte[] competing="new-foreign-account-file".getBytes(
            java.nio.charset.StandardCharsets.US_ASCII);
        FilePlayerRepository createDuringWrite=new FilePlayerRepository(
            LocalAccountProfiles::accountFile,
            ignored->{},ignored->{},ignored->{},ignored->{},
            ignored->Files.write(missing,competing));
        requireDenied(missing,
            ()->createDuringWrite.save(image(MISSING)),competing);
        check(Arrays.equals(competing,Files.readAllBytes(missing)),
            "absent to created competing file was overwritten");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r33-raw-inode-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY
        };
        String[] before=new String[props.length];
        for(int i=0;i<props.length;i++)
            before[i]=System.getProperty(props[i]);
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(props[1],"true");
        System.setProperty(props[2],"true");
        System.setProperty(props[3],"true");
        for(int i=4;i<props.length;i++)
            System.setProperty(props[i],"false");
        try{
            WorldPlayer player=new WorldPlayer();
            primaryAndNamed(player);
            repository();
            check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW/object regression leaked account owners");
            System.out.println(
                "R33_RAW_RESERVED_ACCOUNT_OBJECT_PUBLICATION_PASS"+
                " primarySameByteInodeSwapDenied=true"+
                " namedSameByteInodeSwapDenied=true"+
                " repositorySameByteInodeSwapDenied=true"+
                " absentToCreatedDenied=true"+
                " oldAccountBytesPreserved=true"+
                " positiveRetryRetained=true"+
                " rawReservationsClean=true"+
                " defaultOffUnchanged=true"+
                " externalCAS=false durableEpoch=false");
        }finally{
            for(int i=0;i<props.length;i++){
                if(before[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],before[i]);
            }
            try(Stream<Path> entries=Files.walk(root)){
                for(Path p:entries.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
