package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.61: an uncooperative writer can preserve G21.60 metadata while
 * changing account bytes between decode and World admission. Detect
 * that mismatch without allowing any positive Mailbox settlement.
 */
public final class G2161MailboxAdmittedByteFingerprintIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2161-admitted-bytes-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository original=new FilePlayerRepository(paths);
        boolean camouflagedDenied=false,metadataStillMatches=false;
        boolean hookFired=false,stableLoaded=false;
        boolean stableMissingEmpty=false,rawForensicCompatible=false;
        boolean accountBytesPreserved=false,noRewardGrant=true;
        boolean noMarkerCleanup=true,noTempOrLeaseLeaks=false;
        try{
            original.save(snapshot("g2161-camouflage"));
            original.save(snapshot("g2161-stable"));
            Path affected=paths.resolve("g2161-camouflage");
            BasicFileAttributes first=Files.readAttributes(
                affected,BasicFileAttributes.class);
            FileTime originalTime=first.lastModifiedTime();
            AtomicBoolean injected=new AtomicBoolean();
            AtomicBoolean sameMetadata=new AtomicBoolean();
            FilePlayerRepository guarded=new FilePlayerRepository(
                paths,account->{},account->{},account->{
                    Path file=paths.resolve(account);
                    byte[] bytes=Files.readAllBytes(file);
                    String contents=new String(
                        bytes,StandardCharsets.ISO_8859_1);
                    String comment="SpawnPK LocalLab localhost account state";
                    int offset=contents.indexOf(comment);
                    if(offset<0)throw new IOException(
                        "G21.61 test comment seam missing");
                    bytes[offset]=(byte)'T'; // same length; comment-only
                    Files.write(file,bytes,StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                    Files.setLastModifiedTime(file,originalTime);
                    BasicFileAttributes current=Files.readAttributes(
                        file,BasicFileAttributes.class);
                    sameMetadata.set(
                        Objects.equals(first.fileKey(),current.fileKey())&&
                        first.size()==current.size()&&
                        Objects.equals(first.lastModifiedTime(),
                            current.lastModifiedTime()));
                    injected.set(true);
                });
            try(World world=World.isolatedForTest(60000L,guarded)){
                world.start();
                try{
                    world.persistence().load("g2161-camouflage");
                }catch(IOException veto){
                    camouflagedDenied=veto.getMessage().contains(
                        "G21.61 MAILBOX_SESSION_ACCOUNT_BYTES_CHANGED");
                }
            }
            hookFired=injected.get();
            metadataStillMatches=sameMetadata.get();
            rawForensicCompatible=original.load(
                "g2161-camouflage").isPresent();
            accountBytesPreserved=Files.exists(affected)&&
                Files.size(affected)==first.size();
            try(World world=World.isolatedForTest(60000L,original)){
                world.start();
                Optional<PlayerSnapshot> stable=
                    world.persistence().load("g2161-stable");
                stableLoaded=stable.isPresent()&&
                    stable.get().values().equals(
                        snapshot("g2161-stable").values());
                stableMissingEmpty=!world.persistence()
                    .load("g2161-missing").isPresent();
                noRewardGrant=stable.isPresent()&&
                    stable.get().username().equals("g2161-stable");
            }
            try(Stream<Path> all=Files.walk(root)){
                noTempOrLeaseLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
            noMarkerCleanup=Files.exists(affected);
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2161_ADMITTED_BYTE_FINGERPRINT_DIAGNOSTICS"+
            " camouflagedDenied="+camouflagedDenied+
            " metadataStillMatches="+metadataStillMatches+
            " hookFired="+hookFired+
            " stableLoaded="+stableLoaded+
            " stableMissingEmpty="+stableMissingEmpty+
            " rawForensicCompatible="+rawForensicCompatible+
            " accountBytesPreserved="+accountBytesPreserved+
            " noRewardGrant="+noRewardGrant+
            " noMarkerCleanup="+noMarkerCleanup+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(camouflagedDenied&&metadataStillMatches&&hookFired&&
             stableLoaded&&stableMissingEmpty&&rawForensicCompatible&&
             accountBytesPreserved&&noRewardGrant&&noMarkerCleanup&&
             noTempOrLeaseLeaks))
            throw new AssertionError("G21.61 admitted byte fingerprint");
        System.out.println("G2161_ADMITTED_BYTE_FINGERPRINT_PASS"+
            " grant=false replay=false release=false");
    }

    private static PlayerSnapshot snapshot(String name){
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(name);
        return PlayerSnapshotCodec.capture(name,player);
    }
    private G2161MailboxAdmittedByteFingerprintIntegrationTest(){}
}
