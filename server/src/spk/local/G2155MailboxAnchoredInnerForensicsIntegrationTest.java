package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** G21.55: inner forensic parsing cannot silently resolve an alternate
 * root between outer marker censuses. Read-only, NO_GRANT. */
public final class G2155MailboxAnchoredInnerForensicsIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2155-anchored-forensics-");
        Path first=root.resolve("primary"),other=root.resolve("other");
        Files.createDirectories(first);
        Files.createDirectories(other);
        FilePlayerRepository.PathResolver main=
            name->first.resolve(name+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(main);
        MailboxDurableReviewFence review=new MailboxDurableReviewFence(main);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(main);
        boolean permanentAlternating=false,proposalAlternating=false;
        boolean permanentStable=false,proposalStable=false;
        boolean unaffectedAccount=false,allNoAuthority=true;
        boolean allFenced=false,noAutomaticRelease=false,noLeaks=false;
        try{
            PlayerSnapshot p=seed(repository,"g2155-strict");
            PlayerSnapshot g=seed(repository,"g2155-proposal");
            seed(repository,"g2155-healthy");
            Files.copy(main.resolve("g2155-strict"),
                other.resolve("g2155-strict.properties"));
            Files.copy(main.resolve("g2155-proposal"),
                other.resolve("g2155-proposal.properties"));
            String strict=sha(p);
            MailboxAccountPublicationCoordinator.withExclusivePublication(
                main.resolve("g2155-strict"),()->{
                    permanent.armInsidePublicationLock("g2155-strict",strict);
                    return null;
                });
            Path alternatePermanent=other.resolve(
                "g2155-strict.properties.g2147-strict-uncertain");
            Files.writeString(alternatePermanent,
                strictRecord("g2155-strict",badSha(strict)),
                StandardCharsets.US_ASCII);
            Path primaryProposal=review.fencePath("g2155-proposal");
            Path alternateProposal=other.resolve(
                "g2155-proposal.properties.g2132-mailbox-review");
            Files.writeString(primaryProposal,
                proposalRecord("g2155-proposal",sha(g)),
                StandardCharsets.US_ASCII);
            Files.writeString(alternateProposal,"invalid secondary proposal",
                StandardCharsets.US_ASCII);

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                // BEFORE=A, INNER=B, AFTER=A in the old implementation.
                // G21.55 performs only BEFORE=A, AFTER=B and correctly
                // classifies path drift, without parsing inner B evidence.
                AlternatingResolver alternateStrict=new AlternatingResolver(
                    first,other);
                MailboxFencedRestartForensics.Report ps=inspect(
                    world,new MailboxDurableReviewFence(alternateStrict),
                    "g2155-strict");
                permanentAlternating=pathChanged(ps)&&
                    alternateStrict.calls.get()==2;
                allNoAuthority&=negative(ps);

                AlternatingResolver alternateG32=new AlternatingResolver(
                    first,other);
                MailboxFencedRestartForensics.Report pg=inspect(
                    world,new MailboxDurableReviewFence(alternateG32),
                    "g2155-proposal");
                proposalAlternating=pathChanged(pg)&&
                    alternateG32.calls.get()==2;
                allNoAuthority&=negative(pg);

                CountingResolver fixed=new CountingResolver(first);
                MailboxFencedRestartForensics.Report stableB=inspect(
                    world,new MailboxDurableReviewFence(fixed),
                    "g2155-strict");
                permanentStable=stableB.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY&&
                    fixed.calls.get()==2;
                allNoAuthority&=negative(stableB);

                CountingResolver fixedG32=new CountingResolver(first);
                MailboxFencedRestartForensics.Report stableG=inspect(
                    world,new MailboxDurableReviewFence(fixedG32),
                    "g2155-proposal");
                proposalStable=stableG.state==
                    MailboxFencedRestartForensics.State
                        .OTHER_ACCOUNT_POSTIMAGE&&
                    fixedG32.calls.get()==2;
                allNoAuthority&=negative(stableG);

                allFenced=denied(world,"g2155-strict")&&
                    denied(world,"g2155-proposal");
                unaffectedAccount=world.persistence()
                    .load("g2155-healthy").isPresent();
            }
            noAutomaticRelease=Files.exists(
                permanent.fencePath("g2155-strict"))&&
                Files.exists(alternatePermanent)&&
                Files.exists(primaryProposal)&&
                Files.exists(alternateProposal);
            try(Stream<Path> a=Files.list(first);
                Stream<Path> d=Files.list(other)){
                noLeaks=Stream.concat(a,d).noneMatch(
                    x->x.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2155_ANCHORED_INNER_FORENSICS_DIAGNOSTICS"+
            " alternatingPermanent="+permanentAlternating+
            " alternatingProposal="+proposalAlternating+
            " stablePermanent="+permanentStable+
            " stableProposal="+proposalStable+
            " unaffected="+unaffectedAccount+
            " veto="+allFenced+
            " allNoAuthority="+allNoAuthority+
            " noAutomaticRelease="+noAutomaticRelease+
            " noResourceLeak="+noLeaks);
        if(!(permanentAlternating&&proposalAlternating&&
             permanentStable&&proposalStable&&unaffectedAccount&&
             allFenced&&allNoAuthority&&noAutomaticRelease&&noLeaks))
            throw new AssertionError(
                "G21.55 inner forensic account root drift");
        System.out.println("G2155_ANCHORED_INNER_FORENSICS_PASS"+
            " noGrant=true noReplay=true noRelease=true");
    }
    private static final class AlternatingResolver
        implements FilePlayerRepository.PathResolver{
        final Path first,other;
        final AtomicInteger calls=new AtomicInteger();
        AlternatingResolver(Path first,Path other){
            this.first=first;this.other=other;
        }
        @Override public Path resolve(String name){
            Path base=(calls.incrementAndGet()%2==0)?other:first;
            return base.resolve(name+".properties");
        }
    }
    private static final class CountingResolver
        implements FilePlayerRepository.PathResolver{
        final Path root;
        final AtomicInteger calls=new AtomicInteger();
        CountingResolver(Path root){this.root=root;}
        @Override public Path resolve(String name){
            calls.incrementAndGet();
            return root.resolve(name+".properties");
        }
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String account)throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.markRegistered(account);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(account,p);
        repo.save(snapshot);
        return snapshot;
    }
    private static String sha(PlayerSnapshot s){
        return StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(s);
    }
    private static String badSha(String s){
        return s.equals("0".repeat(64))?
            "1".repeat(64):"0".repeat(64);
    }
    private static String strictRecord(String account,String sha){
        return "SPK-G2147-STRICT-UNCERTAIN-NO-GRANT-V1\n"+
            account+"\n"+sha+"\nMANUAL_REVIEW_NO_GRANT\n";
    }
    private static String proposalRecord(
        String account,String prepared
    )throws Exception{
        String hypo=badSha(prepared);
        String record="SPK-G2132-MAILBOX-REVIEW-V1\n"+
            "REVIEW_REQUIRED_NO_GRANT\n"+account+"\n"+
            "g2155:msg\n"+"a".repeat(64)+"\n"+prepared+"\n"+
            hypo+"\n";
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(
            record.getBytes(StandardCharsets.US_ASCII));
        char[] alphabet="0123456789abcdef".toCharArray();
        char[] hex=new char[hash.length*2];
        for(int j=0;j<hash.length;j++){
            int v=hash[j]&255;
            hex[2*j]=alphabet[v>>>4];
            hex[2*j+1]=alphabet[v&15];
        }
        return record+new String(hex)+"\n";
    }
    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence marker,String account){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),marker,account);
    }
    private static boolean pathChanged(
        MailboxFencedRestartForensics.Report report){
        return report.state==MailboxFencedRestartForensics.State
            .NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY;
    }
    private static boolean negative(
        MailboxFencedRestartForensics.Report r){
        return !r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.releaseFenceAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.fileDurabilityConfirmed&&
            !r.automaticRecoveryAuthorized;
    }
    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refusal){return true;}
    }
    private G2155MailboxAnchoredInnerForensicsIntegrationTest(){}
}
