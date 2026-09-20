package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * World-owned persistence boundary.
 *
 * Live mutable player state is captured only from the World execution context.
 * The resulting immutable PlayerSnapshot is then written by one dedicated,
 * bounded persistence worker so filesystem/repository latency never blocks
 * gameplay.
 */
final class WorldPlayerPersistence
    implements AutoCloseable {

    static final long AUTOSAVE_INTERVAL_TICKS=100L;
    static final int MAX_PENDING_WRITES=64;

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

    private static final class PendingCheckpoint {
        final long tick;
        final PlayerSnapshot snapshot;
        final int petAccessoryItem;

        PendingCheckpoint(
            long tick,
            PlayerSnapshot snapshot,
            int petAccessoryItem
        ){
            this.tick=tick;
            this.snapshot=snapshot;
            this.petAccessoryItem=petAccessoryItem;
        }
    }

    private static final class CheckpointSlot {
        PendingCheckpoint latest;
        boolean scheduled;
    }

    private interface ShutdownAwareTask extends Runnable {
        void droppedOnShutdown();
        boolean checkpointTask();
    }

    private final class SaveWriteTask
        implements ShutdownAwareTask {

        private final long saveSequence;
        private final PlayerSnapshot snapshot;
        private final int petAccessoryItem;
        private final String tag;
        private final String reason;
        private final CompletableFuture<Void> future;

        SaveWriteTask(
            long saveSequence,
            PlayerSnapshot snapshot,
            int petAccessoryItem,
            String tag,
            String reason,
            CompletableFuture<Void> future
        ){
            this.saveSequence=saveSequence;
            this.snapshot=snapshot;
            this.petAccessoryItem=petAccessoryItem;
            this.tag=tag;
            this.reason=reason;
            this.future=future;
        }

        @Override public void run(){
            write(
                saveSequence,
                snapshot,
                petAccessoryItem,
                tag,
                reason,
                future,
                false
            );
        }

        @Override public void droppedOnShutdown(){
            RejectedExecutionException error=
                new RejectedExecutionException(
                    "persistence shutdown before save execution"
                );

            failed.incrementAndGet();
            future.completeExceptionally(
                error
            );

            logShutdownDropped(
                snapshot,
                saveSequence,
                tag,
                reason,
                false,
                error
            );
        }

        @Override public boolean checkpointTask(){
            return false;
        }
    }

    private final class CheckpointDrainTask
        implements ShutdownAwareTask {

        private final EntityId playerId;
        private final CheckpointSlot slot;

        CheckpointDrainTask(
            EntityId playerId,
            CheckpointSlot slot
        ){
            this.playerId=playerId;
            this.slot=slot;
        }

        @Override public void run(){
            drainCheckpoints(
                playerId,
                slot
            );
        }

        @Override public void droppedOnShutdown(){
            PendingCheckpoint dropped;

            synchronized(checkpointLock){
                dropped=slot.latest;
                slot.latest=null;
                slot.scheduled=false;
                checkpoints.remove(
                    playerId,
                    slot
                );
            }

            checkpointRejected.incrementAndGet();
            failed.incrementAndGet();

            if(dropped!=null){
                long saveSequence=
                    sequence.incrementAndGet();

                logShutdownDropped(
                    dropped.snapshot,
                    saveSequence,
                    "[world] ",
                    "AUTOSAVE_TICK_"+
                        dropped.tick,
                    true,
                    new RejectedExecutionException(
                        "persistence shutdown before checkpoint execution"
                    )
                );
            }
        }

        @Override public boolean checkpointTask(){
            return true;
        }
    }

    private static final AtomicLong WORKER_IDS=
        new AtomicLong();

    private final World world;
    private final PlayerRepository repository;
    private final ThreadPoolExecutor io;
    private final Object checkpointLock=
        new Object();
    private final HashMap<EntityId,CheckpointSlot> checkpoints=
        new HashMap<>();

    private final AtomicLong sequence=
        new AtomicLong();
    private final AtomicLong completed=
        new AtomicLong();
    private final AtomicLong failed=
        new AtomicLong();
    private final AtomicLong checkpointCaptured=
        new AtomicLong();
    private final AtomicLong checkpointCoalesced=
        new AtomicLong();
    private final AtomicLong checkpointWritten=
        new AtomicLong();
    private final AtomicLong checkpointRejected=
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

        ThreadFactory threadFactory=
            runnable->{
                Thread thread=
                    new Thread(
                        runnable,
                        "spk-player-persistence-"+
                        workerId
                    );
                thread.setDaemon(true);
                return thread;
            };

        this.io=
            new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(
                    MAX_PENDING_WRITES
                ),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
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
        requireWorldExecutionContext();

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

        String cleanTag=
            cleanTag(tag);
        String cleanReason=
            cleanReason(reason);

        try{
            io.execute(
                new SaveWriteTask(
                    saveSequence,
                    snapshot,
                    petAccessoryItem,
                    cleanTag,
                    cleanReason,
                    future
                )
            );
        }catch(RejectedExecutionException e){
            failed.incrementAndGet();
            future.completeExceptionally(e);
            logRejected(
                snapshot,
                saveSequence,
                cleanTag,
                cleanReason,
                e
            );
        }

        return new SaveTicket(
            saveSequence,
            snapshot,
            future
        );
    }

    /**
     * World-tick autosave. Only the two persistent localhost profiles are
     * checkpointed. At most one worker task per player is queued/in-flight;
     * additional due snapshots replace the pending snapshot with the newest
     * immutable state.
     */
    void checkpointDue(long tick){
        requireWorldExecutionContext();

        if(tick<=0L||
           tick%AUTOSAVE_INTERVAL_TICKS!=0L)
            return;

        for(WorldPlayer player:
                world.players().snapshot()){
            String username=
                player.username();

            if(!LocalAccountProfiles.isPersistent(
                    username))
                continue;

            int accessoryItem=
                player.petAccessoryState()
                    .activeItem();

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    username,
                    player,
                    accessoryItem
                );

            checkpointCaptured.incrementAndGet();

            PendingCheckpoint pending=
                new PendingCheckpoint(
                    tick,
                    snapshot,
                    accessoryItem
                );

            CheckpointSlot slot;
            boolean submit=false;

            synchronized(checkpointLock){
                slot=checkpoints.get(
                    player.id()
                );

                if(slot==null){
                    slot=new CheckpointSlot();
                    checkpoints.put(
                        player.id(),
                        slot
                    );
                }

                if(slot.latest!=null)
                    checkpointCoalesced
                        .incrementAndGet();

                slot.latest=pending;

                if(!slot.scheduled){
                    slot.scheduled=true;
                    submit=true;
                }
            }

            if(submit)
                submitCheckpointDrain(
                    player.id(),
                    slot
                );
        }
    }

    String repositoryName(){
        return repository.getClass()
            .getSimpleName();
    }

    long checkpointCapturedCount(){
        return checkpointCaptured.get();
    }

    long checkpointCoalescedCount(){
        return checkpointCoalesced.get();
    }

    long checkpointWrittenCount(){
        return checkpointWritten.get();
    }

    long checkpointRejectedCount(){
        return checkpointRejected.get();
    }

    int queuedWrites(){
        return io.getQueue().size();
    }

    String metrics(){
        return "WorldPlayerPersistence{"+
            "repository="+repositoryName()+
            ",submitted="+sequence.get()+
            ",completed="+completed.get()+
            ",failed="+failed.get()+
            ",queued="+queuedWrites()+
            ",checkpointCaptured="+
                checkpointCaptured.get()+
            ",checkpointCoalesced="+
                checkpointCoalesced.get()+
            ",checkpointWritten="+
                checkpointWritten.get()+
            ",checkpointRejected="+
                checkpointRejected.get()+
            "}";
    }

    private void submitCheckpointDrain(
        EntityId playerId,
        CheckpointSlot slot
    ){
        try{
            io.execute(
                new CheckpointDrainTask(
                    playerId,
                    slot
                )
            );
        }catch(RejectedExecutionException e){
            PendingCheckpoint rejected;

            synchronized(checkpointLock){
                rejected=slot.latest;
                slot.latest=null;
                slot.scheduled=false;
                checkpoints.remove(
                    playerId,
                    slot
                );
            }

            checkpointRejected.incrementAndGet();
            failed.incrementAndGet();

            if(rejected!=null)
                logRejected(
                    rejected.snapshot,
                    sequence.incrementAndGet(),
                    "[world] ",
                    "AUTOSAVE_TICK_"+
                        rejected.tick,
                    e
                );
        }
    }

    private void drainCheckpoints(
        EntityId playerId,
        CheckpointSlot slot
    ){
        while(true){
            PendingCheckpoint pending;

            synchronized(checkpointLock){
                pending=slot.latest;
                slot.latest=null;

                if(pending==null){
                    slot.scheduled=false;
                    checkpoints.remove(
                        playerId,
                        slot
                    );
                    return;
                }
            }

            long saveSequence=
                sequence.incrementAndGet();

            CompletableFuture<Void> completion=
                new CompletableFuture<>();

            write(
                saveSequence,
                pending.snapshot,
                pending.petAccessoryItem,
                "[world] ",
                "AUTOSAVE_TICK_"+
                    pending.tick,
                completion,
                true
            );
        }
    }

    private void write(
        long saveSequence,
        PlayerSnapshot snapshot,
        int petAccessoryItem,
        String tag,
        String reason,
        CompletableFuture<Void> future,
        boolean checkpoint
    ){
        try{
            repository.save(snapshot);
            completed.incrementAndGet();

            if(checkpoint)
                checkpointWritten
                    .incrementAndGet();

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
                Thread.currentThread().getName()+
                " checkpoint="+checkpoint
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
                " checkpoint="+checkpoint+
                " error="+e
            );

            future.completeExceptionally(e);
        }
    }

    private void logRejected(
        PlayerSnapshot snapshot,
        long saveSequence,
        String tag,
        String reason,
        RejectedExecutionException error
    ){
        System.err.println(
            tag+
            "V5123_ACCOUNT_SAVE_FAILED reason="+reason+
            " profile="+snapshot.username()+
            " repository="+repositoryName()+
            " sequence="+saveSequence+
            " stage=IO_BACKPRESSURE"+
            " queueCapacity="+
                MAX_PENDING_WRITES+
            " queued="+queuedWrites()+
            " error="+error
        );
    }

    private void logShutdownDropped(
        PlayerSnapshot snapshot,
        long saveSequence,
        String tag,
        String reason,
        boolean checkpoint,
        RejectedExecutionException error
    ){
        System.err.println(
            tag+
            "V5123_ACCOUNT_SAVE_FAILED reason="+reason+
            " profile="+snapshot.username()+
            " repository="+repositoryName()+
            " sequence="+saveSequence+
            " checkpoint="+checkpoint+
            " stage=SHUTDOWN_DROPPED"+
            " error="+error
        );
    }

    private int notifyDropped(
        List<Runnable> dropped
    ){
        int explicit=0;

        for(Runnable runnable:dropped){
            if(runnable instanceof ShutdownAwareTask){
                ShutdownAwareTask task=
                    (ShutdownAwareTask)runnable;

                if(!task.checkpointTask())
                    explicit++;

                task.droppedOnShutdown();
            }else{
                failed.incrementAndGet();
                System.err.println(
                    "[world] V5123_ACCOUNT_SAVE_FAILED"+
                    " stage=SHUTDOWN_DROPPED_UNKNOWN"+
                    " task="+runnable
                );
            }
        }

        return explicit;
    }

    private void requireWorldExecutionContext(){
        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "snapshot capture must run on World execution context"
            );
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

        boolean clean=false;

        try{
            clean=
                io.awaitTermination(
                    5,
                    TimeUnit.SECONDS
                );

            if(!clean){
                List<Runnable> dropped=
                    io.shutdownNow();

                int droppedExplicit=
                    notifyDropped(
                        dropped
                    );

                clean=
                    io.awaitTermination(
                        1,
                        TimeUnit.SECONDS
                    );

                System.err.println(
                    "[world] V5123_PERSISTENCE_SHUTDOWN_FORCED"+
                    " droppedTasks="+dropped.size()+
                    " droppedExplicit="+droppedExplicit+
                    " cleanAfterForce="+clean+
                    " "+metrics()
                );
            }
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();

            List<Runnable> dropped=
                io.shutdownNow();

            int droppedExplicit=
                notifyDropped(
                    dropped
                );

            System.err.println(
                "[world] V5123_PERSISTENCE_SHUTDOWN_INTERRUPTED"+
                " droppedTasks="+dropped.size()+
                " droppedExplicit="+droppedExplicit+
                " "+metrics()
            );
            return;
        }

        if(clean)
            System.out.println(
                "[world] V5123_PERSISTENCE_SHUTDOWN_CLEAN "+
                metrics()
            );
    }
}
