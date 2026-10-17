package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.56: a strict World account ATOMIC_MOVE, write-ahead intent,
 * read-only negative veto, confirmed-only cleanup, and permanent
 * postmove uncertainty marker all use the SAME locked account file.
 * Does not authorize item grants or positive claim settlement.
 */
public final class G2156MailboxStrictWorldWalAccountPathIntegrationTest {
    private static final class Seed {
        final String account,message;
        final WorldPlayer owner;
        final long generation;
        final PlayerSnapshot prepared;
        Seed(String a,String m,WorldPlayer p,long g,PlayerSnapshot s){
            account=a;message=m;owner=p;generation=g;prepared=s;
        }
    }
    private static final class DriftingResolver
        implements FilePlayerRepository.PathResolver {
        final Path primary,alternate;
        final AtomicInteger calls=new AtomicInteger();
        DriftingResolver(Path primary,Path alternate){
            this.primary=primary;this.alternate=alternate;
        }
        @Override public Path resolve(String account){
            Path parent=calls.incrementAndGet()==1?primary:alternate;
            return parent.resolve(account+".properties");
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2156-strict-wal-path-");
        Path primary=root.resolve("primary"),alternate=root.resolve("alternate");
        Files.createDirectories(primary);
        Files.createDirectories(alternate);
        FilePlayerRepository.PathResolver paths=
            account->primary.resolve(account+".properties");
        FilePlayerRepository.PathResolver other=
            account->alternate.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        MailboxStrictWriteIntentFence intent=new MailboxStrictWriteIntentFence(paths);
        MailboxStrictWriteIntentFence wrongIntent=
            new MailboxStrictWriteIntentFence(other);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        MailboxStrictUncertainFence wrongPermanent=
            new MailboxStrictUncertainFence(other);

        boolean preIntentAtActualPath=false;
        boolean preFailedWithoutAccountMove=false;
        boolean postBothFencesAtActualPath=false;
        boolean postAlreadyMoved=false;
        boolean normalCleanedActualIntentOnly=false;
        boolean normalReceipt=false;
        boolean existingRealReviewBlocks=false;
        boolean restartVeto=false,healthyAccount=false;
        boolean noAlternateMarkers=true,noGrant=true,noLeaks=false;
        boolean exactlyOneWriterResolve=true;
        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Seed pre=seed(world,"g2156-pre","g2156:pre");
                Seed post=seed(world,"g2156-post","g2156:post");
                Seed normal=seed(world,"g2156-normal","g2156:normal");
                Seed blocked=seed(world,"g2156-blocked","g2156:blocked");
                Seed healthy=seed(world,"g2156-healthy","g2156:healthy");
                for(Seed seed:new Seed[]{pre,post,normal,blocked,healthy})
                    repository.save(seed.prepared);

                byte[] beforePre=Files.readAllBytes(paths.resolve(pre.account));
                byte[] beforePost=Files.readAllBytes(paths.resolve(post.account));
                byte[] beforeBlocked=Files.readAllBytes(
                    paths.resolve(blocked.account));

                DriftingResolver preDrift=
                    new DriftingResolver(primary,alternate);
                StrictDurablePlayerSnapshotWriter preWriter=
                    new StrictDurablePlayerSnapshotWriter(preDrift,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .AFTER_WRITE_AHEAD_INTENT)
                            throw new IOException("G2156 injected pre-move");
                    });
                boolean preRejected=throwsIOException(
                    submit(world,pre,preWriter));
                preIntentAtActualPath=preRejected&&
                    intent.present(pre.account)&&
                    !wrongIntent.present(pre.account)&&
                    !permanent.present(pre.account)&&
                    !wrongPermanent.present(pre.account);
                preFailedWithoutAccountMove=Arrays.equals(
                    beforePre,Files.readAllBytes(paths.resolve(pre.account)));
                exactlyOneWriterResolve&=preDrift.calls.get()==1;

                DriftingResolver postDrift=
                    new DriftingResolver(primary,alternate);
                StrictDurablePlayerSnapshotWriter postWriter=
                    new StrictDurablePlayerSnapshotWriter(postDrift,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_DIRECTORY_FORCE)
                            throw new IOException("G2156 injected post-move");
                    });
                boolean postUnconfirmed=throwsUnconfirmed(
                    submit(world,post,postWriter));
                postBothFencesAtActualPath=postUnconfirmed&&
                    intent.present(post.account)&&
                    permanent.present(post.account)&&
                    !wrongIntent.present(post.account)&&
                    !wrongPermanent.present(post.account);
                postAlreadyMoved=!Arrays.equals(
                    beforePost,Files.readAllBytes(paths.resolve(post.account)));
                exactlyOneWriterResolve&=postDrift.calls.get()==1;

                DriftingResolver normalDrift=
                    new DriftingResolver(primary,alternate);
                StrictDurablePlayerSnapshotWriter normalWriter=
                    new StrictDurablePlayerSnapshotWriter(normalDrift);
                StrictDurablePlayerSnapshotWriter.Receipt receipt=
                    submit(world,normal,normalWriter).get(
                        8,TimeUnit.SECONDS);
                normalReceipt=receipt.matchesSnapshot(normal.prepared)&&
                    receipt.file.equals(
                        paths.resolve(normal.account).toAbsolutePath().normalize()
                    );
                normalCleanedActualIntentOnly=
                    !intent.present(normal.account)&&
                    !permanent.present(normal.account)&&
                    !wrongIntent.present(normal.account)&&
                    !wrongPermanent.present(normal.account);
                exactlyOneWriterResolve&=normalDrift.calls.get()==1;

                // A marker on the actual account file must reject the
                // strict write even if a later resolver lookup would
                // otherwise have switched to a clean alternate root.
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(
                        paths.resolve(blocked.account),()->{
                            permanent.armInsidePublicationLock(
                                blocked.account,sha(blocked.prepared));
                            return null;
                        });
                DriftingResolver blockedDrift=
                    new DriftingResolver(primary,alternate);
                StrictDurablePlayerSnapshotWriter blockedWriter=
                    new StrictDurablePlayerSnapshotWriter(blockedDrift);
                existingRealReviewBlocks=throwsIOException(
                    submit(world,blocked,blockedWriter))&&
                    Arrays.equals(beforeBlocked,
                        Files.readAllBytes(paths.resolve(blocked.account)))&&
                    permanent.present(blocked.account)&&
                    !intent.present(blocked.account)&&
                    !wrongPermanent.present(blocked.account);
                exactlyOneWriterResolve&=blockedDrift.calls.get()==1;

                for(Seed seed:new Seed[]{
                    pre,post,normal,blocked,healthy}){
                    noGrant&=seed.owner.bank().inventorySlots()==0&&
                        seed.owner.mailbox().get(seed.message).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noAlternateMarkers=
                    !wrongIntent.present(pre.account)&&
                    !wrongIntent.present(post.account)&&
                    !wrongIntent.present(normal.account)&&
                    !wrongIntent.present(blocked.account)&&
                    !wrongPermanent.present(post.account)&&
                    !wrongPermanent.present(blocked.account);
            }

            try(World restart=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths))){
                restart.start();
                restartVeto=denied(restart,"g2156-pre")&&
                    denied(restart,"g2156-post")&&
                    denied(restart,"g2156-blocked")&&
                    restart.persistence().load("g2156-normal").isPresent();
                healthyAccount=restart.persistence()
                    .load("g2156-healthy").isPresent();
            }
            try(Stream<Path> a=Files.list(primary);
                Stream<Path> d=Files.list(alternate)){
                noLeaks=Stream.concat(a,d)
                    .noneMatch(x->x.getFileName().toString()
                        .endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path f:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
        System.out.println("G2156_STRICT_WORLD_WAL_PATH_DIAGNOSTICS"+
            " preIntentAtActualPath="+preIntentAtActualPath+
            " preNoAccountMove="+preFailedWithoutAccountMove+
            " postBothFencesAtActualPath="+postBothFencesAtActualPath+
            " postAlreadyMoved="+postAlreadyMoved+
            " normalStrictReceipt="+normalReceipt+
            " normalClearedActualIntent="+normalCleanedActualIntentOnly+
            " preexistingRealMarkerVeto="+existingRealReviewBlocks+
            " strandedRestartVeto="+restartVeto+
            " healthy="+healthyAccount+
            " noAlternateMarkers="+noAlternateMarkers+
            " oneWriterResolvePerAccount="+exactlyOneWriterResolve+
            " noGrant="+noGrant+" noResourceLeaks="+noLeaks);
        if(!(preIntentAtActualPath&&preFailedWithoutAccountMove&&
            postBothFencesAtActualPath&&postAlreadyMoved&&normalReceipt&&
            normalCleanedActualIntentOnly&&existingRealReviewBlocks&&
            restartVeto&&healthyAccount&&noAlternateMarkers&&
            exactlyOneWriterResolve&&noGrant&&noLeaks))
            throw new AssertionError("G21.56 strict World WAL path integrity");
        System.out.println("G2156_STRICT_WORLD_WAL_PATH_PASS"+
            " grant=false replay=false release=false");
    }
    private static Seed seed(
        World world,String account,String message
    )throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<PlayerSnapshot> capture=new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"World WAL path integrity","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2156_TEST"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                owner,MailboxPreparedClaimJournal.prepare(owner,row));
            capture.set(PlayerSnapshotCodec.capture(account,owner));
        },5000L);
        return new Seed(account,message,owner,generation,capture.get());
    }
    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> submit(
        World world,Seed s,StrictDurablePlayerSnapshotWriter writer){
        return world.persistence().submitPreparedStrictBarrier(
            s.owner,s.generation,s.prepared,writer);
    }
    private static boolean throwsIOException(
        CompletableFuture<?> future)throws Exception{
        try{future.get(8,TimeUnit.SECONDS);return false;}
        catch(ExecutionException e){return e.getCause() instanceof IOException;}
    }
    private static boolean throwsUnconfirmed(
        CompletableFuture<?> future)throws Exception{
        try{future.get(8,TimeUnit.SECONDS);return false;}
        catch(ExecutionException e){
            return e.getCause() instanceof
                StrictDurablePlayerSnapshotWriter.UnconfirmedCommitException;
        }
    }
    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refused){return true;}
    }
    private static String sha(PlayerSnapshot s){
        return StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(s);
    }
    private G2156MailboxStrictWorldWalAccountPathIntegrationTest(){}
}
