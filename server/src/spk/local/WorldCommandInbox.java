package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded deterministic command inbox. Commands are executed only by WorldPulse. */
final class WorldCommandInbox {
    interface Action { void run() throws Exception; }
    interface Ownership {
        boolean owns(
            WorldPlayer player,
            long generation
        );
    }
    private static final int DEFAULT_PER_PLAYER_LIMIT=64;
    private static final int DEFAULT_GLOBAL_LIMIT=2048;
    private final AtomicLong sequence=new AtomicLong();
    private final ArrayDeque<C> queue=new ArrayDeque<>();
    private final HashMap<EntityId,Integer> queuedPerPlayer=new HashMap<>();
    private final int perPlayerLimit;
    private final int globalLimit;
    private final Ownership ownership;
    private boolean closed;

    private static final class C {
        final long seq;
        final WorldPlayer player;
        final long generation;
        final Action action;
        final CompletableFuture<Void> future;
        C(long seq,WorldPlayer p,long g,Action a,CompletableFuture<Void> f){this.seq=seq;player=p;generation=g;action=a;future=f;}
    }

    WorldCommandInbox(){
        this(
            DEFAULT_PER_PLAYER_LIMIT,
            DEFAULT_GLOBAL_LIMIT,
            (player,generation)->
                player.accepts(generation)
        );
    }

    WorldCommandInbox(
        int perPlayerLimit,
        int globalLimit
    ){
        this(
            perPlayerLimit,
            globalLimit,
            (player,generation)->
                player.accepts(generation)
        );
    }

    WorldCommandInbox(Ownership ownership){
        this(
            DEFAULT_PER_PLAYER_LIMIT,
            DEFAULT_GLOBAL_LIMIT,
            ownership
        );
    }

    WorldCommandInbox(
        int perPlayerLimit,
        int globalLimit,
        Ownership ownership
    ){
        if(perPlayerLimit<1||globalLimit<1)
            throw new IllegalArgumentException();
        this.perPlayerLimit=perPlayerLimit;
        this.globalLimit=globalLimit;
        this.ownership=
            Objects.requireNonNull(
                ownership,
                "ownership"
            );
    }

    synchronized CompletableFuture<Void> submit(
        WorldPlayer player,
        Action action
    ){
        if(player==null||action==null)
            throw new NullPointerException();

        return submit(
            player,
            player.generation(),
            action
        );
    }

    synchronized CompletableFuture<Void> submit(
        WorldPlayer player,
        long expectedGeneration,
        Action action
    ){
        if(player==null||action==null)
            throw new NullPointerException();

        CompletableFuture<Void> f=
            new CompletableFuture<>();

        if(closed){
            f.completeExceptionally(
                new RejectedExecutionException(
                    "WORLD_COMMAND_INBOX_CLOSED"
                )
            );
            return f;
        }

        if(!player.accepts(
                expectedGeneration
            )){
            f.completeExceptionally(
                new CancellationException(
                    "PLAYER_LIFECYCLE_CHANGED"
                )
            );
            return f;
        }

        if(!ownership.owns(
                player,
                expectedGeneration
            )){
            f.completeExceptionally(
                new CancellationException(
                    "PLAYER_NOT_OWNED_BY_WORLD"
                )
            );
            return f;
        }

        int count=
            queuedPerPlayer.getOrDefault(
                player.id(),
                0
            );

        if(queue.size()>=globalLimit||
           count>=perPlayerLimit){
            f.completeExceptionally(
                new RejectedExecutionException(
                    "WORLD_COMMAND_QUEUE_FULL player="+
                    player.id()
                )
            );
            return f;
        }

        queue.addLast(
            new C(
                sequence.incrementAndGet(),
                player,
                expectedGeneration,
                action,
                f
            )
        );
        queuedPerPlayer.put(
            player.id(),
            count+1
        );
        notifyAll();
        return f;
    }

    int drain(int maxTotal,int maxPerPlayer){
        if(maxTotal<1||maxPerPlayer<1)return 0;
        ArrayList<C> run=new ArrayList<>();
        HashMap<EntityId,Integer> used=new HashMap<>();
        synchronized(this){
            if(closed)return 0;
            int scan=queue.size();
            while(scan-->0 && run.size()<maxTotal){
                C c=queue.removeFirst();
                int u=used.getOrDefault(c.player.id(),0);
                if(u>=maxPerPlayer){queue.addLast(c);continue;}
                decrement(c.player.id());
                used.put(c.player.id(),u+1);
                run.add(c);
            }
        }
        for(C c:run){
            if(closed()){
                c.future.completeExceptionally(new CancellationException("WORLD_COMMAND_INBOX_CLOSED"));
                continue;
            }
            if(!c.player.accepts(c.generation)||
               !ownership.owns(
                   c.player,
                   c.generation
               )){
                c.future.completeExceptionally(
                    new CancellationException(
                        "PLAYER_LIFECYCLE_CHANGED"
                    )
                );
                continue;
            }
            try{
                synchronized(c.player.mutationLock()){
                    if(!c.player.accepts(
                            c.generation
                        )||
                       !ownership.owns(
                            c.player,
                            c.generation
                        ))
                        throw new CancellationException(
                            "PLAYER_LIFECYCLE_CHANGED"
                        );
                    c.action.run();
                }
                c.future.complete(null);
            }catch(Throwable t){
                c.future.completeExceptionally(t);
            }
        }
        return run.size();
    }

    synchronized int cancelPlayer(WorldPlayer player){
        if(player==null)return 0;
        int n=0;
        for(Iterator<C> it=queue.iterator();it.hasNext();){
            C c=it.next();
            if(c.player.id().equals(player.id())){it.remove();decrement(c.player.id());c.future.completeExceptionally(new CancellationException("PLAYER_UNREGISTERED"));n++;}
        }
        return n;
    }

    int close(){
        ArrayList<C> dropped;
        synchronized(this){
            if(closed)return 0;
            closed=true;
            dropped=new ArrayList<>(queue);
            queue.clear();
            queuedPerPlayer.clear();
        }
        for(C c:dropped)
            c.future.completeExceptionally(new CancellationException("WORLD_COMMAND_INBOX_CLOSED"));
        return dropped.size();
    }

    synchronized int size(){return queue.size();}
    synchronized int queuedFor(EntityId id){return queuedPerPlayer.getOrDefault(id,0);}
    synchronized boolean closed(){return closed;}

    private void decrement(EntityId id){int n=queuedPerPlayer.getOrDefault(id,0)-1;if(n<=0)queuedPerPlayer.remove(id);else queuedPerPlayer.put(id,n);}
}