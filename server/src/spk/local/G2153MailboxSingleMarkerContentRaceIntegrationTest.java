package spk.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.53: detect a valid single-sidecar rewrite AFTER the inner
 * G21.33/G21.49 parser but BEFORE the outer forensic return.
 * No transaction, grant, replay, rollback, or marker release.
 */
public final class G2153MailboxSingleMarkerContentRaceIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2153-one-marker-content-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        boolean changedPermanent=false,changedIntent=false;
        boolean changedLegacy=false,stablePermanent=false;
        boolean stableIntent=false,stableLegacy=false;
        boolean healthy=false,noAuthority=true,noCleanup=true,noLeak=false;
        try{
            for(String name:new String[]{
                    "g2153-permanent","g2153-intent","g2153-legacy",
                    "g2153-stable-permanent","g2153-stable-intent",
                    "g2153-stable-legacy","g2153-healthy"}){
                PlayerSnapshot snapshot=seed(repository,name);
                String sha=digest(snapshot);
                if(name.contains("permanent")){
                    publish(paths.resolve(name),()->
                        permanent.armInsidePublicationLock(name,sha));
                }else if(name.contains("intent")){
                    publish(paths.resolve(name),()->
                        intent.armInsidePublicationLock(name,sha));
                }else if(name.contains("legacy")){
                    Files.writeString(legacyPath(root,name),
                        validRecord(name,sha,"legacy"),
                        StandardCharsets.US_ASCII);
                }
            }
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                changedPermanent=lateRewrite(world,paths,root,
                    "g2153-permanent","permanent");
                changedIntent=lateRewrite(world,paths,root,
                    "g2153-intent","intent");
                changedLegacy=lateRewrite(world,paths,root,
                    "g2153-legacy","legacy");

                MailboxDurableReviewFence f=new MailboxDurableReviewFence(paths);
                MailboxFencedRestartForensics.Report p=
                    inspect(world,f,"g2153-stable-permanent");
                MailboxFencedRestartForensics.Report i=
                    inspect(world,f,"g2153-stable-intent");
                MailboxFencedRestartForensics.Report l=
                    inspect(world,f,"g2153-stable-legacy");
                stablePermanent=p.state==MailboxFencedRestartForensics.State
                    .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY;
                stableIntent=i.state==MailboxFencedRestartForensics.State
                    .STRICT_INTENT_DIGEST_MATCH_NO_AUTHORITY;
                stableLegacy=l.state==MailboxFencedRestartForensics.State
                    .STRICT_LEGACY_CHECKSUM_VALID_EXACT_NO_AUTHORITY;
                noAuthority&=nonAuthorizing(p)&&nonAuthorizing(i)&&
                    nonAuthorizing(l);
                healthy=world.persistence().load("g2153-healthy").isPresent();
                for(String account:new String[]{
                    "g2153-permanent","g2153-intent","g2153-legacy",
                    "g2153-stable-permanent","g2153-stable-intent",
                    "g2153-stable-legacy"
                })noCleanup&=refused(world,account);
            }
            noCleanup&=Files.exists(
                    permanent.fencePath("g2153-permanent"))&&
                Files.exists(intent.fencePath("g2153-intent"))&&
                Files.exists(legacyPath(root,"g2153-legacy"))&&
                Files.exists(permanent.fencePath("g2153-stable-permanent"))&&
                Files.exists(intent.fencePath("g2153-stable-intent"))&&
                Files.exists(legacyPath(root,"g2153-stable-legacy"));
            try(Stream<Path> entries=Files.list(root)){
                noLeak=entries.noneMatch(p->p.getFileName().toString()
                    .endsWith(".tmp"))&&MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2153_SINGLE_MARKER_CONTENT_DIAGNOSTICS"+
            " permanentRewrite="+changedPermanent+
            " intentRewrite="+changedIntent+
            " legacyValidChecksumRewrite="+changedLegacy+
            " stablePermanent="+stablePermanent+
            " stableIntent="+stableIntent+
            " stableLegacy="+stableLegacy+
            " healthy="+healthy+" noAuthority="+noAuthority+
            " noCleanup="+noCleanup+" noLeaks="+noLeak);
        if(!(changedPermanent&&changedIntent&&changedLegacy&&
            stablePermanent&&stableIntent&&stableLegacy&&healthy&&
            noAuthority&&noCleanup&&noLeak))
            throw new AssertionError("G21.53 late single-marker rewrite");
        System.out.println("G2153_SINGLE_MARKER_CONTENT_PASS"+
            " noGrant=true noReplay=true noRelease=true");
    }

    private static boolean lateRewrite(
        World world,FilePlayerRepository.PathResolver paths,Path root,
        String account,String kind
    )throws Exception{
        Path marker="permanent".equals(kind)
            ?new MailboxStrictUncertainFence(paths).fencePath(account)
            :("intent".equals(kind)
                ?new MailboxStrictWriteIntentFence(paths).fencePath(account)
                :legacyPath(root,account));
        String sha=digest(world.persistence()
            .observeUntrustedMailboxAccount(account).get());
        byte[] replacement=validRecord(
            account,"0".repeat(64).equals(sha)
                ?"1".repeat(64):"0".repeat(64),kind
        ).getBytes(StandardCharsets.US_ASCII);
        // G21.54 removed the second G21.32 path lookup per census;
        // the outer AFTER read now begins on resolver invocation three.
        AtomicInteger calls=new AtomicInteger();
        FilePlayerRepository.PathResolver injected=name->{
            if(account.equals(name)&&calls.incrementAndGet()==3){
                try{
                    Files.write(marker,replacement);
                }catch(IOException failure){
                    throw new UncheckedIOException(failure);
                }
            }
            return paths.resolve(name);
        };
        MailboxFencedRestartForensics.Report report=inspect(
            world,new MailboxDurableReviewFence(injected),account);
        return calls.get()>=3&&report.state==
            MailboxFencedRestartForensics.State
                .SINGLE_NEGATIVE_MARKER_CONTENT_CHANGED_NO_AUTHORITY&&
            nonAuthorizing(report)&&refused(world,account);
    }

    private static Path legacyPath(Path root,String account){
        return root.resolve(
            account+".properties.g2147-strict-postpublication-review"
        );
    }
    private static String validRecord(
        String account,String sha,String kind)throws Exception{
        if("permanent".equals(kind))
            return "SPK-G2147-STRICT-UNCERTAIN-NO-GRANT-V1\n"+
                account+"\n"+sha+"\nMANUAL_REVIEW_NO_GRANT\n";
        if("intent".equals(kind))
            return "SPK-G2148-STRICT-WRITE-IN-PROGRESS-V1\n"+
                account+"\n"+sha+"\nIN_PROGRESS_NO_GRANT\n";
        String payload=
            "SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1\n"+
            "REVIEW_REQUIRED_NO_GRANT\n"+account+"\n"+sha+"\n";
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(
            payload.getBytes(StandardCharsets.US_ASCII));
        char[] hex=new char[digest.length*2];
        char[] digits="0123456789abcdef".toCharArray();
        for(int k=0;k<digest.length;k++){
            int x=digest[k]&255;
            hex[k*2]=digits[x>>>4];hex[k*2+1]=digits[x&15];
        }
        return payload+new String(hex)+"\n";
    }
    private interface Operation{void run()throws IOException;}
    private static void publish(Path file,Operation op)throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            file,()->{op.run();return null;});
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String account)throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.markRegistered(account);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(account,p);
        repo.save(snapshot);
        return snapshot;
    }
    private static String digest(PlayerSnapshot x){
        return StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(x);
    }
    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence fence,String account){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),fence,account);
    }
    private static boolean nonAuthorizing(
        MailboxFencedRestartForensics.Report r){
        return !r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.releaseFenceAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.fileDurabilityConfirmed&&
            !r.automaticRecoveryAuthorized;
    }
    private static boolean refused(World world,String account){
        try{
            world.persistence().load(account);return false;
        }catch(IOException denied){return true;}
    }
    private G2153MailboxSingleMarkerContentRaceIntegrationTest(){}
}
