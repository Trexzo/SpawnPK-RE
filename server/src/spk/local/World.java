package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import spk.content.api.ContentProvenance;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.UnknownServerInteractionModule;
import spk.content.builtin.RuntimeProvenNpcInteractionModule;
import spk.event.DomainEventBus;
import spk.plugin.api.PluginManager;

/** Shared authoritative ownership root. R2 adds membership, one WorldPulse and command execution. */
final class World implements AutoCloseable {
    private static final World SHARED=new World(GameClock.TICK_MILLIS);
    private final GameClock clock=new GameClock();
    private final WorldEventQueue events=new WorldEventQueue();
    private final GroundItemRegistry groundItems=new GroundItemRegistry();
    private final WorldObjectRegistry objects=new WorldObjectRegistry();
    private final PlayerRegistry players=new PlayerRegistry();
    private final WorldRealtimeQueue realtime;
    private final WorldNpcRegistry npcs=new WorldNpcRegistry();
    private final NpcLifecycleService npcLifecycle=new NpcLifecycleService(npcs);
    private final WorldHomeNpcService homeNpcs=new WorldHomeNpcService(npcs);
    private final WorldPetNpcService petNpcs=new WorldPetNpcService(npcs);
    private final WorldNpcPresentationEvents npcPresentationEvents=new WorldNpcPresentationEvents();
    private final WorldGroundItemPresentationEvents groundItemPresentationEvents=
        new WorldGroundItemPresentationEvents();
    private final PlayerDeathLootLifecycleService deathLootLifecycle;
    private final WorldCommandInbox commands;
    private final DomainEventBus domainEvents;
    private final LinkedHashMap<EntityId,WorldTickTarget> tickTargets=new LinkedHashMap<>();
    private final LinkedHashMap<EntityId,WorldNpcTickTarget> npcTickTargets=new LinkedHashMap<>();
    private final WorldPulse pulse;
    private final WorldPlayerPersistence persistence;
    private final PvpRecordService pvpRecords;
    private final PvmRecordService pvmRecords;
    private final PlayerPrivilegeService playerPrivileges=
        new PlayerPrivilegeService(
            AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
        );
    private final PlayerAppearanceRoleProjection appearanceRoles=
        new PlayerAppearanceRoleProjection(
            AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
        );
    private final LoadoutService loadouts=
        new LoadoutService();
    private final DefaultLoadoutService defaultLoadouts=
        new DefaultLoadoutService(
            loadouts,
            G1DefaultLoadoutRegearService.POLICY_AUTHORITY
        );
    private final LocalLabShopRepository localLabShopRepository;
    private final LocalLabShopRuntime localLabShops;
    private final LocalLabSlayerRuntime localLabSlayer;
    private final ContentRegistry content;
    private final WorldPluginManager plugins;
    private final Object loginInitializationLock=new Object();
    private final Object lifecycleLock=new Object();
    private final CountDownLatch closeCompleted=new CountDownLatch(1);
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Throwable closeFailure;
    private MonsterSpawnerPvmRuntime monsterSpawnerPvmRuntime;

    private World(long tickMillis){
        this(
            tickMillis,
            new FilePlayerRepository(),
            new FileLocalLabShopRepository()
        );
    }

    private World(
        long tickMillis,
        PlayerRepository repository
    ){
        this(
            tickMillis,
            repository,
            new InMemoryLocalLabShopRepository()
        );
    }

    private World(
        long tickMillis,
        PlayerRepository repository,
        LocalLabShopRepository shopRepository
    ){
        this(
            tickMillis,
            repository,
            shopRepository,
            (target,name)->
                new Thread(
                    target,
                    name
                ),
            Thread::start
        );
    }

    private World(
        long tickMillis,
        PlayerRepository repository,
        WorldPulse.PulseThreadFactory pulseThreadFactory,
        WorldPulse.PulseThreadStarter pulseThreadStarter
    ){
        this(
            tickMillis,
            repository,
            new InMemoryLocalLabShopRepository(),
            pulseThreadFactory,
            pulseThreadStarter
        );
    }

    private World(
        long tickMillis,
        PlayerRepository repository,
        LocalLabShopRepository shopRepository,
        WorldPulse.PulseThreadFactory pulseThreadFactory,
        WorldPulse.PulseThreadStarter pulseThreadStarter
    ){
        realtime=
            new WorldRealtimeQueue(
                (player,generation)->
                    players.owns(
                        player,
                        generation
                    ),
                (player,generation,task)->
                    withOpenPlayerMutationOwnershipIfCurrent(
                        player,
                        generation,
                        task::run
                    )
            );
        commands=
            new WorldCommandInbox(
                (player,generation)->
                    players.owns(
                        player,
                        generation
                    ),
                (player,generation,action)->
                    withOpenPlayerMutationOwnershipIfCurrent(
                        player,
                        generation,
                        action::run
                    )
            );
        pulse=
            new WorldPulse(
                this,
                tickMillis,
                pulseThreadFactory,
                pulseThreadStarter
            );
        domainEvents=new DomainEventBus(
            () -> pulse.inExecutionContext()
        );
        persistence=
            new WorldPlayerPersistence(
                this,
                repository
            );
        localLabShopRepository=
            Objects.requireNonNull(
                shopRepository,
                "shopRepository"
            );
        LocalLabShopSnapshot restoredShopState;

        try{
            restoredShopState=
                localLabShopRepository
                    .load()
                    .orElse(null);
        }catch(IOException failure){
            throw new IllegalStateException(
                "failed to load LocalLab Shop world state",
                failure
            );
        }

        localLabShops=
            new LocalLabShopRuntime(
                restoredShopState
            );
        pvpRecords=
            new PvpRecordService(
                this
            );
        deathLootLifecycle=
            new PlayerDeathLootLifecycleService(
                this
            );
        pvmRecords=
            new PvmRecordService(
                this
            );
        localLabSlayer=
            new LocalLabSlayerRuntime(this);
        content=
            new ContentRegistry(this);
        content.installTrusted(
            new LocalLabCoreContentModule(),
            ContentProvenance.CUSTOM_LOCALLAB
        );
        content.installTrusted(
            new LocalDiagnosticContentModule(
                content
            ),
            ContentProvenance.CUSTOM_LOCALLAB
        );
        content.installTrusted(
            new UnknownServerInteractionModule(),
            ContentProvenance.UNKNOWN_SERVER_AUTHORITY
        );
        content.installTrusted(
            new RuntimeProvenNpcInteractionModule(),
            ContentProvenance.LOCAL_RUNTIME_PROVEN
        );
        plugins=
            new WorldPluginManager(
                content,
                domainEvents,
                clock,
                events,
                ()->!closed.get(),
                ()->pulse.inExecutionContext()
            );
    }

    static World shared(){return SHARED;}
    static World isolatedForTest(long tickMillis){
        return new World(
            tickMillis,
            new FilePlayerRepository()
        );
    }
    static World isolatedForTest(
        long tickMillis,
        PlayerRepository repository
    ){
        return new World(
            tickMillis,
            repository
        );
    }

    static World isolatedForTest(
        long tickMillis,
        PlayerRepository repository,
        LocalLabShopRepository shopRepository
    ){
        return new World(
            tickMillis,
            repository,
            shopRepository
        );
    }

    static World isolatedForTest(
        long tickMillis,
        WorldPulse.PulseThreadFactory pulseThreadFactory,
        WorldPulse.PulseThreadStarter pulseThreadStarter
    ){
        return new World(
            tickMillis,
            new FilePlayerRepository(),
            pulseThreadFactory,
            pulseThreadStarter
        );
    }

    GameClock clock(){return clock;}
    WorldEventQueue events(){return events;}
    WorldRealtimeQueue realtime(){return realtime;}
    GroundItemRegistry groundItems(){return groundItems;}
    WorldObjectRegistry objects(){return objects;}
    PlayerRegistry players(){return players;}
    WorldNpcRegistry npcs(){return npcs;}
    NpcLifecycleService npcLifecycle(){return npcLifecycle;}
    WorldHomeNpcService homeNpcs(){return homeNpcs;}
    void installMonsterSpawnerPvmRuntime(
        MonsterSpawnerPvmRuntime runtime
    ){
        MonsterSpawnerPvmRuntime checked=
            Objects.requireNonNull(
                runtime,
                "runtime"
            );

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            if(!checked.isBoundTo(this))
                throw new IllegalArgumentException(
                    "Monster Spawner PvM runtime belongs to another World"
                );

            if(monsterSpawnerPvmRuntime==null){
                monsterSpawnerPvmRuntime=checked;
                return;
            }

            if(monsterSpawnerPvmRuntime!=checked)
                throw new IllegalStateException(
                    "Monster Spawner PvM runtime already installed"
                );
        }
    }

    MonsterSpawnerPvmRuntime.FinalizeResult
        finalizeMonsterSpawnerPvmIfOwned(
            WorldNpc npc
        )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        requireOpen();

        final MonsterSpawnerPvmRuntime runtime;

        synchronized(lifecycleLock){
            requireOpen();
            runtime=monsterSpawnerPvmRuntime;
        }

        if(runtime==null)
            return null;

        /*
         * Do not hold World.lifecycleLock across terminal drop settlement.
         * MonsterSpawnerPvmRuntime's existing combat/finalization services
         * reacquire the exact World lifecycle boundary only where canonical
         * NPC ownership requires it.
         */
        return runtime.finalizeIfOwned(
            checked
        );
    }

    MonsterSpawnerPvmRuntime.FinalizeResult
        retryMonsterSpawnerPvmFinalizationIfPending(
            WorldNpc npc
        )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        requireOpen();

        final MonsterSpawnerPvmRuntime runtime;

        synchronized(lifecycleLock){
            requireOpen();
            runtime=monsterSpawnerPvmRuntime;
        }

        if(runtime==null)
            return null;

        return runtime.retryFinalizationIfPending(
            checked
        );
    }

    int retryMonsterSpawnerPendingSettlements(){
        if(closed.get())
            return 0;

        final MonsterSpawnerPvmRuntime runtime;

        synchronized(lifecycleLock){
            if(closed.get())
                return 0;

            runtime=monsterSpawnerPvmRuntime;
        }

        if(runtime==null)
            return 0;

        /*
         * Do not hold lifecycleLock across ground settlement. The runtime
         * reacquires the exact World-open mutation boundary through runIfOpen.
         */
        return runtime.retryPendingSettlementsOnce();
    }

    WorldPetNpcService petNpcs(){return petNpcs;}
    WorldNpcPresentationEvents npcPresentationEvents(){return npcPresentationEvents;}
    WorldGroundItemPresentationEvents groundItemPresentationEvents(){
        return groundItemPresentationEvents;
    }
    PlayerDeathLootLifecycleService deathLootLifecycle(){
        return deathLootLifecycle;
    }
    WorldCommandInbox commands(){return commands;}
    DomainEventBus domainEvents(){return domainEvents;}
    WorldPulse pulse(){return pulse;}
    WorldPlayerPersistence persistence(){return persistence;}
    PvpRecordService pvpRecords(){return pvpRecords;}
    PvmRecordService pvmRecords(){return pvmRecords;}
    PlayerPrivilegeService playerPrivileges(){return playerPrivileges;}
    PlayerAppearanceRoleProjection appearanceRoles(){return appearanceRoles;}
    LoadoutService loadouts(){return loadouts;}
    DefaultLoadoutService defaultLoadouts(){return defaultLoadouts;}
    LocalLabShopRuntime localLabShops(){return localLabShops;}
    LocalLabSlayerRuntime localLabSlayer(){return localLabSlayer;}
    int appearanceRoleFor(String playerRef){
        return appearanceRoles.project(
            playerPrivileges.snapshot(playerRef)
        );
    }
    int appearanceRoleFor(
        String playerRef,
        PlayerState playerState
    ){
        if(playerState!=null&&
           playerState.hasAppearanceRankOverride())
            return playerState.appearanceRank();
        return appearanceRoleFor(playerRef);
    }

    void scheduleRealtime(
        long atMillis,
        WorldPlayer player,
        long expectedGeneration,
        Runnable task
    ){
        if(player==null||task==null)
            throw new NullPointerException();

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();
            realtime.schedule(
                atMillis,
                player,
                expectedGeneration,
                task
            );
        }
    }
    ContentRegistry content(){return content;}
    PluginManager plugins(){return plugins;}
    Object loginInitializationLock(){return loginInitializationLock;}

    interface ClockEventPublicationAction {
        void run(
            long authoritativeTick
        ) throws Exception;
    }

    /**
     * Linearizes one caller-owned event publication against both logical clock
     * advance and WorldEventQueue due selection/insertion.
     *
     * Lock order is GameClock -> WorldEventQueue. WorldPulse advances the
     * GameClock and releases it before later entering WorldEventQueue.runDue,
     * so the pulse never holds the queue while waiting for the clock.
     */
    void withClockEventPublicationOwnership(
        ClockEventPublicationAction action
    )throws Exception{
        Objects.requireNonNull(
            action,
            "action"
        );

        synchronized(clock){
            synchronized(events){
                action.run(
                    clock.tick()
                );
            }
        }
    }

    interface OwnedPlayerIoAction {
        void run() throws java.io.IOException;
    }

    interface OwnedPlayerAction {
        void run() throws Exception;
    }

    interface OwnedTwoPlayerAction {
        void run() throws Exception;
    }

    interface OpenWorldAction {
        void run() throws Exception;
    }

    boolean withOpenPlayerMutationOwnershipIfCurrent(
        WorldPlayer player,
        long expectedGeneration,
        OwnedPlayerAction action
    )throws Exception{
        if(player==null||action==null)
            throw new NullPointerException();

        if(closed.get())
            return false;

        synchronized(lifecycleLock){
            if(closed.get())
                return false;

            synchronized(player.mutationLock()){
                if(!players.owns(
                        player,
                        expectedGeneration
                    ))
                    return false;

                action.run();
                return true;
            }
        }
    }

    boolean withOpenTwoPlayerOwnershipIfCurrent(
        WorldPlayer firstPlayer,
        long firstGeneration,
        WorldPlayer secondPlayer,
        long secondGeneration,
        OwnedTwoPlayerAction action
    )throws Exception{
        if(firstPlayer==null||
           secondPlayer==null||
           action==null)
            throw new NullPointerException();

        if(firstPlayer==secondPlayer)
            throw new IllegalArgumentException(
                "two-player ownership requires distinct players"
            );

        if(closed.get())
            return false;

        synchronized(lifecycleLock){
            if(closed.get())
                return false;

            WorldPlayer lockFirst=
                firstPlayer.id().value<
                    secondPlayer.id().value
                    ?firstPlayer
                    :secondPlayer;
            WorldPlayer lockSecond=
                lockFirst==firstPlayer
                    ?secondPlayer
                    :firstPlayer;

            synchronized(lockFirst.mutationLock()){
                synchronized(lockSecond.mutationLock()){
                    if(!players.owns(
                            firstPlayer,
                            firstGeneration
                        )||
                       !players.owns(
                            secondPlayer,
                            secondGeneration
                        ))
                        return false;
                }
            }

            /*
             * Keep lifecycle ownership across the action, but do not retain
             * either player mutation lock. Registration/unregistration also
             * requires lifecycle ownership, so the validated generations
             * cannot change while the action runs. Trade final commit no
             * longer acquires participant mutation locks; its generation fence
             * is serialized under TradeService and the World PlayerRegistry.
             */
            action.run();
            return true;
        }
    }

    boolean runIfOpen(Runnable action){
        if(action==null)
            throw new NullPointerException(
                "action"
            );

        if(closed.get())
            return false;

        synchronized(lifecycleLock){
            if(closed.get())
                return false;

            action.run();
            return true;
        }
    }

    boolean withOpenLifecycleOwnership(
        OpenWorldAction action
    )throws Exception{
        Objects.requireNonNull(
            action,
            "action"
        );

        if(closed.get())
            return false;

        synchronized(lifecycleLock){
            if(closed.get())
                return false;

            action.run();
            return true;
        }
    }

    void withOpenPlayerOwnership(
        WorldPlayer player,
        long expectedGeneration,
        OwnedPlayerIoAction action
    )throws java.io.IOException{
        if(player==null||action==null)
            throw new NullPointerException();

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            synchronized(player.mutationLock()){
                if(!players.owns(
                        player,
                        expectedGeneration
                    ))
                    throw new IllegalStateException(
                        "player owner not registered in world: "+
                        player.id()
                    );
            }

            action.run();
        }
    }

    boolean withOpenPlayerOwnershipIfCurrent(
        WorldPlayer player,
        long expectedGeneration,
        OwnedPlayerIoAction action
    )throws java.io.IOException{
        if(player==null||action==null)
            throw new NullPointerException();

        if(closed.get())
            return false;

        synchronized(lifecycleLock){
            if(closed.get())
                return false;

            synchronized(player.mutationLock()){
                if(!players.owns(
                        player,
                        expectedGeneration
                    ))
                    return false;
            }

            action.run();
            return true;
        }
    }

    void start(){
        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();
            pulse.start();
        }
    }

    long registerPlayer(
        WorldPlayer player,
        String username
    ){
        if(player==null)
            throw new NullPointerException(
                "player"
            );

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            synchronized(player.mutationLock()){
                return players.register(
                    player,
                    username
                );
            }
        }
    }

    long registerPlayerAndStart(
        WorldPlayer player,
        String username
    ){
        if(player==null)
            throw new NullPointerException(
                "player"
            );

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            long generation;

            synchronized(player.mutationLock()){
                generation=
                    players.register(
                        player,
                        username
                    );
            }

            try{
                pulse.start();
            }catch(Throwable failure){
                synchronized(player.mutationLock()){
                    if(!players.unregister(
                            player,
                            generation))
                        failure.addSuppressed(
                            new IllegalStateException(
                                "failed World admission could not roll back player generation "+
                                generation
                            )
                        );
                }

                rethrowUnchecked(
                    failure
                );
            }

            return generation;
        }
    }
    boolean unregisterPlayer(WorldPlayer player){
        if(player==null)return false;
        return unregisterPlayer(
            player,
            player.generation()
        );
    }

    boolean unregisterPlayer(
        WorldPlayer player,
        long expectedGeneration
    ){
        if(player==null)return false;

        synchronized(lifecycleLock){
            synchronized(player.mutationLock()){
                if(!players.owns(
                        player,
                        expectedGeneration
                    ))
                    return false;

                synchronized(tickTargets){
                    WorldTickTarget target=
                        tickTargets.get(
                            player.id()
                        );

                    if(target!=null &&
                       target.ownerGeneration()==
                           expectedGeneration)
                        tickTargets.remove(
                            player.id()
                        );
                }

                if(!players.unregister(
                        player,
                        expectedGeneration
                    ))
                    return false;

                WorldPlayerUnregisterCleanup.run(
                    ()->persistence
                        .releaseCheckpointSuppression(
                            player.id(),
                            expectedGeneration
                        ),
                    ()->commands.cancelPlayer(
                        player
                    ),
                    ()->realtime.cancelPlayer(
                        player
                    ),
                    ()->petNpcs.removeMainAndMini(
                        player.id()
                    )
                );

                return true;
            }
        }
    }

    void attachTickTarget(WorldTickTarget target){
        if(target==null)
            throw new NullPointerException(
                "target"
            );

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            WorldPlayer p=
                players.byId(
                    target.ownerId()
                );

            if(p==null||
               !p.accepts(
                   target.ownerGeneration()))
                throw new IllegalStateException(
                    "tick target owner not registered: "+
                    target.ownerId()
                );

            synchronized(tickTargets){
                tickTargets.put(
                    target.ownerId(),
                    target
                );
            }
        }
    }
    void detachTickTarget(EntityId id){
        synchronized(tickTargets){
            tickTargets.remove(id);
        }
    }

    boolean detachTickTarget(
        EntityId id,
        long expectedGeneration
    ){
        synchronized(tickTargets){
            WorldTickTarget target=
                tickTargets.get(id);

            if(target==null ||
               target.ownerGeneration()!=
                   expectedGeneration)
                return false;

            tickTargets.remove(id);
            return true;
        }
    }
    List<WorldTickTarget> tickTargetsSnapshot(){synchronized(tickTargets){return new ArrayList<>(tickTargets.values());}}

    void attachNpcTickTarget(
        WorldNpc npc,
        WorldNpcTickTarget target
    )throws Exception{
        WorldNpc checkedNpc=
            Objects.requireNonNull(
                npc,
                "npc"
            );
        WorldNpcTickTarget checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(!checkedNpc.id.equals(
                checkedTarget.npcId()))
            throw new IllegalArgumentException(
                "NPC tick target id mismatch npc="+
                checkedNpc.id+
                " target="+
                checkedTarget.npcId()
            );

        requireOpen();

        synchronized(lifecycleLock){
            requireOpen();

            boolean current=
                npcs.withCurrentMutationOwnershipIfCurrent(
                    checkedNpc,
                    ()->{
                        synchronized(npcTickTargets){
                            if(npcTickTargets.containsKey(
                                    checkedNpc.id))
                                throw new IllegalStateException(
                                    "NPC tick target already attached id="+
                                    checkedNpc.id
                                );

                            npcTickTargets.put(
                                checkedNpc.id,
                                checkedTarget
                            );
                        }
                    }
                );

            if(!current)
                throw new IllegalStateException(
                    "NPC tick target owner is not canonical id="+
                    checkedNpc.id
                );
        }
    }

    boolean detachNpcTickTarget(
        EntityId npcId,
        WorldNpcTickTarget expected
    ){
        EntityId checkedId=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );
        WorldNpcTickTarget checkedExpected=
            Objects.requireNonNull(
                expected,
                "expected"
            );

        synchronized(npcTickTargets){
            return npcTickTargets.remove(
                checkedId,
                checkedExpected
            );
        }
    }

    List<WorldNpcTickTarget>
        npcTickTargetsSnapshot(){
        synchronized(npcTickTargets){
            return new ArrayList<>(
                npcTickTargets.values()
            );
        }
    }

    int npcTickTargetCount(){
        synchronized(npcTickTargets){
            return npcTickTargets.size();
        }
    }

    boolean pruneNpcTickTargetIfNpcMissing(
        EntityId npcId
    ){
        EntityId checked=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );

        synchronized(lifecycleLock){
            if(npcs.byId(
                    checked
                )!=null)
                return false;

            synchronized(npcTickTargets){
                return npcTickTargets.remove(
                    checked
                )!=null;
            }
        }
    }

    CompletableFuture<Void> submit(
        WorldPlayer player,
        WorldCommandInbox.Action action
    ){
        if(player==null||action==null)
            throw new NullPointerException();

        if(closed.get())
            return rejectedCommandSubmission();

        synchronized(lifecycleLock){
            if(closed.get())
                return rejectedCommandSubmission();

            return commands.submit(
                player,
                action
            );
        }
    }

    CompletableFuture<Void> submit(
        WorldPlayer player,
        long expectedGeneration,
        WorldCommandInbox.Action action
    ){
        if(player==null||action==null)
            throw new NullPointerException();

        if(closed.get())
            return rejectedCommandSubmission();

        synchronized(lifecycleLock){
            if(closed.get())
                return rejectedCommandSubmission();

            return commands.submit(
                player,
                expectedGeneration,
                action
            );
        }
    }

    private static void rethrowUnchecked(
        Throwable failure
    ){
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }

    private static CompletableFuture<Void>
        rejectedCommandSubmission(){
        CompletableFuture<Void> future=
            new CompletableFuture<>();

        future.completeExceptionally(
            new RejectedExecutionException(
                "WORLD_CLOSED"
            )
        );

        return future;
    }

    void submitAndWait(
        WorldPlayer player,
        WorldCommandInbox.Action action,
        long timeoutMillis
    )throws Exception{
        CompletableFuture<Void> f=
            submit(
                player,
                action
            );
        awaitCommand(
            f,
            timeoutMillis
        );
    }

    void submitAndWait(
        WorldPlayer player,
        long expectedGeneration,
        WorldCommandInbox.Action action,
        long timeoutMillis
    )throws Exception{
        CompletableFuture<Void> f=
            submit(
                player,
                expectedGeneration,
                action
            );
        awaitCommand(
            f,
            timeoutMillis
        );
    }

    private static void awaitCommand(
        CompletableFuture<Void> future,
        long timeoutMillis
    )throws Exception{
        try{
            future.get(
                timeoutMillis,
                TimeUnit.MILLISECONDS
            );
        }catch(ExecutionException error){
            Throwable cause=error.getCause();

            if(cause instanceof Exception)
                throw (Exception)cause;

            if(cause instanceof Error)
                throw (Error)cause;

            throw new RuntimeException(cause);
        }
    }

    /** Compatibility hook for older tests/tools; the real server uses WorldPulse.start(). */
    synchronized long observePulse(long nowMillis){
        synchronized(lifecycleLock){
            requireOpen();
            if(!pulse.running())
                pulse.pulseOnce(nowMillis);
            return clock.tick();
        }
    }

    String summary(){return "World{tick="+clock.tick()+",players="+players.size()+",groundItems="+groundItems.size()+",objects="+objects.size()+",commands="+commands.size()+",scheduled="+events.size()+",pulseRunning="+pulse.running()+"}";}
    String metrics(){
        return pulse.metrics()+
            " "+
            persistence.metrics()+
            " "+
            content.summary();
    }

    boolean closed(){
        return closed.get();
    }

    private void requireOpen(){
        if(closed.get())
            throw new IllegalStateException(
                "world closed"
            );
    }

    @Override public void close(){
        boolean owner=
            closed.compareAndSet(
                false,
                true
            );

        if(!owner){
            awaitCloseCompleted();
            WorldCloseSequence.rethrow(
                closeFailure
            );
            return;
        }

        // The atomic flag fences new work immediately. Then wait for any
        // lifecycle-owned action that was already in flight to leave before
        // terminal resources are torn down.
        synchronized(lifecycleLock){
            // quiescence barrier only
        }

        Throwable failure=null;

        try{
            failure=
                WorldCloseSequence.run(
                    plugins::beginClose,
                    ()->pulse.closeWithTerminal(
                        plugins::closeResources
                    ),
                    ()->SharedNpcWorldRelay.closeWorld(
                        this
                    ),
                    ()->Player81WorldSync.closeWorld(
                        this
                    ),
                    ()->TradeService.closeWorld(
                        this
                    ),
                    npcPresentationEvents::close,
                    deathLootLifecycle::close,
                    groundItemPresentationEvents::close,
                    domainEvents::close,
                    commands::close,
                    realtime::close,
                    events::close,
                    this::saveLocalLabShopState,
                    persistence::close
                );

            closeFailure=failure;
        }finally{
            closeCompleted.countDown();
        }

        WorldCloseSequence.rethrow(
            failure
        );
    }

    private void saveLocalLabShopState(){
        try{
            localLabShopRepository.save(
                localLabShops.snapshot()
            );
        }catch(IOException failure){
            throw new IllegalStateException(
                "failed to save LocalLab Shop world state",
                failure
            );
        }
    }

    private void awaitCloseCompleted(){
        boolean interrupted=false;

        for(;;){
            try{
                closeCompleted.await();
                break;
            }catch(InterruptedException error){
                interrupted=true;
            }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }
}