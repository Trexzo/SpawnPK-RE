package spk.local;

import java.util.*;
import java.util.concurrent.*;
import spk.content.api.ContentProvenance;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.UnknownServerInteractionModule;
import spk.content.builtin.RuntimeProvenNpcInteractionModule;
import spk.event.DomainEventBus;

/** Shared authoritative ownership root. R2 adds membership, one WorldPulse and command execution. */
final class World implements AutoCloseable {
    private static final World SHARED=new World(GameClock.TICK_MILLIS);
    private final GameClock clock=new GameClock();
    private final WorldEventQueue events=new WorldEventQueue();
    private final WorldRealtimeQueue realtime=new WorldRealtimeQueue();
    private final GroundItemRegistry groundItems=new GroundItemRegistry();
    private final WorldObjectRegistry objects=new WorldObjectRegistry();
    private final PlayerRegistry players=new PlayerRegistry();
    private final WorldNpcRegistry npcs=new WorldNpcRegistry();
    private final WorldHomeNpcService homeNpcs=new WorldHomeNpcService(npcs);
    private final WorldPetNpcService petNpcs=new WorldPetNpcService(npcs);
    private final WorldNpcPresentationEvents npcPresentationEvents=new WorldNpcPresentationEvents();
    private final WorldCommandInbox commands=new WorldCommandInbox();
    private final DomainEventBus domainEvents;
    private final LinkedHashMap<EntityId,WorldTickTarget> tickTargets=new LinkedHashMap<>();
    private final WorldPulse pulse;
    private final WorldPlayerPersistence persistence;
    private final ContentRegistry content;
    private final Object loginInitializationLock=new Object();
    private final Object lifecycleLock=new Object();
    private volatile boolean closed;

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
    ContentRegistry content(){return content;}
    Object loginInitializationLock(){return loginInitializationLock;}

    void start(){
        synchronized(lifecycleLock){
            requireOpen();
            pulse.start();
        }
    }

    long registerPlayer(
        WorldPlayer player,
        String username
    ){
        synchronized(lifecycleLock){
            requireOpen();
            return players.register(
                player,
                username
            );
        }
    }

    long registerPlayerAndStart(
        WorldPlayer player,
        String username
    ){
        synchronized(lifecycleLock){
            requireOpen();

            long generation=
                players.register(
                    player,
                    username
                );

            pulse.start();
            return generation;
        }
    }
    boolean unregisterPlayer(WorldPlayer player){
        if(player==null)return false;
        synchronized(player.mutationLock()){
            synchronized(tickTargets){tickTargets.remove(player.id());}
            commands.cancelPlayer(player);
            realtime.cancelPlayer(player);
            petNpcs.removeMainAndMini(player.id());
            return players.unregister(player);
        }
    }

    void attachTickTarget(WorldTickTarget target){
        if(target==null)
            throw new NullPointerException(
                "target"
            );

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
    void detachTickTarget(EntityId id){synchronized(tickTargets){tickTargets.remove(id);}}
    List<WorldTickTarget> tickTargetsSnapshot(){synchronized(tickTargets){return new ArrayList<>(tickTargets.values());}}

    CompletableFuture<Void> submit(WorldPlayer player,WorldCommandInbox.Action action){return commands.submit(player,action);}
    void submitAndWait(WorldPlayer player,WorldCommandInbox.Action action,long timeoutMillis)throws Exception{
        CompletableFuture<Void> f=submit(player,action);
        try{f.get(timeoutMillis,TimeUnit.MILLISECONDS);}catch(ExecutionException e){Throwable c=e.getCause();if(c instanceof Exception)throw (Exception)c;if(c instanceof Error)throw (Error)c;throw new RuntimeException(c);}
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
        return closed;
    }

    private void requireOpen(){
        if(closed)
            throw new IllegalStateException(
                "world closed"
            );
    }

    @Override public void close(){
        synchronized(lifecycleLock){
            if(closed)
                return;

            closed=true;
            pulse.close();
        }

        commands.close();
        realtime.close();
        events.close();
        persistence.close();
    }
}