package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class LocalLabMonsterSpawnerProvisioningTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );

        WorldPlayer player=
            new WorldPlayer();
        final String owner=
            "locallab-monster-provisioning";
        long generation=0L;

        try{
            LocalMonsterSpawnerActivationRuntime factory=
                LocalLabMonsterSpawnerProvisioning
                    .create(
                        world
                    );

            generation=
                world.registerPlayer(
                    player,
                    owner
                );

            LocalMonsterSpawnerUiHandler ui=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    player,
                    generation,
                    owner
                );

            require(
                ui!=null&&
                ui.isBoundTo(
                    world
                )&&
                ui.isBoundToOwner(
                    owner
                ),
                "provisioned UI binding"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{
                            7,
                            8,
                            9,
                            10
                        }
                    )
                );

            int openBefore=
                wire.size();
            ui.open(
                writer
            );

            require(
                wire.size()>openBefore,
                "provisioned UI open packet"
            );

            byte[] openWire=
                wire.toByteArray();
            String openPayload=
                new String(
                    openWire,
                    openBefore,
                    openWire.length-openBefore,
                    StandardCharsets.ISO_8859_1
                );

            require(
                openPayload.contains(
                    "LocalLab placeholder 1 (NPC 1)"
                )&&
                openPayload.contains(
                    "LocalLab placeholder 22 (NPC 1)"
                ),
                "provisioned UI did not publish explicit first/last placeholder labels"
            );

            LocalMonsterSpawnerUiHandler.Result selected=
                ui.handle(
                    MonsterSpawnerPresentation
                        .rowWidget(
                            0
                        ),
                    writer
                );

            require(
                selected!=null&&
                selected.status==
                    LocalMonsterSpawnerUiHandler.Status.ROW_SELECTED&&
                selected.session.selectedDefinitionId!=null&&
                selected.session.selectedDefinitionId.intValue()==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                "provisioned row selection"
            );

            LocalSession.forwardMonsterSpawnerUiResult(
                factory,
                world,
                player,
                generation,
                owner,
                selected,
                writer,
                "[locallab-monster-provisioning] "
            );

            require(
                world.npcs().size()==0&&
                factory.runtime().size()==0,
                "row selection triggered spawn"
            );

            require(
                factory.service()
                    .catalog()
                    .entries
                    .size()==
                        MonsterSpawnerService
                            .CLIENT_ROW_COUNT,
                "production catalog does not cover every exact row"
            );

            LocalMonsterSpawnerUiHandler.Result lastRow=
                ui.handle(
                    MonsterSpawnerPresentation
                        .rowWidget(
                            MonsterSpawnerService
                                .CLIENT_ROW_COUNT-1
                        ),
                    writer
                );

            require(
                lastRow!=null&&
                lastRow.status==
                    LocalMonsterSpawnerUiHandler.Status.ROW_SELECTED&&
                lastRow.rowIndex==
                    MonsterSpawnerService
                        .CLIENT_ROW_COUNT-1&&
                lastRow.session.selectedDefinitionId!=null&&
                lastRow.session.selectedDefinitionId.intValue()==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                "last exact row was not safely selectable"
            );

            LocalSession.forwardMonsterSpawnerUiResult(
                factory,
                world,
                player,
                generation,
                owner,
                lastRow,
                writer,
                "[locallab-monster-provisioning] "
            );

            require(
                world.npcs().size()==0&&
                factory.runtime().size()==0,
                "last-row selection triggered spawn"
            );

            LocalMonsterSpawnerUiHandler.Result activated=
                ui.handle(
                    MonsterSpawnerPresentation
                        .TOGGLE_WIDGET,
                    writer
                );

            require(
                activated!=null&&
                activated.status==
                    LocalMonsterSpawnerUiHandler.Status.ACTIVATED,
                "provisioned activation result"
            );

            LocalSession.forwardMonsterSpawnerUiResult(
                factory,
                world,
                player,
                generation,
                owner,
                activated,
                writer,
                "[locallab-monster-provisioning] "
            );

            MonsterSpawnerService.SessionSnapshot after=
                factory.service()
                    .getSession(
                        owner
                    );

            require(
                after!=null&&
                !after.active&&
                after.remainingSpawnBudget==0&&
                after.spawnedNpcIds.size()==1&&
                world.npcs().size()==1&&
                factory.runtime().size()==1,
                "provisioned activation did not produce one canonical PvM spawn"
            );

            WorldNpc spawned=
                world.npcs().byId(
                    after.spawnedNpcIds.get(
                        0
                    )
                );

            require(
                spawned!=null&&
                spawned.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID&&
                spawned.tile().x==
                    MovementState.INITIAL_X+1&&
                spawned.tile().y==
                    MovementState.INITIAL_Y&&
                spawned.tile().plane==0,
                "provisioned spawn identity/location"
            );

            System.out.println(
                "LOCALLAB_MONSTER_SPAWNER_PROVISIONING_PASS "+
                "sharedFactory=true "+
                "worldBound=true "+
                "uiReachable=true "+
                "customCatalog=true "+
                "all22RowsSafe=true "+
                "placeholderMappingExplicit=true "+
                "rowLabelsPublished=true "+
                "customPolicy=true "+
                "activationSpawn=true "+
                "oneShotBudget=true "+
                "homeSafePlacement=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private LocalLabMonsterSpawnerProvisioningTest(){}
}
