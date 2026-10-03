package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Canonical unowned-NPC death finalization and caller-defined respawn schedule.
 *
 * Death consumers run before scheduleDead(...). This service owns only exact
 * dead-NPC retirement and later fresh canonical spawn/lifecycle registration.
 */
final class NpcRespawnService {
    enum State {
        SCHEDULED,
        RESPAWNED,
        CANCELLED
    }

    @FunctionalInterface
    interface FreshLifecycleRegistrar {
        NpcLifecycleService.Snapshot register(
            WorldNpc npc,
            int maxHitpoints,
            String sourceAuthority
        );
    }

    static final class TicketId
        implements Comparable<TicketId> {

        private final long value;

        TicketId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "ticketId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(
            TicketId other
        ){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof TicketId&&
                value==
                    ((TicketId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "npc-respawn-"+
                Long.toUnsignedString(value);
        }
    }

    static final class TicketSnapshot {
        final TicketId ticketId;
        final EntityId oldNpcId;
        final EntityId newNpcId;
        final int definitionId;
        final int x;
        final int y;
        final int plane;
        final int maxHitpoints;
        final long deathTick;
        final long dueTick;
        final State state;
        final String hitpointAuthority;
        final String respawnPolicyAuthority;

        TicketSnapshot(Ticket ticket){
            this.ticketId=ticket.id;
            this.oldNpcId=ticket.oldNpcId;
            this.newNpcId=ticket.newNpcId;
            this.definitionId=ticket.definitionId;
            this.x=ticket.x;
            this.y=ticket.y;
            this.plane=ticket.plane;
            this.maxHitpoints=
                ticket.maxHitpoints;
            this.deathTick=
                ticket.deathTick;
            this.dueTick=ticket.dueTick;
            this.state=ticket.state;
            this.hitpointAuthority=
                ticket.hitpointAuthority;
            this.respawnPolicyAuthority=
                ticket.respawnPolicyAuthority;
        }

        boolean terminal(){
            return state==State.RESPAWNED||
                state==State.CANCELLED;
        }
    }

    static final class RespawnFact {
        final TicketId ticketId;
        final EntityId oldNpcId;
        final EntityId newNpcId;
        final int definitionId;
        final Tile tile;
        final long worldTick;
        final String respawnPolicyAuthority;

        RespawnFact(
            Ticket ticket,
            WorldNpc npc,
            long worldTick
        ){
            this.ticketId=ticket.id;
            this.oldNpcId=ticket.oldNpcId;
            this.newNpcId=npc.id;
            this.definitionId=
                npc.definitionId;
            this.tile=npc.tile();
            this.worldTick=worldTick;
            this.respawnPolicyAuthority=
                ticket.respawnPolicyAuthority;
        }
    }

    static final class TickResult {
        final long worldTick;
        final List<RespawnFact> respawned;
        final List<TicketId> failedTickets;

        TickResult(
            long worldTick,
            List<RespawnFact> respawned,
            List<TicketId> failedTickets
        ){
            this.worldTick=worldTick;
            this.respawned=
                Collections.unmodifiableList(
                    new ArrayList<>(respawned)
                );
            this.failedTickets=
                Collections.unmodifiableList(
                    new ArrayList<>(failedTickets)
                );
        }
    }

    private static final class Ticket {
        final TicketId id;
        final EntityId oldNpcId;
        final int definitionId;
        final int x;
        final int y;
        final int plane;
        final int maxHitpoints;
        final long deathTick;
        final long dueTick;
        final String hitpointAuthority;
        final String respawnPolicyAuthority;

        State state=State.SCHEDULED;
        EntityId newNpcId;

        Ticket(
            TicketId id,
            WorldNpc npc,
            Tile tile,
            NpcLifecycleService.Snapshot lifecycle,
            long dueTick,
            String respawnPolicyAuthority
        ){
            this.id=id;
            this.oldNpcId=npc.id;
            this.definitionId=npc.definitionId;
            this.x=tile.x;
            this.y=tile.y;
            this.plane=tile.plane;
            this.maxHitpoints=
                lifecycle.maxHitpoints;
            this.deathTick=
                lifecycle.deathTick;
            this.dueTick=dueTick;
            this.hitpointAuthority=
                lifecycle.sourceAuthority;
            this.respawnPolicyAuthority=
                respawnPolicyAuthority;
        }

        TicketSnapshot snapshot(){
            return new TicketSnapshot(this);
        }
    }

    private final WorldNpcRegistry npcs;
    private final NpcLifecycleService lifecycle;
    private final FreshLifecycleRegistrar registrar;
    private final AtomicLong ticketSequence=
        new AtomicLong();
    private final LinkedHashMap<TicketId,Ticket>
        tickets=
            new LinkedHashMap<>();
    private long lastObservedTick=-1L;

    NpcRespawnService(
        WorldNpcRegistry npcs,
        NpcLifecycleService lifecycle
    ){
        this(
            npcs,
            lifecycle,
            lifecycle::register
        );
    }

    NpcRespawnService(
        WorldNpcRegistry npcs,
        NpcLifecycleService lifecycle,
        FreshLifecycleRegistrar registrar
    ){
        this.npcs=
            Objects.requireNonNull(
                npcs,
                "npcs"
            );
        this.lifecycle=
            Objects.requireNonNull(
                lifecycle,
                "lifecycle"
            );
        this.registrar=
            Objects.requireNonNull(
                registrar,
                "registrar"
            );
    }

    boolean isBoundTo(
        WorldNpcRegistry expectedNpcs,
        NpcLifecycleService expectedLifecycle
    ){
        return npcs==expectedNpcs&&
            lifecycle==expectedLifecycle;
    }

    synchronized TicketSnapshot scheduleDead(
        WorldNpc npc,
        long respawnDelayTicks,
        String respawnPolicyAuthority
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        if(respawnDelayTicks<0L)
            throw new IllegalArgumentException(
                "respawnDelayTicks="+
                respawnDelayTicks
            );

        if(checked.owned())
            throw new IllegalArgumentException(
                "owned NPC respawn requires separate policy id="+
                checked.id
            );

        String authority=
            requireAuthority(
                respawnPolicyAuthority
            );

        Ticket[] scheduled=
            new Ticket[1];

        boolean current=
            withCurrentNpc(
                checked,
                ()->{
                    NpcLifecycleService.Snapshot state=
                        lifecycle.get(
                            checked.id
                        );

                    if(state==null)
                        throw new IllegalStateException(
                            "NPC lifecycle missing id="+
                            checked.id
                        );

                    if(!state.dead()||
                       !state.hasDeathTick())
                        throw new IllegalStateException(
                            "NPC is not dead id="+
                            checked.id
                        );

                    long dueTick;

                    try{
                        dueTick=
                            Math.addExact(
                                state.deathTick,
                                respawnDelayTicks
                            );
                    }catch(
                        ArithmeticException overflow
                    ){
                        throw new IllegalArgumentException(
                            "respawn due tick overflow death="+
                            state.deathTick+
                            " delay="+
                            respawnDelayTicks,
                            overflow
                        );
                    }

                    Tile tile=
                        checked.tile();

                    TicketId ticketId=
                        nextTicketId();

                    Ticket ticket=
                        new Ticket(
                            ticketId,
                            checked,
                            tile,
                            state,
                            dueTick,
                            authority
                        );

                    lifecycle.retireDeadCanonical(
                        checked
                    );

                    tickets.put(
                        ticketId,
                        ticket
                    );
                    scheduled[0]=ticket;
                }
            );

        if(!current)
            throw new IllegalStateException(
                "NPC is not canonical registry entity id="+
                checked.id
            );

        return scheduled[0].snapshot();
    }

    synchronized TickResult tick(
        long worldTick
    ){
        observeTick(
            worldTick
        );

        ArrayList<Ticket> due=
            new ArrayList<>();

        for(Ticket ticket:
                tickets.values())
            if(ticket.state==
                    State.SCHEDULED&&
               ticket.dueTick<=worldTick)
                due.add(ticket);

        due.sort(
            Comparator
                .comparingLong(
                    (Ticket ticket)->
                        ticket.dueTick
                )
                .thenComparing(
                    ticket->ticket.id
                )
        );

        ArrayList<RespawnFact> respawned=
            new ArrayList<>();
        ArrayList<TicketId> failed=
            new ArrayList<>();

        for(Ticket ticket:due){
            try{
                WorldNpc fresh=
                    npcs.spawnWithMutationOwnership(
                        ticket.definitionId,
                        ticket.x,
                        ticket.y,
                        ticket.plane,
                        npc->{
                            try{
                                registrar.register(
                                    npc,
                                    ticket.maxHitpoints,
                                    ticket.hitpointAuthority
                                );
                            }catch(
                                RuntimeException failure
                            ){
                                lifecycle.unregister(
                                    npc.id
                                );
                                throw failure;
                            }catch(
                                Error failure
                            ){
                                lifecycle.unregister(
                                    npc.id
                                );
                                throw failure;
                            }
                        }
                    );

                ticket.newNpcId=fresh.id;
                ticket.state=State.RESPAWNED;

                respawned.add(
                    new RespawnFact(
                        ticket,
                        fresh,
                        worldTick
                    )
                );
            }catch(
                Exception failure
            ){
                failed.add(
                    ticket.id
                );
            }
        }

        return new TickResult(
            worldTick,
            respawned,
            failed
        );
    }

    synchronized TicketSnapshot cancel(
        TicketId ticketId
    ){
        Ticket ticket=
            require(
                ticketId
            );

        if(ticket.state==State.CANCELLED)
            return ticket.snapshot();

        if(ticket.state==State.RESPAWNED)
            throw new IllegalStateException(
                "cannot cancel respawned ticket "+
                ticket.id
            );

        ticket.state=State.CANCELLED;
        return ticket.snapshot();
    }

    synchronized TicketSnapshot get(
        TicketId ticketId
    ){
        Ticket ticket=
            tickets.get(
                Objects.requireNonNull(
                    ticketId,
                    "ticketId"
                )
            );

        return ticket==null
            ?null
            :ticket.snapshot();
    }

    synchronized int size(){
        return tickets.size();
    }

    synchronized long lastObservedTick(){
        return lastObservedTick;
    }

    private boolean withCurrentNpc(
        WorldNpc npc,
        WorldNpcRegistry.OwnedNpcAction action
    ){
        try{
            return npcs
                .withCurrentMutationOwnershipIfCurrent(
                    npc,
                    action
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected checked NPC respawn ownership failure",
                failure
            );
        }
    }

    private Ticket require(
        TicketId ticketId
    ){
        TicketId id=
            Objects.requireNonNull(
                ticketId,
                "ticketId"
            );

        Ticket ticket=
            tickets.get(
                id
            );

        if(ticket==null)
            throw new IllegalArgumentException(
                "unknown NPC respawn ticket "+
                id
            );

        return ticket;
    }

    private void observeTick(
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        if(worldTick<lastObservedTick)
            throw new IllegalArgumentException(
                "world tick moved backwards "+
                worldTick+" < "+
                lastObservedTick
            );

        lastObservedTick=worldTick;
    }

    private TicketId nextTicketId(){
        long value=
            ticketSequence.incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "NPC respawn ticket sequence exhausted"
            );

        return new TicketId(
            value
        );
    }

    private static String requireAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "respawnPolicyAuthority"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "respawnPolicyAuthority blank"
            );

        return normalized;
    }
}
