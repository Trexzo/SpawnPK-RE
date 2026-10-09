package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.51 adversarial marker-size/identity regression. No reward or
 * recovery authority; existing World fences stay fail-closed.
 */
public final class G2151MailboxBoundedNegativeMarkerReadIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean exactBoundary=false,oversizeRefused=false,symlinkRefused=false;
        boolean singleG2132Denied=false,multiDenied=false;
        boolean permanentDenied=false,intentDenied=false,legacyDenied=false;
        boolean validSmallStillParsed=false,healthyAccountLoads=false;
        boolean noGrant=true,noMarkerCleanup=true,resourceClean=false;
        Path root=Files.createTempDirectory("g2151-bounded-markers-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence review=new MailboxDurableReviewFence(paths);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        try{
            Path small=root.resolve("read-cap-test.bin");
            Files.write(small,new byte[2048]);
            exactBoundary=MailboxNegativeMarkerBoundedRead
                .read(small,80,2048).length==2048;
            try{
                MailboxNegativeMarkerBoundedRead.read(small,0,2047);
            }catch(IOException expected){oversizeRefused=true;}
            Path alias=root.resolve("read-cap-test-symlink.bin");
            Files.createSymbolicLink(alias,small.getFileName());
            try{
                MailboxNegativeMarkerBoundedRead.read(alias,80,2048);
            }catch(IOException expected){symlinkRefused=true;}

            seed(repo,"g2151-huge-review");
            seed(repo,"g2151-multi");
            seed(repo,"g2151-permanent");
            seed(repo,"g2151-intent");
            seed(repo,"g2151-legacy");
            PlayerSnapshot valid=seed(repo,"g2151-valid");
            PlayerSnapshot healthy=seed(repo,"g2151-healthy");
            String sha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(valid);
            sparse(review.fencePath("g2151-huge-review"));
            sparse(review.fencePath("g2151-multi"));
            sparse(permanent.fencePath("g2151-permanent"));
            sparse(intent.fencePath("g2151-intent"));
            Path legacy=root.resolve(
                "g2151-legacy.properties.g2147-strict-postpublication-review"
            );
            Files.createSymbolicLink(legacy,small.getFileName());
            MailboxAccountPublicationCoordinator.withExclusivePublication(
                paths.resolve("g2151-multi"),()->{
                    permanent.armInsidePublicationLock("g2151-multi",sha);
                    return null;
                }
            );
            MailboxAccountPublicationCoordinator.withExclusivePublication(
                paths.resolve("g2151-valid"),()->{
                    permanent.armInsidePublicationLock("g2151-valid",sha);
                    return null;
                }
            );
            validSmallStillParsed=MailboxNegativeMarkerBoundedRead.read(
                permanent.fencePath("g2151-valid"),60,384).length>60;
            try{
                review.inspect("g2151-huge-review");
            }catch(IOException denied){singleG2132Denied=true;}

            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                MailboxFencedRestartForensics.Report multi=
                    inspect(world,review,"g2151-multi");
                MailboxFencedRestartForensics.Report largePermanent=
                    inspect(world,review,"g2151-permanent");
                MailboxFencedRestartForensics.Report largeIntent=
                    inspect(world,review,"g2151-intent");
                MailboxFencedRestartForensics.Report legacyReport=
                    inspect(world,review,"g2151-legacy");
                MailboxFencedRestartForensics.Report normal=
                    inspect(world,review,"g2151-valid");
                multiDenied=multi.state==MailboxFencedRestartForensics.State
                    .MULTIPLE_NEGATIVE_MARKERS_INVALID_NO_AUTHORITY;
                permanentDenied=largePermanent.state==
                    MailboxFencedRestartForensics.State.STRICT_MARKER_INVALID;
                intentDenied=largeIntent.state==
                    MailboxFencedRestartForensics.State.STRICT_MARKER_INVALID;
                legacyDenied=legacyReport.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_LEGACY_INVALID_RECORD_NO_AUTHORITY;
                validSmallStillParsed&=normal.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY;
                MailboxFencedRestartForensics.Report single=
                    inspect(world,review,"g2151-huge-review");
                singleG2132Denied&=single.state==
                    MailboxFencedRestartForensics.State
                        .INVALID_OR_UNREADABLE_FENCE;
                for(MailboxFencedRestartForensics.Report report:
                    new MailboxFencedRestartForensics.Report[]{
                        multi,largePermanent,largeIntent,legacyReport,
                        normal,single}){
                    noGrant&=!report.grantAuthorized&&
                        !report.replayAuthorized&&
                        !report.rollbackAuthorized&&
                        !report.releaseFenceAuthorized&&
                        !report.sessionAdmissionAuthorized&&
                        !report.fileDurabilityConfirmed&&
                        !report.automaticRecoveryAuthorized;
                }
                healthyAccountLoads=world.persistence()
                    .load("g2151-healthy").isPresent();
                for(String account:new String[]{
                    "g2151-huge-review","g2151-multi","g2151-permanent",
                    "g2151-intent","g2151-legacy","g2151-valid"
                })noGrant&=denied(world,account);
            }
            noMarkerCleanup=
                Files.size(review.fencePath("g2151-huge-review"))>16000000&&
                Files.size(review.fencePath("g2151-multi"))>16000000&&
                Files.size(permanent.fencePath("g2151-permanent"))>16000000&&
                Files.size(intent.fencePath("g2151-intent"))>16000000&&
                Files.isSymbolicLink(legacy);
            try(Stream<Path> items=Files.list(root)){
                resourceClean=items.noneMatch(x->x.getFileName()
                    .toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> cleanup=Files.walk(root)){
                for(Path path:cleanup.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
        System.out.println("G2151_BOUNDED_MARKER_DIAGNOSTICS"+
            " exact2048="+exactBoundary+" overCapDenied="+oversizeRefused+
            " symlinkDenied="+symlinkRefused+
            " g2132Denied="+singleG2132Denied+" multipleDenied="+multiDenied+
            " permanentDenied="+permanentDenied+
            " intentDenied="+intentDenied+" legacyDenied="+legacyDenied+
            " validSmall="+validSmallStillParsed+
            " healthy="+healthyAccountLoads+" noGrant="+noGrant+
            " noAutoCleanup="+noMarkerCleanup+" noLeak="+resourceClean);
        if(!(exactBoundary&&oversizeRefused&&symlinkRefused&&
            singleG2132Denied&&multiDenied&&permanentDenied&&
            intentDenied&&legacyDenied&&validSmallStillParsed&&
            healthyAccountLoads&&noGrant&&noMarkerCleanup&&resourceClean))
            throw new AssertionError("G21.51 bounded marker regression");
        System.out.println("G2151_MAILBOX_BOUNDED_NEGATIVE_MARKER_PASS"+
            " grant=false replay=false release=false");
    }

    private static void sparse(Path path)throws IOException{
        try(FileChannel file=FileChannel.open(
            path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            file.position(16L*1024L*1024L);
            file.write(ByteBuffer.wrap(new byte[]{1}));
        }
    }

    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String account)throws IOException{
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(account);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(account,player);
        repo.save(snapshot);
        return snapshot;
    }

    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence fence,String account){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),fence,account);
    }

    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return true;
        }
    }

    private G2151MailboxBoundedNegativeMarkerReadIntegrationTest(){}
}
