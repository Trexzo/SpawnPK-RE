package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalTeleportNavigationRuntimeTest {
    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        WorldPlayer other=
            new WorldPlayer();

        long playerGeneration=
            world.registerPlayer(
                player,
                "teleport-runtime"
            );

        long otherGeneration=0L;

        try{
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            CombatEngine combat=
                new CombatEngine(dev);
            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    player.movement(),
                    player.equipment()
                );

            final boolean[] followCancelled=
                {false};

            LocalRegionDevCommandHandler relocation=
                new LocalRegionDevCommandHandler(
                    world,
                    player,
                    player.movement(),
                    interactions,
                    combat,
                    npcs,
                    player.petState(),
                    new HomeWorldRuntimePlan(),
                    ()->followCancelled[0]=true
                );

            LocalTeleportNavigationRuntime runtime=
                new LocalTeleportNavigationRuntime(
                    relocation
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{81,82,83,84}
                    )
                );

            SceneUpdatePublisher original=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalTeleportDestinationCatalog.Destination boss=
                LocalTeleportDestinationCatalog.get(
                    TeleportNavigationService.EntryKind.BOSS
                );

            Tile expectedBossLanding=
                WorldCollisionAuthority.safeTile(
                    boss.regionId,
                    boss.plane
                );

            LocalTeleportNavigationRuntime.Result
                bossResult=
                    runtime.request(
                        "teleport-runtime",
                        TeleportNavigationService.EntryKind.BOSS,
                        original,
                        writer
                    );

            require(
                bossResult.succeeded()&&
                bossResult.relocation!=null&&
                bossResult.relocation.scenePublisher!=null&&
                bossResult.relocation.scenePublisher!=original&&
                bossResult.relocation.logText.startsWith(
                    "V5160_REGION_LOAD OK ")&&
                bossResult.relocation.saveReason!=null,
                "BOSS runtime relocation failed"
            );

            require(
                player.movement().transientRegion()&&
                player.movement().x()==expectedBossLanding.x&&
                player.movement().y()==expectedBossLanding.y&&
                player.movement().plane()==expectedBossLanding.plane&&
                followCancelled[0],
                "BOSS runtime did not commit collision-safe relocation"
            );

            TeleportNavigationService.PlayerSnapshot
                afterBoss=
                    runtime.snapshot(
                        "teleport-runtime"
                    );

            require(
                afterBoss.successful(
                    TeleportNavigationService.EntryKind.BOSS
                )==1L&&
                afterBoss.totalSuccessfulRequests==1L,
                "successful BOSS request not recorded exactly once"
            );

            LocalTeleportNavigationRuntime.Result
                house=
                    runtime.request(
                        "teleport-runtime",
                        TeleportNavigationService.EntryKind.HOUSE,
                        bossResult.relocation.scenePublisher,
                        writer
                    );

            require(
                !house.succeeded()&&
                house.relocation==null&&
                house.navigation.detail.contains(
                    "HOUSE_UNCONFIGURED")&&
                runtime.snapshot(
                    "teleport-runtime"
                ).totalSuccessfulRequests==1L,
                "HOUSE unconfigured failure mutated success ledger"
            );

            boolean homeRejected=false;
            try{
                runtime.request(
                    "teleport-runtime",
                    TeleportNavigationService.EntryKind.HOME,
                    bossResult.relocation.scenePublisher,
                    writer
                );
            }catch(IllegalArgumentException expected){
                homeRejected=true;
            }

            require(
                homeRejected,
                "HOME escaped canonical magic-home ownership"
            );

            otherGeneration=
                world.registerPlayer(
                    other,
                    "teleport-other"
                );

            int xBefore=
                player.movement().x();
            int yBefore=
                player.movement().y();
            int planeBefore=
                player.movement().plane();

            LocalTeleportNavigationRuntime.Result
                multiplayer=
                    runtime.request(
                        "teleport-runtime",
                        TeleportNavigationService.EntryKind.PK,
                        bossResult.relocation.scenePublisher,
                        writer
                    );

            require(
                !multiplayer.succeeded()&&
                multiplayer.relocation!=null&&
                multiplayer.relocation.logText.contains(
                    "REJECTED_MULTIPLAYER")&&
                multiplayer.navigation.detail.contains(
                    "REJECTED_MULTIPLAYER")&&
                player.movement().x()==xBefore&&
                player.movement().y()==yBefore&&
                player.movement().plane()==planeBefore&&
                runtime.snapshot(
                    "teleport-runtime"
                ).totalSuccessfulRequests==1L,
                "multiplayer rejection mutated relocation or success ledger"
            );

            require(
                wire.size()>0,
                "successful BOSS relocation emitted no wire bytes"
            );

            System.out.println(
                "LOCAL_TELEPORT_NAVIGATION_RUNTIME_PASS "+
                "bossRelocation=true "+
                "collisionSafeLanding=true "+
                "semanticLedgerExactlyOnce=true "+
                "houseUnconfigured=true "+
                "homeCanonical=true "+
                "multiplayerRejected=true "+
                "multiplayerStateUnchanged=true "+
                "policy="+
                LocalTeleportDestinationCatalog.POLICY_AUTHORITY
            );
        }finally{
            if(other.registered())
                world.unregisterPlayer(
                    other,
                    otherGeneration
                );
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    playerGeneration
                );
            world.close();
        }
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

    private LocalTeleportNavigationRuntimeTest(){}
}
