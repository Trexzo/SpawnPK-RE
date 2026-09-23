package spk.local;

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
    private final WorldHomeNpcService homeNpcs=new WorldHomeNpcService(npcs);
    private final WorldPetNpcService petNpcs=new WorldPetNpcService(npcs);
    private final WorldNpcPresentationEvents npcPresentationEvents=new WorldNpcPresentationEvents();
    private final WorldCommandInbox commands;
    private final DomainEventBus domainEvents;
    private final LinkedHashMap<EntityId,WorldTickTarget> tickTargets=new LinkedHashMap<>();
    private final WorldPulse pulse;
    private final WorldPlayerPersistence persistence;
    private final ContentRegistry content;
    private final WorldPluginManager plugins;
    private final Object loginInitializationLock=new Object();
    private final Object lifecycleLock=new Object();
    private final CountDownLatch closeCompleted=new CountDownLatch(1);
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Throwable closeFailure;

    private World(long tickMillis){
        this(
            tickMillis,
            new FilePlayerRepository()
        );
    }

    private World(
        long tickMillis,
        PlayerRepository repository
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
        pulse=new WorldPulse(this,tickMillis);
        domainEvents=new DomainEventBus(
            () -> pulse.inExecutionContext()
        );
        persistence=
            new WorldPlayerPersistence(
                this,
                repository
            );
        content=
            new ContentRegistry(this);
        content.installTrusted(
            new LocalLabCoreContentModule(),
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
                ()->!closed.get()
            );
    }

    static World shared(){return SHARED;}
    static World isolatedForTest(long tickMillis){return new World(tickMillis);}
    static World isolatedForTest(
        long tickMillis,
        PlayerRepository repository
    ){
        return new World(
            tickMillis,
            repository
        );
    }

    GameClock clock(){return clock;}
    WorldEventQueue events(){return events;}
    WorldRealtimeQueue realtime(){return realtime;}
    GroundItemRegistry groundItems(){return groundItems;}
    WorldObjectRegistry objects(){return objects;}
    PlayerRegistry players(){return players;}
    WorldNpcRegistry npcs(){return npcs;}
    WorldHomeNpcService homeNpcs(){return homeNpcs;}
    WorldPetNpcService petNpcs(){return petNpcs;}
    WorldNpcPresentationEvents npcPresentationEvents(){return npcPresentationEvents;}
    WorldCommandInbox commands(){return commands;}
    DomainEventBus domainEvents(){return domainEvents;}
    WorldPulse pulse(){return pulse;}
    WorldPlayerPersistence persistence(){return persistence;}

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

    interface OwnedPlayerIoAction {
        void run() throws java.io.IOException;
    }

    interface OwnedPlayerAction {
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

            pulse.start();
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

                persistence.releaseCheckpointSuppression(
                    player.id(),
                    expectedGeneration
                );
                commands.cancelPlayer(player);
                realtime.cancelPlayer(player);
                petNpcs.removeMainAndMini(
                    player.id()
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
                    pulse::close,
                    npcPresentationEvents::close,
                    plugins::closeResources,
                    domainEvents::close,
                    commands::close,
                    realtime::close,
                    events::close,
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