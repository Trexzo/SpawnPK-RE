package spk.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.52: four-name negative sidecar membership must remain stable even
 * when the initial/current set contains only 0 or 1 marker.
 * No positive reward, session, recovery, or release authority.
 */
public final class G2152MailboxSingleMarkerMembershipRaceIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2152-single-membership-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence fence=new MailboxDurableReviewFence(paths);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        boolean newDuringNoFence=false,removedDuringRead=false;
        boolean switchedDuringRead=false,stablePermanent=false;
        boolean stableEmpty=false,healthyLoads=false;
        boolean allNonAuthorizing=true,noAutoCleanup=true,noLeaks=false;
        try{
            PlayerSnapshot created=seed(repo,"g2152-new");
            PlayerSnapshot removed=seed(repo,"g2152-remove");
            PlayerSnapshot switched=seed(repo,"g2152-switch");
            PlayerSnapshot stable=seed(repo,"g2152-stable");
            seed(repo,"g2152-empty");
            seed(repo,"g2152-healthy");

            // Build one authentic same-account permanent marker. Remove
            // it and inject its bytes at the start of the AFTER census,
            // after the baseline single-marker inspector saw NO_FENCE.
            // G21.54 uses ONE resolver invocation per marker census;
            // after-census now begins on invocation five, not six.
            writePermanent(permanent,paths,"g2152-new",digest(created));
            Path newborn=permanent.fencePath("g2152-new");
            byte[] payload=Files.readAllBytes(newborn);
            Files.delete(newborn);
            AtomicInteger calls=new AtomicInteger();
            FilePlayerRepository.PathResolver timed=account->{
                if("g2152-new".equals(account)&&calls.incrementAndGet()==5){
                    try{
                        Files.write(newborn,payload);
                    }catch(IOException problem){
                        throw new UncheckedIOException(problem);
                    }
                }
                return paths.resolve(account);
            };
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                MailboxFencedRestartForensics.Report appeared=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),new MailboxDurableReviewFence(timed),
                        "g2152-new");
                newDuringNoFence=isChange(appeared)&&
                    calls.get()>=5&&
                    permanent.present("g2152-new")&&
                    refused(world,"g2152-new");
                allNonAuthorizing&=noAuthority(appeared);
            }

            writePermanent(permanent,paths,"g2152-remove",digest(removed));
            writePermanent(permanent,paths,"g2152-switch",digest(switched));
            writePermanent(permanent,paths,"g2152-stable",digest(stable));

            MailboxFencedRestartForensics.Report removedReport=
                duringHeldLoad(repo,fence,"g2152-remove",()->{
                    Files.delete(permanent.fencePath("g2152-remove"));
                });
            removedDuringRead=isChange(removedReport)&&
                !permanent.present("g2152-remove");
            allNonAuthorizing&=noAuthority(removedReport);

            MailboxFencedRestartForensics.Report switchedReport=
                duringHeldLoad(repo,fence,"g2152-switch",()->{
                    Files.delete(permanent.fencePath("g2152-switch"));
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(
                            paths.resolve("g2152-switch"),()->{
                                intent.armInsidePublicationLock(
                                    "g2152-switch",digest(switched));
                                return null;
                            });
                });
            switchedDuringRead=isChange(switchedReport)&&
                !permanent.present("g2152-switch")&&
                intent.present("g2152-switch");
            allNonAuthorizing&=noAuthority(switchedReport);

            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                MailboxFencedRestartForensics.Report stableReport=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,"g2152-stable");
                MailboxFencedRestartForensics.Report emptyReport=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,"g2152-empty");
                stablePermanent=stableReport.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY&&
                    refused(world,"g2152-stable");
                stableEmpty=emptyReport.state==
                    MailboxFencedRestartForensics.State
                        .NO_FENCE_NO_AUTHORITY;
                healthyLoads=world.persistence()
                    .load("g2152-healthy").isPresent();
                allNonAuthorizing&=noAuthority(stableReport)&&
                    noAuthority(emptyReport);
            }
            noAutoCleanup=permanent.present("g2152-new")&&
                permanent.present("g2152-stable")&&
                intent.present("g2152-switch");
            try(Stream<Path> listing=Files.list(dir)){
                noLeaks=listing.noneMatch(p->p.getFileName()
                    .toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
        System.out.println("G2152_SINGLE_MARKER_MEMBERSHIP_DIAGNOSTICS"+
            " newlyAppeared="+newDuringNoFence+
            " removedDuringFIFO="+removedDuringRead+
            " switchedDuringFIFO="+switchedDuringRead+
            " stablePermanent="+stablePermanent+
            " stableEmpty="+stableEmpty+
            " unaffectedAccount="+healthyLoads+
            " noAuthority="+allNonAuthorizing+
            " noAutoRelease="+noAutoCleanup+
            " noLeaseOrTempLeaks="+noLeaks);
        if(!(newDuringNoFence&&removedDuringRead&&switchedDuringRead&&
            stablePermanent&&stableEmpty&&healthyLoads&&
            allNonAuthorizing&&noAutoCleanup&&noLeaks))
            throw new AssertionError(
                "G21.52 single-marker presence race regression");
        System.out.println("G2152_SINGLE_MARKER_MEMBERSHIP_PASS"+
            " noGrant=true noReplay=true noRelease=true");
    }

    private static boolean isChange(
        MailboxFencedRestartForensics.Report result){
        return result.state==MailboxFencedRestartForensics.State
            .SINGLE_NEGATIVE_MARKER_MEMBERSHIP_CHANGED_NO_AUTHORITY;
    }
    private static boolean noAuthority(
        MailboxFencedRestartForensics.Report result){
        return !result.grantAuthorized&&!result.replayAuthorized&&
            !result.rollbackAuthorized&&!result.releaseFenceAuthorized&&
            !result.sessionAdmissionAuthorized&&
            !result.fileDurabilityConfirmed&&
            !result.automaticRecoveryAuthorized;
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String name)throws Exception{
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(name);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(name,player);
        repo.save(snapshot);
        return snapshot;
    }
    private static String digest(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private static void writePermanent(
        MailboxStrictUncertainFence fence,
        FilePlayerRepository.PathResolver paths,
        String account,String sha)throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            paths.resolve(account),()->{
                fence.armInsidePublicationLock(account,sha);
                return null;
            });
    }
    private static boolean refused(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException denied){
            return true;
        }
    }
    private interface Action{void run()throws Exception;}
    private static MailboxFencedRestartForensics.Report duringHeldLoad(
        FilePlayerRepository repo,MailboxDurableReviewFence fence,
        String account,Action mutation)throws Exception{
        BlockingRepository block=new BlockingRepository(repo,account);
        try(World world=World.isolatedForTest(60000L,block)){
            world.start();
            ExecutorService executor=Executors.newSingleThreadExecutor();
            try{
                CompletableFuture<MailboxFencedRestartForensics.Report>
                    pending=CompletableFuture.supplyAsync(
                        ()->MailboxFencedRestartForensics.inspect(
                            world.persistence(),fence,account),executor);
                if(!block.entered.await(5,TimeUnit.SECONDS))
                    throw new AssertionError("G21.52 held FIFO not reached");
                mutation.run();
                block.release.countDown();
                return pending.get(8,TimeUnit.SECONDS);
            }finally{
                block.release.countDown();
                executor.shutdownNow();
            }
        }
    }
    private static final class BlockingRepository
        implements PlayerRepository{
        final FilePlayerRepository delegate;
        final String heldAccount;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        BlockingRepository(FilePlayerRepository delegate,String account){
            this.delegate=delegate;
            heldAccount=account;
        }
        @Override public Optional<PlayerSnapshot> load(String account)
            throws IOException{
            if(account.equals(heldAccount)){
                entered.countDown();
                try{
                    if(!release.await(8,TimeUnit.SECONDS))
                        throw new IOException("G21.52 held FIFO timed out");
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.52 held FIFO interrupted",interrupted);
                }
            }
            return delegate.load(account);
        }
        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            delegate.save(snapshot);
        }
    }
    private G2152MailboxSingleMarkerMembershipRaceIntegrationTest(){}
}
