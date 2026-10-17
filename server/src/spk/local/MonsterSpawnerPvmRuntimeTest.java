package spk.local;

import java.util.*;

public final class MonsterSpawnerPvmRuntimeTest {
    private static final String OWNER="pvm-runtime-owner";

    public static void main(String[] args)throws Exception{
        successfulLifecycle();
        terminalObserverFailureAfterSettlementHasNoRetry();
        disconnectedOwnerRetiresAfterLastNpc();
        reconnectedOwnerKeepsCurrentSession();
        recipientOriginRetainedAcrossReconnectAndSettlementRetry();
        unregisteredRecipientHasNoInventedOrigin();
        aliveAndForeignFailClosed();
        settlementPendingRetry();
        settlementRetryFencedByWorldClose();
        spawnFailurePublishesNothing();
        exactGraphRequired();

        System.out.println(
            "MONSTER_SPAWNER_PVM_RUNTIME_PASS "+
            "singleRuntimeOwner=true "+
            "spawnOwnership=true "+
            "spawnOwnershipAtomic=true "+
            "exactWorldLifecycle=true "+
            "deathFinalization=true "+
            "settlement=true "+
            "disconnectedLastNpcRetiresSession=true "+
            "reconnectedLastNpcPreservesSession=true "+
            "settlementPendingRetry=true "+
            "settlementWorldPulseRetry=true "+
            "settlementRetryExactlyOnce=true "+
            "settlementWorldCloseFence=true "+
            "terminalRetryNoMutation=true "+
            "dropResolutionOnce=true "+
            "duplicateDeathBlocked=true "+
            "spawnRecipientOriginWitness=true "+
            "noUnprovenOriginInference=true "+
            "terminalObserverExceptionDoesNotRollbackSettlement=true "+
            "protocolIndependent=true"
        );
    }

    /**
     * G18.5.1: the existing runtime catches a terminal observer failure
     * *after* ground settlement and removal of the canonical NPC entry.
     * There is no replayable pending observer operation for that NPC ID.
     * This characterizes LocalLab's current behavior, NOT a fix.
     */
    private static void terminalObserverFailureAfterSettlementHasNoRetry()
        throws Exception{
        Fixture f=new Fixture(false);
        final int[] callbacks={0};
        try{
            f.runtime.installTerminalObserver(result->{
                callbacks[0]++;
                require(result.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        result.settlement!=null,
                    "observer should only see fully settled NPC");
                throw new IllegalStateException(
                    "G1851_SYNTHETIC_TERMINAL_OBSERVER_FAILURE"
                );
            });

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,"killer",3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;

            require(f.lifecycle.applyDamage(
                    npc.id,99,91L
                ).newlyDied,
                "G1851 canonical lethal event");

            // The runtime explicitly swallows observer Throwable: this
            // must still report FINALIZED and commit the ground drop.
            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                f.runtime.finalizeIfOwned(npc);
            require(finished!=null&&finished.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finished.settlement!=null&&
                    callbacks[0]==1&&
                    f.world.groundItems().size()==1&&
                    f.dropCalls==1&&
                    f.runtime.get(npc.id)==null&&
                    f.runtime.size()==0,
                "observer failure did not preserve already-terminal settlement");

            MonsterSpawnerPvmRuntime.FinalizeResult retried=
                f.runtime.retrySettlement(npc.id);
            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                f.runtime.finalizeIfOwned(npc);
            require(retried.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    duplicate.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    callbacks[0]==1&&
                    f.world.groundItems().size()==1&&
                    f.dropCalls==1,
                "observer failure must not replay a removed terminal entry");

            System.out.println(
                "G1851_TERMINAL_OBSERVER_FAILURE_NONRETRYABLE_PASS"+
                " syntheticObserverFailureSwallowed=true"+
                " dropSettlementAlreadyCommitted=true"+
                " npcEntryRemovedBeforeObserver=true"+
                " existingObserverDeliveryRetry=false"+
                " duplicateDropPrevented=true"+
                " originalServerRecoveryPolicyClaim=false"+
                " productionObserverChanged=false"
            );
        }finally{
            f.close();
        }
    }

    private static void successfulLifecycle()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;

            require(
                spawned.runtime.state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                f.runtime.size()==1&&
                f.runtime.get(npc.id)!=null&&
                f.lifecycle.get(npc.id)!=null&&
                f.binder.get(npc.id)!=null,
                "runtime spawn ownership"
            );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    50L
                ).newlyDied,
                "runtime lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult result=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                result.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                result.finalization!=null&&
                result.settlement!=null&&
                result.settlement.npcId.equals(
                    npc.id
                )&&
                result.settlement.groundItems.size()==1&&
                f.world.groundItems().size()==1&&
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.world.npcs().byId(npc.id)==null&&
                f.lifecycle.get(npc.id)==null&&
                f.binder.get(npc.id)==null&&
                f.dropCalls==1,
                "runtime terminal composition"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                duplicate.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                f.world.groundItems().size()==1&&
                f.dropCalls==1,
                "duplicate runtime finalization"
            );
        }finally{
            f.close();
        }
    }

    /** #1394: a disconnected owner must not leave an orphan session after final settlement. */
    private static void disconnectedOwnerRetiresAfterLastNpc()
        throws Exception{
        Fixture f=new Fixture(false);
        WorldPlayer player=new WorldPlayer();
        long generation=f.world.registerPlayer(player,OWNER);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(OWNER,"killer",3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;

            require(f.spawner.getSession(OWNER)!=null,
                "tracked Monster Spawner session missing before disconnect");
            require(f.world.unregisterPlayer(player,generation),
                "disconnected Monster Spawner owner unregister");
            require(f.spawner.getSession(OWNER)!=null,
                "tracked NPC must retain disconnected session before terminal death");

            require(f.lifecycle.applyDamage(npc.id,99,50L).newlyDied,
                "disconnected owner terminal lethal event");
            MonsterSpawnerPvmRuntime.FinalizeResult finalized=
                f.runtime.finalizeIfOwned(npc);

            require(finalized.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                finalized.settlement!=null&&
                f.spawner.getSession(OWNER)==null&&
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.world.groundItems().size()==1&&
                f.dropCalls==1,
                "last tracked NPC should retire exact disconnected session after settlement");

            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                f.runtime.finalizeIfOwned(npc);
            require(duplicate.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                f.spawner.getSession(OWNER)==null&&
                f.dropCalls==1,
                "retired session must not resettle duplicate terminal NPC");
        }finally{
            if(f.world.players().owns(player,generation))
                f.world.unregisterPlayer(player,generation);
            f.close();
        }
    }

    /** #1394: reconnecting before settlement must preserve a live owner's current session. */
    private static void reconnectedOwnerKeepsCurrentSession()
        throws Exception{
        Fixture f=new Fixture(false);
        WorldPlayer original=new WorldPlayer();
        long oldGeneration=f.world.registerPlayer(original,OWNER);
        WorldPlayer replacement=null;
        long newGeneration=-1L;

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(OWNER,"killer",3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;

            require(f.world.unregisterPlayer(original,oldGeneration),
                "original owner disconnect before replacement");
            require(f.spawner.getSession(OWNER)!=null,
                "tracked session must survive disconnect");

            replacement=new WorldPlayer();
            newGeneration=f.world.registerPlayer(replacement,OWNER);

            require(f.lifecycle.applyDamage(npc.id,99,50L).newlyDied,
                "reconnected owner terminal lethal event");
            MonsterSpawnerPvmRuntime.FinalizeResult finalized=
                f.runtime.finalizeIfOwned(npc);

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(OWNER);
            require(finalized.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                finalized.settlement!=null&&
                after!=null&&
                after.spawnedNpcIds.isEmpty()&&
                f.world.players().owns(replacement,newGeneration)&&
                f.world.players().byName(OWNER)==replacement&&
                f.runtime.size()==0&&
                f.world.groundItems().size()==1&&
                f.dropCalls==1,
                "last NPC finalization may not retire a reconnected owner's current session");
        }finally{
            if(f.world.players().owns(original,oldGeneration))
                f.world.unregisterPlayer(original,oldGeneration);
            if(replacement!=null&&
                    f.world.players().owns(replacement,newGeneration))
                f.world.unregisterPlayer(replacement,newGeneration);
            f.close();
        }
    }

    /** G18.4.5: the recipient is observed inside the spawn, never re-resolved at death. */
    private static void recipientOriginRetainedAcrossReconnectAndSettlementRetry()
        throws Exception{
        Fixture f=new Fixture(false);
        WorldPlayer initial=new WorldPlayer();
        long firstGeneration=f.world.registerPlayer(initial,"killer");
        WorldPlayer replacement=null;
        long secondGeneration=-1L;
        final MonsterSpawnerPvmRuntime.FinalizeResult[] observed={null};

        try{
            f.runtime.installTerminalObserver(result->observed[0]=result);

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(OWNER,"killer",3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            Tile tile=npc.tile();
            MonsterSpawnerPvmRuntime.RecipientOrigin stamp=
                spawned.runtime.recipientOrigin;

            require(stamp!=null&&stamp.player==initial&&
                    stamp.generation==firstGeneration&&
                    "killer".equals(stamp.recipientRef)&&
                    stamp.stillCurrentIn(f.world)&&
                    f.runtime.get(npc.id).recipientOrigin==stamp,
                "spawn-time recipient must be exact live WorldPlayer");

            require(f.world.unregisterPlayer(initial,firstGeneration),
                "original recipient unregister");
            replacement=new WorldPlayer();
            secondGeneration=f.world.registerPlayer(replacement,"killer");

            require(!stamp.stillCurrentIn(f.world)&&
                    f.world.players().byName("killer")==replacement&&
                    f.runtime.get(npc.id).recipientOrigin==stamp,
                "same username must not retarget earlier NPC origin");

            // Deliberately force the canonical terminal settlement to
            // remain pending so the original stamp must survive a retry.
            GroundItem blocker=f.world.groundItems().add(
                995,Integer.MAX_VALUE,tile,"killer",1L,false
            );
            require(f.lifecycle.applyDamage(npc.id,99,75L).newlyDied,
                "recipient-origin pending lethal event");
            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                f.runtime.finalizeIfOwned(npc);
            MonsterSpawnerPvmRuntime.Snapshot retained=
                f.runtime.get(npc.id);

            require(pending.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                    pending.recipientOrigin==stamp&&
                    pending.runtime!=null&&
                    pending.runtime.recipientOrigin==stamp&&
                    retained!=null&&retained.recipientOrigin==stamp&&
                    observed[0]==null&&f.dropCalls==1,
                "pending settlement must preserve origin without observer delivery");

            require(f.world.groundItems().remove(blocker.id),
                "remove pending origin test blocker");
            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                f.runtime.retrySettlement(npc.id);

            require(finished.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finished.recipientOrigin==stamp&&
                    finished.runtime==null&&
                    finished.settlement!=null&&
                    observed[0]==finished&&
                    observed[0].recipientOrigin==stamp&&
                    !stamp.stillCurrentIn(f.world)&&
                    f.world.players().owns(replacement,secondGeneration)&&
                    f.runtime.get(npc.id)==null&&
                    f.dropCalls==1,
                "successful retry/observer must carry old immutable origin");

            System.out.println(
                "G1845_MONSTER_SPAWNER_RECIPIENT_ORIGIN_WITNESS_PASS"+
                " capturedAtSpawn=true"+
                " sameNameReplacementRefused=true"+
                " pendingRetryPreserved=true"+
                " terminalObserverPreserved=true"+
                " originalServerCreditPolicyClaim=false"+
                " liveDmmObserverWired=false"
            );
        }finally{
            if(f.world.players().owns(initial,firstGeneration))
                f.world.unregisterPlayer(initial,firstGeneration);
            if(replacement!=null&&
                    f.world.players().owns(replacement,secondGeneration))
                f.world.unregisterPlayer(replacement,secondGeneration);
            f.close();
        }
    }

    /** The test fixture's usual "killer" is not a registered WorldPlayer. */
    private static void unregisteredRecipientHasNoInventedOrigin()
        throws Exception{
        Fixture f=new Fixture(false);
        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(OWNER,"absent-recipient",3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(spawned.runtime.recipientOrigin==null&&
                    f.runtime.get(npc.id).recipientOrigin==null,
                "unregistered recipient cannot manufacture origin");
        }finally{
            f.close();
        }
    }

    private static void aliveAndForeignFailClosed()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;

            expect(
                IllegalStateException.class,
                ()->f.runtime.finalizeIfOwned(
                    npc
                ),
                "alive owned finalization"
            );

            require(
                f.runtime.get(npc.id)!=null&&
                f.runtime.get(npc.id).state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                f.world.npcs().byId(npc.id)==npc&&
                f.lifecycle.get(npc.id).alive()&&
                f.dropCalls==0,
                "alive rejection changed runtime"
            );

            WorldNpc foreign=
                f.world.npcs().spawn(
                    1700,
                    3090,
                    3495,
                    0
                );

            MonsterSpawnerPvmRuntime.FinalizeResult notOwned=
                f.runtime.finalizeIfOwned(
                    foreign
                );

            require(
                notOwned.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                f.world.npcs().byId(foreign.id)==foreign,
                "foreign NPC mutated"
            );

            f.world.npcs().remove(
                foreign.id
            );
        }finally{
            f.close();
        }
    }

    private static void settlementPendingRetry()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            f.world.installMonsterSpawnerPvmRuntime(
                f.runtime
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;
            Tile deathTile=
                npc.tile();

            GroundItem blocker=
                f.world.groundItems().add(
                    995,
                    Integer.MAX_VALUE,
                    deathTile,
                    "killer",
                    1L,
                    false
                );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    60L
                ).newlyDied,
                "pending lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                f.runtime.finalizeIfOwned(
                    npc
                );

            MonsterSpawnerPvmRuntime.Snapshot snapshot=
                f.runtime.get(
                    npc.id
                );

            require(
                pending.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                pending.finalization!=null&&
                pending.settlement==null&&
                snapshot!=null&&
                snapshot.state==
                    MonsterSpawnerPvmRuntime.State.SETTLEMENT_PENDING&&
                snapshot.deathTick!=null&&
                snapshot.deathTick.longValue()==60L&&
                f.world.npcs().byId(npc.id)==null&&
                f.lifecycle.get(npc.id)==null&&
                f.binder.get(npc.id)==null&&
                f.dropCalls==1&&
                f.settlement.get(npc.id)==null,
                "settlement pending state"
            );

            require(
                f.world.groundItems().remove(
                    blocker.id
                ),
                "remove overflow blocker"
            );

            long tickBefore=
                f.world.clock().tick();

            f.world.pulse().pulseOnce(
                1_000L
            );

            GroundItem settled=
                f.world.groundItems().findOwned(
                    995,
                    deathTile.x,
                    deathTile.y,
                    deathTile.plane,
                    "killer"
                );
            int settledSize=
                f.world.groundItems().size();

            require(
                f.world.clock().tick()==
                    tickBefore+1L&&
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.dropCalls==1&&
                settled!=null&&
                settledSize==1,
                "WorldPulse did not retry pending settlement"
            );

            f.world.pulse().pulseOnce(
                1_600L
            );

            require(
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.dropCalls==1&&
                f.world.groundItems().size()==
                    settledSize&&
                f.world.groundItems().findOwned(
                    995,
                    deathTile.x,
                    deathTile.y,
                    deathTile.plane,
                    "killer"
                )==settled,
                "later WorldPulse retried settled entry"
            );
        }finally{
            f.close();
        }
    }

    private static void settlementRetryFencedByWorldClose()
        throws Exception{
        postCloseRetryDoesNotMutate();
        closeWindowRetryDoesNotMutate();
    }

    private static void postCloseRetryDoesNotMutate()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;
            Tile deathTile=
                npc.tile();

            GroundItem blocker=
                f.world.groundItems().add(
                    995,
                    Integer.MAX_VALUE,
                    deathTile,
                    "killer",
                    1L,
                    false
                );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    61L
                ).newlyDied,
                "post-close pending lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                pending.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                f.runtime.get(npc.id)!=null&&
                f.runtime.get(npc.id).state==
                    MonsterSpawnerPvmRuntime.State.SETTLEMENT_PENDING&&
                f.dropCalls==1,
                "post-close pending state"
            );

            require(
                f.world.groundItems().remove(
                    blocker.id
                ),
                "remove post-close overflow blocker"
            );

            require(
                f.world.groundItems().size()==0,
                "post-close fixture retained blocker"
            );

            f.world.close();

            MonsterSpawnerPvmRuntime.FinalizeResult retried=
                f.runtime.retrySettlement(
                    npc.id
                );

            MonsterSpawnerPvmRuntime.Snapshot after=
                f.runtime.get(
                    npc.id
                );

            require(
                retried.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                retried.settlement==null&&
                after!=null&&
                after.state==
                    MonsterSpawnerPvmRuntime.State.SETTLEMENT_PENDING&&
                f.settlement.get(npc.id)==null&&
                f.world.groundItems().size()==0&&
                f.world.groundItemPresentationEvents().closed()&&
                f.dropCalls==1,
                "post-close settlement retry mutated terminal World"
            );
        }finally{
            f.close();
        }
    }

    private static void closeWindowRetryDoesNotMutate()
        throws Exception{
        Fixture f=new Fixture(false);

        java.util.concurrent.CountDownLatch lifecycleHeld=
            new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseLifecycle=
            new java.util.concurrent.CountDownLatch(1);

        Throwable[] blockerFailure={null};
        Throwable[] retryFailure={null};
        Throwable[] closeFailure={null};
        MonsterSpawnerPvmRuntime.FinalizeResult[]
            retryResult={null};

        Thread blockerThread=null;
        Thread retryThread=null;
        Thread closeThread=null;

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;
            Tile deathTile=
                npc.tile();

            GroundItem blocker=
                f.world.groundItems().add(
                    995,
                    Integer.MAX_VALUE,
                    deathTile,
                    "killer",
                    1L,
                    false
                );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    62L
                ).newlyDied,
                "close-window pending lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                pending.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                f.runtime.get(npc.id)!=null,
                "close-window pending state"
            );

            require(
                f.world.groundItems().remove(
                    blocker.id
                ),
                "remove close-window overflow blocker"
            );

            blockerThread=
                new Thread(
                    ()->{
                        try{
                            f.world.withOpenLifecycleOwnership(
                                ()->{
                                    lifecycleHeld.countDown();

                                    if(!releaseLifecycle.await(
                                            5L,
                                            java.util.concurrent.TimeUnit.SECONDS))
                                        throw new AssertionError(
                                            "close-window lifecycle release timeout"
                                        );
                                }
                            );
                        }catch(Throwable failure){
                            blockerFailure[0]=failure;
                        }
                    },
                    "pvm-settlement-lifecycle-blocker"
                );

            retryThread=
                new Thread(
                    ()->{
                        try{
                            retryResult[0]=
                                f.runtime.retrySettlement(
                                    npc.id
                                );
                        }catch(Throwable failure){
                            retryFailure[0]=failure;
                        }
                    },
                    "pvm-settlement-retry"
                );

            closeThread=
                new Thread(
                    ()->{
                        try{
                            f.world.close();
                        }catch(Throwable failure){
                            closeFailure[0]=failure;
                        }
                    },
                    "pvm-settlement-close"
                );

            blockerThread.start();

            await(
                lifecycleHeld,
                "close-window lifecycle blocker did not enter"
            );

            retryThread.start();

            awaitBlocked(
                retryThread,
                "settlement retry did not wait for lifecycle ownership"
            );

            closeThread.start();

            awaitClosed(
                f.world,
                "close-window World terminal flag was not published"
            );

            releaseLifecycle.countDown();

            join(
                blockerThread,
                "close-window lifecycle blocker did not exit"
            );
            join(
                retryThread,
                "close-window settlement retry did not exit"
            );
            join(
                closeThread,
                "close-window World close did not exit"
            );

            MonsterSpawnerPvmRuntime.Snapshot after=
                f.runtime.get(
                    npc.id
                );

            require(
                blockerFailure[0]==null&&
                retryFailure[0]==null&&
                closeFailure[0]==null&&
                retryResult[0]!=null&&
                retryResult[0].status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                retryResult[0].settlement==null&&
                after!=null&&
                after.state==
                    MonsterSpawnerPvmRuntime.State.SETTLEMENT_PENDING&&
                f.settlement.get(npc.id)==null&&
                f.world.groundItems().size()==0&&
                f.world.groundItemPresentationEvents().closed()&&
                f.dropCalls==1,
                "close-window settlement crossed terminal World boundary"
            );
        }finally{
            releaseLifecycle.countDown();

            if(blockerThread!=null&&
               blockerThread.isAlive())
                blockerThread.join(5_000L);
            if(retryThread!=null&&
               retryThread.isAlive())
                retryThread.join(5_000L);
            if(closeThread!=null&&
               closeThread.isAlive())
                closeThread.join(5_000L);

            f.close();
        }
    }

    private static void spawnFailurePublishesNothing()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            expect(
                IllegalStateException.class,
                ()->f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                ),
                "lifecycle spawn failure"
            );

            require(
                f.runtime.size()==0&&
                f.world.npcs().size()==0&&
                f.lifecycle.size()==0&&
                f.binder.size()==0&&
                f.spawner.getSession(OWNER)
                    .spawnedNpcIds.isEmpty(),
                "failed spawn published runtime ownership"
            );
        }finally{
            f.close();
        }
    }

    private static void exactGraphRequired()
        throws Exception{
        Fixture f=new Fixture(false);
        World other=
            World.isolatedForTest(600L);

        try{
            NpcDropGroundSettlementService wrongSettlement=
                new NpcDropGroundSettlementService(
                    other,
                    "CUSTOM_LOCALLAB_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            expect(
                IllegalArgumentException.class,
                ()->new MonsterSpawnerPvmRuntime(
                    f.world,
                    f.lifecycleBinding,
                    f.finalizer,
                    wrongSettlement
                ),
                "mismatched settlement World"
            );
        }finally{
            other.close();
            f.close();
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final MonsterSpawnerService spawner=
            configuredSpawner(world);
        final NpcLifecycleService lifecycle=
            world.npcLifecycle();
        final NpcCombatRuntimeBinder binder=
            new NpcCombatRuntimeBinder(
                world,
                context->combatPlan()
            );
        final MonsterSpawnerCombatBindingService combat=
            new MonsterSpawnerCombatBindingService(
                world,
                spawner,
                binder
            );
        final boolean failLifecycle;
        int dropCalls;

        final MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding;
        final NpcDropResolutionService drops;
        final MonsterSpawnerNpcDeathFinalizationService
            finalizer;
        final NpcDropGroundSettlementService settlement;
        final MonsterSpawnerPvmRuntime runtime;

        Fixture(
            boolean failLifecycle
        ){
            this.failLifecycle=failLifecycle;

            lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->{
                        if(this.failLifecycle)
                            throw new IllegalStateException(
                                "LIFECYCLE_POLICY_FAILURE"
                            );

                        return new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "pvm-runtime-hp",
                                10,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            );
                    }
                );

            drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<NpcDropResolutionService.Drop>
                            resolve(
                                NpcDropResolutionService.DeathContext context
                            ){
                            dropCalls++;

                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    10
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_DROP_TEST";
                        }
                    }
                );

            finalizer=
                new MonsterSpawnerNpcDeathFinalizationService(
                    world,
                    combat,
                    lifecycle,
                    drops
                );

            settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            runtime=
                new MonsterSpawnerPvmRuntime(
                    world,
                    lifecycleBinding,
                    finalizer,
                    settlement
                );
        }

        void close(){
            world.close();
        }
    }

    private static MonsterSpawnerService configuredSpawner(
        World world
    ){
        MonsterSpawnerService spawner=
            new MonsterSpawnerService(
                world.npcs()
            );

        spawner.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "pvm-runtime",
                    1530
                )
            ),
            "CUSTOM_LOCALLAB_CATALOG"
        );
        spawner.openSession(
            OWNER,
            "CUSTOM_LOCALLAB_SPAWNER"
        );
        spawner.selectRow(
            OWNER,
            0
        );
        spawner.activate(
            OWNER,
            1
        );

        return spawner;
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "pvm-runtime-combat",
            "CUSTOM_LOCALLAB_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .ineligible(),
            "CUSTOM_LOCALLAB_TARGET",
            "NONE",
            new NpcCombatApproachService.ApproachPolicy(){
                @Override public int stopRange(
                    NpcCombatApproachService.Context context
                ){ return 1; }

                @Override public RouteRequest.Policy routePolicy(
                    NpcCombatApproachService.Context context
                ){
                    return RouteRequest.Policy
                        .WORLD_STATIC_AUTHORITY;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_APPROACH";
                }

                @Override public String policy(){
                    return "FIXED_RANGE_1";
                }
            },
            new NpcCombatEngagementService.CadenceResolver(){
                @Override public int nextDelayTicks(
                    NpcCombatEngagementService.Context context
                ){ return 3; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_CADENCE";
                }

                @Override public String policy(){
                    return "FIXED_3";
                }
            },
            new NpcPlayerCombatResolutionService.DamageResolver(){
                @Override public int resolve(
                    NpcPlayerCombatResolutionService.DamageContext context
                ){ return 1; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CONTROLLER",
            "CUSTOM_LOCALLAB_AI"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void await(
        java.util.concurrent.CountDownLatch latch,
        String label
    )throws Exception{
        if(!latch.await(
                5L,
                java.util.concurrent.TimeUnit.SECONDS))
            throw new AssertionError(
                label
            );
    }

    private static void awaitBlocked(
        Thread thread,
        String label
    ){
        long deadline=
            System.nanoTime()+
            java.util.concurrent.TimeUnit.SECONDS
                .toNanos(
                    5L
                );

        while(System.nanoTime()<deadline){
            if(thread.getState()==
                    Thread.State.BLOCKED)
                return;

            if(!thread.isAlive())
                break;

            Thread.yield();
        }

        throw new AssertionError(
            label+
            " state="+
            thread.getState()
        );
    }

    private static void awaitClosed(
        World world,
        String label
    ){
        long deadline=
            System.nanoTime()+
            java.util.concurrent.TimeUnit.SECONDS
                .toNanos(
                    5L
                );

        while(!world.closed()&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(!world.closed())
            throw new AssertionError(
                label
            );
    }

    private static void join(
        Thread thread,
        String label
    )throws Exception{
        thread.join(
            5_000L
        );

        if(thread.isAlive())
            throw new AssertionError(
                label+
                " state="+
                thread.getState()
            );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MonsterSpawnerPvmRuntimeTest(){}
}
