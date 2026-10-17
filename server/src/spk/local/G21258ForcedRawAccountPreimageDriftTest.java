package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.258: opt-in forced RAW preimage CAS-like negative, NOT a
 * durable owner epoch or a full cross-process compare-and-swap.
 */
public final class G21258ForcedRawAccountPreimageDriftTest {
    private G21258ForcedRawAccountPreimageDriftTest(){}
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.258 "+message);
    }

    private static PlayerSnapshot snapshot(String name){
        WorldPlayer worldPlayer=new WorldPlayer();
        worldPlayer.snapshotExtensions().replaceNamespace(
            "core_g21258",Collections.singletonMap("phase","NEW"));
        return PlayerSnapshotCodec.capture(name,worldPlayer);
    }
    private static byte[] changeSameInode(Path path)throws IOException{
        byte[] original=Files.readAllBytes(path);
        check(original.length>16,"baseline account too short");
        FileTime mtime=Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS);
        byte[] altered=original.clone();
        altered[3]^=1;
        try(java.nio.channels.FileChannel channel=
                java.nio.channels.FileChannel.open(
                    path,StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)){
            channel.position(3L);
            channel.write(java.nio.ByteBuffer.wrap(
                new byte[]{altered[3]}));
        }
        Files.setLastModifiedTime(path,mtime);
        check(Arrays.equals(altered,Files.readAllBytes(path)),
            "test did not mutate actual old account");
        check(Files.size(path)==original.length&&
            Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS)
                .equals(mtime),
            "test did not restore original mtime and length");
        return altered;
    }

    @FunctionalInterface
    private interface Save {void run()throws IOException;}
    private static void denyDrift(Path path,Save action,byte[][] changed)
        throws Exception{
        byte[] before=Files.readAllBytes(path);
        boolean refused=false;
        try{action.run();}
        catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "FORCED_RAW_PREIMAGE_DRIFT_NO_MOVE");
        }
        check(refused,"old account preimage drift was not vetoed "+path);
        check(changed[0]!=null&&!Arrays.equals(before,changed[0])&&
            Arrays.equals(changed[0],Files.readAllBytes(path)),
            "forced RAW overwrote externally changed preimage "+path);
        assertNoTemp(path);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "forced RAW left cooperating reservation");
    }
    private static void assertNoTemp(Path account)throws IOException{
        try(Stream<Path> all=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(all.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),"owned temporary file leaked");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21258-raw-preimage-");
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
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path primary=AccountStore.accountFile();
            byte[][] changed={null};
            denyDrift(primary,()->AccountStore.saveWithPreMoveHookForTest(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState(),
                account->changed[0]=changeSameInode(primary)),changed);

            final String namedUsername="g21258-named";
            LocalAccountProfiles.save(namedUsername,
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(namedUsername);
            changed[0]=null;
            denyDrift(named,()->LocalAccountProfiles
                .saveWithPreMoveHookForTest(
                    namedUsername,player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState(),
                    account->changed[0]=changeSameInode(named)),changed);

            final String rawUser="g21258-repo";
            FilePlayerRepository baseline=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            baseline.save(snapshot(rawUser));
            Path repositoryPath=LocalAccountProfiles.accountFile(rawUser);
            changed[0]=null;
            FilePlayerRepository drifted=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                account->{},account->{},account->{},account->{},
                account->changed[0]=changeSameInode(repositoryPath));
            denyDrift(repositoryPath,()->drifted.save(snapshot(rawUser)),
                changed);

            final String absentName="g21258-missing";
            Path absent=LocalAccountProfiles.accountFile(absentName);
            check(!Files.exists(absent,LinkOption.NOFOLLOW_LINKS),
                "absent control already has account file");
            byte[] competing="COMPETING_POSTIMAGE".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
            FilePlayerRepository createdDuringWrite=
                new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    account->{},account->{},account->{},account->{},
                    account->Files.write(absent,competing,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE));
            boolean absentVeto=false;
            try{createdDuringWrite.save(snapshot(absentName));}
            catch(IOException expected){
                absentVeto=String.valueOf(expected.getMessage()).contains(
                    "FORCED_RAW_PREIMAGE_DRIFT_NO_MOVE");
            }
            check(absentVeto&&Arrays.equals(
                competing,Files.readAllBytes(absent)),
                "absent-to-created competing account not protected");
            assertNoTemp(absent);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "account RAW reservation leak");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication JVM lease leak");
            System.out.println(
                "G21258_FORCED_RAW_ACCOUNT_PREIMAGE_DRIFT_PASS"+
                " primaryNamedRepositorySameInodeDriftVeto=true"+
                " sameLengthAndMtimePreserved=true"+
                " absentToCreatedDenied=true"+
                " competingAccountBytesRetained=true"+
                " ownedTempsAndLeasesClean=true"+
                " optimisticNegativeOnly=true"+
                " noDurableOwnerEpoch=true"+
                " finalSaveAck=false");
        }finally{
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
