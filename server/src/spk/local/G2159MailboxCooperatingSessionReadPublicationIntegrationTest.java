package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.59: admitted file-backed World snapshot reads participate in the
 * same cooperating account-local publication lock as account saves and
 * manual-review marker writers. No settlement or recovery authority.
 */
public final class G2159MailboxCooperatingSessionReadPublicationIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2159-session-publication-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        String readFirst="g2159-read-first";
        String writerFirst="g2159-writer-first";
        String markerFirst="g2159-marker-first";
        String readerMarker="g2159-reader-marker";
        String independent="g2159-independent";
        CountDownLatch readEntered=new CountDownLatch(1);
        CountDownLatch releaseRead=new CountDownLatch(1);
        CountDownLatch writerBeforeLock=new CountDownLatch(1);
        CountDownLatch writerInside=new CountDownLatch(1);
        CountDownLatch releaseWriter=new CountDownLatch(1);
        CountDownLatch markerReadEntered=new CountDownLatch(1);
        CountDownLatch releaseMarkerRead=new CountDownLatch(1);
        AtomicBoolean gatedRead=new AtomicBoolean();
        AtomicBoolean gatedWriter=new AtomicBoolean();
        AtomicBoolean gatedMarkerRead=new AtomicBoolean();

        FilePlayerRepository repository=new FilePlayerRepository(
            paths,
            account->{
                if(readFirst.equals(account))
                    writerBeforeLock.countDown();
            },
            account->{
                if(writerFirst.equals(account)&&
                   gatedWriter.compareAndSet(false,true)){
                    writerInside.countDown();
                    await(releaseWriter,"writer-first publication");
                }
            },
            account->{
                if(readFirst.equals(account)&&
                   gatedRead.compareAndSet(false,true)){
                    readEntered.countDown();
                    await(releaseRead,"read-first admission");
                }
                if(readerMarker.equals(account)&&
                   gatedMarkerRead.compareAndSet(false,true)){
                    markerReadEntered.countDown();
                    await(releaseMarkerRead,"reader-first marker");
                }
            }
        );
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        boolean readFirstWriterWaits=false;
        boolean writerFirstReadSeesNew=false;
        boolean markerFirstReadRefused=false;
        boolean readerFirstMarkerWaits=false;
        boolean unrelatedStillReads=false;
        boolean markedRestartRefused=false;
        boolean noUnexpectedMarkerCleanup=false;
        boolean noTempOrLeaseLeaks=false;
        boolean noRewardAuthority=true;
        ExecutorService pool=Executors.newFixedThreadPool(5);
        try{
            PlayerSnapshot readOriginal=seed(repository,readFirst);
            PlayerSnapshot writerOriginal=seed(repository,writerFirst);
            PlayerSnapshot markerOriginal=seed(repository,markerFirst);
            PlayerSnapshot readerMarkerOriginal=seed(repository,readerMarker);
            PlayerSnapshot independentOriginal=seed(repository,independent);
            PlayerSnapshot readEdited=edit(readOriginal,"read-updated");
            PlayerSnapshot writerEdited=edit(writerOriginal,"write-updated");
            noRewardAuthority&=!readOriginal.values().containsKey(
                "extension.g2159.rewardClaim");

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();

                // A: the admitted read owns the cooperating lock first.
                // An independently queued World save must not replace
                // account bytes until the read has completed.
                CompletableFuture<Optional<PlayerSnapshot>> reading=
                    CompletableFuture.supplyAsync(
                        ()->read(world,readFirst),pool);
                check(readEntered.await(8,TimeUnit.SECONDS),
                    "reader did not enter protected read");
                CompletableFuture<Void> saving=
                    CompletableFuture.runAsync(()->write(
                        repository,readEdited),pool);
                check(writerBeforeLock.await(8,TimeUnit.SECONDS),
                    "writer did not reach account publication frontier");

                // Account-local lock must not serialize other accounts.
                CompletableFuture<Optional<PlayerSnapshot>> otherReading=
                    CompletableFuture.supplyAsync(
                        ()->readDirect(repository,independent),pool);
                unrelatedStillReads=otherReading.get(
                    5,TimeUnit.SECONDS).get().values().equals(
                        independentOriginal.values());
                boolean waitingForReader=!saving.isDone()&&
                    Arrays.equals(Files.readAllBytes(paths.resolve(readFirst)),
                        Files.readAllBytes(paths.resolve(readFirst)));
                releaseRead.countDown();
                PlayerSnapshot firstObserved=reading.get(
                    8,TimeUnit.SECONDS).get();
                saving.get(8,TimeUnit.SECONDS);
                PlayerSnapshot afterSave=world.persistence()
                    .load(readFirst).get();
                readFirstWriterWaits=waitingForReader&&
                    firstObserved.values().equals(readOriginal.values())&&
                    afterSave.values().equals(readEdited.values())&&
                    !permanent.present(readFirst);

                // B: an admitted save owns the lock first. Session read
                // must hydrate the new account snapshot, not stale bytes
                // from the pre-publication file.
                CompletableFuture<Void> firstSave=
                    CompletableFuture.runAsync(()->write(
                        repository,writerEdited),pool);
                check(writerInside.await(8,TimeUnit.SECONDS),
                    "writer-first did not enter publication");
                CountDownLatch readStarted=new CountDownLatch(1);
                CompletableFuture<Optional<PlayerSnapshot>> laterRead=
                    CompletableFuture.supplyAsync(()->{
                        readStarted.countDown();
                        return read(world,writerFirst);
                    },pool);
                check(readStarted.await(5,TimeUnit.SECONDS),
                    "writer-first reader did not start");
                boolean readerHasNotCompleted=!laterRead.isDone();
                releaseWriter.countDown();
                firstSave.get(8,TimeUnit.SECONDS);
                writerFirstReadSeesNew=readerHasNotCompleted&&
                    laterRead.get(8,TimeUnit.SECONDS).get()
                        .values().equals(writerEdited.values());

                // C: marker publisher owns the same lock first, arms
                // a genuine permanent negative sidecar, then releases.
                // The waiting World read MUST fail G21.32 admission.
                CountDownLatch markerHeld=new CountDownLatch(1);
                CountDownLatch releaseMarker=new CountDownLatch(1);
                CompletableFuture<Void> marking=
                    CompletableFuture.runAsync(()->{
                        try{
                            MailboxAccountPublicationCoordinator
                                .withExclusivePublication(
                                    paths.resolve(markerFirst),()->{
                                        permanent.armInsidePublicationLock(
                                            markerFirst,sha(markerOriginal));
                                        markerHeld.countDown();
                                        await(releaseMarker,"marker-first");
                                        return null;
                                    });
                        }catch(IOException e){
                            throw new IllegalStateException(e);
                        }
                    },pool);
                check(markerHeld.await(8,TimeUnit.SECONDS),
                    "marker-first publisher did not enter lock");
                CountDownLatch markerReaderStarted=new CountDownLatch(1);
                CompletableFuture<Boolean> blockedRead=
                    CompletableFuture.supplyAsync(()->{
                        markerReaderStarted.countDown();
                        return denied(world,markerFirst);
                    },pool);
                check(markerReaderStarted.await(5,TimeUnit.SECONDS),
                    "marker-first reader did not start");
                boolean markerReadStillPending=!blockedRead.isDone();
                releaseMarker.countDown();
                marking.get(8,TimeUnit.SECONDS);
                markerFirstReadRefused=markerReadStillPending&&
                    blockedRead.get(8,TimeUnit.SECONDS)&&
                    permanent.present(markerFirst);

                // D: admitted World read owns the lock first; a genuine
                // later permanent marker must wait and then quarantine
                // the next session attempt, without cancelling the
                // already-consistent earlier read.
                CompletableFuture<Optional<PlayerSnapshot>> earlyReader=
                    CompletableFuture.supplyAsync(
                        ()->read(world,readerMarker),pool);
                check(markerReadEntered.await(8,TimeUnit.SECONDS),
                    "reader-first marker gate not entered");
                CountDownLatch publisherStarted=new CountDownLatch(1);
                CompletableFuture<Void> laterPublisher=
                    CompletableFuture.runAsync(()->{
                        publisherStarted.countDown();
                        try{
                            MailboxAccountPublicationCoordinator
                                .withExclusivePublication(
                                    paths.resolve(readerMarker),()->{
                                        permanent.armInsidePublicationLock(
                                            readerMarker,sha(readerMarkerOriginal));
                                        return null;
                                    });
                        }catch(IOException failure){
                            throw new IllegalStateException(failure);
                        }
                    },pool);
                check(publisherStarted.await(5,TimeUnit.SECONDS),
                    "late marker publisher not started");
                boolean markerNotYetPublished=
                    !laterPublisher.isDone()&&!permanent.present(readerMarker);
                releaseMarkerRead.countDown();
                PlayerSnapshot beforeMarker=earlyReader.get(
                    8,TimeUnit.SECONDS).get();
                laterPublisher.get(8,TimeUnit.SECONDS);
                readerFirstMarkerWaits=markerNotYetPublished&&
                    beforeMarker.values().equals(readerMarkerOriginal.values())&&
                    permanent.present(readerMarker)&&
                    denied(world,readerMarker);

                markedRestartRefused=denied(world,markerFirst)&&
                    denied(world,readerMarker);
            }
            try(World fresh=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths))){
                fresh.start();
                markedRestartRefused&=denied(fresh,markerFirst)&&
                    denied(fresh,readerMarker)&&
                    fresh.persistence().load(readFirst).get()
                        .values().equals(readEdited.values());
            }
            noUnexpectedMarkerCleanup=
                permanent.present(markerFirst)&&
                permanent.present(readerMarker)&&
                !permanent.present(readFirst)&&
                !permanent.present(writerFirst)&&
                !permanent.present(independent);
            try(Stream<Path> all=Files.list(root)){
                noTempOrLeaseLeaks=all.noneMatch(
                    file->file.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            releaseRead.countDown();
            releaseWriter.countDown();
            releaseMarkerRead.countDown();
            pool.shutdownNow();
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2159_COOPERATING_SESSION_LOAD_DIAGNOSTICS"+
            " readerFirstWriterWaits="+readFirstWriterWaits+
            " writerFirstReaderSeesNew="+writerFirstReadSeesNew+
            " markerFirstReadDenied="+markerFirstReadRefused+
            " readerFirstMarkerWaits="+readerFirstMarkerWaits+
            " unrelatedAccountContinues="+unrelatedStillReads+
            " markedRestartDenied="+markedRestartRefused+
            " noMarkerAutoClear="+noUnexpectedMarkerCleanup+
            " noRewardAuthority="+noRewardAuthority+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(readFirstWriterWaits&&writerFirstReadSeesNew&&
             markerFirstReadRefused&&readerFirstMarkerWaits&&
             unrelatedStillReads&&markedRestartRefused&&
             noUnexpectedMarkerCleanup&&noRewardAuthority&&
             noTempOrLeaseLeaks))
            throw new AssertionError("G21.59 cooperating read publication");
        System.out.println("G2159_COOPERATING_SESSION_LOAD_PASS"+
            " grant=false replay=false release=false");
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.59 "+why);
    }
    private static void await(CountDownLatch latch,String label)
        throws IOException{
        try{
            if(!latch.await(8,TimeUnit.SECONDS))
                throw new IOException("G21.59 "+label+" timed out");
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("G21.59 "+label+" interrupted",
                interrupted);
        }
    }
    private static Optional<PlayerSnapshot> read(
        World world,String account){
        try{
            return world.persistence().load(account);
        }catch(IOException e){
            throw new IllegalStateException(e);
        }
    }
    private static Optional<PlayerSnapshot> readDirect(
        FilePlayerRepository repo,String account){
        try{
            return repo.loadForWorldSession(account);
        }catch(IOException e){
            throw new IllegalStateException(e);
        }
    }
    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){return expected.getMessage()
            .contains("G21.32 MAILBOX_DURABLE_REVIEW_FENCE");}
    }
    private static void write(
        FilePlayerRepository repo,PlayerSnapshot snapshot){
        try{
            repo.saveForWorld(snapshot);
        }catch(IOException e){
            throw new IllegalStateException(e);
        }
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String name)throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.markRegistered(name);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(name,p);
        repo.save(snapshot);
        return snapshot;
    }
    private static PlayerSnapshot edit(
        PlayerSnapshot snap,String value){
        TreeMap<String,String> changed=new TreeMap<>(snap.values());
        changed.put("extension.g2159.admissionOrder",value);
        return new PlayerSnapshot(
            PlayerSnapshot.CURRENT_VERSION,snap.username(),changed);
    }
    private static String sha(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private G2159MailboxCooperatingSessionReadPublicationIntegrationTest(){}
}
