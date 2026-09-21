package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final class DroppedTaskCounts {
        int loads;
        int saves;
        int checkpoints;
        int unknown;
    }

    private static final AtomicLong WORKER_IDS=
        new AtomicLong();

    private final World world;
    private final PlayerRepository repository;
    private final ThreadPoolExecutor io;
    private final AtomicReference<LoadTask> inFlightLoad=
        new AtomicReference<>();
    private final AtomicReference<SaveTask> inFlightSave=
        new AtomicReference<>();
    private final Object checkpointLock=
        new Object();
    private final HashMap<EntityId,CheckpointSlot> checkpoints=
        new HashMap<>();
    private boolean checkpointAbort;
    private PendingCheckpoint activeCheckpoint;
    private CompletableFuture<Void> activeCheckpointCompletion;
    private CheckpointSlot activeCheckpointSlot;
    private EntityId activeCheckpointPlayerId;

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

        LoadTask task=
            new LoadTask(username);

        enqueueLoad(task);

        boolean interrupted=false;

        try{
            for(;;){
                try{
                    return task.future.get();
                }catch(InterruptedException ignored){
                    interrupted=true;
                }catch(ExecutionException failure){
                    Throwable cause=
                        failure.getCause();

                    if(cause instanceof IOException)
                        throw (IOException)cause;

                    if(cause instanceof Error)
                        throw (Error)cause;

                    throw new IOException(
                        "repository load failed profile="+
                        username,
                        cause
                    );
                }
            }
        }finally{
            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
    }

    private void enqueueLoad(
        LoadTask task
    )throws IOException{
        boolean interrupted=false;

        try{
            synchronized(io){
                if(io.isShutdown())
                    throw new IOException(
                        "persistence closed before repository load"
                    );

                try{
                    io.execute(task);
                    return;
                }catch(RejectedExecutionException full){
                    if(io.isShutdown())
                        throw new IOException(
                            "persistence closed before repository load",
                            full
                        );

                    for(;;){
                        try{
                            io.getQueue().put(task);
                            return;
                        }catch(InterruptedException ignored){
                            interrupted=true;
                        }
                    }
                }
            }
        }finally{
            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
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

        SaveTask task=
            new SaveTask(
                saveSequence,
                snapshot,
                petAccessoryItem,
                cleanTag(tag),
                cleanReason(reason),
                future
            );

        try{
            io.execute(task);
        }catch(RejectedExecutionException e){
            task.reject(
                e,
                "IO_BACKPRESSURE"
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

    long failedCount(){
        return failed.get();
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
        CheckpointDrainTask task=
            new CheckpointDrainTask(
                playerId,
                slot
            );

        try{
            io.execute(task);
        }catch(RejectedExecutionException e){
            task.reject(
                e,
                "IO_BACKPRESSURE"
            );
        }
    }

    private void drainCheckpoints(
        EntityId playerId,
        CheckpointSlot slot
    ){
        while(true){
            PendingCheckpoint pending;
            CompletableFuture<Void> completion=
                new CompletableFuture<>();

            synchronized(checkpointLock){
                if(checkpointAbort){
                    slot.latest=null;
                    slot.scheduled=false;
                    checkpoints.remove(
                        playerId,
                        slot
                    );
                    return;
                }

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

                activeCheckpoint=pending;
                activeCheckpointCompletion=
                    completion;
                activeCheckpointSlot=slot;
                activeCheckpointPlayerId=
                    playerId;
            }

            long saveSequence=
                sequence.incrementAndGet();

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

            synchronized(checkpointLock){
                if(activeCheckpointCompletion==
                        completion){
                    activeCheckpoint=null;
                    activeCheckpointCompletion=null;
                    activeCheckpointSlot=null;
                    activeCheckpointPlayerId=null;
                }

                if(checkpointAbort){
                    slot.latest=null;
                    slot.scheduled=false;
                    checkpoints.remove(
                        playerId,
                        slot
                    );
                    return;
                }
            }
        }
    }

    private final class LoadTask
        implements Runnable {

        private final String username;
        private final CompletableFuture<
            Optional<PlayerSnapshot>
        > future=new CompletableFuture<>();

        LoadTask(String username){
            this.username=username;
        }

        @Override public void run(){
            inFlightLoad.set(this);

            try{
                if(future.isDone())
                    return;

                future.complete(
                    repository.load(username)
                );
            }catch(Throwable error){
                future.completeExceptionally(
                    error
                );
            }finally{
                inFlightLoad.compareAndSet(
                    this,
                    null
                );
            }
        }

        boolean reject(
            RejectedExecutionException error
        ){
            return future.completeExceptionally(
                error
            );
        }
    }

    private final class SaveTask
        implements Runnable {

        private final long saveSequence;
        private final PlayerSnapshot snapshot;
        private final int petAccessoryItem;
        private final String tag;
        private final String reason;
        private final CompletableFuture<Void> future;

        SaveTask(
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
            inFlightSave.set(this);
            try{
                write(
                    saveSequence,
                    snapshot,
                    petAccessoryItem,
                    tag,
                    reason,
                    future,
                    false
                );
            }finally{
                inFlightSave.compareAndSet(
                    this,
                    null
                );
            }
        }

        boolean reject(
            RejectedExecutionException error,
            String stage
        ){
            synchronized(future){
                if(future.isDone())
                    return false;

                failed.incrementAndGet();
                future.completeExceptionally(
                    error
                );
            }

            logRejected(
                snapshot,
                saveSequence,
                tag,
                reason,
                stage,
                error
            );
            return true;
        }
    }

    private final class CheckpointDrainTask
        implements Runnable {

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

        void reject(
            RejectedExecutionException error,
            String stage
        ){
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
                    stage,
                    error
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

            synchronized(future){
                if(future.isDone())
                    return;

                completed.incrementAndGet();

                if(checkpoint)
                    checkpointWritten
                        .incrementAndGet();

                future.complete(null);
            }

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
        }catch(Throwable e){
            synchronized(future){
                if(future.isDone())
                    return;

                failed.incrementAndGet();
                future.completeExceptionally(e);
            }

            System.err.println(
                tag+
                "V5123_ACCOUNT_SAVE_FAILED reason="+reason+
                " profile="+snapshot.username()+
                " repository="+repositoryName()+
                " sequence="+saveSequence+
                " checkpoint="+checkpoint+
                " error="+e
            );
        }
    }

    private void logRejected(
        PlayerSnapshot snapshot,
        long saveSequence,
        String tag,
        String reason,
        RejectedExecutionException error
    ){
        logRejected(
            snapshot,
            saveSequence,
            tag,
            reason,
            "IO_BACKPRESSURE",
            error
        );
    }

    private void logRejected(
        PlayerSnapshot snapshot,
        long saveSequence,
        String tag,
        String reason,
        String stage,
        RejectedExecutionException error
    ){
        System.err.println(
            tag+
            "V5123_ACCOUNT_SAVE_FAILED reason="+reason+
            " profile="+snapshot.username()+
            " repository="+repositoryName()+
            " sequence="+saveSequence+
            " stage="+stage+
            " queueCapacity="+
                MAX_PENDING_WRITES+
            " queued="+queuedWrites()+
            " error="+error
        );
    }

    private DroppedTaskCounts rejectDroppedTasks(
        List<Runnable> dropped,
        String stage
    ){
        DroppedTaskCounts counts=
            new DroppedTaskCounts();

        for(Runnable runnable:dropped){
            RejectedExecutionException error=
                new RejectedExecutionException(
                    "persistence "+stage+
                    " dropped queued task"
                );

            if(runnable instanceof LoadTask){
                counts.loads++;
                ((LoadTask)runnable).reject(
                    error
                );
                continue;
            }

            if(runnable instanceof SaveTask){
                counts.saves++;
                ((SaveTask)runnable).reject(
                    error,
                    stage
                );
                continue;
            }

            if(runnable instanceof CheckpointDrainTask){
                counts.checkpoints++;
                ((CheckpointDrainTask)runnable).reject(
                    error,
                    stage
                );
                continue;
            }

            counts.unknown++;
            failed.incrementAndGet();
            System.err.println(
                "[world] V5123_ACCOUNT_SAVE_FAILED "+
                "stage="+stage+
                " unknownDroppedTask="+
                runnable.getClass().getName()
            );
        }

        return counts;
    }

    private boolean abortInFlightCheckpoint(
        String stage
    ){
        PendingCheckpoint active;
        PendingCheckpoint queued=null;
        CompletableFuture<Void> completion;
        RejectedExecutionException error=
            new RejectedExecutionException(
                "persistence "+stage+
                " active checkpoint did not terminate"
            );

        synchronized(checkpointLock){
            checkpointAbort=true;
            active=activeCheckpoint;
            completion=
                activeCheckpointCompletion;

            if(activeCheckpointSlot!=null){
                queued=
                    activeCheckpointSlot.latest;
                activeCheckpointSlot.latest=null;
                activeCheckpointSlot.scheduled=false;
                checkpoints.remove(
                    activeCheckpointPlayerId,
                    activeCheckpointSlot
                );
            }
        }

        boolean settled=false;

        if(completion!=null){
            synchronized(completion){
                if(!completion.isDone()){
                    checkpointRejected.incrementAndGet();
                    failed.incrementAndGet();
                    completion.completeExceptionally(
                        error
                    );
                    settled=true;
                }
            }
        }

        if(!settled&&queued!=null){
            checkpointRejected.incrementAndGet();
            failed.incrementAndGet();
            settled=true;
        }

        PendingCheckpoint rejected=
            active!=null
                ?active
                :queued;

        if(settled&&rejected!=null)
            logRejected(
                rejected.snapshot,
                sequence.incrementAndGet(),
                "[world] ",
                "AUTOSAVE_TICK_"+
                    rejected.tick,
                stage,
                error
            );

        return settled;
    }

    private boolean rejectInFlightLoad(
        String stage
    ){
        LoadTask active=
            inFlightLoad.get();

        if(active==null)
            return false;

        return active.reject(
            new RejectedExecutionException(
                "persistence "+stage+
                " active load did not terminate"
            )
        );
    }

    private boolean rejectInFlightSave(
        String stage
    ){
        SaveTask active=
            inFlightSave.get();

        if(active==null)
            return false;

        return active.reject(
            new RejectedExecutionException(
                "persistence "+stage+
                " active save did not terminate"
            ),
            stage
        );
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
        synchronized(io){
            io.shutdown();
        }

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

                DroppedTaskCounts counts=
                    rejectDroppedTasks(
                        dropped,
                        "SHUTDOWN_FORCED"
                    );

                clean=
                    io.awaitTermination(
                        1,
                        TimeUnit.SECONDS
                    );

                boolean inFlightLoadSettled=
                    !clean&&
                    rejectInFlightLoad(
                        "SHUTDOWN_LOAD_IN_FLIGHT"
                    );
                boolean inFlightSettled=
                    !clean&&
                    rejectInFlightSave(
                        "SHUTDOWN_IN_FLIGHT"
                    );
                boolean checkpointInFlightSettled=
                    !clean&&
                    abortInFlightCheckpoint(
                        "SHUTDOWN_CHECKPOINT_IN_FLIGHT"
                    );

                System.err.println(
                    "[world] V5123_PERSISTENCE_SHUTDOWN_FORCED"+
                    " droppedTasks="+dropped.size()+
                    " droppedLoads="+counts.loads+
                    " droppedSaves="+counts.saves+
                    " droppedCheckpoints="+
                        counts.checkpoints+
                    " droppedUnknown="+
                        counts.unknown+
                    " inFlightLoadSettled="+
                        inFlightLoadSettled+
                    " inFlightSettled="+
                        inFlightSettled+
                    " checkpointInFlightSettled="+
                        checkpointInFlightSettled+
                    " cleanAfterForce="+clean+
                    " "+metrics()
                );
            }
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();

            List<Runnable> dropped=
                io.shutdownNow();

            DroppedTaskCounts counts=
                rejectDroppedTasks(
                    dropped,
                    "SHUTDOWN_INTERRUPTED"
                );

            boolean inFlightLoadSettled=
                rejectInFlightLoad(
                    "SHUTDOWN_INTERRUPTED_LOAD_IN_FLIGHT"
                );
            boolean inFlightSettled=
                rejectInFlightSave(
                    "SHUTDOWN_INTERRUPTED_IN_FLIGHT"
                );
            boolean checkpointInFlightSettled=
                abortInFlightCheckpoint(
                    "SHUTDOWN_INTERRUPTED_CHECKPOINT_IN_FLIGHT"
                );

            System.err.println(
                "[world] V5123_PERSISTENCE_SHUTDOWN_INTERRUPTED"+
                " droppedTasks="+dropped.size()+
                " droppedLoads="+counts.loads+
                " droppedSaves="+counts.saves+
                " droppedCheckpoints="+
                    counts.checkpoints+
                " droppedUnknown="+
                    counts.unknown+
                " inFlightLoadSettled="+
                    inFlightLoadSettled+
                " inFlightSettled="+
                    inFlightSettled+
                " checkpointInFlightSettled="+
                    checkpointInFlightSettled+
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