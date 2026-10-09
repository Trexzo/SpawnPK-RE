package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.54: a read-only forensic observation must not combine evidence
 * from two separately resolved account paths, even with identical bytes.
 * No grants, session admission, replay, rollback, or auto-marker release.
 */
public final class G2154MailboxForensicAccountPathIdentityIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2154-account-path-");
        Path first=root.resolve("first"),second=root.resolve("second");
        Files.createDirectories(first);
        Files.createDirectories(second);
        FilePlayerRepository.PathResolver primary=
            account->first.resolve(account+".properties");
        FilePlayerRepository.PathResolver alternate=
            account->second.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(primary);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(primary);
        MailboxDurableReviewFence review=
            new MailboxDurableReviewFence(primary);
        boolean duplicateExactBytesStillVeto=false;
        boolean noFencePathDriftVeto=false;
        boolean splitProposalIsNotCombined=false;
        boolean stablePermanent=false,stableEmpty=false;
        boolean stableMultiMarker=false,unaffectedAccount=false;
        boolean allNoAuthority=true,noMarkerCleanup=true,noLeaks=false;
        try{
            String[] accounts={
                "g2154-duplicate","g2154-nomarker","g2154-split",
                "g2154-stable","g2154-empty","g2154-multi",
                "g2154-healthy"
            };
            for(String account:accounts){
                seed(repository,account);
                Files.copy(primary.resolve(account),
                    alternate.resolve(account));
            }
            for(String account:new String[]{
                "g2154-duplicate","g2154-split",
                "g2154-stable","g2154-multi"
            }){
                String sha=sha(repository.load(account).get());
                MailboxAccountPublicationCoordinator.withExclusivePublication(
                    primary.resolve(account),()->{
                        permanent.armInsidePublicationLock(account,sha);
                        return null;
                    });
                if("g2154-duplicate".equals(account))
                    Files.copy(permanent.fencePath(account),
                        second.resolve(account+
                            ".properties.g2147-strict-uncertain"));
            }
            writeProposal(review.fencePath("g2154-multi"),
                "g2154-multi",sha(repository.load("g2154-multi").get()));
            writeProposal(
                second.resolve("g2154-split.properties.g2132-mailbox-review"),
                "g2154-split",sha(repository.load("g2154-split").get()));

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                SwitchingResolver duplicate=new SwitchingResolver(
                    primary,alternate,3);
                MailboxFencedRestartForensics.Report dup=
                    inspect(world,new MailboxDurableReviewFence(duplicate),
                        "g2154-duplicate");
                duplicateExactBytesStillVeto=pathChanged(dup)&&
                    duplicate.calls.get()>=3&&
                    Arrays.equals(
                        Files.readAllBytes(permanent.fencePath(
                            "g2154-duplicate")),
                        Files.readAllBytes(second.resolve(
                            "g2154-duplicate.properties.g2147-strict-uncertain"))
                    )&&refused(world,"g2154-duplicate");
                allNoAuthority&=noAuthority(dup);

                SwitchingResolver empty=new SwitchingResolver(
                    primary,alternate,4);
                MailboxFencedRestartForensics.Report nr=
                    inspect(world,new MailboxDurableReviewFence(empty),
                        "g2154-nomarker");
                noFencePathDriftVeto=pathChanged(nr)&&
                    empty.calls.get()>=4;
                allNoAuthority&=noAuthority(nr);

                SwitchingResolver split=new SwitchingResolver(
                    primary,alternate,2);
                MailboxFencedRestartForensics.Report sr=
                    inspect(world,new MailboxDurableReviewFence(split),
                        "g2154-split");
                splitProposalIsNotCombined=pathChanged(sr)&&
                    split.calls.get()>=3&&
                    refused(world,"g2154-split")&&
                    Files.isRegularFile(second.resolve(
                        "g2154-split.properties.g2132-mailbox-review"));
                allNoAuthority&=noAuthority(sr);

                MailboxFencedRestartForensics.Report pr=
                    inspect(world,review,"g2154-stable");
                MailboxFencedRestartForensics.Report er=
                    inspect(world,review,"g2154-empty");
                MailboxFencedRestartForensics.Report mr=
                    inspect(world,review,"g2154-multi");
                stablePermanent=pr.state==MailboxFencedRestartForensics.State
                    .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY&&
                    refused(world,"g2154-stable");
                stableEmpty=er.state==MailboxFencedRestartForensics.State
                    .NO_FENCE_NO_AUTHORITY;
                stableMultiMarker=mr.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY&&
                    refused(world,"g2154-multi")&&
                    review.inspect("g2154-multi").account.equals(
                        "g2154-multi");
                unaffectedAccount=world.persistence()
                    .load("g2154-healthy").isPresent();
                allNoAuthority&=noAuthority(pr)&&noAuthority(er)&&
                    noAuthority(mr);
            }
            noMarkerCleanup=permanent.present("g2154-duplicate")&&
                permanent.present("g2154-split")&&
                permanent.present("g2154-stable")&&
                permanent.present("g2154-multi")&&
                review.present("g2154-multi")&&
                Files.exists(second.resolve(
                    "g2154-split.properties.g2132-mailbox-review"));
            try(Stream<Path> firstFiles=Files.list(first);
                Stream<Path> secondFiles=Files.list(second)){
                noLeaks=Stream.concat(firstFiles,secondFiles)
                    .noneMatch(x->x.getFileName().toString()
                        .endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2154_ACCOUNT_PATH_IDENTITY_DIAGNOSTICS"+
            " identicalBytesDifferentRoot="+duplicateExactBytesStillVeto+
            " emptyPathMoved="+noFencePathDriftVeto+
            " splitG2132NotCombined="+splitProposalIsNotCombined+
            " stablePermanent="+stablePermanent+
            " stableEmpty="+stableEmpty+
            " stableG2132AndPermanent="+stableMultiMarker+
            " healthy="+unaffectedAccount+
            " allNoAuthority="+allNoAuthority+
            " noAutoCleanup="+noMarkerCleanup+
            " noResourceLeaks="+noLeaks);
        if(!(duplicateExactBytesStillVeto&&noFencePathDriftVeto&&
            splitProposalIsNotCombined&&stablePermanent&&stableEmpty&&
            stableMultiMarker&&unaffectedAccount&&allNoAuthority&&
            noMarkerCleanup&&noLeaks))
            throw new AssertionError("G21.54 forensic account path identity");
        System.out.println("G2154_ACCOUNT_PATH_IDENTITY_PASS"+
            " noGrant=true noReplay=true noRelease=true");
    }

    private static final class SwitchingResolver
        implements FilePlayerRepository.PathResolver{
        private final FilePlayerRepository.PathResolver a,b;
        final AtomicInteger calls=new AtomicInteger();
        private final int flipAt;
        SwitchingResolver(
            FilePlayerRepository.PathResolver a,
            FilePlayerRepository.PathResolver b,int flipAt
        ){
            this.a=a;this.b=b;this.flipAt=flipAt;
        }
        @Override public Path resolve(String account){
            return (calls.incrementAndGet()>=flipAt?b:a).resolve(account);
        }
    }
    private static boolean pathChanged(
        MailboxFencedRestartForensics.Report r){
        return r.state==MailboxFencedRestartForensics.State
            .NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY;
    }
    private static boolean noAuthority(
        MailboxFencedRestartForensics.Report r){
        return !r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.releaseFenceAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.fileDurabilityConfirmed&&
            !r.automaticRecoveryAuthorized;
    }
    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence review,String account){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),review,account);
    }
    private static boolean refused(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){return true;}
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repository,String account)throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.markRegistered(account);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(account,p);
        repository.save(snapshot);
        return snapshot;
    }
    private static String sha(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private static void writeProposal(
        Path file,String account,String prepared)throws Exception{
        String hypo=prepared.equals("0".repeat(64))
            ?"1".repeat(64):"0".repeat(64);
        String payload="SPK-G2132-MAILBOX-REVIEW-V1\n"+
            "REVIEW_REQUIRED_NO_GRANT\n"+account+"\n"+
            "g2154:msg\n"+"a".repeat(64)+"\n"+prepared+"\n"+
            hypo+"\n";
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(
            payload.getBytes(StandardCharsets.US_ASCII));
        char[] chars=new char[digest.length*2];
        char[] alphabet="0123456789abcdef".toCharArray();
        for(int i=0;i<digest.length;i++){
            int v=digest[i]&255;
            chars[2*i]=alphabet[v>>>4];
            chars[2*i+1]=alphabet[v&15];
        }
        Files.writeString(file,payload+new String(chars)+"\n",
            StandardCharsets.US_ASCII);
    }
    private G2154MailboxForensicAccountPathIdentityIntegrationTest(){}
}
