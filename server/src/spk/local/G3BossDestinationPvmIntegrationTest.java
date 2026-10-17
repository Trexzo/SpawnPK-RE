package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;

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

        LocalLabSlayerRuntime.StartResult
            slayerStart=
                world.localLabSlayer()
                    .startMonsterHunter(
                        OWNER,
                        world.clock().tick()
                    );

        require(
            slayerStart.created&&
            slayerStart.status.active()&&
            slayerStart.status.task.objective.progress==0L&&
            slayerStart.status.task.objective.goal==1L,
            "G4.1 Blood Slayer task did not arm before Boss PvM"
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

        LocalMonsterSpawnerUiHandler monsterUi=
            LocalSession
                .resolveMonsterSpawnerUiAfterLogin(
                    activation,
                    world,
                    player,
                    generation,
                    OWNER
                );

        require(
            monsterUi!=null&&
            monsterUi.isBoundTo(world)&&
            monsterUi.isBoundToOwner(OWNER),
            "production Monster Spawner UI unavailable for session routing"
        );

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                player.bank(),
                player.miniPets(),
                player.petState(),
                views,
                player.movement(),
                new PetAccessoryState()
            );

        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                player.prayers(),
                player.playerState(),
                player.equipment(),
                player.combatStyles(),
                player.magic(),
                player.bank()
            );

        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                player.bank(),
                player.playerState()
            );

        final LocalMonsterSpawnerUiHandler.Result[]
            lastMonsterResult={null};
        final int[] monsterResultCount={0};

        LocalSessionUiActionHandler sessionUi=
            new LocalSessionUiActionHandler(
                player,
                new NativeItemLibraryService(),
                new DevControlCenter(),
                player.bank(),
                compCape,
                petDialogs,
                gameplay,
                player.movement(),
                true,
                player.equipment(),
                monsterUi,
                boss,
                new LocalSessionUiActionHandler.SessionBridge(){
                    @Override public void saveAccount(
                        String tag,
                        String reason
                    ){}

                    @Override public void clearDialogNumberKeys(){}

                    @Override public void handleDevPanelWidget(
                        int widget,
                        ServerPacketWriter packets,
                        String tag
                    ){}

                    @Override public void applyPetDialog(
                        LocalPetInventoryDialogHandler.Result result,
                        String tag
                    ){}

                    @Override public void handleMonsterSpawnerResult(
                        LocalMonsterSpawnerUiHandler.Result result,
                        ServerPacketWriter packets,
                        String tag
                    )throws java.io.IOException{
                        monsterResultCount[0]++;
                        lastMonsterResult[0]=result;

                        LocalSession
                            .forwardMonsterSpawnerUiResult(
                                activation,
                                world,
                                player,
                                generation,
                                OWNER,
                                result,
                                packets,
                                tag
                            );
                    }

                    @Override public void requestLogout(){}
                }
            );

        try{
            int bossOpenBefore=wire.size();

            sessionUi.handleWidget(
                1170,
                writer,
                "[g3-pvm] "
            );

            require(
                boss.isOpen()&&
                wire.size()>bossOpenBefore,
                "session UI did not open exact Boss Teleport root"
            );

            sessionUi.handleWidget(
                BossTeleportPresentation
                    .rowWidget(
                        LocalBossTeleportUiHandler
                            .CONFIGURED_ROW
                    ),
                writer,
                "[g3-pvm] "
            );

            BossTeleportService.PlayerSnapshot
                bossSelection=
                    boss.snapshot();

            require(
                bossSelection!=null&&
                bossSelection.hasSelection()&&
                LocalBossTeleportUiHandler
                    .BOSS_KEY
                    .equals(
                        bossSelection
                            .selectedBossKey
                    ),
                "session UI Boss row selection"
            );

            int monsterResultsBeforeTeleport=
                monsterResultCount[0];

            sessionUi.handleWidget(
                BossTeleportPresentation
                    .TELEPORT_WIDGET,
                writer,
                "[g3-pvm] "
            );

            require(
                !boss.isOpen()&&
                monsterResultCount[0]==
                    monsterResultsBeforeTeleport&&
                regionId(
                    player.movement().x(),
                    player.movement().y()
                )==
                    LocalBossTeleportUiHandler
                        .REGION_ID&&
                player.movement().transientRegion(),
                "session UI Boss Teleport/handoff did not land in live region 16168"
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

            int beforeSpawnerRow=
                monsterResultCount[0];

            sessionUi.handleWidget(
                MonsterSpawnerPresentation
                    .rowWidget(0),
                writer,
                "[g3-pvm] "
            );

            LocalMonsterSpawnerUiHandler.Result
                row=
                    lastMonsterResult[0];

            require(
                monsterResultCount[0]==
                    beforeSpawnerRow+1&&
                row!=null&&
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
                "session UI exact-client LocalLab encounter definition selection"
            );

            sessionUi.handleWidget(
                MonsterSpawnerPresentation
                    .TOGGLE_WIDGET,
                writer,
                "[g3-pvm] "
            );

            LocalMonsterSpawnerUiHandler.Result
                activated=
                    lastMonsterResult[0];

            require(
                monsterResultCount[0]==
                    beforeSpawnerRow+2&&
                activated!=null&&
                activated.status==
                    LocalMonsterSpawnerUiHandler
                        .Status.ACTIVATED,
                "session UI Monster Spawner did not activate at Boss destination"
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

            LocalLabSlayerRuntime.StatusSnapshot
                slayerAfterKill=
                    world.localLabSlayer()
                        .status(
                            OWNER
                        );

            require(
                slayerAfterKill.complete()&&
                slayerAfterKill.task.objective.progress==1L&&
                slayerAfterKill.task.objective.goal==1L,
                "canonical Boss-destination finalization did not complete G4.1 Blood Slayer"
            );

            String pickupMove=
                player.movement()
                    .accept(
                        new MovementRequest(
                            164,
                            false,
                            new int[]{drop.tile.x},
                            new int[]{drop.tile.y},
                            new byte[0]
                        )
                    );

            require(
                pickupMove.startsWith("ACCEPTED"),
                "Boss-destination movement to owner drop rejected "+
                    pickupMove
            );

            MovementState.Tick pickupStep=
                player.movement()
                    .advance();

            require(
                pickupStep!=null&&
                player.movement().x()==
                    drop.tile.x&&
                player.movement().y()==
                    drop.tile.y&&
                player.movement().plane()==
                    drop.tile.plane&&
                player.movement()
                    .transientRegion(),
                "Boss-destination movement did not retain transient loaded window"
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

            verifyFreshWorldPersistence(
                player
            );

            System.out.println(
                "G3_COMMANDLESS_BOSS_PVM_LOOP_PASS"+
                " sessionUiOwner=true"+
                " bossRoot=true"+
                " teleport60448=true"+
                " automaticSpawnerRoot=true"+
                " monsterRowViaSessionUi=true"+
                " monsterToggleViaSessionUi=true"+
                " noMonsterSpawnerCommand=true"+
                " noManualSpawnerOpen=true"+
                " canonicalAttack=true"+
                " ownerDrop=true"+
                " groundTake=true"+
                " pvmProgression=true"+
                " bloodSlayerMonsterHunter=true"+
                " scheduledRespawn=true"+
                " freshWorldPersistence=true"+
                " vetionNpcClaim=false"+
                " originalSpawnpkBossPolicyClaim=false"
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

    private static void verifyFreshWorldPersistence(
        WorldPlayer player
    )throws Exception{
        Path root=
            Files.createTempDirectory(
                "spawnpk-g3-boss-pvm-"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        World restarted=null;
        boolean temporaryRepositoryClean=false;

        try{
            repository.save(
                PlayerSnapshotCodec.capture(
                    OWNER,
                    player,
                    0
                )
            );

            restarted=
                World.isolatedForTest(
                    60_000L,
                    repository
                );

            Optional<PlayerSnapshot> loaded=
                restarted.persistence()
                    .load(
                        OWNER
                    );

            require(
                loaded.isPresent(),
                "fresh World persistence owner did not load Boss PvM snapshot"
            );

            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                loaded.get(),
                restored
            );

            require(
                restarted.pvmRecords()
                    .snapshot(
                        restored
                    )
                    .kills==1L,
                "fresh World Boss PvM kill progression mismatch"
            );

            require(
                restored.bank()
                    .inventoryCount(995)==1,
                "fresh World Boss PvM owner-drop coin mismatch"
            );
        }finally{
            if(restarted!=null)
                restarted.close();

            deleteTree(root);
            temporaryRepositoryClean=
                !Files.exists(root);
        }

        require(
            temporaryRepositoryClean,
            "temporary Boss PvM repository cleanup failed path="+
                root
        );

        System.out.println(
            "G3_BOSS_PVM_FRESH_WORLD_PERSISTENCE_PASS"+
            " repositorySave=true"+
            " freshWorldLoad=true"+
            " pvmKills1=true"+
            " ownerDropCoins1=true"+
            " snapshotValidated=true"+
            " temporaryRepositoryClean=true"+
            " transientBossRuntimePersistenceClaim=false"+
            " originalSpawnpkBossPolicyClaim=false"
        );
    }

    private static void deleteTree(
        Path root
    )throws Exception{
        if(root==null||
           !Files.exists(root))
            return;

        try(java.util.stream.Stream<Path> paths=
                Files.walk(root)){
            Iterator<Path> iterator=
                paths.sorted(
                    Comparator.reverseOrder()
                ).iterator();

            while(iterator.hasNext())
                Files.deleteIfExists(
                    iterator.next()
                );
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
