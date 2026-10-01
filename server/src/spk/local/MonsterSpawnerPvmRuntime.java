package spk.local;

import java.util.*;

/**
 * Single semantic composition owner for one Monster Spawner PvM runtime graph.
 *
 * Individual gameplay policy remains in the injected spawner/lifecycle/combat,
 * drop-resolution and settlement services. This class owns only the canonical
 * npcId -> owner/recipient composition identity and terminal settlement retry.
 */
final class MonsterSpawnerPvmRuntime {
    enum State {
        ACTIVE,
        SETTLEMENT_PENDING
    }

    enum FinalizeStatus {
        NOT_OWNED,
        FINALIZED,
        SETTLEMENT_PENDING
    }

    static final class Snapshot {
        final EntityId npcId;
        final int definitionId;
        final String ownerRef;
        final String recipientRef;
        final State state;
        final Long deathTick;

        private Snapshot(Entry entry){
            this.npcId=entry.npc.id;
            this.definitionId=entry.npc.definitionId;
            this.ownerRef=entry.ownerRef;
            this.recipientRef=entry.recipientRef;
            this.state=entry.state;
            this.deathTick=
                entry.finalization==null
                    ?null
                    :Long.valueOf(
                        entry.finalization.deathTick
                    );
        }
    }

    static final class SpawnResult {
        final MonsterSpawnerNpcLifecycleBindingService.Result spawn;
        final Snapshot runtime;

        private SpawnResult(
            MonsterSpawnerNpcLifecycleBindingService.Result spawn,
            Snapshot runtime
        ){
            this.spawn=Objects.requireNonNull(
                spawn,
                "spawn"
            );
            this.runtime=Objects.requireNonNull(
                runtime,
                "runtime"
            );
        }
    }

    static final class FinalizeResult {
        final FinalizeStatus status;
        final Snapshot runtime;
        final MonsterSpawnerNpcDeathFinalizationService.Result finalization;
        final NpcDropGroundSettlementService.Receipt settlement;

        private FinalizeResult(
            FinalizeStatus status,
            Snapshot runtime,
            MonsterSpawnerNpcDeathFinalizationService.Result finalization,
            NpcDropGroundSettlementService.Receipt settlement
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.runtime=runtime;
            this.finalization=finalization;
            this.settlement=settlement;
        }
    }

    private static final class Entry {
        final WorldNpc npc;
        final String ownerRef;
        final String recipientRef;

        State state=State.ACTIVE;
        boolean terminalInProgress;
        MonsterSpawnerNpcDeathFinalizationService.Result
            finalization;

        Entry(
            WorldNpc npc,
            String ownerRef,
            String recipientRef
        ){
            this.npc=Objects.requireNonNull(
                npc,
                "npc"
            );
            this.ownerRef=requireRef(
                ownerRef,
                "ownerRef"
            );
            this.recipientRef=requireRef(
                recipientRef,
                "recipientRef"
            );
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final World world;
    private final MonsterSpawnerNpcLifecycleBindingService
        lifecycleBinding;
    private final MonsterSpawnerNpcDeathFinalizationService
        finalizer;
    private final NpcDropGroundSettlementService settlement;

    private final LinkedHashMap<EntityId,Entry>
        entries=new LinkedHashMap<>();

    MonsterSpawnerPvmRuntime(
        World world,
        MonsterSpawnerNpcLifecycleBindingService lifecycleBinding,
        MonsterSpawnerNpcDeathFinalizationService finalizer,
        NpcDropGroundSettlementService settlement
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.lifecycleBinding=Objects.requireNonNull(
            lifecycleBinding,
            "lifecycleBinding"
        );
        this.finalizer=Objects.requireNonNull(
            finalizer,
            "finalizer"
        );
        this.settlement=Objects.requireNonNull(
            settlement,
            "settlement"
        );

        MonsterSpawnerCombatBindingService combat=
            this.lifecycleBinding.combatAuthority();

        if(!this.lifecycleBinding.isBoundTo(
                this.world,
                combat
            )||
           !this.finalizer.isBoundTo(
                this.world,
                combat
            )||
           !this.settlement.isBoundTo(
                this.world
            ))
            throw new IllegalArgumentException(
                "Monster Spawner PvM runtime services must share one exact World/combat authority graph"
            );
    }

    boolean isBoundTo(
        World expectedWorld
    ){
        return world==expectedWorld;
    }

    SpawnResult spawnAndBind(
        String ownerRef,
        String recipientRef,
        int x,
        int y,
        int plane
    )throws Exception{
        String owner=requireRef(
            ownerRef,
            "ownerRef"
        );
        String recipient=requireRef(
            recipientRef,
            "recipientRef"
        );

        MonsterSpawnerNpcLifecycleBindingService.Result
            spawned=
                lifecycleBinding.spawnBindAndRegister(
                    owner,
                    x,
                    y,
                    plane
                );

        WorldNpc npc=
            spawned.combat.spawn.npc;

        Entry entry=
            new Entry(
                npc,
                owner,
                recipient
            );

        synchronized(this){
            Entry prior=entries.put(
                npc.id,
                entry
            );

            if(prior!=null){
                entries.put(
                    npc.id,
                    prior
                );
                throw new IllegalStateException(
                    "Monster Spawner PvM runtime duplicate NPC ownership id="+
                    npc.id
                );
            }
        }

        return new SpawnResult(
            spawned,
            entry.snapshot()
        );
    }

    FinalizeResult finalizeIfOwned(
        WorldNpc npc
    )throws Exception{
        WorldNpc checked=Objects.requireNonNull(
            npc,
            "npc"
        );

        Entry entry;

        synchronized(this){
            entry=entries.get(
                checked.id
            );

            if(entry==null||
               entry.npc!=checked)
                return new FinalizeResult(
                    FinalizeStatus.NOT_OWNED,
                    null,
                    null,
                    null
                );

            if(entry.terminalInProgress)
                throw new IllegalStateException(
                    "Monster Spawner PvM terminal operation already in progress id="+
                    checked.id
                );

            if(entry.state==
                    State.SETTLEMENT_PENDING){
                // Retry outside the runtime monitor.
            }else{
                entry.terminalInProgress=true;
            }
        }

        if(entry.state==
                State.SETTLEMENT_PENDING)
            return settlePending(entry);

        MonsterSpawnerNpcDeathFinalizationService.Result
            finalized;

        try{
            finalized=
                finalizer.finalizeDead(
                    entry.ownerRef,
                    entry.npc,
                    entry.recipientRef
                );
        }catch(Throwable failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }
            rethrow(failure);
            return null;
        }

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry)
                throw new IllegalStateException(
                    "Monster Spawner PvM runtime ownership changed during finalization id="+
                    entry.npc.id
                );

            entry.finalization=finalized;
            entry.state=State.SETTLEMENT_PENDING;
            entry.terminalInProgress=false;
        }

        return settlePending(
            entry
        );
    }

    FinalizeResult retrySettlement(
        EntityId npcId
    ){
        Entry entry;

        synchronized(this){
            entry=entries.get(
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                )
            );

            if(entry==null)
                return new FinalizeResult(
                    FinalizeStatus.NOT_OWNED,
                    null,
                    null,
                    null
                );

            if(entry.state!=
                    State.SETTLEMENT_PENDING)
                throw new IllegalStateException(
                    "Monster Spawner PvM settlement is not pending id="+
                    npcId
                );
        }

        return settlePending(
            entry
        );
    }

    private FinalizeResult settlePending(
        Entry entry
    ){
        MonsterSpawnerNpcDeathFinalizationService.Result
            finalized;

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry)
                return new FinalizeResult(
                    FinalizeStatus.NOT_OWNED,
                    null,
                    null,
                    null
                );

            if(entry.state!=
                    State.SETTLEMENT_PENDING||
               entry.finalization==null)
                throw new IllegalStateException(
                    "Monster Spawner PvM missing terminal resolution id="+
                    entry.npc.id
                );

            if(entry.terminalInProgress)
                throw new IllegalStateException(
                    "Monster Spawner PvM settlement already in progress id="+
                    entry.npc.id
                );

            entry.terminalInProgress=true;
            finalized=entry.finalization;
        }

        final NpcDropGroundSettlementService.Receipt receipt;

        try{
            receipt=
                settlement.settle(
                    finalized.drops
                );
        }catch(RuntimeException failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }

            return new FinalizeResult(
                FinalizeStatus.SETTLEMENT_PENDING,
                entry.snapshot(),
                finalized,
                null
            );
        }catch(Error failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }

            throw failure;
        }

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry)
                throw new IllegalStateException(
                    "Monster Spawner PvM ownership changed during settlement id="+
                    entry.npc.id
                );

            entries.remove(
                entry.npc.id
            );
            entry.terminalInProgress=false;
        }

        return new FinalizeResult(
            FinalizeStatus.FINALIZED,
            null,
            finalized,
            receipt
        );
    }

    synchronized Snapshot get(
        EntityId npcId
    ){
        Entry entry=entries.get(
            Objects.requireNonNull(
                npcId,
                "npcId"
            )
        );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized int size(){
        return entries.size();
    }

    private static String requireRef(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;
        if(failure instanceof Exception)
            throw (Exception)failure;

        throw new RuntimeException(failure);
    }
}
