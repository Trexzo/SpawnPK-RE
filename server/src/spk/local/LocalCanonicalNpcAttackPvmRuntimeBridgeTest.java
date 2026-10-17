package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class LocalCanonicalNpcAttackPvmRuntimeBridgeTest {
    private static final String OWNER="click-pvm-owner";

    public static void main(String[] args)throws Exception{
        int definition=attackDefinition();

        unconfiguredLeavesDeadCanonical(definition);
        configuredRuntimeFinalizes(definition);
        differentCanonicalAttackerKeepsConfiguredSpawnRecipient(definition);
        preTeardownFailureRetries(definition);
        unexpectedErrorPropagates(definition);

        System.out.println(
            "CANONICAL_NPC_ATTACK_PVM_RUNTIME_BRIDGE_PASS "+
            "optionalRuntime=true "+
            "exactWorldRuntime=true "+
            "lethalFinalizes=true "+
            "hitBeforeTeardown=true "+
            "dropsSettled=true "+
            "liveLootPending=true "+
            "foreignNpcUntouched=true "+
            "distinctAttackerVsConfiguredRecipientCharacterized=true "+
            "policyNeutral=true"
        );

        System.out.println(
            "CANONICAL_NPC_ATTACK_PVM_FINALIZATION_RETRY_PASS "+
            "preTeardownFailureRetained=true "+
            "noSessionThrow=true "+
            "deadRetry=true "+
            "noSecondDamage=true "+
            "noSecondHitPacket=true "+
            "terminalOnce=true "+
            "lootOnce=true "+
            "foreignUnchanged=true "+
            "cadenceUnchanged=true "+
            "errorPropagates=true"
        );
    }

    private static void unconfiguredLeavesDeadCanonical(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        WorldNpc target=null;

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            target=
                world.npcs().spawn(
                    definition,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle().register(
                target,
                10,
                "CUSTOM_LOCALLAB_OPTIONAL_RUNTIME_HP"
            );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                target
            );
            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            final int[] finalizerCalls={0};
            final Object[] finalizerResult={null};

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->{
                        finalizerCalls[0]++;
                        finalizerResult[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            );
                    }
                );

            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(hitBytes)
                );

            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                result!=null&&
                result.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                result.newlyDied&&
                result.hitpointsAfter==0&&
                hitBytes.size()>0,
                "unconfigured lethal click"
            );

            require(
                finalizerCalls[0]==1&&
                finalizerResult[0]==null&&
                world.npcs().byId(target.id)==target&&
                dead!=null&&dead.dead()&&
                world.groundItems().size()==0,
                "unconfigured World unexpectedly finalized NPC"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(target!=null){
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,
                    target.id
                );
                world.npcLifecycle()
                    .unregisterExact(
                        target
                    );
                world.npcs().remove(
                    target.id
                );
            }

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    /**
     * G19.4.3/44: an actual MonsterSpawnerPvmRuntime must settle drops
     * and remove its exact World-owned NPC even though the originating
     * player's packet-65 hitmark failed after lifecycle lethal damage.
     */
    static void runSpawnerLethalAfterMaskFailure(
        boolean uncheckedWriterFailure
    )throws Exception{
        final int definition=attackDefinition();
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        final String username=uncheckedWriterFailure
            ?"g1944-runtime-mask-spawner"
            :"g1943-io-mask-spawner";
        long generation=world.registerPlayer(player,username);
        player.movement().restoreAccountState(
            false,100,3087,3495,0);
        RuntimeGraph graph=new RuntimeGraph(
            world,definition,"g1943-mask-owner");
        world.installMonsterSpawnerPvmRuntime(graph.runtime);
        NpcRegistry registry=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relayWriter=
            writer(new ByteArrayOutputStream());
        boolean relayRegistered=false;
        try{
            SharedNpcWorldRelay.register(
                relayWriter,world,player,registry,player.movement());
            relayRegistered=true;
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "g1943-mask-owner",username,3088,3495,0);
            WorldNpc canonical=spawned.spawn.combat.spawn.npc;
            SharedNpcWorldRelay.syncRemotePets(relayWriter);
            NpcEntity view=requireView(registry,canonical);
            NpcActionClientRequest exact=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        view.sceneIndex,
                        new int[]{1943,1944,1945,1946});
            require(exact.action().opcode==72&&
                    exact.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "real decoded attacker click before broken mask");

            final int[] callbacks={0};
            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                terminal={null};
            final long expectedGeneration=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->expectedGeneration,
                    player.equipment(),player.combatStyles(),registry,
                    (npc,expected)->{
                        require(npc==canonical&&
                                expected==expectedGeneration,
                            "final spawner click matched current source");
                    },
                    npc->{
                        require(npc==canonical,"exact terminal source");
                        callbacks[0]++;
                        terminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(npc);
                    },
                    npc->
                        world.retryMonsterSpawnerPvmFinalizationIfPending(npc));

            ServerPacketWriter failingWriter;
            if(uncheckedWriterFailure){
                java.io.OutputStream crashing=new java.io.OutputStream(){
                    @Override public void write(int b){
                        throw new IllegalStateException(
                            "G1944_MASK_RUNTIME_OUTPUT_FAILURE");
                    }
                    @Override public void write(
                        byte[] b,int off,int len
                    ){
                        throw new IllegalStateException(
                            "G1944_MASK_RUNTIME_OUTPUT_FAILURE");
                    }
                };
                failingWriter=new ServerPacketWriter(
                    crashing,new IsaacCipher(new int[]{0,0,0,0}));
            }else{
                failingWriter=writer(new ByteArrayOutputStream());
                failingWriter.markTerminal();
            }

            Throwable original=null;
            try{
                handler.handle(
                    exact.action(),view,failingWriter);
            }catch(java.io.IOException|RuntimeException failed){
                original=failed;
            }
            require(
                original!=null&&
                (uncheckedWriterFailure
                    ?original instanceof IllegalStateException&&
                     "G1944_MASK_RUNTIME_OUTPUT_FAILURE".equals(
                         original.getMessage())
                    :original instanceof java.io.IOException&&
                     String.valueOf(original.getMessage()).contains(
                         "server packet writer terminal")),
                "actual outbound exception preserved from lethal hit");
            GroundItem ground=
                world.groundItems().findOwned(
                    995,3088,3495,0,username);
            List<WorldGroundItemPresentationEvents.Event> pending=
                world.groundItemPresentationEvents().pendingFor(
                    player.id(),generation,System.currentTimeMillis());
            require(
                callbacks[0]==1&&
                terminal[0]!=null&&
                terminal[0].status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                graph.runtime.get(canonical.id)==null&&
                graph.runtime.size()==0&&
                world.npcs().byId(canonical.id)==null&&
                world.npcLifecycle().get(canonical.id)==null&&
                graph.binder.get(canonical.id)==null&&
                graph.dropCalls==1&&
                ground!=null&&ground.amount==5&&
                pending.size()==1&&
                pending.get(0).itemId==995&&
                pending.get(0).newAmount==5,
                "actual runtime must settle exactly one drop after mask failure");

            // The event is terminal and detached; another finalization
            // attempt must not recreate NPC or deliver a second drop.
            MonsterSpawnerPvmRuntime.FinalizeResult again=
                world.finalizeMonsterSpawnerPvmIfOwned(canonical);
            GroundItem sameGround=
                world.groundItems().findOwned(
                    995,3088,3495,0,username);
            require(
                again!=null&&
                again.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                graph.dropCalls==1&&
                graph.runtime.size()==0&&
                sameGround!=null&&sameGround.amount==5&&
                world.groundItemPresentationEvents().pendingFor(
                    player.id(),generation,System.currentTimeMillis()
                ).size()==1,
                "repeated terminalization cannot duplicate committed loot");
        }finally{
            if(relayRegistered)
                SharedNpcWorldRelay.unregister(relayWriter);
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G19.5.1/52: use the real MonsterSpawner lifecycle/drop settlement
     * graph with an installed observer. The runtime must expose the
     * nonfatal post-settlement outcome, not infer reward application or
     * accidentally replay an observer that may have partially mutated.
     */
    static void runObserverOutcomeDiagnostics(
        int failingMode
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            final String recipient=
                failingMode==0?"g1951-observer-success":
                failingMode==1?"g1952-observer-runtime":
                    "g1952-observer-error";
            generation=world.registerPlayer(player,recipient);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);

            int definition=attackDefinition();
            RuntimeGraph graph=new RuntimeGraph(
                world,definition,"g1951-observer-owner");
            world.installMonsterSpawnerPvmRuntime(graph.runtime);
            final int[] calls={0};
            graph.runtime.installTerminalObserver(
                terminal->{
                    checkObserver(terminal.status==
                            MonsterSpawnerPvmRuntime
                                .FinalizeStatus.FINALIZED&&
                        terminal.finalization!=null&&
                        terminal.settlement!=null,
                        "observer runs only on post-ground-settlement result");
                    calls[0]++;
                    if(failingMode==1)
                        throw new IllegalStateException(
                            "G1952_POST_SETTLEMENT_OBSERVER_EXCEPTION");
                    if(failingMode==2)
                        throw new AssertionError(
                            "G1952_POST_SETTLEMENT_OBSERVER_ERROR");
                });

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "g1951-observer-owner",recipient,
                    3088,3495,0);
            WorldNpc canonical=spawned.spawn.combat.spawn.npc;
            // Only the observer path is under test here. Commit a
            // genuine canonical lethal HP result via the lifecycle API;
            // no fake FinalizeResult is constructed by the test.
            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamageOwned(
                    canonical.id,10,world.clock().tick());
            checkObserver(lethal.newlyDied&&
                    lethal.hitpointsAfter==0,
                "real canonical lifecycle reached lethal state");
            MonsterSpawnerPvmRuntime.FinalizeResult completed=
                world.finalizeMonsterSpawnerPvmIfOwned(canonical);

            GroundItem ground=world.groundItems().findOwned(
                995,3088,3495,0,recipient);
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics diag=
                graph.runtime.terminalObserverDiagnostics();
            checkObserver(completed!=null&&
                    completed.status==
                        MonsterSpawnerPvmRuntime
                            .FinalizeStatus.FINALIZED&&
                    completed.settlement!=null&&
                    calls[0]==1&&graph.dropCalls==1&&
                    graph.runtime.get(canonical.id)==null&&
                    world.npcs().byId(canonical.id)==null&&
                    world.npcLifecycle().get(canonical.id)==null&&
                    ground!=null&&ground.amount==5&&
                    diag.totalAttempts==1L&&
                    diag.failedAttempts==(failingMode==0?0L:1L)&&
                    diag.evictedAttempts==0L&&
                    diag.recentAttempts.size()==1,
                "drop settled and one observer outcome recorded");

            MonsterSpawnerPvmRuntime.TerminalObserverAttempt observed=
                diag.recentAttempts.get(0);
            checkObserver(observed.npcId.equals(canonical.id)&&
                    observed.deathTick==
                        completed.finalization.deathTick&&
                    recipient.equals(observed.recipientRef)&&
                    observed.spawnRecipientGeneration==generation&&
                    observed.outcome==
                        (failingMode==0
                            ?MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.RETURNED
                            :MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.THREW)&&
                    (failingMode==0
                        ?observed.failureType==null
                        :(failingMode==1
                            ?IllegalStateException.class.getName()
                                .equals(observed.failureType)
                            :AssertionError.class.getName()
                                .equals(observed.failureType))),
                "exact immutable terminal identity and failure class recorded");

            boolean immutable=false;
            try{
                diag.recentAttempts.clear();
            }catch(UnsupportedOperationException expected){
                immutable=true;
            }
            checkObserver(immutable&&diag.recentAttempts.size()==1,
                "diagnostic snapshot cannot be mutated by caller");

            MonsterSpawnerPvmRuntime.FinalizeResult noReplay=
                graph.runtime.retrySettlement(canonical.id);
            MonsterSpawnerPvmRuntime.FinalizeResult noSecondFinalizer=
                graph.runtime.finalizeIfOwned(canonical);
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics after=
                graph.runtime.terminalObserverDiagnostics();
            GroundItem unchanged=world.groundItems().findOwned(
                995,3088,3495,0,recipient);
            checkObserver(noReplay.status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.NOT_OWNED&&
                    noSecondFinalizer.status==
                        MonsterSpawnerPvmRuntime
                            .FinalizeStatus.NOT_OWNED&&
                    calls[0]==1&&graph.dropCalls==1&&
                    after.totalAttempts==1&&
                    after.failedAttempts==diag.failedAttempts&&
                    unchanged!=null&&unchanged.amount==5,
                "post-settlement observation does not enable unsafe replay");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G2077–81: genuine canonical settled NPC observer failure boundary.
     * Injects deliberately hostile Throwable messages/toString while capturing
     * stderr, proving that no error-message content or Throwable.toString()
     * escapes through the post-settlement diagnostic log.
     */
    static void runObserverSafeLogCapture(int mode)throws Exception{
        if(mode<0||mode>4)
            throw new IllegalArgumentException("G2077_INVALID_LOG_MODE");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        java.io.PrintStream oldErr=System.err;
        java.io.ByteArrayOutputStream buffer=
            new java.io.ByteArrayOutputStream();
        java.io.PrintStream localErr=
            new java.io.PrintStream(buffer,true,"UTF-8");
        final String secret="G2077_REDACT_PRIVATE_ACCOUNT_TOKEN_DO_NOT_LOG";
        final java.util.concurrent.atomic.AtomicInteger toStringCalls=
            new java.util.concurrent.atomic.AtomicInteger();
        final Throwable injected;
        switch(mode){
            case 0:
                injected=new IllegalStateException(secret);
                break;
            case 1:
                injected=new AssertionError(secret){
                    @Override public String toString(){
                        toStringCalls.incrementAndGet();
                        throw new IllegalStateException(
                            "TOSTRING_MUST_NEVER_BE_CALLED_"+secret);
                    }
                };
                break;
            case 2:
                RuntimeException chained=new RuntimeException(
                    secret,new IllegalArgumentException(secret));
                chained.addSuppressed(new IllegalArgumentException(secret));
                injected=chained;
                break;
            case 3:
                injected=new Error(secret);
                break;
            default:
                injected=null;
        }
        try{
            String recipient="g2077-real-lethal-recipient-"+mode;
            generation=world.registerPlayer(player,recipient);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            RuntimeGraph graph=new RuntimeGraph(
                world,attackDefinition(),"g2077-real-owner-"+mode);
            world.installMonsterSpawnerPvmRuntime(graph.runtime);
            final int[] calls={0};
            graph.runtime.installTerminalObserver(terminal->{
                calls[0]++;
                if(injected instanceof Error)
                    throw (Error)injected;
                if(injected!=null)
                    throw (RuntimeException)injected;
            });
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                graph.runtime.spawnAndBind(
                    "g2077-real-owner-"+mode,recipient,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick());
            checkObserver(lethal.newlyDied&&lethal.hitpointsAfter==0,
                "real canonical NPC reaches lethal HP");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal;
            System.setErr(localErr);
            try{
                terminal=world.finalizeMonsterSpawnerPvmIfOwned(npc);
            }finally{
                System.setErr(oldErr);
            }
            String stderr=buffer.toString("UTF-8");
            localErr.flush();
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics report=
                graph.runtime.terminalObserverDiagnostics();
            MonsterSpawnerPvmRuntime.TerminalObserverAttempt attempt=
                report.recentAttempts.size()==1
                    ?report.recentAttempts.get(0):null;
            checkObserver(terminal!=null&&
                terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&terminal.finalization!=null&&
                graph.runtime.get(npc.id)==null&&calls[0]==1&&
                graph.dropCalls==1&&
                report.totalAttempts==1L&&
                report.failedAttempts==(injected==null?0L:1L)&&
                attempt!=null&&
                attempt.outcome==(injected==null
                    ?MonsterSpawnerPvmRuntime.TerminalObserverOutcome.RETURNED
                    :MonsterSpawnerPvmRuntime.TerminalObserverOutcome.THREW)&&
                (injected==null?attempt.failureType==null
                    :injected.getClass().getName().equals(
                        attempt.failureType))&&
                world.groundItems().findOwned(
                    995,3088,3495,0,recipient)!=null,
                "observer log protection preserves one settled death and ring");
            checkObserver(toStringCalls.get()==0&&
                !stderr.contains(secret)&&
                !stderr.contains("TOSTRING_MUST_NEVER_BE_CALLED")&&
                !stderr.contains("error="+secret)&&
                (injected==null
                    ?!stderr.contains("G1_PVM_TERMINAL_OBSERVER_FAILED")
                    :(stderr.contains("G1_PVM_TERMINAL_OBSERVER_FAILED")&&
                      stderr.contains("failureType="+
                          injected.getClass().getName()))),
                "logs contain only throwable type, never sensitive message");
            MonsterSpawnerPvmRuntime.FinalizeResult retry=
                graph.runtime.retrySettlement(npc.id);
            MonsterSpawnerPvmRuntime.FinalizeResult second=
                graph.runtime.finalizeIfOwned(npc);
            checkObserver(retry.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                second.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                graph.dropCalls==1,
                "ground settlement never replayed");
        }finally{
            System.setErr(oldErr);
            localErr.close();
            if(generation>=0&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G2082–86: real ground-settled NPC after-drop optional session cleanup.
     * A cleanup failure occurs AFTER the irrecoverable canonical settlement,
     * so it must not prevent the terminal observer or trigger drop replay.
     */
    static void runPostSettlementCleanupBoundary(int mode)throws Exception{
        if(mode<0||mode>4)
            throw new IllegalArgumentException("G2082_INVALID_CLEANUP_MODE");
        World world=World.isolatedForTest(620L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        java.io.PrintStream oldErr=System.err;
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        java.io.PrintStream isolatedErr=new java.io.PrintStream(out,true,"UTF-8");
        final String secret="G2082_SECRET_FROM_SYNTHETIC_CLEANUP_DO_NOT_LOG";
        final java.util.concurrent.atomic.AtomicInteger toStringCalls=
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger injectionCalls=
            new java.util.concurrent.atomic.AtomicInteger();
        try{
            String recipient="g2082-real-recipient-"+mode;
            // Mode 4 uses the SAME registered owner as the recipient,
            // so optional owner-absent retirement must be skipped.
            String owner=mode==4?recipient:"g2082-absent-owner-"+mode;
            generation=world.registerPlayer(player,recipient);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            RuntimeGraph graph=new RuntimeGraph(
                world,attackDefinition(),owner);
            world.installMonsterSpawnerPvmRuntime(graph.runtime);
            final int[] observerCalls={0};
            graph.runtime.installTerminalObserver(terminal->{
                observerCalls[0]++;
                if(mode==3)
                    throw new IllegalArgumentException(
                        "G2085_OBSERVER_EXCEPTION_ALSO_NOT_A_GRANT");
            });
            if(mode==0||mode==3){
                graph.runtime.installPostSettlementCleanupFaultForTest(()->{
                    injectionCalls.incrementAndGet();
                    throw new IllegalStateException(secret);
                });
            }else if(mode==1){
                graph.runtime.installPostSettlementCleanupFaultForTest(()->{
                    injectionCalls.incrementAndGet();
                    throw new AssertionError(secret){
                        @Override public String toString(){
                            toStringCalls.incrementAndGet();
                            throw new IllegalStateException(
                                "UNSAFE_THROWABLE_TOSTRING_"+secret);
                        }
                    };
                });
            }else if(mode==4){
                graph.runtime.installPostSettlementCleanupFaultForTest(()->{
                    injectionCalls.incrementAndGet();
                    throw new IllegalStateException(
                        "FAULT_MUST_NOT_RUN_WHEN_OWNER_PRESENT");
                });
            }
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                graph.runtime.spawnAndBind(
                    owner,recipient,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick());
            checkObserver(lethal.newlyDied&&lethal.hitpointsAfter==0,
                "real canonical NPC lethal HP");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal;
            System.setErr(isolatedErr);
            try{
                terminal=world.finalizeMonsterSpawnerPvmIfOwned(npc);
            }finally{ System.setErr(oldErr); }
            isolatedErr.flush();
            String logged=out.toString("UTF-8");
            boolean shouldFail=mode==0||mode==1||mode==3;
            String type=mode==1?"spk.local.LocalCanonicalNpcAttackPvmRuntimeBridgeTest$":
                IllegalStateException.class.getName();
            checkObserver(
                terminal!=null&&
                terminal.status==MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.finalization!=null&&terminal.settlement!=null&&
                observerCalls[0]==1&&
                graph.dropCalls==1&&
                graph.runtime.get(npc.id)==null&&
                world.groundItems().findOwned(
                    995,3088,3495,0,recipient)!=null,
                "post-settlement cleanup failure preserves real drop and observer");
            checkObserver(
                toStringCalls.get()==0&&
                injectionCalls.get()==(shouldFail?1:0)&&
                !logged.contains(secret)&&
                !logged.contains("UNSAFE_THROWABLE_TOSTRING")&&
                !logged.contains("FAULT_MUST_NOT_RUN")&&
                (shouldFail
                    ?logged.contains("G1_PVM_POST_SETTLEMENT_CLEANUP_FAILED")&&
                        logged.contains("failureType="+type)
                    :!logged.contains("G1_PVM_POST_SETTLEMENT_CLEANUP_FAILED")),
                "only sanitized failure type is logged, never secret payload");
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics report=
                graph.runtime.terminalObserverDiagnostics();
            checkObserver(report.totalAttempts==1L&&
                report.failedAttempts==(mode==3?1L:0L)&&
                report.recentAttempts.size()==1&&
                report.recentAttempts.get(0).outcome==
                    (mode==3
                        ?MonsterSpawnerPvmRuntime.TerminalObserverOutcome.THREW
                        :MonsterSpawnerPvmRuntime.TerminalObserverOutcome.RETURNED),
                "cleanup exception and observer outcome remain separate");
            if(mode==3)
                checkObserver(
                    logged.contains("G1_PVM_TERMINAL_OBSERVER_FAILED")&&
                    logged.contains("failureType=java.lang.IllegalArgumentException"),
                    "both cleanup and observer failures recorded without replay");
            else
                checkObserver(
                    !logged.contains("G1_PVM_TERMINAL_OBSERVER_FAILED"),
                    "healthy observer did not falsely report a failure");
            MonsterSpawnerPvmRuntime.FinalizeResult retried=
                graph.runtime.retrySettlement(npc.id);
            MonsterSpawnerPvmRuntime.FinalizeResult redelivered=
                graph.runtime.finalizeIfOwned(npc);
            checkObserver(
                retried.status==MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                redelivered.status==MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                graph.dropCalls==1&&observerCalls[0]==1,
                "cleanup failure does not replay drops or observer callbacks");
        }finally{
            System.setErr(oldErr);
            isolatedErr.close();
            if(generation>=0&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void checkObserver(boolean condition,String message){
        if(!condition)
            throw new AssertionError("G19.5.1/52 "+message);
    }

    /**
     * G19.5.5/56: repeatedly settle actual canonical spawner deaths
     * into one World-local runtime. The 128-entry observer ring remains
     * bounded and older failures survive only as observational totals.
     * A runtime without an observer records no fabricated attempts.
     */
    static void runObserverHistoryBoundaries(
        boolean installObserver
    )throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            final String recipient=installObserver
                ?"g1955-observer-bounded"
                :"g1956-observer-absent";
            generation=world.registerPlayer(player,recipient);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            RuntimeGraph graph=new RuntimeGraph(
                world,attackDefinition(),recipient);
            world.installMonsterSpawnerPvmRuntime(graph.runtime);
            final int[] callbackCount={0};
            if(installObserver)
                graph.runtime.installTerminalObserver(
                    terminal->{
                        if(terminal.status!=
                                MonsterSpawnerPvmRuntime
                                    .FinalizeStatus.FINALIZED||
                           terminal.finalization==null||
                           terminal.settlement==null)
                            throw new AssertionError(
                                "G1955 callback before real settlement");
                        callbackCount[0]++;
                        // The one failed observer is deliberately FIRST.
                        // After 131 completions its detailed ring entry
                        // is evicted, but the failure total must survive.
                        if(callbackCount[0]==1)
                            throw new IllegalStateException(
                                "G1955_FIRST_OBSERVER_AFTER_GROUND_FAILURE");
                    });

            int cycles=installObserver
                ?MonsterSpawnerPvmRuntime
                    .MAX_TERMINAL_OBSERVER_ATTEMPTS+3
                :3;
            java.util.ArrayList<EntityId> ids=
                new java.util.ArrayList<EntityId>();
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics
                firstSnapshot=null;

            for(int n=0;n<cycles;n++){
                if(n>0)
                    graph.spawner.activate(recipient,1);
                MonsterSpawnerPvmRuntime.SpawnResult spawned=
                    graph.runtime.spawnAndBind(
                        recipient,recipient,3088,3495,0);
                WorldNpc npc=spawned.spawn.combat.spawn.npc;
                ids.add(npc.id);
                NpcLifecycleService.DamageResult killed=
                    world.npcLifecycle().applyDamageOwned(
                        npc.id,10,world.clock().tick());
                if(!killed.newlyDied)
                    throw new AssertionError(
                        "G1955 expected exact canonical lethal NPC");
                MonsterSpawnerPvmRuntime.FinalizeResult finalized=
                    graph.runtime.finalizeIfOwned(npc);
                if(finalized==null||
                   finalized.status!=
                       MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED||
                   finalized.settlement==null||
                   graph.runtime.get(npc.id)!=null||
                   world.npcs().byId(npc.id)!=null)
                    throw new AssertionError(
                        "G1955 actual canonical teardown and drop");
                if(n==0)
                    firstSnapshot=
                        graph.runtime.terminalObserverDiagnostics();
            }

            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics after=
                graph.runtime.terminalObserverDiagnostics();
            GroundItem ground=world.groundItems().findOwned(
                995,3088,3495,0,recipient);
            checkObserver(ground!=null&&ground.amount==5*cycles&&
                    graph.dropCalls==cycles&&
                    graph.runtime.size()==0,
                "all genuine deaths settle exactly once into ground stack");

            if(installObserver){
                int limit=MonsterSpawnerPvmRuntime
                    .MAX_TERMINAL_OBSERVER_ATTEMPTS;
                MonsterSpawnerPvmRuntime.TerminalObserverAttempt
                    firstVisible=after.recentAttempts.get(0);
                MonsterSpawnerPvmRuntime.TerminalObserverAttempt
                    lastVisible=after.recentAttempts.get(limit-1);
                checkObserver(
                    firstSnapshot!=null&&
                    firstSnapshot.totalAttempts==1L&&
                    firstSnapshot.failedAttempts==1L&&
                    firstSnapshot.recentAttempts.size()==1&&
                    firstSnapshot.recentAttempts.get(0)
                        .npcId.equals(ids.get(0))&&
                    after.totalAttempts==cycles&&
                    after.failedAttempts==1L&&
                    after.evictedAttempts==3L&&
                    after.recentAttempts.size()==limit&&
                    firstVisible.npcId.equals(ids.get(3))&&
                    lastVisible.npcId.equals(ids.get(cycles-1))&&
                    firstVisible.outcome==
                        MonsterSpawnerPvmRuntime
                            .TerminalObserverOutcome.RETURNED&&
                    lastVisible.outcome==
                        MonsterSpawnerPvmRuntime
                            .TerminalObserverOutcome.RETURNED&&
                    callbackCount[0]==cycles,
                    "bounded ring evicts detail but preserves failure totals");

                boolean immutable=false;
                try{
                    after.recentAttempts.remove(0);
                }catch(UnsupportedOperationException expected){
                    immutable=true;
                }
                checkObserver(immutable&&after.recentAttempts.size()==limit,
                    "full ring returns detached immutable snapshot");
            }else
                checkObserver(
                    firstSnapshot!=null&&
                    firstSnapshot.totalAttempts==0&&
                    firstSnapshot.failedAttempts==0&&
                    firstSnapshot.recentAttempts.isEmpty()&&
                    after.totalAttempts==0L&&
                    after.failedAttempts==0L&&
                    after.evictedAttempts==0L&&
                    after.recentAttempts.isEmpty()&&
                    callbackCount[0]==0,
                    "runtime without installed observer invents no attempts");

            checkObserver(
                graph.runtime.retrySettlement(ids.get(cycles-1))
                    .status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                graph.dropCalls==cycles&&
                graph.runtime.terminalObserverDiagnostics()
                    .totalAttempts==
                    (installObserver?cycles:0),
                "post-settlement retry cannot replay old observers");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void configuredRuntimeFinalizes(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        World other=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                OWNER
            );
        RuntimeGraph alternate=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-alternate"
            );
        RuntimeGraph foreignWorldGraph=
            new RuntimeGraph(
                other,
                definition,
                "click-pvm-foreign-world"
            );

        expect(
            IllegalArgumentException.class,
            ()->world.installMonsterSpawnerPvmRuntime(
                foreignWorldGraph.runtime
            ),
            "cross-World runtime registration"
        );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );
        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        expect(
            IllegalStateException.class,
            ()->world.installMonsterSpawnerPvmRuntime(
                alternate.runtime
            ),
            "distinct runtime replacement"
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        WorldNpc foreign=null;

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            final int[] finalizerCalls={0};
            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                terminal={null};
            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->{
                        require(
                            hitBytes.size()>0,
                            "terminal teardown ran before hit packet publication"
                        );

                        finalizerCalls[0]++;
                        terminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            );
                    }
                );

            LocalCanonicalNpcAttackHandler.Result lethal=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(hitBytes)
                );

            GroundItem loot=
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "killer"
                );

            require(
                lethal!=null&&
                lethal.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                lethal.newlyDied&&
                lethal.hitpointsAfter==0&&
                finalizerCalls[0]==1,
                "runtime-owned lethal click"
            );

            require(
                terminal[0]!=null&&
                terminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.FINALIZED&&
                graph.runtime.get(target.id)==null&&
                graph.runtime.size()==0&&
                world.npcs().byId(target.id)==null&&
                world.npcLifecycle().get(target.id)==null&&
                graph.binder.get(target.id)==null&&
                graph.dropCalls==1&&
                loot!=null&&
                loot.amount==5,
                "configured runtime did not terminalize exactly once"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            player.id(),
                            generation,
                            System.currentTimeMillis()
                        );

            require(
                pending.size()==1&&
                pending.get(0).itemId==995&&
                pending.get(0).newAmount==5,
                "settled owner loot was not pending live presentation"
            );

            foreign=
                world.npcs().spawn(
                    definition,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle().register(
                foreign,
                10,
                "CUSTOM_LOCALLAB_FOREIGN_CANONICAL_HP"
            );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                foreign
            );
            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity foreignView=
                requireView(
                    npcs,
                    foreign
                );

            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                foreignTerminal={null};
            ByteArrayOutputStream foreignHitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler foreignHandler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->
                        foreignTerminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            )
                );

            // These two independent terminalization cases intentionally
            // occur on different authoritative World ticks. They are NOT
            // a test of bypassing the same WorldPlayer's click cadence.
            for(int elapsed=0;elapsed<12;elapsed++)
                world.clock().advance();

            LocalCanonicalNpcAttackHandler.Result foreignHit=
                foreignHandler.handle(
                    new NpcAction(
                        72,
                        foreignView.sceneIndex
                    ),
                    foreignView,
                    writer(foreignHitBytes)
                );

            NpcLifecycleService.Snapshot foreignDead=
                world.npcLifecycle().get(
                    foreign.id
                );

            require(
                foreignHit.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                foreignHit.newlyDied&&
                foreignTerminal[0]!=null&&
                foreignTerminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.NOT_OWNED&&
                world.npcs().byId(foreign.id)==foreign&&
                foreignDead!=null&&
                foreignDead.dead()&&
                graph.dropCalls==1&&
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "killer"
                ).amount==5,
                "configured runtime mutated foreign canonical NPC"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(foreign!=null){
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,
                    foreign.id
                );

                if(world.npcLifecycle().get(
                        foreign.id
                    )!=null)
                    world.npcLifecycle()
                        .unregisterExact(
                            foreign
                        );

                world.npcs().remove(
                    foreign.id
                );
            }

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            other.close();
            world.close();
        }
    }

    /**
     * G18.4.6 / #2558: the admitted lethal WorldPlayer can differ from
     * the configured spawn recipient. This is evidence of the current
     * CUSTOM_LOCALLAB policy boundary, NOT recovered SpawnPK rewards.
     */
    private static void differentCanonicalAttackerKeepsConfiguredSpawnRecipient(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer spawnRecipient=new WorldPlayer();
        WorldPlayer lethalAttacker=new WorldPlayer();
        long recipientGeneration=
            world.registerPlayer(spawnRecipient,"spawn-recipient");
        long attackerGeneration=
            world.registerPlayer(lethalAttacker,"lethal-attacker");

        lethalAttacker.movement().restoreAccountState(
            false,100,3087,3495,0
        );

        RuntimeGraph graph=
            new RuntimeGraph(world,definition,"distinct-spawn-owner");
        world.installMonsterSpawnerPvmRuntime(graph.runtime);

        NpcRegistry attackerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                lethalAttacker.id()
            );
        ByteArrayOutputStream relayBytes=new ByteArrayOutputStream();
        ServerPacketWriter relay=writer(relayBytes);

        try{
            SharedNpcWorldRelay.register(
                relay,world,lethalAttacker,attackerNpcs,
                lethalAttacker.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "distinct-spawn-owner",
                    "spawn-recipient",
                    3088,3495,0
                );

            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin original=
                spawned.runtime.recipientOrigin;

            require(original!=null&&
                    original.player==spawnRecipient&&
                    original.generation==recipientGeneration&&
                    original.stillCurrentIn(world)&&
                    lethalAttacker!=original.player,
                "configured spawn-time recipient distinct from attacker");

            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity projected=requireView(attackerNpcs,npc);

            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                terminal={null};
            ByteArrayOutputStream hitBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    lethalAttacker,
                    ()->attackerGeneration,
                    lethalAttacker.equipment(),
                    lethalAttacker.combatStyles(),
                    attackerNpcs,
                    (target,generation)->{},
                    target->terminal[0]=
                        world.finalizeMonsterSpawnerPvmIfOwned(target)
                );

            LocalCanonicalNpcAttackHandler.Result hit=
                handler.handle(
                    new NpcAction(72,projected.sceneIndex),
                    projected,
                    writer(hitBytes)
                );

            require(hit!=null&&
                    hit.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    hit.newlyDied&&
                    hitBytes.size()>0&&
                    terminal[0]!=null&&
                    terminal[0].status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal[0].recipientOrigin==original&&
                    terminal[0].recipientOrigin.player!=lethalAttacker&&
                    "spawn-recipient".equals(
                        terminal[0].finalization.recipientRef)&&
                    world.players().owns(
                        lethalAttacker,attackerGeneration)&&
                    graph.dropCalls==1,
                "lethal attack actor must not silently replace spawn recipient");

            require(
                world.groundItems().findOwned(
                    995,3088,3495,0,"spawn-recipient"
                )!=null&&
                world.groundItems().findOwned(
                    995,3088,3495,0,"lethal-attacker"
                )==null,
                "existing LocalLab drop settlement uses configured recipient, not lethal attacker"
            );

            System.out.println(
                "G1846_CANONICAL_ATTACKER_VS_SPAWN_RECIPIENT_PASS"+
                " lethalAttackerDistinct=true"+
                " configuredRecipientPreserved=true"+
                " spawnOriginNotRetargeted=true"+
                " localLabLootRecipientIsConfigured=true"+
                " originalServerRewardPolicyClaim=false"+
                " endToEndCreditPolicyFix=false"
            );
        }finally{
            SharedNpcWorldRelay.unregister(relay);
            if(world.players().owns(lethalAttacker,attackerGeneration))
                world.unregisterPlayer(
                    lethalAttacker,attackerGeneration
                );
            if(world.players().owns(spawnRecipient,recipientGeneration))
                world.unregisterPlayer(
                    spawnRecipient,recipientGeneration
                );
            world.close();
        }
    }

    private static void preTeardownFailureRetries(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "retry-killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-retry-owner",
                true
            );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "click-pvm-retry-owner",
                    "retry-killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->
                        world.finalizeMonsterSpawnerPvmIfOwned(
                            npc
                        ),
                    npc->
                        world.retryMonsterSpawnerPvmFinalizationIfPending(
                            npc
                        )
                );

            ByteArrayOutputStream lethalBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result lethal=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(lethalBytes)
                );

            MonsterSpawnerPvmRuntime.Snapshot pending=
                graph.runtime.get(
                    target.id
                );
            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                lethal.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                lethal.newlyDied&&
                lethalBytes.size()>0&&
                graph.dropCalls==1&&
                pending!=null&&
                pending.state==
                    MonsterSpawnerPvmRuntime.State
                        .FINALIZATION_PENDING&&
                world.npcs().byId(target.id)==target&&
                dead!=null&&dead.dead()&&
                world.groundItems().size()==0,
                "pre-teardown failure was not retained"
            );

            long cadenceBeforeRetry=
                handler.nextAllowedAttackTick();

            ByteArrayOutputStream retryBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result retry=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(retryBytes)
                );

            GroundItem loot=
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "retry-killer"
                );

            require(
                retry.status==
                    LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD&&
                retry.appliedDamage==0&&
                retryBytes.size()==0&&
                handler.nextAllowedAttackTick()==
                    cadenceBeforeRetry&&
                graph.dropCalls==2&&
                graph.runtime.get(target.id)==null&&
                world.npcs().byId(target.id)==null&&
                world.npcLifecycle().get(target.id)==null&&
                graph.binder.get(target.id)==null&&
                loot!=null&&
                loot.amount==5,
                "dead-click finalization retry did not terminalize exactly once"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void unexpectedErrorPropagates(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "error-killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-error-owner",
                false,
                true
            );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "click-pvm-error-owner",
                    "error-killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->
                        world.finalizeMonsterSpawnerPvmIfOwned(
                            npc
                        ),
                    npc->
                        world.retryMonsterSpawnerPvmFinalizationIfPending(
                            npc
                        )
                );

            ByteArrayOutputStream lethalBytes=
                new ByteArrayOutputStream();

            expect(
                AssertionError.class,
                ()->handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(lethalBytes)
                ),
                "pre-teardown Error propagation"
            );

            MonsterSpawnerPvmRuntime.Snapshot runtime=
                graph.runtime.get(
                    target.id
                );
            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                lethalBytes.size()>0&&
                runtime!=null&&
                runtime.state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                world.npcs().byId(target.id)==target&&
                dead!=null&&dead.dead()&&
                graph.binder.get(target.id)!=null&&
                graph.dropCalls==1&&
                world.groundItems().size()==0,
                "Error was converted into pending finalization"
            );

            long cadenceAfterError=
                handler.nextAllowedAttackTick();
            ByteArrayOutputStream deadBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result deadClick=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(deadBytes)
                );

            require(
                deadClick.status==
                    LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD&&
                deadClick.appliedDamage==0&&
                deadBytes.size()==0&&
                handler.nextAllowedAttackTick()==
                    cadenceAfterError&&
                graph.runtime.get(target.id)!=null&&
                graph.runtime.get(target.id).state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                graph.dropCalls==1&&
                world.groundItems().size()==0,
                "Error path was retried as FINALIZATION_PENDING"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static final class RuntimeGraph {
        final World world;
        final MonsterSpawnerService spawner;
        final NpcLifecycleService lifecycle;
        final NpcCombatRuntimeBinder binder;
        final MonsterSpawnerCombatBindingService combat;
        final MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding;
        final NpcDropResolutionService drops;
        final MonsterSpawnerNpcDeathFinalizationService
            finalizer;
        final NpcDropGroundSettlementService settlement;
        final MonsterSpawnerPvmRuntime runtime;

        int dropCalls;

        RuntimeGraph(
            World world,
            int definition,
            String owner
        ){
            this(
                world,
                definition,
                owner,
                false
            );
        }

        RuntimeGraph(
            World world,
            int definition,
            String owner,
            boolean failFirstDropResolution
        ){
            this(
                world,
                definition,
                owner,
                failFirstDropResolution,
                false
            );
        }

        RuntimeGraph(
            World world,
            int definition,
            String owner,
            boolean failFirstDropResolution,
            boolean errorFirstDropResolution
        ){
            this.world=world;
            this.lifecycle=world.npcLifecycle();

            spawner=
                new MonsterSpawnerService(
                    world.npcs()
                );

            spawner.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "click-pvm",
                        definition
                    )
                ),
                "CUSTOM_LOCALLAB_CLICK_PVM_CATALOG"
            );
            spawner.openSession(
                owner,
                "CUSTOM_LOCALLAB_CLICK_PVM_SPAWNER"
            );
            spawner.selectRow(
                owner,
                0
            );
            spawner.activate(
                owner,
                1
            );

            binder=
                new NpcCombatRuntimeBinder(
                    world,
                    context->combatPlan()
                );

            combat=
                new MonsterSpawnerCombatBindingService(
                    world,
                    spawner,
                    binder
                );

            lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "click-pvm-hp",
                                10,
                                "CUSTOM_LOCALLAB_CLICK_PVM_HP"
                            )
                );

            drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<
                            NpcDropResolutionService.Drop
                        > resolve(
                            NpcDropResolutionService
                                .DeathContext context
                        ){
                            dropCalls++;

                            if(errorFirstDropResolution&&
                               dropCalls==1)
                                throw new AssertionError(
                                    "synthetic pre-teardown Error"
                                );

                            if(failFirstDropResolution&&
                               dropCalls==1)
                                throw new IllegalStateException(
                                    "synthetic pre-teardown drop failure"
                                );

                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    5
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_CLICK_PVM_DROP";
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
                    "CUSTOM_LOCALLAB_CLICK_PVM_SETTLEMENT",
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
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "click-pvm-combat",
            "CUSTOM_LOCALLAB_CLICK_PVM_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .ineligible(),
            "CUSTOM_LOCALLAB_CLICK_PVM_TARGET",
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
                    return "CUSTOM_LOCALLAB_CLICK_PVM_APPROACH";
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
                    return "CUSTOM_LOCALLAB_CLICK_PVM_CADENCE";
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
                    return "CUSTOM_LOCALLAB_CLICK_PVM_NPC_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CLICK_PVM_CONTROLLER",
            "CUSTOM_LOCALLAB_CLICK_PVM_AI"
        );
    }

    private static int attackDefinition(){
        for(int definition=0;
            definition<16384;
            definition++){
            NpcEntity candidate;

            try{
                candidate=
                    new NpcEntity(
                        100,
                        definition,
                        3088,
                        3495
                    );
            }catch(IllegalArgumentException ignored){
                continue;
            }

            NpcInteractionRouter.Route route=
                NpcInteractionRouter.resolve(
                    new NpcAction(
                        72,
                        candidate.sceneIndex
                    ),
                    candidate
                );

            if(route.service==
                    NpcInteractionRouter.Service.ATTACK&&
               !CombatTargetRepository
                    .isCombatDummy(
                        definition
                    ))
                return definition;
        }

        throw new AssertionError(
            "no non-dummy exact option-2 Attack definition"
        );
    }

    private static NpcEntity requireView(
        NpcRegistry npcs,
        WorldNpc npc
    ){
        NpcEntity view=
            npcs.canonical(
                npc.id
            );

        require(
            view!=null&&
            npc.id.equals(
                view.canonicalId()
            ),
            "canonical projection missing id="+
            npc.id
        );

        return view;
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[4]
            )
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

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LocalCanonicalNpcAttackPvmRuntimeBridgeTest(){}
}
