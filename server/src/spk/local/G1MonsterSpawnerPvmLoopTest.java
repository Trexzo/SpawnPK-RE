package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.SortedMap;

public final class G1MonsterSpawnerPvmLoopTest {
    public static void main(String[] args)
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "pvm"
            );

        player.movement().restoreAccountState(
            false,
            100,
            MovementState.INITIAL_X,
            MovementState.INITIAL_Y,
            0
        );

        LocalMonsterSpawnerActivationRuntime activation=
            LocalLabMonsterSpawnerProvisioning
                .create(
                    world
                );
        MonsterSpawnerService spawner=
            activation.service();

        NpcRegistry views=
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
                views,
                player.movement()
            );

            MonsterSpawnerService.SessionSnapshot opened=
                spawner.openSession(
                    "pvm",
                    LocalLabMonsterSpawnerProvisioning
                        .SESSION_AUTHORITY
                );

            MonsterSpawnerService.SessionSnapshot selected=
                spawner.selectRow(
                    "pvm",
                    0
                );

            MonsterSpawnerService.SessionSnapshot activated=
                spawner.activateIfCurrent(
                    "pvm",
                    selected,
                    1
                );

            MonsterSpawnerPvmSpawnExecutor.Result
                firstSpawn=
                    activation.executor()
                        .execute(
                            "pvm"
                        );

            require(
                firstSpawn.status==
                    MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED&&
                firstSpawn.spawn!=null,
                "first canonical PvM spawn"
            );

            WorldNpc firstNpc=
                firstSpawn.spawn.spawn.combat.spawn.npc;

            require(
                firstNpc.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                "first definition"
            );

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity firstView=
                requireView(
                    views,
                    firstNpc
                );

            LocalCanonicalNpcAttackHandler attacks=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    views,
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

            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result firstKill=
                attacks.handle(
                    new NpcAction(
                        72,
                        firstView.sceneIndex
                    ),
                    firstView,
                    writer(hitBytes)
                );

            require(
                firstKill!=null&&
                firstKill.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                firstKill.newlyDied&&
                firstKill.hitpointsAfter==0,
                "first canonical PvM kill"
            );

            GroundItem firstDrop=
                world.groundItems()
                    .findOwned(
                        995,
                        MovementState.INITIAL_X+1,
                        MovementState.INITIAL_Y,
                        0,
                        "pvm"
                    );

            require(
                firstDrop!=null&&
                firstDrop.amount==1,
                "first owner-scoped drop"
            );

            PvmRecordService.Snapshot firstRecord=
                world.pvmRecords()
                    .snapshot(
                        player
                    );

            require(
                firstRecord.kills==1L,
                "first PvM progression "+
                firstRecord
            );

            MonsterSpawnerService.SessionSnapshot
                afterFirst=
                    spawner.getSession(
                        "pvm"
                    );

            require(
                afterFirst!=null&&
                !afterFirst.active&&
                afterFirst.remainingSpawnBudget==0&&
                afterFirst.spawnedNpcIds.isEmpty(),
                "post-kill retained respawn session"
            );

            require(
                world.events().size()==1,
                "respawn event not scheduled"
            );

            player.movement().restoreAccountState(
                false,
                100,
                firstDrop.tile.x,
                firstDrop.tile.y,
                firstDrop.tile.plane
            );

            ByteArrayOutputStream pickupBytes=
                new ByteArrayOutputStream();
            ServerPacketWriter pickupWriter=
                writer(
                    pickupBytes
                );
            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    pickupWriter,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );
            LocalGroundItemInteractionHandler ground=
                new LocalGroundItemInteractionHandler(
                    world,
                    player.bank(),
                    player.movement()
                );

            LocalGroundItemInteractionHandler.Result
                taken=
                    ground.handle(
                        new GroundItemInteraction(
                            236,
                            3,
                            995,
                            firstDrop.tile.x,
                            firstDrop.tile.y
                        ),
                        "pvm",
                        scene,
                        pickupWriter
                    );

            require(
                taken!=null&&
                "GROUND_TAKE".equals(
                    taken.saveReason
                )&&
                player.bank()
                    .inventoryCount(
                        995
                    )==1&&
                world.groundItems()
                    .byId(
                        firstDrop.id
                    )==null,
                "ground pickup/persistence handoff"
            );

            PlayerSnapshot captured=
                PlayerSnapshotCodec.capture(
                    "pvm",
                    player,
                    0
                );

            require(
                "1".equals(
                    captured.value(
                        "extension.pvm-record.kills"
                    )
                ),
                "PvM progression not captured with pickup reward"
            );

            for(int tick=1;
                tick<
                    G1MonsterSpawnerPvmLoopService
                        .RESPAWN_DELAY_TICKS;
                tick++)
                world.observePulse(
                    tick*600L
                );

            require(
                spawner.getSession(
                    "pvm"
                ).spawnedNpcIds.isEmpty(),
                "NPC respawned before due tick"
            );

            world.observePulse(
                G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS*
                    600L
            );

            MonsterSpawnerService.SessionSnapshot
                respawnedSession=
                    spawner.getSession(
                        "pvm"
                    );

            require(
                respawnedSession!=null&&
                respawnedSession.spawnedNpcIds.size()==1&&
                !respawnedSession.active&&
                respawnedSession.remainingSpawnBudget==0,
                "same-session respawn missing"
            );

            EntityId secondId=
                respawnedSession
                    .spawnedNpcIds
                    .get(0);
            WorldNpc secondNpc=
                world.npcs().byId(
                    secondId
                );

            require(
                secondNpc!=null&&
                secondNpc.definitionId==
                    firstNpc.definitionId&&
                !secondNpc.id.equals(
                    firstNpc.id
                ),
                "respawn identity/definition"
            );

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity secondView=
                requireView(
                    views,
                    secondNpc
                );

            LocalCanonicalNpcAttackHandler.Result secondKill=
                attacks.handle(
                    new NpcAction(
                        72,
                        secondView.sceneIndex
                    ),
                    secondView,
                    writer(
                        new ByteArrayOutputStream()
                    )
                );

            require(
                secondKill!=null&&
                secondKill.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                secondKill.newlyDied,
                "second canonical PvM kill"
            );

            require(
                world.pvmRecords()
                    .snapshot(
                        player
                    )
                    .kills==2L,
                "second PvM kill did not progress once"
            );

            SortedMap<String,String> extension=
                player.snapshotExtensions()
                    .namespace(
                        PvmRecordService.NAMESPACE
                    );

            require(
                "2".equals(
                    extension.get(
                        "kills"
                    )
                )&&
                PvmRecordService.AUTHORITY.equals(
                    extension.get(
                        "authority"
                    )
                ),
                "live PvM extension state"
            );

            System.out.println(
                "G1_MONSTER_SPAWNER_PVM_LOOP_PASS "+
                "canonicalAttack=true "+
                "terminalDrop=true "+
                "ownerScopedCoin=true "+
                "groundTake=true "+
                "groundTakeSave=true "+
                "pvmProgression=true "+
                "snapshotProgression=true "+
                "scheduledRespawn=true "+
                "sameDefinitionRespawn=true "+
                "secondKillRepeatable=true "+
                "respawnDelayTicks="+
                G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS+
                " dropItem=995x1 "+
                "authority="+
                G1MonsterSpawnerPvmLoopService.AUTHORITY
            );
        }finally{
            try{
                SharedNpcWorldRelay.unregister(
                    relayWriter
                );
            }catch(Throwable ignored){}

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static NpcEntity requireView(
        NpcRegistry views,
        WorldNpc npc
    ){
        NpcEntity view=
            views.canonical(
                npc.id
            );

        require(
            view!=null&&
            npc.id.equals(
                view.canonicalId()
            ),
            "canonical projection missing "+
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

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G1MonsterSpawnerPvmLoopTest(){}
}
