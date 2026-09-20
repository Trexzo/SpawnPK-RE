package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * World-owned persistence boundary.
 *
 * Live mutable player state is captured only from the World execution context.
 * The resulting immutable PlayerSnapshot is then written by one dedicated
 * persistence worker so filesystem/repository latency never blocks gameplay.
 */
final class WorldPlayerPersistence
    implements AutoCloseable {

    static final class SaveTicket {
        final long sequence;
        final PlayerSnapshot snapshot;
        final CompletableFuture<Void> completion;

        SaveTicket(
            long sequence,
            PlayerSnapshot snapshot,
            CompletableFuture<Void> completion
        ){
            this.sequence=sequence;
            this.snapshot=snapshot;
            this.completion=completion;
        }
    }

    private static final AtomicLong WORKER_IDS=
        new AtomicLong();

    private final World world;
    private final PlayerRepository repository;
    private final ExecutorService io;
    private final AtomicLong sequence=
        new AtomicLong();
    private final AtomicLong completed=
        new AtomicLong();
    private final AtomicLong failed=
        new AtomicLong();

    WorldPlayerPersistence(
        World world,
        PlayerRepository repository
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.repository=Objects.requireNonNull(
            repository,
            "repository"
        );

        final long workerId=
            WORKER_IDS.incrementAndGet();

        this.io=
            Executors.newSingleThreadExecutor(
                runnable->{
                    Thread thread=
                        new Thread(
                            runnable,
                            "spk-player-persistence-"+
                            workerId
                        );
                    thread.setDaemon(true);
                    return thread;
                }
            );
    }

    Optional<PlayerSnapshot> load(
        String username
    )throws IOException{
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "repository load on World execution context"
            );

        return repository.load(username);
    }

    SaveTicket captureAndSave(
        String username,
        WorldPlayer player,
        int petAccessoryItem,
        String tag,
        String reason
    ){
        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "snapshot capture must run on World execution context"
            );

        Objects.requireNonNull(
            player,
            "player"
        );

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                username,
                player,
                petAccessoryItem
            );

        long saveSequence=
            sequence.incrementAndGet();

        CompletableFuture<Void> future=
            new CompletableFuture<>();

        try{
            io.execute(
                ()->write(
                    saveSequence,
                    snapshot,
                    petAccessoryItem,
                    cleanTag(tag),
                    cleanReason(reason),
                    future
                )
            );
        }catch(RejectedExecutionException e){
            failed.incrementAndGet();
            future.completeExceptionally(e);
            System.err.println(
                cleanTag(tag)+
                "V5123_ACCOUNT_SAVE_FAILED reason="+
                cleanReason(reason)+
                " profile="+snapshot.username()+
                " repository="+
                repository.getClass().getSimpleName()+
                " sequence="+saveSequence+
                " error="+e
            );
        }

        return new SaveTicket(
            saveSequence,
            snapshot,
            future
        );
    }

    String repositoryName(){
        return repository.getClass()
            .getSimpleName();
    }

    String metrics(){
        return "WorldPlayerPersistence{"+
            "repository="+repositoryName()+
            ",submitted="+sequence.get()+
            ",completed="+completed.get()+
            ",failed="+failed.get()+
            "}";
    }

    private void write(
        long saveSequence,
        PlayerSnapshot snapshot,
        int petAccessoryItem,
        String tag,
        String reason,
        CompletableFuture<Void> future
    ){
        try{
            repository.save(snapshot);
            completed.incrementAndGet();

            System.out.println(
                tag+
                "V5123_ACCOUNT_SAVE reason="+reason+
                " repository="+repositoryName()+
                " sequence="+saveSequence+
                " snapshot="+snapshot+
                " petAccessory="+
                (petAccessoryItem==0
                    ?"NONE"
                    :petAccessoryItem)+
                " ioThread="+
                Thread.currentThread().getName()
            );

            future.complete(null);
        }catch(Throwable e){
            failed.incrementAndGet();

            System.err.println(
                tag+
                "V5123_ACCOUNT_SAVE_FAILED reason="+reason+
                " profile="+snapshot.username()+
                " repository="+repositoryName()+
                " sequence="+saveSequence+
                " error="+e
            );

            future.completeExceptionally(e);
        }
    }

    private static String cleanTag(
        String tag
    ){
        return tag==null?"":tag;
    }

    private static String cleanReason(
        String reason
    ){
        return reason==null
            ?"UNSPECIFIED"
            :reason;
    }

    @Override public void close(){
        io.shutdown();

        try{
            if(!io.awaitTermination(
                    5,
                    TimeUnit.SECONDS)){
                io.shutdownNow();
                io.awaitTermination(
                    1,
                    TimeUnit.SECONDS
                );
            }
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
    }
}
