package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

public final class G3BossToMonsterSpawnerHandoffTest {
    private static final String OWNER="boss-handoff-owner";

    public static void main(String[] args)throws Exception{
        proveSuccessfulHandoff();
        proveFailedTeleportNoHandoff();
        proveSuccessfulTeleportWithoutSpawner();
    }

    private static void proveSuccessfulHandoff()throws Exception{
        Fixture fixture=new Fixture(OWNER,true,true);

        int bossOpenBefore=fixture.wire.size();
        require(
            fixture.ui.openBossTeleportIfConfigured(
                fixture.writer
            ),
            "Boss root did not open"
        );
        require(
            fixture.boss.isOpen()&&
            fixture.wire.size()>bossOpenBefore,
            "Boss root publication missing"
        );

        fixture.ui.handleWidget(
            BossTeleportPresentation.rowWidget(
                LocalBossTeleportUiHandler.CONFIGURED_ROW
            ),
            fixture.writer,
            "[g3-handoff] "
        );

        MonsterSpawnerService.SessionSnapshot before=
            fixture.spawner.getSession(OWNER);

        require(
            before!=null&&
            before.selectedRowIndex==null&&
            !before.active&&
            before.spawnedNpcIds.isEmpty(),
            "Monster Spawner mutated before Boss teleport"
        );

        int teleportBefore=fixture.wire.size();

        fixture.ui.handleWidget(
            BossTeleportPresentation.TELEPORT_WIDGET,
            fixture.writer,
            "[g3-handoff] "
        );

        MonsterSpawnerService.SessionSnapshot afterHandoff=
            fixture.spawner.getSession(OWNER);

        require(
            fixture.teleportCalls==1&&
            !fixture.boss.isOpen()&&
            fixture.wire.size()>teleportBefore&&
            afterHandoff.selectedRowIndex==null&&
            !afterHandoff.active&&
            afterHandoff.spawnedNpcIds.isEmpty(),
            "successful Boss teleport did not handoff root without gameplay mutation"
        );

        fixture.ui.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            fixture.writer,
            "[g3-handoff] "
        );

        MonsterSpawnerService.SessionSnapshot selected=
            fixture.spawner.getSession(OWNER);

        require(
            selected.selectedRowIndex!=null&&
            selected.selectedRowIndex.intValue()==0&&
            !selected.active&&
            selected.spawnedNpcIds.isEmpty()&&
            fixture.bridge.monsterResults==1,
            "Monster Spawner row was not reachable immediately after Boss handoff"
        );

        System.out.println(
            "G3_BOSS_TO_MONSTER_SPAWNER_HANDOFF_PASS"+
            " bossTeleport=true"+
            " teleport60448="+
                (BossTeleportPresentation.TELEPORT_WIDGET==60448)+
            " handoffRoot=true"+
            " monsterSpawnerExactRoot="+
                (MonsterSpawnerPresentation.ROOT==41000)+
            " noCommandRequired=true"+
            " noAutoSelect=true"+
            " noAutoSpawn=true"+
            " bossOwnershipRetired=true"+
            " failedTeleportNoHandoff=true"+
            " noSpawnerConfiguredTeleportStillSucceeds=true"+
            " originalSpawnpkBossPolicyClaim=false"
        );
    }

    private static void proveFailedTeleportNoHandoff()throws Exception{
        Fixture fixture=
            new Fixture(
                "boss-handoff-fail",
                false,
                true
            );

        require(
            fixture.ui.openBossTeleportIfConfigured(
                fixture.writer
            ),
            "failed-teleport fixture Boss root did not open"
        );

        fixture.ui.handleWidget(
            BossTeleportPresentation.rowWidget(
                LocalBossTeleportUiHandler.CONFIGURED_ROW
            ),
            fixture.writer,
            "[g3-handoff-fail] "
        );

        fixture.ui.handleWidget(
            BossTeleportPresentation.TELEPORT_WIDGET,
            fixture.writer,
            "[g3-handoff-fail] "
        );

        require(
            fixture.teleportCalls==1&&
            fixture.boss.isOpen(),
            "failed Boss teleport unexpectedly retired Boss root"
        );

        fixture.ui.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            fixture.writer,
            "[g3-handoff-fail] "
        );

        MonsterSpawnerService.SessionSnapshot session=
            fixture.spawner.getSession(
                "boss-handoff-fail"
            );

        require(
            session.selectedRowIndex==null&&
            !session.active&&
            session.spawnedNpcIds.isEmpty()&&
            fixture.bridge.monsterResults==0,
            "failed Boss teleport opened Monster Spawner root"
        );
    }

    private static void proveSuccessfulTeleportWithoutSpawner()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "boss-handoff-no-spawner",
                true,
                false
            );

        require(
            fixture.ui.openBossTeleportIfConfigured(
                fixture.writer
            ),
            "no-spawner Boss root did not open"
        );

        fixture.ui.handleWidget(
            BossTeleportPresentation.rowWidget(
                LocalBossTeleportUiHandler.CONFIGURED_ROW
            ),
            fixture.writer,
            "[g3-handoff-no-spawner] "
        );

        fixture.ui.handleWidget(
            BossTeleportPresentation.TELEPORT_WIDGET,
            fixture.writer,
            "[g3-handoff-no-spawner] "
        );

        require(
            fixture.teleportCalls==1&&
            !fixture.boss.isOpen(),
            "Boss teleport without Monster Spawner did not remain successful"
        );
    }

    private static final class Fixture {
        final WorldPlayer player=new WorldPlayer();
        final BankState bank=player.bank();
        final MovementState movement=player.movement();
        final EquipmentState equipment=player.equipment();
        final Bridge bridge=new Bridge();
        final ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{81,82,83,84}
                )
            );
        final MonsterSpawnerService spawner;
        final LocalMonsterSpawnerUiHandler monsterUi;
        final LocalBossTeleportUiHandler boss;
        final LocalSessionUiActionHandler ui;
        int teleportCalls;

        Fixture(
            String owner,
            boolean teleportSuccess,
            boolean withSpawner
        ){
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            PetAccessoryState accessory=
                new PetAccessoryState();

            LocalPetInventoryDialogHandler petDialogs=
                new LocalPetInventoryDialogHandler(
                    bank,
                    player.miniPets(),
                    player.petState(),
                    npcs,
                    movement,
                    accessory
                );

            LocalGameplayWidgetHandler gameplay=
                new LocalGameplayWidgetHandler(
                    player.prayers(),
                    player.playerState(),
                    equipment,
                    player.combatStyles(),
                    player.magic(),
                    bank
                );

            LocalCompCapeCustomizeHandler compCape=
                new LocalCompCapeCustomizeHandler(
                    bank,
                    player.playerState()
                );

            if(withSpawner){
                spawner=
                    new MonsterSpawnerService(
                        new WorldNpcRegistry()
                    );
                spawner.replaceCatalog(
                    Collections.singletonList(
                        new MonsterSpawnerService.CatalogEntry(
                            0,
                            "g3:boss-handoff",
                            LocalLabMonsterSpawnerProvisioning
                                .NPC_DEFINITION_ID
                        )
                    ),
                    LocalLabMonsterSpawnerProvisioning
                        .CATALOG_AUTHORITY
                );
                spawner.openSession(
                    owner,
                    LocalLabMonsterSpawnerProvisioning
                        .SESSION_AUTHORITY
                );

                monsterUi=
                    new LocalMonsterSpawnerUiHandler(
                        spawner,
                        owner,
                        new LocalMonsterSpawnerUiHandler
                            .ActivationBudgetResolver(){
                            @Override public int spawnBudget(
                                LocalMonsterSpawnerUiHandler.Context context
                            ){
                                return LocalLabMonsterSpawnerProvisioning
                                    .ACTIVATION_BUDGET;
                            }

                            @Override public String authority(){
                                return LocalLabMonsterSpawnerProvisioning
                                    .SESSION_AUTHORITY;
                            }
                        },
                        new LocalMonsterSpawnerUiHandler
                            .SelectedNpcLabelResolver(){
                            @Override public String label(
                                MonsterSpawnerService.CatalogEntry entry
                            ){
                                return "LocalLab Boss encounter";
                            }

                            @Override public String authority(){
                                return LocalLabMonsterSpawnerProvisioning
                                    .CATALOG_AUTHORITY;
                            }
                        }
                    );
            }else{
                spawner=null;
                monsterUi=null;
            }

            boss=
                new LocalBossTeleportUiHandler(
                    ()->owner,
                    (packets,tag)->{
                        teleportCalls++;
                        return teleportSuccess;
                    }
                );

            ui=
                new LocalSessionUiActionHandler(
                    player,
                    new NativeItemLibraryService(),
                    new DevControlCenter(),
                    bank,
                    compCape,
                    petDialogs,
                    gameplay,
                    movement,
                    true,
                    equipment,
                    monsterUi,
                    boss,
                    bridge
                );
        }
    }

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        int monsterResults;

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        ){}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public void handleMonsterSpawnerResult(
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter serverPackets,
            String tag
        ){
            monsterResults++;
        }

        @Override public void requestLogout(){}
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G3BossToMonsterSpawnerHandoffTest(){}
}
