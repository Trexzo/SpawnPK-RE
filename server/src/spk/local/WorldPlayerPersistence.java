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

    static final class CapturedSave {
        final SaveTicket ticket;
        final WorldPlayer owner;
        final long expectedGeneration;
        final int petAccessoryItem;
        final String tag;
        final String reason;
        final boolean finalDisconnect;
        private boolean submitted;

        CapturedSave(
            SaveTicket ticket,
            WorldPlayer owner,
            long expectedGeneration,
            int petAccessoryItem,
            String tag,
            String reason,
            boolean finalDisconnect
        ){
            this.ticket=ticket;
            this.owner=owner;
            this.expectedGeneration=expectedGeneration;
            this.petAccessoryItem=petAccessoryItem;
            this.tag=tag;
            this.reason=reason;
            this.finalDisconnect=finalDisconnect;
        }

        synchronized void markSubmitted(){
            if(submitted)
                throw new IllegalStateException(
                    "captured save already submitted sequence="+
                    ticket.sequence
                );

            submitted=true;
        }
    }

    final class FinalSaveReservation {
        private final ReservedFinalSaveTask task;
        private boolean settled;

        private FinalSaveReservation(
            ReservedFinalSaveTask task
        ){
            this.task=Objects.requireNonNull(
                task,
                "task"
            );
        }

        SaveTicket publish(
            CapturedSave captured
        ){
            Objects.requireNonNull(
                captured,
                "captured"
            );

            if(!captured.finalDisconnect){
                IllegalArgumentException failure=
                    new IllegalArgumentException(
                        "final save reservation requires final disconnect capture"
                    );

                abort(
                    failure
                );
                throw failure;
            }

            if(task.owner!=null&&
               (captured.owner!=task.owner||
                captured.expectedGeneration!=
                    task.ownerGeneration)){
                IllegalArgumentException failure=
                    new IllegalArgumentException(
                        "final save reservation owner mismatch player="+
                        task.owner.id()+
                        " expectedGeneration="+
                        task.ownerGeneration
                    );

                abort(
                    failure
                );
                throw failure;
            }

            synchronized(this){
                if(settled)
                    throw new IllegalStateException(
                        "final save reservation already settled"
                    );
                settled=true;
            }

            try{
                captured.markSubmitted();
            }catch(RuntimeException|Error failure){
                task.abort(
                    failure
                );
                throw failure;
            }

            if(!task.publish(captured)){
                RejectedExecutionException failure=
                    new RejectedExecutionException(
                        "final save reservation no longer active"
                    );

                saveTask(captured).reject(
                    failure,
                    "FINAL_RESERVATION_REJECTED"
                );

                throw failure;
            }

            return captured.ticket;
        }

        void abort(
            Throwable failure
        ){
            synchronized(this){
                if(settled)
                    return;
                settled=true;
            }

            task.abort(
                failure==null
                    ?new IllegalStateException(
                        "final save reservation aborted"
                    )
                    :failure
            );
        }
    }

    private static final class PendingCheckpoint {
        final long tick;
        final long captureSequence;
        final PlayerSnapshot snapshot;
        final int petAccessoryItem;

        PendingCheckpoint(
            long tick,
            long captureSequence,
            PlayerSnapshot snapshot,
            int petAccessoryItem
        ){
            this.tick=tick;
            this.captureSequence=captureSequence;
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
        int finalReservations;
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
    private final AtomicReference<PreparedStrictBarrierTask>
        inFlightStrictBarrier=new AtomicReference<>();
    private final AtomicReference<ReservedPreparedDrainTask>
        inFlightPreparedDrain=new AtomicReference<>();

    /*
     * Protected by io, shared with the single writer's admission lock.
     * Prevents deferred pre-barrier captures/checkpoints admitted later
     * from overwriting a strict PREPARED-only account snapshot.
     * This is NOT a durable claim/grant transaction or a full account
     * mutation epoch.
     */
    private final HashMap<String,Long>
        strictPreparedAccountCutoffs=new HashMap<>();

    // Opt-in G21.27 exclusion: in-memory admission/capture fence only.
    // Existing writes already running inside repository.save may finish.
    // No durable transaction, item credit or CLAIMED change is authorized.
    static final int MAX_PREPARED_ACCOUNT_RESERVATIONS=32;
    private final HashMap<String,PreparedAccountReservation>
        preparedAccountReservations=new HashMap<>();

    /*
     * Protected by the persistence admission lock (io). A bound final
     * reservation becomes visible here only while it actually owns a FIFO
     * position. Ordinary saves for that exact owner/generation are then
     * rejected instead of being admitted behind the final slot.
     */
    private final IdentityHashMap<WorldPlayer,Long>
        finalReservationGenerations=
            new IdentityHashMap<>();

    private final Object checkpointLock=
        new Object();
    private final HashMap<EntityId,CheckpointSlot> checkpoints=
        new HashMap<>();
    private final HashMap<EntityId,Long>
        checkpointSuppressedGenerations=
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
        return loadInternal(username,true);
    }

    /**
     * Read-only G21.26 disk observation, NOT a session hydration entry.
     * Preserves the FIFO worker and intentionally permits inspecting
     * untrusted hypothetical account bytes which the normal loader denies.
     * Caller must enforce G21.26 exact owner/proposal validation; this
     * method never applies account state or authorizes a settlement.
     */
    Optional<PlayerSnapshot> observeUntrustedMailboxAccount(
        String username
    )throws IOException{
        return loadInternal(username,false);
    }

    private Optional<PlayerSnapshot> loadInternal(
        String username,boolean enforceAdmission
    )throws IOException{
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "repository load on World execution context"
            );

        LoadTask task=
            new LoadTask(username,enforceAdmission);

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

                    if(cause instanceof RuntimeException)
                        throw (RuntimeException)cause;

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
        boolean executeAttempted=false;

        try{
            for(;;){
                synchronized(io){
                    if(io.isShutdown())
                        throw new IOException(
                            "persistence closed before repository load"
                        );

                    if(!executeAttempted){
                        executeAttempted=true;

                        try{
                            io.execute(task);
                            return;
                        }catch(RejectedExecutionException full){
                            if(io.isShutdown())
                                throw new IOException(
                                    "persistence closed before repository load",
                                    full
                                );
                        }
                    }

                    if(io.getQueue().offer(task))
                        return;
                }

                try{
                    Thread.sleep(5L);
                }catch(InterruptedException ignored){
                    interrupted=true;
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
        Objects.requireNonNull(
            player,
            "player"
        );

        return captureAndSave(
            username,
            player,
            player.generation(),
            petAccessoryItem,
            tag,
            reason
        );
    }

    SaveTicket captureAndSave(
        String username,
        WorldPlayer player,
        long expectedGeneration,
        int petAccessoryItem,
        String tag,
        String reason
    ){
        CapturedSave captured=
            captureDeferredSave(
                username,
                player,
                expectedGeneration,
                petAccessoryItem,
                tag,
                reason
            );

        captured.markSubmitted();

        SaveTask task=
            saveTask(captured);

        enqueueSaveImmediate(
            task
        );

        return captured.ticket;
    }

    CapturedSave captureDeferredSave(
        String username,
        WorldPlayer player,
        int petAccessoryItem,
        String tag,
        String reason
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        return captureDeferredSave(
            username,
            player,
            player.generation(),
            petAccessoryItem,
            tag,
            reason
        );
    }

    CapturedSave captureDeferredSave(
        String username,
        WorldPlayer player,
        long expectedGeneration,
        int petAccessoryItem,
        String tag,
        String reason
    ){
        requireWorldExecutionContext();

        Objects.requireNonNull(
            player,
            "player"
        );

        PlayerSnapshot snapshot;

        synchronized(player.mutationLock()){
            if(!world.players().owns(
                    player,
                    expectedGeneration
                ))
                throw new IllegalStateException(
                    "persistence capture owner changed player="+
                    player.id()+
                    " expectedGeneration="+
                    expectedGeneration
                );

            snapshot=
                PlayerSnapshotCodec.capture(
                    username,
                    player,
                    petAccessoryItem
                );
        }

        long saveSequence=
            sequence.incrementAndGet();

        CompletableFuture<Void> future=
            new CompletableFuture<>();

        return new CapturedSave(
            new SaveTicket(
                saveSequence,
                snapshot,
                future
            ),
            player,
            expectedGeneration,
            petAccessoryItem,
            cleanTag(tag),
            cleanReason(reason),
            false
        );
    }

    CapturedSave captureDeferredFinalSave(
        String username,
        WorldPlayer player,
        long expectedGeneration,
        int petAccessoryItem,
        String tag,
        String reason
    ){
        requireWorldExecutionContext();

        Objects.requireNonNull(
            player,
            "player"
        );

        PlayerSnapshot snapshot;

        synchronized(player.mutationLock()){
            if(!world.players().owns(
                    player,
                    expectedGeneration
                ))
                throw new IllegalStateException(
                    "final persistence capture owner changed player="+
                    player.id()+
                    " expectedGeneration="+
                    expectedGeneration
                );

            Long previousSuppression;

            synchronized(checkpointLock){
                previousSuppression=
                    checkpointSuppressedGenerations.put(
                        player.id(),
                        expectedGeneration
                    );
            }

            try{
                snapshot=
                    PlayerSnapshotCodec.capture(
                        username,
                        player,
                        petAccessoryItem
                    );

                /*
                 * A checkpoint captured after the ordered final reservation
                 * was accepted can be queued behind that reservation. If left
                 * pending, its drain would run after the final save and could
                 * overwrite the repository with older pre-disconnect state.
                 *
                 * Only discard the still-pending/coalesced checkpoint after
                 * final capture succeeds. A checkpoint already running was
                 * dequeued ahead of the reservation and may finish normally
                 * before the final save.
                 */
                synchronized(checkpointLock){
                    CheckpointSlot slot=
                        checkpoints.get(
                            player.id()
                        );

                    if(slot!=null)
                        slot.latest=null;
                }
            }catch(RuntimeException|Error failure){
                synchronized(checkpointLock){
                    Long current=
                        checkpointSuppressedGenerations.get(
                            player.id()
                        );

                    if(current!=null&&
                       current.longValue()==
                           expectedGeneration){
                        if(previousSuppression==null)
                            checkpointSuppressedGenerations.remove(
                                player.id()
                            );
                        else
                            checkpointSuppressedGenerations.put(
                                player.id(),
                                previousSuppression
                            );
                    }
                }

                throw failure;
            }
        }

        long saveSequence=
            sequence.incrementAndGet();

        CompletableFuture<Void> future=
            new CompletableFuture<>();

        return new CapturedSave(
            new SaveTicket(
                saveSequence,
                snapshot,
                future
            ),
            player,
            expectedGeneration,
            petAccessoryItem,
            cleanTag(tag),
            cleanReason(reason),
            true
        );
    }

    FinalSaveReservation reserveFinalSaveWithBackpressure(
        long timeoutMillis
    ){
        return reserveFinalSaveWithBackpressure(
            null,
            0L,
            timeoutMillis
        );
    }

    FinalSaveReservation reserveFinalSaveWithBackpressure(
        WorldPlayer owner,
        long expectedGeneration,
        long timeoutMillis
    ){
        if(timeoutMillis<0L)
            throw new IllegalArgumentException(
                "timeoutMillis="+timeoutMillis
            );

        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "blocking final-save reservation on World execution context"
            );

        if(owner!=null&&
           !world.players().owns(
               owner,
               expectedGeneration))
            throw new IllegalStateException(
                "final-save reservation owner changed player="+
                owner.id()+
                " expectedGeneration="+
                expectedGeneration
            );

        ReservedFinalSaveTask task=
            new ReservedFinalSaveTask(
                owner,
                expectedGeneration
            );

        enqueueFinalReservationWithBackpressure(
            task,
            timeoutMillis
        );

        return new FinalSaveReservation(
            task
        );
    }

    private void enqueueFinalReservationWithBackpressure(
        ReservedFinalSaveTask task,
        long timeoutMillis
    ){
        long timeoutNanos=
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );
        long started=
            System.nanoTime();
        boolean interrupted=false;
        boolean executeAttempted=false;

        try{
            for(;;){
                synchronized(io){
                    if(io.isShutdown())
                        throw new RejectedExecutionException(
                            "persistence closed before final-save reservation"
                        );

                    if(!executeAttempted){
                        executeAttempted=true;

                        try{
                            if(tryAdmitFinalReservationLocked(
                                    task,
                                    true))
                                return;
                        }catch(RejectedExecutionException full){
                            if(io.isShutdown())
                                throw new RejectedExecutionException(
                                    "persistence closed before final-save reservation",
                                    full
                                );
                        }
                    }

                    if(tryAdmitFinalReservationLocked(
                            task,
                            false))
                        return;
                }

                long elapsed=
                    System.nanoTime()-started;

                if(elapsed>=timeoutNanos)
                    throw new RejectedExecutionException(
                        "final-save reservation timed out after "+
                        timeoutMillis+"ms"
                    );

                long remaining=
                    timeoutNanos-elapsed;
                long sleepMillis=
                    Math.max(
                        1L,
                        Math.min(
                            5L,
                            TimeUnit.NANOSECONDS.toMillis(
                                remaining
                            )
                        )
                    );

                try{
                    Thread.sleep(
                        sleepMillis
                    );
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
            }
        }finally{
            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
    }

    SaveTicket submitCapturedWithBackpressure(
        CapturedSave captured,
        long timeoutMillis
    ){
        Objects.requireNonNull(
            captured,
            "captured"
        );

        if(timeoutMillis<0L)
            throw new IllegalArgumentException(
                "timeoutMillis="+timeoutMillis
            );

        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "blocking persistence admission on World execution context"
            );

        synchronized(captured.owner.mutationLock()){
            if(!world.players().owns(
                    captured.owner,
                    captured.expectedGeneration
                ))
                throw new IllegalStateException(
                    "captured save owner changed player="+
                    captured.owner.id()+
                    " expectedGeneration="+
                    captured.expectedGeneration
                );

            captured.markSubmitted();
        }

        SaveTask task=
            saveTask(captured);

        enqueueSaveWithBackpressure(
            task,
            timeoutMillis
        );

        return captured.ticket;
    }

    private SaveTask saveTask(
        CapturedSave captured
    ){
        return new SaveTask(
            captured
        );
    }

    private boolean finalReservationBlocksLocked(
        SaveTask task
    ){
        if(!Thread.holdsLock(io))
            throw new IllegalStateException(
                "final reservation save fence requires persistence admission ownership"
            );

        if(task==null||
           task.finalDisconnect||
           task.owner==null)
            return false;

        Long generation=
            finalReservationGenerations.get(
                task.owner
            );

        return generation!=null&&
            generation.longValue()==
                task.expectedGeneration;
    }

    private RejectedExecutionException
        finalReservationSaveRejection(
            SaveTask task
        )
    {
        return new RejectedExecutionException(
            "ordinary save blocked behind final reservation player="+
            task.owner.id()+
            " generation="+
            task.expectedGeneration
        );
    }

    private void activateFinalReservationLocked(
        ReservedFinalSaveTask task
    ){
        if(!Thread.holdsLock(io))
            throw new IllegalStateException(
                "final reservation activation requires persistence admission ownership"
            );

        if(task.owner==null)
            return;

        if(preparedAccountBlockedLocked(task.owner.username()))
            throw preparedWriteRejection(
                task.owner.username()
            );

        Long existing=
            finalReservationGenerations.get(
                task.owner
            );

        if(existing!=null)
            throw new IllegalStateException(
                "final reservation already active player="+
                task.owner.id()+
                " generation="+
                existing
            );

        finalReservationGenerations.put(
            task.owner,
            task.ownerGeneration
        );
    }

    private void clearFinalReservationLocked(
        WorldPlayer owner,
        long expectedGeneration
    ){
        if(!Thread.holdsLock(io))
            throw new IllegalStateException(
                "final reservation clear requires persistence admission ownership"
            );

        if(owner==null)
            return;

        Long existing=
            finalReservationGenerations.get(
                owner
            );

        if(existing!=null&&
           existing.longValue()==expectedGeneration)
            finalReservationGenerations.remove(
                owner
            );
    }

    private void clearFinalReservation(
        WorldPlayer owner,
        long expectedGeneration
    ){
        if(owner==null)
            return;

        synchronized(io){
            clearFinalReservationLocked(
                owner,
                expectedGeneration
            );
        }
    }

    private boolean tryAdmitFinalReservationLocked(
        ReservedFinalSaveTask task,
        boolean executorAdmission
    ){
        if(!Thread.holdsLock(io))
            throw new IllegalStateException(
                "final reservation admission requires persistence admission ownership"
            );

        activateFinalReservationLocked(
            task
        );

        boolean admitted=false;

        try{
            if(executorAdmission){
                io.execute(
                    task
                );
                admitted=true;
                return true;
            }

            admitted=
                io.getQueue().offer(
                    task
                );
            return admitted;
        }finally{
            if(!admitted)
                clearFinalReservationLocked(
                    task.owner,
                    task.ownerGeneration
                );
        }
    }

    private void enqueueSaveImmediate(
        SaveTask task
    ){
        synchronized(io){
            if(preparedAccountBlockedLocked(task.snapshot.username())){
                task.reject(
                    preparedWriteRejection(task.snapshot.username()),
                    "G2127_PREPARED_WRITE_RESERVATION"
                );
                return;
            }
            if(finalReservationBlocksLocked(
                    task)){
                task.reject(
                    finalReservationSaveRejection(
                        task
                    ),
                    "FINAL_RESERVATION_FENCE"
                );
                return;
            }

            try{
                io.execute(
                    task
                );
            }catch(RejectedExecutionException failure){
                task.reject(
                    failure,
                    io.isShutdown()
                        ?"SHUTDOWN"
                        :"IO_BACKPRESSURE"
                );
            }
        }
    }

    private void enqueueSaveWithBackpressure(
        SaveTask task,
        long timeoutMillis
    ){
        long timeoutNanos=
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );
        long started=
            System.nanoTime();
        boolean interrupted=false;
        boolean executeAttempted=false;

        try{
            for(;;){
                synchronized(io){
                    if(preparedAccountBlockedLocked(task.snapshot.username())){
                        task.reject(
                            preparedWriteRejection(task.snapshot.username()),
                            "G2127_PREPARED_WRITE_RESERVATION"
                        );
                        return;
                    }
                    if(finalReservationBlocksLocked(
                            task)){
                        task.reject(
                            finalReservationSaveRejection(
                                task
                            ),
                            "FINAL_RESERVATION_FENCE"
                        );
                        return;
                    }

                    if(io.isShutdown()){
                        task.reject(
                            new RejectedExecutionException(
                                "persistence closed before captured save admission"
                            ),
                            "SHUTDOWN"
                        );
                        return;
                    }

                    if(!executeAttempted){
                        executeAttempted=true;

                        try{
                            io.execute(task);
                            return;
                        }catch(RejectedExecutionException full){
                            if(io.isShutdown()){
                                task.reject(
                                    full,
                                    "SHUTDOWN"
                                );
                                return;
                            }
                        }
                    }

                    if(io.getQueue().offer(task))
                        return;
                }

                long elapsed=
                    System.nanoTime()-started;

                if(elapsed>=timeoutNanos){
                    task.reject(
                        new RejectedExecutionException(
                            "captured save admission timed out after "+
                            timeoutMillis+"ms"
                        ),
                        "IO_BACKPRESSURE_TIMEOUT"
                    );
                    return;
                }

                long remaining=
                    timeoutNanos-elapsed;
                long sleepMillis=
                    Math.max(
                        1L,
                        Math.min(
                            5L,
                            TimeUnit.NANOSECONDS
                                .toMillis(
                                    remaining
                                )
                        )
                    );

                try{
                    Thread.sleep(
                        sleepMillis
                    );
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
            }
        }finally{
            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
    }

    /**
     * World-tick autosave. Every persistence-eligible local profile is
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
            PendingCheckpoint pending=null;
            CheckpointSlot slot=null;
            boolean submit=false;

            synchronized(player.mutationLock()){
                long generation=
                    player.generation();

                if(!world.players().owns(
                        player,
                        generation
                    ))
                    continue;

                String username=
                    player.username();

                if(!LocalAccountProfiles.isPersistent(
                        username))
                    continue;

                // Prevent even capturing NEWER autosave snapshots for
                // this account while the opt-in exclusive fence lives.
                synchronized(io){
                    if(preparedAccountBlockedLocked(username))
                        continue;
                }

                synchronized(checkpointLock){
                    Long suppressed=
                        checkpointSuppressedGenerations.get(
                            player.id()
                        );

                    if(suppressed!=null&&
                       suppressed.longValue()==generation)
                        continue;
                }

                int accessoryItem=
                    player.petAccessoryState()
                        .activeItem();

                PlayerSnapshot snapshot=
                    PlayerSnapshotCodec.capture(
                        username,
                        player,
                        accessoryItem
                    );

                pending=
                    new PendingCheckpoint(
                        tick,
                        sequence.incrementAndGet(),
                        snapshot,
                        accessoryItem
                    );

                synchronized(checkpointLock){
                    Long suppressed=
                        checkpointSuppressedGenerations.get(
                            player.id()
                        );

                    if(suppressed!=null&&
                       suppressed.longValue()==generation)
                        continue;

                    checkpointCaptured.incrementAndGet();

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
            }

            if(submit)
                submitCheckpointDrain(
                    player.id(),
                    slot
                );
        }
    }

    void releaseCheckpointSuppression(
        EntityId playerId,
        long expectedGeneration
    ){
        if(playerId==null)
            return;

        synchronized(checkpointLock){
            Long suppressed=
                checkpointSuppressedGenerations.get(
                    playerId
                );

            if(suppressed!=null&&
               suppressed.longValue()==expectedGeneration)
                checkpointSuppressedGenerations.remove(
                    playerId
                );
        }
    }

    boolean checkpointSuppressed(
        EntityId playerId,
        long expectedGeneration
    ){
        synchronized(checkpointLock){
            Long suppressed=
                checkpointSuppressedGenerations.get(
                    playerId
                );
            return suppressed!=null&&
                suppressed.longValue()==expectedGeneration;
        }
    }

    String repositoryName(){
        return repository.getClass()
            .getSimpleName();
    }

    /** G21.37 socket-thread marker-only review check (not a world tick). */
    boolean supportsDurableMailboxReviewFence(){
        return repository instanceof FilePlayerRepository;
    }

    boolean hasDurableMailboxReviewFence(String account)
        throws IOException{
        if(!(repository instanceof FilePlayerRepository))
            return false;
        return ((FilePlayerRepository)repository)
            .hasUnresolvedMailboxReviewFence(account);
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

            // The cutoff compares the World *capture* order,
            // not the later worker drain order. A pending checkpoint
            // older than a strict barrier is never allowed to overwrite
            // the new durable PREPARED-only account snapshot.
            write(
                pending.captureSequence,
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
        private final boolean enforceAdmission;
        private final CompletableFuture<
            Optional<PlayerSnapshot>
        > future=new CompletableFuture<>();

        LoadTask(String username,boolean enforceAdmission){
            this.username=username;
            this.enforceAdmission=enforceAdmission;
        }

        @Override public void run(){
            inFlightLoad.set(this);

            try{
                if(future.isDone())
                    return;

                // G21.32: detect a durable negative review marker BEFORE
                // any session snapshot hydration, including absent accounts.
                // Read-only G21.26 forensic observations intentionally skip
                // admission but must never be used as session loads.
                if(enforceAdmission&&
                   repository instanceof FilePlayerRepository&&
                   ((FilePlayerRepository)repository)
                       .hasUnresolvedMailboxReviewFence(username))
                    throw new IOException(
                        "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"+
                        " account="+username+" action=REJECT_SESSION"
                    );
                java.util.Optional<PlayerSnapshot> loaded=
                    repository.load(username);
                if(enforceAdmission&&
                   repository instanceof FilePlayerRepository&&
                   ((FilePlayerRepository)repository)
                       .hasUnresolvedMailboxReviewFence(username))
                    throw new IOException(
                        "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"+
                        " account="+username+" action=REJECT_SESSION"
                    );
                if(enforceAdmission&&loaded.isPresent()){
                    MailboxPreparedRestartAdmission.Decision admission=
                        MailboxPreparedRestartAdmission.inspect(
                            loaded.get()
                        );
                    if(!admission.admissionAllowed)
                        throw new IOException(
                            "G21.31 MAILBOX_PREPARED_LOAD_QUARANTINE"+
                            " account="+username+
                            " reason="+admission.state
                        );
                }
                future.complete(loaded);
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

    private final class ReservedFinalSaveTask
        implements Runnable {

        final WorldPlayer owner;
        final long ownerGeneration;
        private CapturedSave captured;
        private Throwable terminalFailure;

        ReservedFinalSaveTask(
            WorldPlayer owner,
            long ownerGeneration
        ){
            this.owner=owner;
            this.ownerGeneration=ownerGeneration;
        }

        @Override public void run(){
            CapturedSave ready=null;
            Throwable terminal=null;
            boolean interrupted=false;

            synchronized(this){
                while(captured==null&&
                      terminalFailure==null){
                    try{
                        wait();
                    }catch(InterruptedException failure){
                        interrupted=true;
                        terminalFailure=
                            new RejectedExecutionException(
                                "final-save reservation worker interrupted",
                                failure
                            );
                    }
                }

                ready=captured;
                terminal=terminalFailure;
            }

            if(interrupted)
                Thread.currentThread()
                    .interrupt();

            if(ready!=null){
                saveTask(ready).run();
                return;
            }

            if(terminal!=null)
                clearFinalReservation(
                    owner,
                    ownerGeneration
                );
        }

        synchronized boolean publish(
            CapturedSave captured
        ){
            if(this.captured!=null||
               terminalFailure!=null)
                return false;

            CapturedSave checked=
                Objects.requireNonNull(
                    captured,
                    "captured"
                );

            if(owner!=null&&
               (checked.owner!=owner||
                checked.expectedGeneration!=
                    ownerGeneration))
                throw new IllegalArgumentException(
                    "final reservation capture owner mismatch player="+
                    owner.id()+
                    " expectedGeneration="+
                    ownerGeneration
                );

            this.captured=checked;
            notifyAll();
            return true;
        }

        void abort(
            Throwable failure
        ){
            boolean changed=false;

            synchronized(this){
                if(captured!=null||
                   terminalFailure!=null)
                    return;

                terminalFailure=
                    failure==null
                        ?new IllegalStateException(
                            "final-save reservation aborted"
                        )
                        :failure;
                changed=true;
                notifyAll();
            }

            if(changed)
                clearFinalReservation(
                    owner,
                    ownerGeneration
                );
        }

        boolean reject(
            RejectedExecutionException error,
            String stage
        ){
            CapturedSave ready;

            synchronized(this){
                if(terminalFailure!=null)
                    return false;

                terminalFailure=error;
                ready=captured;
                notifyAll();
            }

            if(ready!=null)
                return saveTask(ready)
                    .reject(
                        error,
                        stage
                    );

            clearFinalReservation(
                owner,
                ownerGeneration
            );
            return true;
        }
    }

    /**
     * An opt-in PREPARED_NO_GRANT strict-write barrier, serialized on the
     * existing single persistence worker. Never invoked by live native
     * Mailbox reward-claim widgets.
     */
    final class PreparedStrictBarrierTask implements Runnable {
        final long barrierSequence;
        final PlayerSnapshot snapshot;
        final StrictDurablePlayerSnapshotWriter writer;
        final CompletableFuture<
            StrictDurablePlayerSnapshotWriter.Receipt> completion=
                new CompletableFuture<>();

        PreparedStrictBarrierTask(
            long barrierSequence,
            PlayerSnapshot snapshot,
            StrictDurablePlayerSnapshotWriter writer
        ){
            this.barrierSequence=barrierSequence;
            this.snapshot=snapshot;
            this.writer=writer;
        }

        @Override public void run(){
            inFlightStrictBarrier.set(this);
            try{
                if(completion.isDone())
                    return;
                // G21.42: real file-backed World strict PREPARED saves
                // coordinate with the G21.34 negative review marker.
                // Keep legacy non-file adapters explicitly outside the
                // exact FilePlayerRepository path guarantee.
                StrictDurablePlayerSnapshotWriter.Receipt receipt=
                    repository instanceof FilePlayerRepository
                        ?writer.saveStrictForWorld(
                            snapshot,
                            ((FilePlayerRepository)repository)
                                .accountFilePath(snapshot.username())
                        )
                        :writer.saveStrict(snapshot);
                completion.complete(receipt);
            }catch(Throwable failure){
                completion.completeExceptionally(failure);
            }finally{
                inFlightStrictBarrier.compareAndSet(this,null);
            }
        }

        void reject(Throwable failure){
            completion.completeExceptionally(failure);
        }
    }

    /**
     * Off-World admission only; snapshot must already be captured in
     * current World ownership and contain G21.22 PREPARED_NO_GRANT.
     * Existing queued and in-flight I/O precedes this FIFO barrier.
     * Old deferred/captured saves are rejected by capture sequence.
     *
     * THIS IS NOT CLAIM SETTLEMENT: new captures after this barrier
     * can still overwrite the snapshot unless their owner state has
     * the committed postimage. No reward credit/CLAIMED allowed.
     */
    CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
        submitPreparedStrictBarrier(
            WorldPlayer owner,
            long expectedGeneration,
            PlayerSnapshot snapshot,
            StrictDurablePlayerSnapshotWriter strictWriter
        ){
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(strictWriter,"strictWriter");
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "strict file I/O barrier admission on World context"
            );

        if(!MailboxPreparedClaimJournal.STATE.equals(
                snapshot.value("extension."+
                    MailboxPreparedClaimJournal.NAMESPACE+
                    ".state")))
            throw new IllegalArgumentException(
                "strict claim barrier requires PREPARED_NO_GRANT"
            );

        synchronized(owner.mutationLock()){
            if(!world.players().owns(owner,expectedGeneration)||
               !snapshot.username().equals(owner.username()))
                throw new IllegalStateException(
                    "strict prepared barrier World owner changed"
                );

            // G21.43: for the concrete file-backed World persistence
            // path, a caller-supplied PREPARED snapshot is never accepted
            // merely because its account/generation or journal-state
            // fields look correct. Check the FULL canonical snapshot
            // against the actual currently owned player under the same
            // mutation lock, BEFORE reserving any worker sequence/cutoff.
            // Historical custom PlayerRepository adapters retain their
            // separate strict-barrier semantics and are not claimed safe.
            if(repository instanceof FilePlayerRepository){
                final PlayerSnapshot current;
                final PlayerSnapshot normalized;
                try{
                    normalized=PlayerSnapshotCodec
                        .validateAndNormalize(snapshot);
                    current=PlayerSnapshotCodec.capture(
                        snapshot.username(),
                        owner,
                        PlayerSnapshotCodec.accessoryItem(snapshot)
                    );
                }catch(RuntimeException invalid){
                    throw new IllegalStateException(
                        "G21.43 STRICT_PREPARED_ACCOUNT_SNAPSHOT_INVALID",
                        invalid
                    );
                }
                if(!normalized.values().equals(snapshot.values())||
                   normalized.version()!=snapshot.version()||
                   MailboxPreparedRestartAdmission.inspect(snapshot)
                       .state!=MailboxPreparedRestartAdmission.State
                           .VALID_PREPARED_UNCLAIMED)
                    throw new IllegalStateException(
                        "G21.43 STRICT_PREPARED_ACCOUNT_NOT_CANONICAL"
                    );
                if(!StrictDurablePlayerSnapshotWriter
                       .canonicalSnapshotSha256(snapshot)
                       .equals(StrictDurablePlayerSnapshotWriter
                           .canonicalSnapshotSha256(current)))
                    throw new IllegalStateException(
                        "G21.43 STRICT_PREPARED_STALE_LIVE_OWNER_SNAPSHOT"
                    );
            }

            synchronized(io){
                if(io.isShutdown())
                    throw new RejectedExecutionException(
                        "strict prepared barrier after shutdown"
                    );
                if(preparedAccountBlockedLocked(snapshot.username()))
                    throw preparedWriteRejection(snapshot.username());
                Long finalGeneration=
                    finalReservationGenerations.get(owner);
                if(finalGeneration!=null&&
                   finalGeneration.longValue()==expectedGeneration)
                    throw new RejectedExecutionException(
                        "strict prepared barrier blocked by final-save reservation"
                    );

                long barrierSequence=sequence.incrementAndGet();
                PreparedStrictBarrierTask task=
                    new PreparedStrictBarrierTask(
                        barrierSequence,snapshot,strictWriter
                    );
                // The cutoff must become visible only on a successful
                // enqueue. Failed/backpressured admission changes nothing.
                io.execute(task);
                strictPreparedAccountCutoffs.put(
                    snapshot.username(),barrierSequence
                );
                return task.completion;
            }
        }
    }

    /**
     * G21.27 exclusive, fail-closed account WRITE reservation.
     * No I/O receipt, actual transaction, or reward authorization.
     * Deliberately no AutoCloseable: an arbitrary close() must never
     * accidentally remove a fence around an uncertain claim.
     */
    final class PreparedAccountReservation {
        final WorldPlayer owner;
        final long generation;
        final String account;
        final String intentKey;
        final RewardDeliveryMessage envelopeIdentity;
        final PlayerSnapshot exactPreimage;
        private boolean released;
        private int pendingDrains;
        private boolean drainAttempted;
        private boolean lastDrainWasExactPrepared;

        private PreparedAccountReservation(
            WorldPlayer owner,long generation,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
            this.owner=owner;
            this.generation=generation;
            this.account=proposal.account;
            this.intentKey=proposal.idempotencyKey;
            MailboxRewardDeliveryService.Snapshot bound=
                owner.mailbox().get(proposal.messageId);
            if(bound==null)
                throw new IllegalStateException(
                    "G21.27 reservation envelope missing"
                );
            this.envelopeIdentity=bound.message;
            this.exactPreimage=proposal.preparedPreimage;
        }

        boolean isActive(){
            synchronized(io){
                return !released&&
                    preparedAccountReservations.get(account)==this;
            }
        }

        /**
         * Explicit cancellation ONLY while the owner is still at the
         * exact PREPARED/UNCLAIMED inventory+Mailbox preimage. Divergence,
         * logout or generation replacement leaves the reservation held.
         * Never interprets a disk write or saved intent as a grant.
         */
        boolean cancelIfStillUnclaimed(){
            synchronized(owner.mutationLock()){
                synchronized(io){
                    if(released)
                        return false;
                    if(pendingDrains!=0)
                        throw new IllegalStateException(
                            "G21.28 cannot release reservation while disk drain pending"
                        );
                    if(drainAttempted&&!lastDrainWasExactPrepared)
                        throw new IllegalStateException(
                            "G21.28 last disk drain was not exact PREPARED"
                        );
                    if(preparedAccountReservations.get(account)!=this||
                       !world.players().owns(owner,generation)||
                       !owner.accepts(generation))
                        throw new IllegalStateException(
                            "G21.27 reservation owner retired"
                        );
                    MailboxRewardDeliveryService.Snapshot row=
                        owner.mailbox().get(
                            preparedAccountMessageId(this)
                        );
                    if(row==null||row.message!=envelopeIdentity)
                        throw new IllegalStateException(
                            "G21.27 selected envelope identity changed"
                        );
                    // Full snapshot rather than just a stable key:
                    // prevents releasing across changes to inventory,
                    // mail, items or unrelated account state.
                    PlayerSnapshot current=PlayerSnapshotCodec.capture(
                        account,owner,
                        PlayerSnapshotCodec.accessoryItem(
                            exactPreimage
                        )
                    );
                    if(!current.values().equals(
                            exactPreimage.values())||
                       row.claimState!=
                           MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                        throw new IllegalStateException(
                            "G21.27 reservation preimage changed"
                        );
                    MailboxPreparedClaimJournal.Intent prepared=
                        MailboxPreparedClaimJournal.inspectPrepared(owner);
                    if(prepared==null||
                       !intentKey.equals(prepared.idempotencyKey))
                        throw new IllegalStateException(
                            "G21.27 prepared intent changed"
                        );
                    preparedAccountReservations.remove(account);
                    released=true;
                    return true;
                }
            }
        }
    }

    /**
     * A FIFO drain is a point-in-time repository observation after all
     * previously admitted tasks. This is NOT file fsync proof or permission
     * to mutate inventory or Mailbox state.
     */
    static final class PreparedDrainObservation {
        enum State {
            MISSING_ACCOUNT,
            EXACT_PREPARED,
            EXACT_HYPOTHETICAL,
            DIVERGENT
        }

        final State state;
        final String account;
        final String intentKey;
        final long generation;
        final boolean workerQuiescentAtRead;
        final boolean durabilityReceipt=false;
        final boolean grantAuthorized=false;

        PreparedDrainObservation(
            State state,String account,String key,long generation
        ){
            this.state=state;
            this.account=account;
            this.intentKey=key;
            this.generation=generation;
            this.workerQuiescentAtRead=true;
        }
    }

    private void verifyReservedProposal(
        PreparedAccountReservation token,
        MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        // Lock order must remain owner -> io, even on the I/O worker.
        synchronized(token.owner.mutationLock()){
            if(!world.players().owns(token.owner,token.generation)||
               !token.owner.accepts(token.generation)||
               !proposal.account.equals(token.account)||
               proposal.ownerGeneration!=token.generation||
               !proposal.idempotencyKey.equals(token.intentKey))
                throw new IllegalStateException(
                    "G21.28 reservation owner or proposal retired"
                );

            MailboxRewardDeliveryService.Snapshot row=
                token.owner.mailbox().get(proposal.messageId);
            if(row==null||row.message!=token.envelopeIdentity)
                throw new IllegalStateException(
                    "G21.28 selected immutable message replaced"
                );

            MailboxSettlementPostimagePlanner.Proposal current=
                MailboxSettlementPostimagePlanner.plan(
                    token.owner,token.generation,row
                );
            if(!current.preparedPreimage.values().equals(
                    proposal.preparedPreimage.values())||
               !current.hypotheticalPostimage.values().equals(
                    proposal.hypotheticalPostimage.values()))
                throw new IllegalStateException(
                    "G21.28 live account postimage changed"
                );

            synchronized(io){
                if(token.released||
                   preparedAccountReservations.get(token.account)!=token||
                   token.pendingDrains<1)
                    throw new IllegalStateException(
                        "G21.28 reservation no longer owns drain"
                    );
            }
        }
    }

    private final class ReservedPreparedDrainTask implements Runnable {
        final PreparedAccountReservation token;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final CompletableFuture<PreparedDrainObservation> completion=
            new CompletableFuture<>();
        private boolean unpinned;

        ReservedPreparedDrainTask(
            PreparedAccountReservation token,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
            this.token=token;
            this.proposal=proposal;
        }

        private void unpin(boolean exact){
            synchronized(io){
                if(unpinned)
                    return;
                unpinned=true;
                token.pendingDrains--;
                token.lastDrainWasExactPrepared=exact;
            }
        }

        @Override public void run(){
            inFlightPreparedDrain.set(this);
            try{
                if(completion.isDone())
                    return;
                verifyReservedProposal(token,proposal);
                java.util.Optional<PlayerSnapshot> disk=
                    repository.load(token.account);
                verifyReservedProposal(token,proposal);

                PreparedDrainObservation.State state;
                if(!disk.isPresent()){
                    state=PreparedDrainObservation.State.MISSING_ACCOUNT;
                }else{
                    MailboxSettlementPostimagePlanner.RecoveryClass match=
                        proposal.classify(disk.get());
                    switch(match){
                        case EXACT_PREPARED_PREIMAGE:
                            state=PreparedDrainObservation.State.EXACT_PREPARED;
                            break;
                        case EXACT_HYPOTHETICAL_POSTIMAGE:
                            state=PreparedDrainObservation.State.EXACT_HYPOTHETICAL;
                            break;
                        default:
                            state=PreparedDrainObservation.State.DIVERGENT;
                            break;
                    }
                }
                // Unpin BEFORE waking any waiting futures, so a caller
                // can immediately attempt the matching safe cancellation.
                unpin(state==PreparedDrainObservation.State.EXACT_PREPARED);
                completion.complete(new PreparedDrainObservation(
                    state,token.account,token.intentKey,token.generation
                ));
            }catch(Throwable failure){
                unpin(false);
                completion.completeExceptionally(failure);
            }finally{
                inFlightPreparedDrain.compareAndSet(this,null);
            }
        }

        void reject(Throwable failure){
            completion.completeExceptionally(failure);
            unpin(false);
        }
    }

    /**
     * Asynchronous, bounded-queue, off-World FIFO admission.
     * The reservation remains pinned until the task resolves; no
     * concurrent cancellation can unfreeze conflicting writers.
     */
    CompletableFuture<PreparedDrainObservation>
        drainReservedPreparedAccount(
            PreparedAccountReservation token,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
        Objects.requireNonNull(token,"token");
        Objects.requireNonNull(proposal,"proposal");
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "G21.28 drain cannot perform file I/O on World"
            );

        verifyReservedAdmission(token,proposal);
        synchronized(token.owner.mutationLock()){
            synchronized(io){
                if(io.isShutdown())
                    throw new RejectedExecutionException(
                        "G21.28 persistence worker closed"
                    );
                if(token.released||
                   preparedAccountReservations.get(token.account)!=token||
                   token.generation!=proposal.ownerGeneration||
                   !token.intentKey.equals(proposal.idempotencyKey))
                    throw new IllegalStateException(
                        "G21.28 stale reserved drain admission"
                    );
                // Only one in-flight observation per token; no unbounded
                // competing queued readers and no premature release.
                if(token.pendingDrains!=0)
                    throw new IllegalStateException(
                        "G21.28 duplicate pending account drain"
                    );

                ReservedPreparedDrainTask task=
                    new ReservedPreparedDrainTask(token,proposal);
                io.execute(task); // if rejected, no reservation changes
                token.pendingDrains++;
                token.drainAttempted=true;
                token.lastDrainWasExactPrepared=false;
                return task.completion;
            }
        }
    }

    private void verifyReservedAdmission(
        PreparedAccountReservation token,
        MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        synchronized(token.owner.mutationLock()){
            if(!world.players().owns(token.owner,token.generation)||
               !token.owner.accepts(token.generation)||
               !proposal.account.equals(token.account)||
               !proposal.idempotencyKey.equals(token.intentKey)||
               proposal.ownerGeneration!=token.generation)
                throw new IllegalStateException(
                    "G21.28 drain requested for stale owner"
                );
            MailboxRewardDeliveryService.Snapshot row=
                token.owner.mailbox().get(proposal.messageId);
            if(row==null||row.message!=token.envelopeIdentity)
                throw new IllegalStateException(
                    "G21.28 drain envelope identity changed"
                );
            MailboxSettlementPostimagePlanner.Proposal current=
                MailboxSettlementPostimagePlanner.plan(
                    token.owner,token.generation,row
                );
            if(!current.preparedPreimage.values().equals(
                    proposal.preparedPreimage.values())||
               !current.hypotheticalPostimage.values().equals(
                    proposal.hypotheticalPostimage.values()))
                throw new IllegalStateException(
                    "G21.28 drain postimage changed"
                );
        }
    }

    private static String preparedAccountMessageId(
        PreparedAccountReservation reservation
    ){
        // The captured intent namespace is immutable and canonical.
        String id=reservation.exactPreimage.value(
            "extension."+MailboxPreparedClaimJournal.NAMESPACE+
            ".message"
        );
        if(id==null)
            throw new IllegalStateException(
                "G21.27 missing prepared envelope"
            );
        return id;
    }

    PreparedAccountReservation reservePreparedAccount(
        WorldPlayer owner,
        long expectedGeneration,
        MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(proposal,"proposal");
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "G21.27 reservation admission on World context"
            );
        synchronized(owner.mutationLock()){
            if(!world.players().owns(owner,expectedGeneration)||
               !owner.accepts(expectedGeneration)||
               proposal.ownerGeneration!=expectedGeneration||
               !proposal.account.equals(owner.username()))
                throw new IllegalStateException(
                    "G21.27 reservation account/generation mismatch"
                );
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(proposal.messageId);
            if(row==null)
                throw new IllegalStateException(
                    "G21.27 selected message absent"
                );
            MailboxSettlementPostimagePlanner.Proposal fresh=
                MailboxSettlementPostimagePlanner.plan(
                    owner,expectedGeneration,row
                );
            if(!fresh.idempotencyKey.equals(
                    proposal.idempotencyKey)||
               !fresh.preparedPreimage.values().equals(
                    proposal.preparedPreimage.values())||
               !fresh.hypotheticalPostimage.values().equals(
                    proposal.hypotheticalPostimage.values()))
                throw new IllegalStateException(
                    "G21.27 stale PREPARED reservation proposal"
                );
            synchronized(io){
                if(io.isShutdown())
                    throw new RejectedExecutionException(
                        "G21.27 persistence already shut down"
                    );
                if(preparedAccountReservations.containsKey(
                        proposal.account))
                    throw new IllegalStateException(
                        "G21.27 duplicate account reservation"
                    );
                if(preparedAccountReservations.size()>=
                        MAX_PREPARED_ACCOUNT_RESERVATIONS)
                    throw new RejectedExecutionException(
                        "G21.27 prepared account reservation capacity"
                    );
                if(finalReservationGenerations.containsKey(owner))
                    throw new RejectedExecutionException(
                        "G21.27 final save already reserved"
                    );

                PreparedAccountReservation token=
                    new PreparedAccountReservation(
                        owner,expectedGeneration,proposal
                    );
                preparedAccountReservations.put(
                    proposal.account,token
                );
                return token;
            }
        }
    }

    private boolean preparedAccountBlockedLocked(String username){
        if(!Thread.holdsLock(io))
            throw new IllegalStateException(
                "G21.27 account fence requires io lock"
            );
        return username!=null&&
            preparedAccountReservations.containsKey(username);
    }

    private RejectedExecutionException preparedWriteRejection(
        String username
    ){
        return new RejectedExecutionException(
            "G21.27 PREPARED_ACCOUNT_WRITE_RESERVED "+username
        );
    }

    private final class SaveTask
        implements Runnable {

        private final WorldPlayer owner;
        private final long expectedGeneration;
        private final boolean finalDisconnect;
        private final long saveSequence;
        private final PlayerSnapshot snapshot;
        private final int petAccessoryItem;
        private final String tag;
        private final String reason;
        private final CompletableFuture<Void> future;

        SaveTask(
            CapturedSave captured
        ){
            CapturedSave checked=
                Objects.requireNonNull(
                    captured,
                    "captured"
                );

            this.owner=checked.owner;
            this.expectedGeneration=
                checked.expectedGeneration;
            this.finalDisconnect=
                checked.finalDisconnect;
            this.saveSequence=
                checked.ticket.sequence;
            this.snapshot=
                checked.ticket.snapshot;
            this.petAccessoryItem=
                checked.petAccessoryItem;
            this.tag=checked.tag;
            this.reason=checked.reason;
            this.future=
                checked.ticket.completion;
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

                if(finalDisconnect)
                    clearFinalReservation(
                        owner,
                        expectedGeneration
                    );
            }
        }

        boolean reject(
            RejectedExecutionException error,
            String stage
        ){
            boolean rejected;

            synchronized(future){
                if(future.isDone())
                    rejected=false;
                else{
                    failed.incrementAndGet();
                    future.completeExceptionally(
                        error
                    );
                    rejected=true;
                }
            }

            if(rejected)
                logRejected(
                    snapshot,
                    saveSequence,
                    tag,
                    reason,
                    stage,
                    error
                );

            if(finalDisconnect)
                clearFinalReservation(
                    owner,
                    expectedGeneration
                );

            return rejected;
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
            synchronized(io){
                if(preparedAccountBlockedLocked(snapshot.username()))
                    throw preparedWriteRejection(snapshot.username());
                Long cutoff=strictPreparedAccountCutoffs.get(
                    snapshot.username()
                );
                if(cutoff!=null&&saveSequence<=cutoff)
                    throw new RejectedExecutionException(
                        "stale prepared strict barrier capture account="+
                        snapshot.username()+" capture="+saveSequence+
                        " cutoff="+cutoff
                    );
            }
            // G21.36: ordinary, checkpoint and final session writes use
            // the same file-backed review-fence admission boundary. This
            // does NOT change non-file PlayerRepository adapter contracts.
            if(repository instanceof FilePlayerRepository)
                ((FilePlayerRepository)repository).saveForWorld(snapshot);
            else
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

            if(runnable instanceof ReservedFinalSaveTask){
                counts.finalReservations++;
                ((ReservedFinalSaveTask)runnable)
                    .reject(
                        error,
                        stage
                    );
                continue;
            }

            if(runnable instanceof PreparedStrictBarrierTask){
                counts.saves++;
                ((PreparedStrictBarrierTask)runnable).reject(error);
                continue;
            }

            if(runnable instanceof ReservedPreparedDrainTask){
                counts.loads++;
                ((ReservedPreparedDrainTask)runnable).reject(error);
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

    private boolean rejectInFlightPreparedDrain(String stage){
        ReservedPreparedDrainTask active=inFlightPreparedDrain.get();
        if(active==null)
            return false;
        active.reject(new RejectedExecutionException(
            "G21.28 reserved drain outcome unconfirmed during "+stage
        ));
        return true;
    }

    private boolean rejectInFlightStrictBarrier(String stage){
        PreparedStrictBarrierTask active=inFlightStrictBarrier.get();
        if(active==null)
            return false;
        active.reject(new RejectedExecutionException(
            "strict prepared barrier outcome unconfirmed during "+stage
        ));
        return true;
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
        synchronized(checkpointLock){
            checkpointSuppressedGenerations.clear();
        }

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
                boolean strictBarrierSettled=
                    !clean&&rejectInFlightStrictBarrier(
                        "SHUTDOWN_STRICT_IN_FLIGHT"
                    );
                boolean reservedDrainSettled=
                    !clean&&rejectInFlightPreparedDrain(
                        "SHUTDOWN_RESERVED_DRAIN_IN_FLIGHT"
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