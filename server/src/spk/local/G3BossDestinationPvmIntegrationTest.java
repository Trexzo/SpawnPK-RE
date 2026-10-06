package spk.local;

import java.io.ByteArrayOutputStream;

public final class G3BossDestinationPvmIntegrationTest {
    private static final String OWNER=
        "g3-boss-destination-pvm";

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );

        LocalMonsterSpawnerActivationRuntime activation=
            LocalLabMonsterSpawnerProvisioning
                .create(
                    world
                );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            writer(wire);

        LocalRegionDevCommandHandler regionDev=
            new LocalRegionDevCommandHandler(
                world,
                player,
                player.movement(),
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    player.movement(),
                    player.equipment()
                ),
                new CombatEngine(
                    new DevAuthorityWorkbench()
                ),
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                ),
                player.petState(),
                new HomeWorldRuntimePlan(),
                ()->{}
            );

        LocalTeleportNavigationRuntime navigation=
            new LocalTeleportNavigationRuntime(
                regionDev
            );

        final SceneUpdatePublisher[] scene={
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            )
        };

        LocalBossTeleportUiHandler boss=
            new LocalBossTeleportUiHandler(
                ()->OWNER,
                (packets,tag)->{
                    LocalTeleportNavigationRuntime.Result
                        result=
                            navigation.request(
                                OWNER,
                                TeleportNavigationService
                                    .EntryKind.BOSS,
                                scene[0],
                                packets
                            );

                    if(result.relocation!=null&&
                       result.relocation.scenePublisher!=null)
                        scene[0]=
                            result.relocation
                                .scenePublisher;

                    return result.succeeded();
                }
            );

        NpcRegistry views=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(
                relayBytes
            );
        boolean relayRegistered=false;

        try{
            boss.open(
                writer
            );
            LocalBossTeleportUiHandler.Result
                selected=
                    boss.handleWidget(
                        BossTeleportPresentation
                            .rowWidget(
                                LocalBossTeleportUiHandler
                                    .CONFIGURED_ROW
                            ),
                        writer,
                        "[g3-pvm] "
                    );

            require(
                selected.status==
                    LocalBossTeleportUiHandler
                        .Status.SELECTED,
                "Boss row selection"
            );

            LocalBossTeleportUiHandler.Result
                teleported=
                    boss.handleWidget(
                        BossTeleportPresentation
                            .TELEPORT_WIDGET,
                        writer,
                        "[g3-pvm] "
                    );

            require(
                teleported.status==
                    LocalBossTeleportUiHandler
                        .Status.TELEPORTED&&
                teleported.teleportSucceeded&&
                regionId(
                    player.movement().x(),
                    player.movement().y()
                )==
                    LocalBossTeleportUiHandler
                        .REGION_ID&&
                player.movement().transientRegion(),
                "Boss Teleport did not land in live region 16168"
            );

            Tile landing=
                new Tile(
                    player.movement().x(),
                    player.movement().y(),
                    player.movement().plane()
                );

            require(
                !WorldCollisionAuthority
                    .blockedTile(
                        landing.x,
                        landing.y,
                        landing.plane
                    ),
                "Boss landing is blocked"
            );

            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                views,
                player.movement()
            );
            relayRegistered=true;

            LocalMonsterSpawnerUiHandler ui=
                LocalSession
                    .resolveMonsterSpawnerUiAfterLogin(
                        activation,
                        world,
                        player,
                        generation,
                        OWNER
                    );

            require(
                ui!=null&&
                ui.isBoundTo(world)&&
                ui.isBoundToOwner(OWNER),
                "production Monster Spawner UI unavailable after Boss teleport"
            );

            ui.open(
                writer
            );

            LocalMonsterSpawnerUiHandler.Result
                row=
                    ui.handle(
                        MonsterSpawnerPresentation
                            .rowWidget(0),
                        writer
                    );

            LocalSession
                .forwardMonsterSpawnerUiResult(
                    activation,
                    world,
                    player,
                    generation,
                    OWNER,
                    row,
                    writer,
                    "[g3-pvm] "
                );

            require(
                row.status==
                    LocalMonsterSpawnerUiHandler
                        .Status.ROW_SELECTED&&
                row.session
                    .selectedDefinitionId!=null&&
                row.session
                    .selectedDefinitionId
                    .intValue()==
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID&&
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID==1,
                "exact-client LocalLab encounter definition selection"
            );

            LocalMonsterSpawnerUiHandler.Result
                activated=
                    ui.handle(
                        MonsterSpawnerPresentation
                            .TOGGLE_WIDGET,
                        writer
                    );

            LocalSession
                .forwardMonsterSpawnerUiResult(
                    activation,
                    world,
                    player,
                    generation,
                    OWNER,
                    activated,
                    writer,
                    "[g3-pvm] "
                );

            require(
                activated.status==
                    LocalMonsterSpawnerUiHandler
                        .Status.ACTIVATED,
                "Monster Spawner did not activate at Boss destination"
            );

            MonsterSpawnerService.SessionSnapshot
                session=
                    activation.service()
                        .getSession(
                            OWNER
                        );

            require(
                session!=null&&
                session.spawnedNpcIds.size()==1&&
                !session.active&&
                session.remainingSpawnBudget==0,
                "Boss-destination Monster Spawner session postimage"
            );

            WorldNpc target=
                world.npcs()
                    .byId(
                        session.spawnedNpcIds
                            .get(0)
                    );

            require(
                target!=null&&
                target.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID&&
                regionId(
                    target.tile().x,
                    target.tile().y
                )==
                    LocalBossTeleportUiHandler
                        .REGION_ID&&
                target.tile()
                    .chebyshev(
                        landing
                    )==1&&
                WorldCollisionAuthority
                    .canStep(
                        landing.x,
                        landing.y,
                        landing.plane,
                        target.tile().x,
                        target.tile().y
                    ),
                "PvM target was not spawned collision-safely adjacent in Boss region"
            );

            require(
                target.tile().x!=
                    MovementState.INITIAL_X+1||
                target.tile().y!=
                    MovementState.INITIAL_Y,
                "Boss-destination PvM target still used fixed HOME placement"
            );

            SharedNpcWorldRelay
                .syncRemotePets(
                    relayWriter
                );

            NpcEntity view=
                requireView(
                    views,
                    target
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

            LocalCanonicalNpcAttackHandler.Result
                kill=
                    attacks.handle(
                        new NpcAction(
                            72,
                            view.sceneIndex
                        ),
                        view,
                        writer(
                            new ByteArrayOutputStream()
                        )
                    );

            require(
                kill!=null&&
                kill.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.HIT&&
                kill.maxHitpoints==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_HITPOINTS&&
                kill.maxHitpoints==10&&
                kill.hitpointsAfter==0&&
                kill.newlyDied,
                "canonical Boss-destination PvM kill"
            );

            GroundItem drop=
                world.groundItems()
                    .findOwned(
                        995,
                        target.tile().x,
                        target.tile().y,
                        target.tile().plane,
                        OWNER
                    );

            require(
                drop!=null&&
                drop.amount==1,
                "Boss-destination owner-scoped coin drop"
            );

            require(
                world.pvmRecords()
                    .snapshot(
                        player
                    )
                    .kills==1L,
                "Boss-destination kill did not advance PvM record"
            );

            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    drop.tile.x,
                    drop.tile.y,
                    drop.tile.plane
                );

            LocalGroundItemInteractionHandler
                ground=
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
                            drop.tile.x,
                            drop.tile.y
                        ),
                        OWNER,
                        scene[0],
                        writer
                    );

            require(
                taken!=null&&
                "GROUND_TAKE".equals(
                    taken.saveReason
                )&&
                player.bank()
                    .inventoryCount(995)==1&&
                world.groundItems()
                    .byId(drop.id)==null,
                "Boss-destination coin pickup"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    player,
                    0
                );

            require(
                "1".equals(
                    snapshot.value(
                        "extension.pvm-record.kills"
                    )
                ),
                "Boss-destination PvM record not persisted"
            );

            require(
                world.events().size()==1,
                "certified Monster Spawner respawn was not scheduled"
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
                activation.service()
                    .getSession(OWNER)
                    .spawnedNpcIds
                    .isEmpty(),
                "Boss encounter respawned early"
            );

            world.observePulse(
                G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS*
                    600L
            );

            MonsterSpawnerService.SessionSnapshot
                respawned=
                    activation.service()
                        .getSession(
                            OWNER
                        );

            require(
                respawned!=null&&
                respawned.spawnedNpcIds.size()==1,
                "Boss encounter did not preserve certified respawn loop"
            );

            WorldNpc second=
                world.npcs()
                    .byId(
                        respawned.spawnedNpcIds
                            .get(0)
                    );

            Tile current=
                new Tile(
                    player.movement().x(),
                    player.movement().y(),
                    player.movement().plane()
                );

            require(
                second!=null&&
                second.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID&&
                regionId(
                    second.tile().x,
                    second.tile().y
                )==
                    LocalBossTeleportUiHandler
                        .REGION_ID&&
                second.tile()
                    .chebyshev(
                        current
                    )==1&&
                WorldCollisionAuthority
                    .canStep(
                        current.x,
                        current.y,
                        current.plane,
                        second.tile().x,
                        second.tile().y
                    ),
                "certified respawn did not remain current-owner/collision-safe in Boss region"
            );

            System.out.println(
                "G3_BOSS_DESTINATION_PVM_PASS"+
                " bossTeleport=true"+
                " region16168=true"+
                " collisionSafeLanding=true"+
                " monsterSpawnerReachable=true"+
                " currentOwnerPlacement=true"+
                " definition1ExactClient=true"+
                " localLabHp10=true"+
                " canonicalAttack=true"+
                " terminalDeath=true"+
                " ownerScopedCoin=true"+
                " groundTake=true"+
                " pvmProgression=true"+
                " snapshotProgression=true"+
                " scheduledRespawn=true"+
                " respawnStaysBossRegion=true"+
                " homeFixedPlacementRemoved=true"+
                " vetionNpcClaim=false"+
                " originalSpawnpkBossPolicyClaim=false"
            );
        }finally{
            if(relayRegistered)
                try{
                    SharedNpcWorldRelay
                        .unregister(
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

    private static int regionId(
        int x,
        int y
    ){
        return ((x>>6)<<8)|(y>>6);
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
            "canonical NPC projection missing"
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

    private G3BossDestinationPvmIntegrationTest(){}
}
