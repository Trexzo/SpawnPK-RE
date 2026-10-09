package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.stream.Stream;

/** G21.50: multiple negative files require consistent diagnostic evidence.
 * All outcomes are observational; no replay, grant or marker deletion. */
public final class G2150MailboxNegativeMarkerCorrelationIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2150-multi-marker-");
        FilePlayerRepository.PathResolver paths=
            name->root.resolve(name+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence proposals=new MailboxDurableReviewFence(paths);
        MailboxStrictUncertainFence permanent=new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=new MailboxStrictWriteIntentFence(paths);
        boolean agrees=false,conflict=false,damaged=false,legacyConflict=false;
        boolean clean=false,allNegative=true,veto=true,resourceClean=false;
        boolean proposalAgree=false,proposalConflict=false,noItemCredit=false;
        boolean changedWhileForensicRead=false;
        try{
            PlayerSnapshot a=seed(repo,"g2150-agree");
            PlayerSnapshot b=seed(repo,"g2150-conflict");
            PlayerSnapshot c=seed(repo,"g2150-corrupt");
            PlayerSnapshot d=seed(repo,"g2150-legacy");
            seed(repo,"g2150-clean");
            String aSha=sha(a),bSha=sha(b),cSha=sha(c),dSha=sha(d);
            writeMarkers(paths.resolve("g2150-agree"),()->{
                permanent.armInsidePublicationLock("g2150-agree",aSha);
                intent.armInsidePublicationLock("g2150-agree",aSha);
            });
            writeMarkers(paths.resolve("g2150-conflict"),()->{
                permanent.armInsidePublicationLock("g2150-conflict",bSha);
                intent.armInsidePublicationLock("g2150-conflict",badSha(bSha));
            });
            writeMarkers(paths.resolve("g2150-corrupt"),()->{
                permanent.armInsidePublicationLock("g2150-corrupt",cSha);
                intent.armInsidePublicationLock("g2150-corrupt",cSha);
            });
            Files.writeString(intent.fencePath("g2150-corrupt"),"damaged");
            writeMarkers(paths.resolve("g2150-legacy"),()->{
                permanent.armInsidePublicationLock("g2150-legacy",dSha);
            });
            Files.writeString(
                root.resolve("g2150-legacy.properties.g2147-strict-postpublication-review"),
                legacyRecord("g2150-legacy",badSha(dSha))
            );
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                WorldPlayer sameOwner=prepare(world,repo,proposals,permanent,
                    paths,"g2150-proposal-agree",false);
                WorldPlayer diffOwner=prepare(world,repo,proposals,permanent,
                    paths,"g2150-proposal-conflict",true);
                noItemCredit=sameOwner.bank().inventorySlots()==0&&
                    diffOwner.bank().inventorySlots()==0&&
                    sameOwner.mailbox().get("g2150:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                    diffOwner.mailbox().get("g2150:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                MailboxFencedRestartForensics.Report pa=
                    inspect(world,proposals,"g2150-proposal-agree");
                MailboxFencedRestartForensics.Report pb=
                    inspect(world,proposals,"g2150-proposal-conflict");
                proposalAgree=pa.state==MailboxFencedRestartForensics.State
                    .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY;
                proposalConflict=pb.state==MailboxFencedRestartForensics.State
                    .MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY;
                allNegative&=noAuthority(pa)&&noAuthority(pb);
                veto&=refused(world,"g2150-proposal-agree")&&
                    refused(world,"g2150-proposal-conflict");
                MailboxFencedRestartForensics.Report ar=
                    inspect(world,proposals,"g2150-agree");
                MailboxFencedRestartForensics.Report br=
                    inspect(world,proposals,"g2150-conflict");
                MailboxFencedRestartForensics.Report cr=
                    inspect(world,proposals,"g2150-corrupt");
                MailboxFencedRestartForensics.Report dr=
                    inspect(world,proposals,"g2150-legacy");
                MailboxFencedRestartForensics.Report er=
                    inspect(world,proposals,"g2150-clean");
                agrees=ar.state==MailboxFencedRestartForensics.State
                    .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY;
                conflict=br.state==MailboxFencedRestartForensics.State
                    .MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY;
                damaged=cr.state==MailboxFencedRestartForensics.State
                    .MULTIPLE_NEGATIVE_MARKERS_INVALID_NO_AUTHORITY;
                legacyConflict=dr.state==MailboxFencedRestartForensics.State
                    .MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY;
                clean=er.state==MailboxFencedRestartForensics.State
                    .NO_FENCE_NO_AUTHORITY&&
                    world.persistence().load("g2150-clean").isPresent();
                for(MailboxFencedRestartForensics.Report report:
                        new MailboxFencedRestartForensics.Report[]{ar,br,cr,dr,er})
                    allNegative&=noAuthority(report);
                for(String account:new String[]{
                        "g2150-agree","g2150-conflict",
                        "g2150-corrupt","g2150-legacy"})
                    veto&=refused(world,account);
            }
            // Reproduce actual FIFO forensic observation while a second
            // sidecar changes: never retain the first marker's exact label.
            HoldingRepository holding=new HoldingRepository(
                repo,"g2150-agree");
            try(World observation=World.isolatedForTest(60000L,holding)){
                observation.start();
                ExecutorService thread=Executors.newSingleThreadExecutor();
                try{
                    CompletableFuture<MailboxFencedRestartForensics.Report>
                        pending=CompletableFuture.supplyAsync(
                            ()->inspect(observation,proposals,"g2150-agree"),
                            thread);
                    if(!holding.entered.await(5,TimeUnit.SECONDS))
                        throw new AssertionError("G21.50 FIFO pause missing");
                    Files.writeString(
                        intent.fencePath("g2150-agree"),
                        "changed during forensic FIFO read"
                    );
                    holding.resume.countDown();
                    MailboxFencedRestartForensics.Report changed=
                        pending.get(8,TimeUnit.SECONDS);
                    changedWhileForensicRead=changed.state==
                        MailboxFencedRestartForensics.State
                            .MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY;
                    allNegative&=noAuthority(changed);
                }finally{
                    holding.resume.countDown();
                    thread.shutdownNow();
                }
            }
            try(Stream<Path> p=Files.list(root)){
                resourceClean=p.noneMatch(x->
                    x.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> p=Files.walk(root)){
                for(Path file:p.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
        System.out.println("G2150_MARKER_CORRELATION_DIAGNOSTICS"+
            " agreeing="+agrees+" conflict="+conflict+
            " malformedSecondary="+damaged+" legacyConflict="+legacyConflict+
            " clean="+clean+" proposalAgree="+proposalAgree+
            " proposalConflict="+proposalConflict+
            " noItemCredit="+noItemCredit+
            " markerChangedDuringFIFO="+changedWhileForensicRead+
            " allNoAuthority="+allNegative+
            " allRestartVeto="+veto+" resourceClean="+resourceClean);
        if(!(agrees&&conflict&&damaged&&legacyConflict&&clean&&
             proposalAgree&&proposalConflict&&noItemCredit&&
             changedWhileForensicRead&&allNegative&&veto&&resourceClean))
            throw new AssertionError("G21.50 marker correlation failure");
        System.out.println("G2150_MARKER_CORRELATION_PASS"+
            " grant=false replay=false release=false");
    }

    private static final class HoldingRepository implements PlayerRepository {
        private final FilePlayerRepository underlying;
        private final String heldAccount;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch resume=new CountDownLatch(1);
        HoldingRepository(FilePlayerRepository repo,String account){
            underlying=repo;heldAccount=account;
        }
        @Override public Optional<PlayerSnapshot> load(String account)
            throws IOException{
            if(heldAccount.equals(account)){
                entered.countDown();
                try{
                    if(!resume.await(8,TimeUnit.SECONDS))
                        throw new IOException("G21.50 forensic release timed out");
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException("G21.50 forensic read interrupted",interrupted);
                }
            }
            return underlying.load(account);
        }
        @Override public void save(PlayerSnapshot snapshot)throws IOException{
            underlying.save(snapshot);
        }
    }

    private static WorldPlayer prepare(
        World world,FilePlayerRepository repo,
        MailboxDurableReviewFence fence,MailboxStrictUncertainFence permanent,
        FilePlayerRepository.PathResolver paths,String account,boolean conflict
    )throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal> ref=
            new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            owner.mailbox().deliver(new RewardDeliveryMessage(
                "g2150:gift","Negative marker correlation","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2150_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get("g2150:gift");
            MailboxPreparedClaimJournal.stageOnly(
                owner,MailboxPreparedClaimJournal.prepare(owner,row));
            ref.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,row));
        },5000L);
        MailboxSettlementPostimagePlanner.Proposal proposed=ref.get();
        repo.save(proposed.preparedPreimage);
        fence.arm(proposed);
        String sha=sha(proposed.preparedPreimage);
        writeMarkers(paths.resolve(account),()->
            permanent.armInsidePublicationLock(
                account,conflict?badSha(sha):sha));
        return owner;
    }

    private interface Checked {void run()throws IOException;}
    private static void writeMarkers(Path file,Checked action)throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            file,()->{action.run();return null;});
    }
    private static PlayerSnapshot seed(FilePlayerRepository repo,String account)
        throws Exception{
        WorldPlayer owner=new WorldPlayer();
        owner.markRegistered(account);
        PlayerSnapshot result=PlayerSnapshotCodec.capture(account,owner);
        repo.save(result);
        return result;
    }
    private static String sha(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(snapshot);
    }
    private static String badSha(String original){
        return original.equals("0".repeat(64))
            ?"1".repeat(64):"0".repeat(64);
    }
    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence markers,String account){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),markers,account);
    }
    private static boolean refused(World world,String account)throws Exception{
        try{world.persistence().load(account);return false;}
        catch(IOException expected){return true;}
    }
    private static boolean noAuthority(
        MailboxFencedRestartForensics.Report x){
        return !x.grantAuthorized&&!x.replayAuthorized&&!x.rollbackAuthorized&&
            !x.releaseFenceAuthorized&&!x.sessionAdmissionAuthorized&&
            !x.fileDurabilityConfirmed&&!x.automaticRecoveryAuthorized;
    }
    private static String legacyRecord(String account,String sha)throws Exception{
        String payload="SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1\n"+
            "REVIEW_REQUIRED_NO_GRANT\n"+account+"\n"+sha+"\n";
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload.getBytes(StandardCharsets.US_ASCII));
        final char[] digits="0123456789abcdef".toCharArray();
        char[] hex=new char[digest.length*2];
        for(int i=0;i<digest.length;i++){
            int v=digest[i]&255;hex[i*2]=digits[v>>>4];hex[i*2+1]=digits[v&15];
        }
        return payload+new String(hex)+"\n";
    }
    private G2150MailboxNegativeMarkerCorrelationIntegrationTest(){}
}
