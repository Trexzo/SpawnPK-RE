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
        /** R71: World-thread snapshot capture order, not IO enqueue order. */
        final long captureOrdinal;
        final PlayerSnapshot snapshot;
        final CompletableFuture<Void> completion;
        /** R91: immutable test-only World-thread capture provenance. */
        final OfflineCaptureStamp provenance;

        SaveTicket(
            long sequence,
            long captureOrdinal,
            PlayerSnapshot snapshot,
            CompletableFuture<Void> completion
        ){
            this(sequence,captureOrdinal,snapshot,completion,null);
        }

        SaveTicket(
            long sequence,long captureOrdinal,
            PlayerSnapshot snapshot,CompletableFuture<Void> completion,
            OfflineCaptureStamp provenance
        ){
            this.sequence=sequence;
            this.captureOrdinal=captureOrdinal;
            this.snapshot=snapshot;
            this.completion=completion;
            this.provenance=provenance;
        }
    }

    static final class CapturedSave {
        final SaveTicket ticket;
        final WorldPlayer owner;
        final long expectedGeneration;
        final int petAccessoryItem;
        final String tag;
        final String reason;
        final boolean finalSnapshot;
        private boolean submitted;

        CapturedSave(
            SaveTicket ticket,
            WorldPlayer owner,
            long expectedGeneration,
            int petAccessoryItem,
            String tag,
            String reason,
            boolean finalSnapshot
        ){
            this.ticket=ticket;
            this.owner=owner;
            this.expectedGeneration=expectedGeneration;
            this.petAccessoryItem=petAccessoryItem;
            this.tag=tag;
            this.reason=reason;
            this.finalSnapshot=finalSnapshot;
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

    private static final class PendingCheckpoint {
        final long tick;
        final long captureOrdinal;
        final PlayerSnapshot snapshot;
        final int petAccessoryItem;
        final OfflineCaptureStamp provenance;

        PendingCheckpoint(
            long tick,
            long captureOrdinal,
            PlayerSnapshot snapshot,
            int petAccessoryItem,
            OfflineCaptureStamp provenance
        ){
            this.tick=tick;
            this.captureOrdinal=captureOrdinal;
            this.snapshot=snapshot;
            this.petAccessoryItem=petAccessoryItem;
            this.provenance=provenance;
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
    /**
     * R69 offline test seam. The existing worker continues to call the
     * repository directly unless an isolated fixture explicitly installs
     * this hook. LocalSession and World never install it.
     */
    @FunctionalInterface
    interface OfflineSaveAdmission {
        void withAdmission(PlayerSnapshot snapshot, CheckedSaveWriter writer)
            throws IOException;
    }

    /**
     * R71 test-only extension. The absolute capture ordinal originates on
     * the World thread and survives deferred submission and checkpoint
     * coalescing, rather than being assigned at IO drain.
     */
    interface OfflineOrderedSaveAdmission extends OfflineSaveAdmission {
        void withOrderedAdmission(
            long captureOrdinal,
            PlayerSnapshot snapshot,
            CheckedSaveWriter writer
        )throws IOException;
    }

    /**
     * R91 OFFLINE ONLY. Stamp is formed on the actual World execution
     * thread within the player mutation lock, alongside the exact immutable
     * snapshot and capture ordinal. It is never a login credential or a
     * cryptographic proof, and no live code installs an issuer.
     */
    static final class OfflineCaptureStamp {
        final String username,snapshotDigest;
        final UUID stream,incarnation;
        final long ownerEpoch,producerGeneration,captureOrdinal;
        final long worldPlayerGeneration;

        OfflineCaptureStamp(
            String username,UUID stream,UUID incarnation,
            long ownerEpoch,long producerGeneration,
            long captureOrdinal,String snapshotDigest,
            long worldPlayerGeneration
        ){
            this.username=Objects.requireNonNull(username,"username");
            this.stream=Objects.requireNonNull(stream,"stream");
            this.incarnation=Objects.requireNonNull(incarnation,"incarnation");
            this.ownerEpoch=ownerEpoch;
            this.producerGeneration=producerGeneration;
            this.captureOrdinal=captureOrdinal;
            this.snapshotDigest=Objects.requireNonNull(
                snapshotDigest,"snapshotDigest");
            this.worldPlayerGeneration=worldPlayerGeneration;
        }
    }

    @FunctionalInterface
    interface OfflineCaptureStampIssuer {
        OfflineCaptureStamp issue(
            long ordinal,PlayerSnapshot snapshot,long worldGeneration
        );
    }

    interface OfflineStampedSaveAdmission
            extends OfflineOrderedSaveAdmission {
        void withStampedAdmission(
            long ordinal,PlayerSnapshot snapshot,
            OfflineCaptureStamp stamp,
            CheckedSaveWriter rawWriter
        )throws IOException;
    }

    /**
     * R74 test-only cooperative shutdown witness. A test gate may refuse
     * an IO writer blocked before R60 admission even when that writer
     * deliberately ignores Java thread interruption.
     *
     * Calling this callback does NOT mean a real filesystem operation
     * already in progress was rolled back or physically canceled.
     */
    interface OfflineForcedShutdownWitness {
        void onOfflineForcedShutdownForTest();
    }

    private void notifyOfflineForcedShutdownForTest(){
        OfflineSaveAdmission gate=offlineSaveAdmission;
        if(gate instanceof OfflineForcedShutdownWitness)
            ((OfflineForcedShutdownWitness)gate)
                .onOfflineForcedShutdownForTest();
    }

    @FunctionalInterface
    interface CheckedSaveWriter {
        void run() throws IOException;
    }

    private volatile OfflineSaveAdmission offlineSaveAdmission;
    private volatile OfflineCaptureStampIssuer offlineCaptureIssuer;

    synchronized void installOfflineCaptureIssuerForTest(
        OfflineCaptureStampIssuer issuer
    ){
        if(offlineCaptureIssuer!=null||world.pulse().running())
            throw new IllegalStateException(
                "R91_CAPTURE_ISSUER_DENIED already installed or started");
        offlineCaptureIssuer=Objects.requireNonNull(issuer,"issuer");
    }

    private OfflineCaptureStamp stampAtCaptureForTest(
        long ordinal,PlayerSnapshot snapshot,long worldGeneration
    ){
        OfflineCaptureStampIssuer issuer=offlineCaptureIssuer;
        if(issuer==null)return null;
        // Called ONLY by World execution during snapshot capture;
        // no issuer callback runs on the persistence I/O worker.
        OfflineCaptureStamp stamp=Objects.requireNonNull(
            issuer.issue(ordinal,snapshot,worldGeneration),
            "R91_CAPTURE_STAMP_DENIED missing issuer result");
        if(stamp.captureOrdinal!=ordinal||
           !snapshot.username().equals(stamp.username)||
           stamp.worldPlayerGeneration!=worldGeneration||
           stamp.worldPlayerGeneration<1L||
           !R57TradePairJournal.digest(snapshot).equals(
               stamp.snapshotDigest))
            throw new IllegalStateException(
                "R91_CAPTURE_STAMP_DENIED source/ordinal/digest mismatch");
        return stamp;
    }

    synchronized void installOfflineSaveAdmissionForTest(
        OfflineSaveAdmission hook
    ){
        if(offlineSaveAdmission!=null)
            throw new IllegalStateException(
                "R69_OFFLINE_SAVE_GATE_DENIED already installed"
            );
        offlineSaveAdmission=Objects.requireNonNull(hook,"hook");
    }

    private final ThreadPoolExecutor io;
    private final AtomicReference<LoadTask> inFlightLoad=
        new AtomicReference<>();
    private final AtomicReference<SaveTask> inFlightSave=
        new AtomicReference<>();
    private final Object checkpointLock=
        new Object();
    private final HashMap<EntityId,CheckpointSlot> checkpoints=
        new HashMap<>();
    private final HashMap<EntityId,Long>
        checkpointSuppressedGenerations=
            new HashMap<>();
    /** R72: only populated when an isolated test explicitly opts in. */
    private final HashMap<EntityId,Long> offlineTerminalGenerations=
        new HashMap<>();
    private volatile boolean offlineTerminalCaptureFence;
    private boolean offlineCaptureOrderSeeded;
    private boolean checkpointAbort;
    private PendingCheckpoint activeCheckpoint;
    private CompletableFuture<Void> activeCheckpointCompletion;
    private CheckpointSlot activeCheckpointSlot;
    private EntityId activeCheckpointPlayerId;

    private final AtomicLong sequence=
        new AtomicLong();
    private final AtomicLong captureOrder=
        new AtomicLong();

    /**
     * R72 test-only restart seed. A checked durable watermark must be
     * loaded under R60/R64 before World execution or snapshot capture.
     */
    synchronized void seedOfflineCaptureOrderForTest(long committedOrdinal){
        if(committedOrdinal<0L ||
           committedOrdinal==Long.MAX_VALUE ||
           offlineCaptureOrderSeeded ||
           captureOrder.get()!=0L ||
           world.pulse().running())
            throw new IllegalStateException(
                "R72_CAPTURE_SEED_DENIED started, already seeded or invalid"
            );
        offlineCaptureOrderSeeded=true;
        captureOrder.set(committedOrdinal);
    }

    synchronized void installOfflineTerminalCaptureFenceForTest(){
        if(offlineTerminalCaptureFence ||
           captureOrder.get()!=0L ||
           world.pulse().running())
            throw new IllegalStateException(
                "R72_TERMINAL_FENCE_DENIED already active or started"
            );
        offlineTerminalCaptureFence=true;
    }

    /** Called while owning the World player's capture mutation lock. */
    private long nextCaptureOrdinal(){
        long value=captureOrder.incrementAndGet();
        if(value<=0L)
            throw new IllegalStateException(
                "R71_CAPTURE_ORDER_DENIED ordinal overflow"
            );
        return value;
    }
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

        try{
            io.execute(task);
        }catch(RejectedExecutionException e){
            task.reject(
                e,
                "IO_BACKPRESSURE"
            );
        }

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
        long snapshotCaptureOrdinal;
        OfflineCaptureStamp captureStamp;

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

            if(offlineTerminalCaptureFence){
                synchronized(checkpointLock){
                    Long terminal=offlineTerminalGenerations.get(player.id());
                    if(terminal!=null &&
                       terminal.longValue()==expectedGeneration)
                        throw new IllegalStateException(
                            "R72_TERMINAL_CAPTURE_DENIED after final snapshot"
                        );
                }
            }
            snapshot=
                PlayerSnapshotCodec.capture(
                    username,
                    player,
                    petAccessoryItem
                );
            snapshotCaptureOrdinal=nextCaptureOrdinal();
            captureStamp=stampAtCaptureForTest(
                snapshotCaptureOrdinal,snapshot,expectedGeneration);
        }

        long saveSequence=
            sequence.incrementAndGet();

        CompletableFuture<Void> future=
            new CompletableFuture<>();

        return new CapturedSave(
            new SaveTicket(
                saveSequence,
                snapshotCaptureOrdinal,
                snapshot,
                future,
                captureStamp
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
        long snapshotCaptureOrdinal;
        OfflineCaptureStamp captureStamp;

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
                if(offlineTerminalCaptureFence){
                    Long terminal=offlineTerminalGenerations.get(player.id());
                    if(terminal!=null &&
                       terminal.longValue()==expectedGeneration)
                        throw new IllegalStateException(
                            "R72_TERMINAL_CAPTURE_DENIED duplicate final"
                        );
                }
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
                snapshotCaptureOrdinal=nextCaptureOrdinal();
                captureStamp=stampAtCaptureForTest(
                    snapshotCaptureOrdinal,snapshot,expectedGeneration);
                if(offlineTerminalCaptureFence){
                    synchronized(checkpointLock){
                        offlineTerminalGenerations.put(
                            player.id(),expectedGeneration
                        );
                    }
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
                snapshotCaptureOrdinal,
                snapshot,
                future,
                captureStamp
            ),
            player,
            expectedGeneration,
            petAccessoryItem,
            cleanTag(tag),
            cleanReason(reason),
            true
        );
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

            if(offlineTerminalCaptureFence && !captured.finalSnapshot){
                synchronized(checkpointLock){
                    Long terminal=offlineTerminalGenerations.get(
                        captured.owner.id()
                    );
                    if(terminal!=null &&
                       terminal.longValue()==captured.expectedGeneration){
                        IOException denied=new IOException(
                            "R72_TERMINAL_SUBMISSION_DENIED older non-final"
                        );
                        captured.ticket.completion.completeExceptionally(denied);
                        throw new IllegalStateException(
                            denied.getMessage(),denied
                        );
                    }
                }
            }
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
            captured.ticket.sequence,
            captured.ticket.captureOrdinal,
            captured.ticket.snapshot,
            captured.ticket.provenance,
            captured.petAccessoryItem,
            captured.tag,
            captured.reason,
            captured.ticket.completion
        );
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
                        nextCaptureOrdinal(),
                        snapshot,
                        accessoryItem,
                        stampAtCaptureForTest(
                            captureOrder.get(),snapshot,generation)
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
            Long finalGeneration=offlineTerminalGenerations.get(playerId);
            if(finalGeneration!=null &&
               finalGeneration.longValue()==expectedGeneration)
                offlineTerminalGenerations.remove(playerId);
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

    /**
     * R73 offline-only queue drain check. A sentinel on the real single IO
     * executor proves all tasks admitted before the sentinel have finished.
     * It must never be called from the World execution thread. A timeout
     * is uncertain, NOT proof that a final save committed.
     */
    void awaitOfflineIoDrainForTest(long timeoutMillis)throws IOException{
        if(timeoutMillis<=0L)
            throw new IllegalArgumentException(
                "R73_IO_DRAIN_DENIED invalid timeout"
            );
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R73_IO_DRAIN_DENIED cannot block World actor"
            );
        final CompletableFuture<Void> reached=new CompletableFuture<>();
        final Runnable barrier=()->reached.complete(null);
        final long started=System.nanoTime();
        final long budget=TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        boolean queued=false;
        while(!queued){
            synchronized(io){
                if(io.isShutdown())
                    throw new IOException(
                        "R73_IO_DRAIN_DENIED worker shutdown"
                    );
                try{
                    io.execute(barrier);
                    queued=true;
                }catch(RejectedExecutionException busy){
                    if(io.isShutdown())
                        throw new IOException(
                            "R73_IO_DRAIN_DENIED worker shutdown",busy
                        );
                    queued=io.getQueue().offer(barrier);
                }
            }
            if(queued)break;
            long elapsed=System.nanoTime()-started;
            if(elapsed>=budget)
                throw new IOException(
                    "R73_IO_DRAIN_DENIED queue admission timed out"
                );
            try{
                Thread.sleep(1L);
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "R73_IO_DRAIN_DENIED interrupted",interrupted
                );
            }
        }
        long remaining=budget-(System.nanoTime()-started);
        if(remaining<=0L)
            throw new IOException(
                "R73_IO_DRAIN_DENIED completion timed out"
            );
        try{
            reached.get(remaining,TimeUnit.NANOSECONDS);
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(
                "R73_IO_DRAIN_DENIED interrupted",interrupted
            );
        }catch(TimeoutException timedOut){
            throw new IOException(
                "R73_IO_DRAIN_DENIED completion timed out",timedOut
            );
        }catch(ExecutionException impossible){
            throw new IOException(
                "R73_IO_DRAIN_DENIED completion failed",impossible
            );
        }
    }

    boolean offlineTerminalFenceEnabledForTest(){
        return offlineTerminalCaptureFence;
    }

    /**
     * R73 deterministic saturation fixture. This runs only when a test
     * explicitly calls it; no game or LocalSession path references it.
     * Synthetic no-op tasks are distinguished from real save operations.
     */
    void holdOfflineIoWorkerForTest(
        CountDownLatch entered,CountDownLatch release
    ){
        Objects.requireNonNull(entered,"entered");
        Objects.requireNonNull(release,"release");
        io.execute(()->{
            entered.countDown();
            try{
                if(!release.await(20L,TimeUnit.SECONDS))
                    System.err.println(
                        "[r73] OFFLINE_IO_HOLD_TIMED_OUT"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
            }
        });
    }

    void fillOfflineIoQueueWithNoopsForTest(){
        synchronized(io){
            if(io.isShutdown()||
               io.getQueue().size()!=0)
                throw new IllegalStateException(
                    "R73_QUEUE_FIXTURE_DENIED worker not empty"
                );
            for(int i=0;i<MAX_PENDING_WRITES;i++)
                io.execute(()->{});
            if(io.getQueue().size()!=MAX_PENDING_WRITES)
                throw new IllegalStateException(
                    "R73_QUEUE_FIXTURE_DENIED not saturated"
                );
        }
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
                pending.captureOrdinal,
                pending.snapshot,
                pending.provenance,
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
        private final long captureOrdinal;
        private final PlayerSnapshot snapshot;
        private final OfflineCaptureStamp provenance;
        private final int petAccessoryItem;
        private final String tag;
        private final String reason;
        private final CompletableFuture<Void> future;

        SaveTask(
            long saveSequence,
            long captureOrdinal,
            PlayerSnapshot snapshot,
            OfflineCaptureStamp provenance,
            int petAccessoryItem,
            String tag,
            String reason,
            CompletableFuture<Void> future
        ){
            this.saveSequence=saveSequence;
            this.captureOrdinal=captureOrdinal;
            this.snapshot=snapshot;
            this.provenance=provenance;
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
                    captureOrdinal,
                    snapshot,
                    provenance,
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
        long captureOrdinal,
        PlayerSnapshot snapshot,
        OfflineCaptureStamp provenance,
        int petAccessoryItem,
        String tag,
        String reason,
        CompletableFuture<Void> future,
        boolean checkpoint
    ){
        try{
            OfflineSaveAdmission gate=offlineSaveAdmission;
            if(gate==null)
                repository.save(snapshot);
            else if(gate instanceof OfflineStampedSaveAdmission)
                ((OfflineStampedSaveAdmission)gate).withStampedAdmission(
                    captureOrdinal,snapshot,provenance,
                    ()->repository.save(snapshot)
                );
            else if(gate instanceof OfflineOrderedSaveAdmission)
                ((OfflineOrderedSaveAdmission)gate).withOrderedAdmission(
                    captureOrdinal,snapshot,()->repository.save(snapshot)
                );
            else
                gate.withAdmission(snapshot,()->repository.save(snapshot));

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
        synchronized(checkpointLock){
            checkpointSuppressedGenerations.clear();
            offlineTerminalGenerations.clear();
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
                notifyOfflineForcedShutdownForTest();
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

            notifyOfflineForcedShutdownForTest();
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