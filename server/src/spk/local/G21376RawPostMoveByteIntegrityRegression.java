package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R48: all four opt-in RAW save paths must deny normal results when
 * account bytes are modified IN PLACE after atomic replacement,
 * retaining exactly the same fileKey, creation-time, size and mtime.
 * Source-level observation, not externally atomic ownership or
 * crash-durable World account settlement.
 */
public final class G21376RawPostMoveByteIntegrityRegression {
    private G21376RawPostMoveByteIntegrityRegression(){}
    @FunctionalInterface interface Save{void run()throws IOException;}
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("R48_RAW_POSTMOVE_BYTES "+message);
    }
    private static PlayerSnapshot snapshot(String account,String value){
        WorldPlayer world=new WorldPlayer();
        world.snapshotExtensions().replaceNamespace(
            "r48_postmove",Collections.singletonMap("phase",value));
        return PlayerSnapshotCodec.capture(account,world);
    }
    private static void leakFree(Path account)throws IOException{
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked");
        try(Stream<Path> siblings=Files.list(account.getParent())){
            final String strictPrefix=account.getFileName()+".g2123-";
            check(siblings.noneMatch(path->path.getFileName().toString()
                .startsWith(strictPrefix)),"strict temp leaked");
        }
        check(!Files.exists(
                account.resolveSibling(account.getFileName()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),"legacy account temp leaked");
    }
    private static void verify(
        String name,Path account,Save save,boolean strict
    )throws Exception{
        save.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            name+" baseline account missing");
        final AtomicBoolean changed=new AtomicBoolean();
        LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
            actual->{
                check(account.equals(actual),name+" probe wrong account");
                BasicFileAttributes original=Files.readAttributes(
                    actual,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                byte[] previous=Files.readAllBytes(actual);
                byte[] mutated=previous.clone();
                // All four serializers use the SpawnPK account header.
                // Change a COMMENT byte; test does not need to corrupt
                // live inventory or synthesize a gameplay mutation.
                int offset=-1;
                for(int i=0;i+6<mutated.length;i++){
                    if(mutated[i]=='S'&&mutated[i+1]=='p'&&
                            mutated[i+2]=='a'&&mutated[i+3]=='w'&&
                            mutated[i+4]=='n'&&mutated[i+5]=='P'&&
                            mutated[i+6]=='K'){
                        offset=i;
                        break;
                    }
                }
                check(offset>=0,name+" lacked expected account header");
                mutated[offset]='X';
                Files.write(actual,mutated,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                // Restore mtime so a metadata-only guard cannot pass.
                FileTime beforeMtime=original.lastModifiedTime();
                Files.setLastModifiedTime(actual,beforeMtime);
                BasicFileAttributes after=Files.readAttributes(
                    actual,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(original.fileKey().equals(after.fileKey())&&
                        original.creationTime().equals(after.creationTime())&&
                        original.size()==after.size()&&
                        beforeMtime.equals(after.lastModifiedTime())&&
                        !Arrays.equals(previous,Files.readAllBytes(actual)),
                    name+" test mutation changed inode/metadata or did not change bytes");
                changed.set(true);
            });
        boolean denied=false;
        try{
            save.run();
        }catch(IOException expected){
            String identifier=strict
                ?"R48 STRICT_RAW_POST_MOVE_BYTES_UNCONFIRMED_MANUAL_REVIEW"
                :"R48_RAW_POST_MOVE_BYTES_UNCONFIRMED_MANUAL_REVIEW";
            denied=String.valueOf(expected.getMessage()).contains(identifier)&&
                expected.getCause()!=null;
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
        }
        check(changed.get()&&denied,
            name+" in-place postmove byte mutation was ACKed");
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            name+" postmove account unexpectedly missing");
        leakFree(account);

        // Separate healthy publication after removing the test hook.
        // Not an automated recovery of any live/manual-review account.
        save.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            name+" independent healthy fixture retry failed");
        leakFree(account);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r48-raw-postmove-");
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
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            prior[i]=System.getProperty(keys[i]);
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
            verify("primary",AccountStore.accountFile(),()->
                AccountStore.save(
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),false);
            String named="r48-named";
            verify("named",LocalAccountProfiles.accountFile(named),()->
                LocalAccountProfiles.save(named,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),false);
            String repositoryAccount="r48-repository";
            FilePlayerRepository repository=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            verify("repository",root.resolve(repositoryAccount+".properties"),()->
                repository.save(PlayerSnapshotCodec.capture(
                    repositoryAccount,new WorldPlayer())),false);
            String strictAccount="r48-strict";
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    name->root.resolve(name+".properties"));
            PlayerSnapshot strictSnapshot=snapshot(strictAccount,"NORMAL");
            verify("standalone-strict",root.resolve(strictAccount+".properties"),()->
                strict.saveStrict(strictSnapshot),true);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "World token leaked");
            System.out.println("R48_RAW_POSTMOVE_BYTE_INTEGRITY_PASS"+
                " ordinaryPaths=repository,primary,named"+
                " standaloneStrict=true"+
                " sameInodeSameSizeSameMtimeMutatedBytesRejected=4"+
                " fixtureOnlyHealthyRetries=4"+
                " noRawReservationsOrLegacyTemps=true"+
                " optInOnly=true"+
                " authenticCrossJvmEpoch=false"+
                " crashDurableWorldFinalAck=false"+
                " originalV308Runtime=false");
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}
