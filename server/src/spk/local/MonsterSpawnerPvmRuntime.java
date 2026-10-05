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
        FINALIZATION_PENDING,
        SETTLEMENT_PENDING,
        TERMINAL_PENDING
    }

    enum FinalizeStatus {
        NOT_OWNED,
        FINALIZED,
        FINALIZATION_PENDING,
        SETTLEMENT_PENDING,
        TERMINAL_PENDING
    }

    interface TerminalPolicy {
        void onSettled(TerminalContext context);
        Tile replacementTile(TerminalContext context);
        String authority();
    }

    static final class TerminalContext {
        final String ownerRef;
        final String recipientRef;
        final MonsterSpawnerNpcDeathFinalizationService.Result finalization;
        final NpcDropGroundSettlementService.Receipt settlement;
        final MonsterSpawnerService.SessionSnapshot session;

        TerminalContext(
            String ownerRef,
            String recipientRef,
            MonsterSpawnerNpcDeathFinalizationService.Result finalization,
            NpcDropGroundSettlementService.Receipt settlement,
            MonsterSpawnerService.SessionSnapshot session
        ){
            this.ownerRef=ownerRef;
            this.recipientRef=recipientRef;
            this.finalization=Objects.requireNonNull(finalization,"finalization");
            this.settlement=Objects.requireNonNull(settlement,"settlement");
            this.session=Objects.requireNonNull(session,"session");
        }
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
        final SpawnResult replacement;

        private FinalizeResult(
            FinalizeStatus status,
            Snapshot runtime,
            MonsterSpawnerNpcDeathFinalizationService.Result finalization,
            NpcDropGroundSettlementService.Receipt settlement
        ){
            this(
                status,
                runtime,
                finalization,
                settlement,
                null
            );
        }

        private FinalizeResult(
            FinalizeStatus status,
            Snapshot runtime,
            MonsterSpawnerNpcDeathFinalizationService.Result finalization,
            NpcDropGroundSettlementService.Receipt settlement,
            SpawnResult replacement
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.runtime=runtime;
            this.finalization=finalization;
            this.settlement=settlement;
            this.replacement=replacement;
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
        NpcDropGroundSettlementService.Receipt settlement;
        boolean terminalPolicyApplied;

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
    private final MonsterSpawnerCombatBindingService
        combat;
    private final MonsterSpawnerNpcDeathFinalizationService
        finalizer;
    private final NpcDropGroundSettlementService settlement;
    private final TerminalPolicy terminalPolicy;

    private final LinkedHashMap<EntityId,Entry>
        entries=new LinkedHashMap<>();

    MonsterSpawnerPvmRuntime(
        World world,
        MonsterSpawnerNpcLifecycleBindingService lifecycleBinding,
        MonsterSpawnerNpcDeathFinalizationService finalizer,
        NpcDropGroundSettlementService settlement
    ){
        this(
            world,
            lifecycleBinding,
            finalizer,
            settlement,
            null
        );
    }

    MonsterSpawnerPvmRuntime(
        World world,
        MonsterSpawnerNpcLifecycleBindingService lifecycleBinding,
        MonsterSpawnerNpcDeathFinalizationService finalizer,
        NpcDropGroundSettlementService settlement,
        TerminalPolicy terminalPolicy
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
        this.terminalPolicy=terminalPolicy;

        if(terminalPolicy!=null)
            requireServerAuthority(
                terminalPolicy.authority()
            );

        this.combat=
            this.lifecycleBinding.combatAuthority();

        if(!this.lifecycleBinding.isBoundTo(
                this.world,
                this.combat
            )||
           !this.finalizer.isBoundTo(
                this.world,
                this.combat
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

    boolean isBoundTo(
        World expectedWorld,
        MonsterSpawnerService expectedSpawner
    ){
        MonsterSpawnerCombatBindingService combat=
            lifecycleBinding.combatAuthority();

        return world==expectedWorld&&
            combat.isBoundTo(
                expectedWorld,
                Objects.requireNonNull(
                    expectedSpawner,
                    "expectedSpawner"
                )
            );
    }

    SpawnResult spawnAndBind(
        String ownerRef,
        String recipientRef,
        int x,
        int y,
        int plane
    )throws Exception{
        return spawnAndBindExpected(
            ownerRef,
            recipientRef,
            null,
            x,
            y,
            plane
        );
    }

    SpawnResult spawnAndBindIfCurrent(
        String ownerRef,
        String recipientRef,
        MonsterSpawnerService.SessionSnapshot expected,
        int x,
        int y,
        int plane
    )throws Exception{
        return spawnAndBindExpected(
            ownerRef,
            recipientRef,
            Objects.requireNonNull(
                expected,
                "expected"
            ),
            x,
            y,
            plane
        );
    }

    private SpawnResult spawnAndBindExpected(
        String ownerRef,
        String recipientRef,
        MonsterSpawnerService.SessionSnapshot expected,
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

        final Entry[] published={null};

        MonsterSpawnerNpcLifecycleBindingService
            .RegistrationCommitAction publishRuntime=
                (npc,lifecycle,planKey)->{
                    Entry entry=
                        new Entry(
                            npc,
                            owner,
                            recipient
                        );

                    synchronized(this){
                        if(entries.containsKey(
                                npc.id))
                            throw new IllegalStateException(
                                "Monster Spawner PvM runtime duplicate NPC ownership id="+
                                npc.id
                            );

                        entries.put(
                            npc.id,
                            entry
                        );
                        published[0]=entry;
                    }
                };

        MonsterSpawnerNpcLifecycleBindingService.Result
            spawned=
                expected==null
                    ?lifecycleBinding.spawnBindAndRegisterComposed(
                        owner,
                        x,
                        y,
                        plane,
                        publishRuntime
                    )
                    :lifecycleBinding.spawnBindAndRegisterComposedIfCurrent(
                        owner,
                        expected,
                        x,
                        y,
                        plane,
                        publishRuntime
                    );

        Entry entry=
            Objects.requireNonNull(
                published[0],
                "runtime spawn ownership"
            );

        if(entry.npc!=spawned.combat.spawn.npc)
            throw new IllegalStateException(
                "Monster Spawner PvM runtime spawn identity changed id="+
                entry.npc.id
            );

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
                    State.SETTLEMENT_PENDING||
               entry.state==
                    State.TERMINAL_PENDING){
                // Retry outside the runtime monitor.
            }else{
                entry.terminalInProgress=true;
            }
        }

        if(entry.state==
                State.SETTLEMENT_PENDING)
            return settlePending(entry);

        if(entry.state==
                State.TERMINAL_PENDING)
            return completeTerminal(
                entry,
                false
            );

        MonsterSpawnerNpcDeathFinalizationService.Result
            finalized;

        try{
            finalized=
                finalizer.finalizeDead(
                    entry.ownerRef,
                    entry.npc,
                    entry.recipientRef
                );
        }catch(Error failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }
            throw failure;
        }catch(Exception failure){
            NpcLifecycleService.Snapshot lifecycle=
                world.npcLifecycle().get(
                    entry.npc.id
                );
            boolean retryable=
                world.npcs().byId(
                    entry.npc.id
                )==entry.npc&&
                lifecycle!=null&&
                lifecycle.dead();

            synchronized(this){
                if(entries.get(entry.npc.id)==entry){
                    entry.terminalInProgress=false;

                    if(retryable)
                        entry.state=
                            State.FINALIZATION_PENDING;
                }
            }

            if(!retryable){
                rethrow(
                    failure
                );
                return null;
            }

            return new FinalizeResult(
                FinalizeStatus.FINALIZATION_PENDING,
                entry.snapshot(),
                null,
                null
            );
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

    FinalizeResult retryFinalizationIfPending(
        WorldNpc npc
    )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        synchronized(this){
            Entry entry=
                entries.get(
                    checked.id
                );

            if(entry==null||
               entry.npc!=checked||
               entry.state!=
                    State.FINALIZATION_PENDING)
                return null;
        }

        return finalizeIfOwned(
            checked
        );
    }

    int retryPendingSettlementsOnce(){
        ArrayList<Entry> pending=
            new ArrayList<>();

        synchronized(this){
            for(Entry entry:entries.values())
                if(entry.state==
                        State.SETTLEMENT_PENDING||
                   entry.state==
                        State.TERMINAL_PENDING)
                    pending.add(
                        entry
                    );
        }

        int attempts=0;

        for(Entry entry:pending){
            FinalizeResult result=
                entry.state==
                    State.SETTLEMENT_PENDING
                    ?settlePending(
                        entry,
                        true
                    )
                    :completeTerminal(
                        entry,
                        true
                    );

            if(result!=null)
                attempts++;
        }

        return attempts;
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
        return settlePending(
            entry,
            false
        );
    }

    private FinalizeResult settlePending(
        Entry entry,
        boolean skipUnavailable
    ){
        MonsterSpawnerNpcDeathFinalizationService.Result
            finalized;

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry){
                if(skipUnavailable)
                    return null;

                return new FinalizeResult(
                    FinalizeStatus.NOT_OWNED,
                    null,
                    null,
                    null
                );
            }

            if(entry.state!=
                    State.SETTLEMENT_PENDING||
               entry.finalization==null){
                if(skipUnavailable)
                    return null;

                throw new IllegalStateException(
                    "Monster Spawner PvM missing terminal resolution id="+
                    entry.npc.id
                );
            }

            if(entry.terminalInProgress){
                if(skipUnavailable)
                    return null;

                throw new IllegalStateException(
                    "Monster Spawner PvM settlement already in progress id="+
                    entry.npc.id
                );
            }

            entry.terminalInProgress=true;
            finalized=entry.finalization;
        }

        final NpcDropGroundSettlementService.Receipt[]
            receipt={null};
        final boolean worldOpen;

        try{
            worldOpen=
                world.runIfOpen(
                    ()->
                        receipt[0]=
                            settlement.settle(
                                finalized.drops
                            )
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

        if(!worldOpen){
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
        }

        NpcDropGroundSettlementService.Receipt
            settled=
                Objects.requireNonNull(
                    receipt[0],
                    "ground settlement receipt"
                );

        MonsterSpawnerService.SessionSnapshot
            postTeardownSession=
                finalized.teardown.session;

        if(terminalPolicy!=null){
            synchronized(this){
                if(entries.get(entry.npc.id)!=entry)
                    throw new IllegalStateException(
                        "Monster Spawner PvM ownership changed during settlement id="+
                        entry.npc.id
                    );

                entry.settlement=settled;
                entry.state=State.TERMINAL_PENDING;
                entry.terminalInProgress=false;
            }

            return completeTerminal(
                entry,
                false
            );
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

        retireOfflineEmptySession(
            entry,
            postTeardownSession
        );

        return new FinalizeResult(
            FinalizeStatus.FINALIZED,
            null,
            finalized,
            settled
        );
    }

    private FinalizeResult completeTerminal(
        Entry entry,
        boolean skipUnavailable
    ){
        final MonsterSpawnerNpcDeathFinalizationService.Result finalized;
        final NpcDropGroundSettlementService.Receipt settled;
        final MonsterSpawnerService.SessionSnapshot session;
        boolean applyPolicy;

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry){
                if(skipUnavailable)
                    return null;
                return new FinalizeResult(
                    FinalizeStatus.NOT_OWNED,
                    null,
                    null,
                    null
                );
            }

            if(entry.state!=State.TERMINAL_PENDING||
               entry.finalization==null||
               entry.settlement==null){
                if(skipUnavailable)
                    return null;
                throw new IllegalStateException(
                    "Monster Spawner PvM terminal completion not pending id="+
                    entry.npc.id
                );
            }

            if(entry.terminalInProgress){
                if(skipUnavailable)
                    return null;
                throw new IllegalStateException(
                    "Monster Spawner PvM terminal completion already in progress id="+
                    entry.npc.id
                );
            }

            entry.terminalInProgress=true;
            finalized=entry.finalization;
            settled=entry.settlement;
            session=finalized.teardown.session;
            applyPolicy=!entry.terminalPolicyApplied;
        }

        TerminalContext context=
            new TerminalContext(
                entry.ownerRef,
                entry.recipientRef,
                finalized,
                settled,
                session
            );

        if(applyPolicy){
            try{
                terminalPolicy.onSettled(
                    context
                );
            }catch(RuntimeException failure){
                synchronized(this){
                    if(entries.get(entry.npc.id)==entry)
                        entry.terminalInProgress=false;
                }

                return new FinalizeResult(
                    FinalizeStatus.TERMINAL_PENDING,
                    entry.snapshot(),
                    finalized,
                    settled
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
                        "Monster Spawner PvM ownership changed during terminal policy id="+
                        entry.npc.id
                    );
                entry.terminalPolicyApplied=true;
            }
        }

        final Tile replacementTile;

        try{
            replacementTile=
                terminalPolicy.replacementTile(
                    context
                );
        }catch(RuntimeException failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }

            return new FinalizeResult(
                FinalizeStatus.TERMINAL_PENDING,
                entry.snapshot(),
                finalized,
                settled
            );
        }

        if(replacementTile==null||
           !session.active||
           session.remainingSpawnBudget<=0){
            synchronized(this){
                if(entries.get(entry.npc.id)!=entry)
                    throw new IllegalStateException(
                        "Monster Spawner PvM ownership changed before terminal retirement id="+
                        entry.npc.id
                    );
                entries.remove(
                    entry.npc.id
                );
                entry.terminalInProgress=false;
            }

            retireOfflineEmptySession(
                entry,
                session
            );

            return new FinalizeResult(
                FinalizeStatus.FINALIZED,
                null,
                finalized,
                settled
            );
        }

        final SpawnResult replacement;

        try{
            replacement=
                spawnAndBindIfCurrent(
                    entry.ownerRef,
                    entry.recipientRef,
                    session,
                    replacementTile.x,
                    replacementTile.y,
                    replacementTile.plane
                );
        }catch(MonsterSpawnerService.StaleSessionException stale){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry){
                    entries.remove(
                        entry.npc.id
                    );
                    entry.terminalInProgress=false;
                }
            }

            return new FinalizeResult(
                FinalizeStatus.FINALIZED,
                null,
                finalized,
                settled
            );
        }catch(Exception failure){
            synchronized(this){
                if(entries.get(entry.npc.id)==entry)
                    entry.terminalInProgress=false;
            }

            return new FinalizeResult(
                FinalizeStatus.TERMINAL_PENDING,
                entry.snapshot(),
                finalized,
                settled
            );
        }

        synchronized(this){
            if(entries.get(entry.npc.id)!=entry)
                throw new IllegalStateException(
                    "Monster Spawner PvM ownership changed after replacement id="+
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
            settled,
            replacement
        );
    }

    private void retireOfflineEmptySession(
        Entry entry,
        MonsterSpawnerService.SessionSnapshot session
    ){
        if(!session.spawnedNpcIds.isEmpty())
            return;

        world.runIfOpen(
            ()->{
                if(world.players().byName(
                        entry.ownerRef
                    )==null)
                    combat
                        .retireSessionIfCurrentAndNoTrackedNpcs(
                            entry.ownerRef,
                            session
                        );
            }
        );
    }

    private static String requireServerAuthority(
        String value
    ){
        String clean=requireRef(
            value,
            "authority"
        );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot own Monster Spawner terminal policy actual="+
                clean
            );

        return clean;
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
