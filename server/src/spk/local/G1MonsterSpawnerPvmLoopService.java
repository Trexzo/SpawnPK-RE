package spk.local;

import java.util.*;

final class G1MonsterSpawnerPvmLoopService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_PVM_LOOP_V1";
    static final long RESPAWN_DELAY_TICKS=5L;
    static final int RESPAWN_BUDGET=1;

    enum State {
        SCHEDULED,
        RESPAWNED,
        CANCELLED
    }

    static final class TicketSnapshot {
        final long id;
        final String ownerRef;
        final String recipientRef;
        final EntityId deadNpcId;
        final int definitionId;
        final long deathTick;
        final long dueTick;
        final State state;
        final EntityId respawnedNpcId;

        private TicketSnapshot(
            Ticket ticket
        ){
            this.id=ticket.id;
            this.ownerRef=ticket.ownerRef;
            this.recipientRef=ticket.recipientRef;
            this.deadNpcId=ticket.deadNpcId;
            this.definitionId=ticket.definitionId;
            this.deathTick=ticket.deathTick;
            this.dueTick=ticket.dueTick;
            this.state=ticket.state;
            this.respawnedNpcId=
                ticket.respawnedNpcId;
        }
    }

    private static final class Ticket {
        final long id;
        final String ownerRef;
        final String recipientRef;
        final EntityId deadNpcId;
        final int definitionId;
        final long deathTick;
        final long dueTick;
        final MonsterSpawnerService.SessionSnapshot
            expectedSession;
        State state=State.SCHEDULED;
        EntityId respawnedNpcId;
        WorldEventQueue.Handle handle;

        Ticket(
            long id,
            String ownerRef,
            String recipientRef,
            EntityId deadNpcId,
            int definitionId,
            long deathTick,
            long dueTick,
            MonsterSpawnerService.SessionSnapshot
                expectedSession
        ){
            this.id=id;
            this.ownerRef=ownerRef;
            this.recipientRef=recipientRef;
            this.deadNpcId=deadNpcId;
            this.definitionId=definitionId;
            this.deathTick=deathTick;
            this.dueTick=dueTick;
            this.expectedSession=expectedSession;
        }

        TicketSnapshot snapshot(){
            return new TicketSnapshot(this);
        }
    }

    private final World world;
    private final MonsterSpawnerService spawner;
    private final MonsterSpawnerPvmSpawnExecutor executor;
    private final PvmRecordService records;

    private final LinkedHashMap<Long,Ticket>
        tickets=new LinkedHashMap<>();
    private final LinkedHashMap<String,Ticket>
        pendingByOwner=new LinkedHashMap<>();
    private long sequence;

    G1MonsterSpawnerPvmLoopService(
        World world,
        MonsterSpawnerService spawner,
        MonsterSpawnerPvmSpawnExecutor executor,
        PvmRecordService records
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.spawner=
            Objects.requireNonNull(
                spawner,
                "spawner"
            );
        this.executor=
            Objects.requireNonNull(
                executor,
                "executor"
            );
        this.records=
            Objects.requireNonNull(
                records,
                "records"
            );
    }

    void onFinalized(
        MonsterSpawnerPvmRuntime.FinalizeResult
            result
    ){
        MonsterSpawnerPvmRuntime.FinalizeResult
            terminal=
                Objects.requireNonNull(
                    result,
                    "result"
                );

        if(terminal.status!=
                MonsterSpawnerPvmRuntime
                    .FinalizeStatus.FINALIZED)
            return;

        MonsterSpawnerNpcDeathFinalizationService.Result
            finalization=
                Objects.requireNonNull(
                    terminal.finalization,
                    "finalization"
                );

        if(terminal.settlement==null)
            throw new IllegalStateException(
                "G1 PvM terminal finalization missing ground settlement"
            );

        MonsterSpawnerService.SessionSnapshot
            session=
                Objects.requireNonNull(
                    finalization.teardown.session,
                    "post-teardown session"
                );

        if(!session.spawnedNpcIds.isEmpty())
            throw new IllegalStateException(
                "G1 PvM terminal session still tracks NPCs owner="+
                session.ownerRef
            );

        WorldPlayer recipient=
            world.players().byName(
                finalization.recipientRef
            );

        if(recipient!=null){
            long generation=
                recipient.generation();

            if(world.players().owns(
                    recipient,
                    generation))
                records.recordIfCurrent(
                    recipient,
                    generation,
                    finalization.npcId,
                    finalization.deathTick
                );
        }

        WorldPlayer owner=
            world.players().byName(
                session.ownerRef
            );

        if(owner==null||
           !world.players().owns(
                owner,
                owner.generation()))
            return;

        schedule(
            session,
            finalization
        );
    }

    private synchronized void schedule(
        MonsterSpawnerService.SessionSnapshot
            session,
        MonsterSpawnerNpcDeathFinalizationService.Result
            finalization
    ){
        Ticket existing=
            pendingByOwner.get(
                session.ownerRef
            );

        if(existing!=null&&
           existing.state==State.SCHEDULED)
            throw new IllegalStateException(
                "G1 PvM duplicate pending respawn owner="+
                session.ownerRef
            );

        long minimumDue;

        try{
            minimumDue=
                Math.addExact(
                    finalization.deathTick,
                    RESPAWN_DELAY_TICKS
                );
        }catch(ArithmeticException overflow){
            throw new IllegalStateException(
                "G1 PvM respawn tick overflow",
                overflow
            );
        }

        long nextTick=
            world.clock().tick()==
                Long.MAX_VALUE
                ?Long.MAX_VALUE
                :world.clock().tick()+1L;

        long dueTick=
            Math.max(
                minimumDue,
                nextTick
            );

        long id=
            ++sequence;

        Ticket ticket=
            new Ticket(
                id,
                session.ownerRef,
                finalization.recipientRef,
                finalization.npcId,
                finalization.definitionId,
                finalization.deathTick,
                dueTick,
                session
            );

        ticket.handle=
            world.events().schedule(
                dueTick,
                ()->run(ticket)
            );

        tickets.put(id,ticket);
        pendingByOwner.put(
            ticket.ownerRef,
            ticket
        );
    }

    private void run(
        Ticket ticket
    ){
        synchronized(this){
            if(ticket.state!=State.SCHEDULED)
                return;
        }

        WorldPlayer owner=
            world.players().byName(
                ticket.ownerRef
            );

        if(owner==null||
           !world.players().owns(
                owner,
                owner.generation())){
            cancel(ticket);
            return;
        }

        final MonsterSpawnerService.SessionSnapshot
            activated;

        try{
            activated=
                spawner.activateIfCurrent(
                    ticket.ownerRef,
                    ticket.expectedSession,
                    RESPAWN_BUDGET
                );
        }catch(RuntimeException stale){
            cancel(ticket);
            return;
        }

        final MonsterSpawnerPvmSpawnExecutor.Result
            spawned;

        try{
            spawned=
                executor.execute(
                    ticket.ownerRef
                );
        }catch(Exception failure){
            rollbackActivation(
                ticket.ownerRef,
                activated
            );
            cancel(ticket);
            return;
        }

        if(spawned.status!=
                MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED||
           spawned.spawn==null||
           spawned.spawn.spawn.combat.spawn.npc.definitionId!=
                ticket.definitionId){
            rollbackActivation(
                ticket.ownerRef,
                activated
            );
            cancel(ticket);
            return;
        }

        synchronized(this){
            if(ticket.state!=State.SCHEDULED)
                return;

            ticket.state=State.RESPAWNED;
            ticket.respawnedNpcId=
                spawned.spawn.spawn.combat.spawn.npc.id;
            pendingByOwner.remove(
                ticket.ownerRef,
                ticket
            );
        }
    }

    private void rollbackActivation(
        String ownerRef,
        MonsterSpawnerService.SessionSnapshot
            activated
    ){
        try{
            spawner.deactivateIfCurrent(
                ownerRef,
                activated
            );
        }catch(Throwable ignored){}
    }

    private synchronized void cancel(
        Ticket ticket
    ){
        if(ticket.state!=State.SCHEDULED)
            return;

        ticket.state=State.CANCELLED;
        pendingByOwner.remove(
            ticket.ownerRef,
            ticket
        );
    }

    synchronized TicketSnapshot get(
        long id
    ){
        Ticket ticket=tickets.get(id);
        return ticket==null
            ?null
            :ticket.snapshot();
    }

    synchronized TicketSnapshot pending(
        String ownerRef
    ){
        Ticket ticket=
            pendingByOwner.get(
                PartyService.requireRef(
                    ownerRef
                )
            );

        return ticket==null
            ?null
            :ticket.snapshot();
    }

    synchronized int ticketCount(){
        return tickets.size();
    }
}
