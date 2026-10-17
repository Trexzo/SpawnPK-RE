package spk.local;

public final class PlayerPvpDelayedEligibilityIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "g52-attacker"
            );
        long targetGeneration=
            world.registerPlayer(
                target,
                "g52-target"
            );

        try{
            LocalTeleportDestinationCatalog.Destination pk=
                LocalTeleportDestinationCatalog.get(
                    TeleportNavigationService.EntryKind.PK
                );
            Tile tile=
                WorldCollisionAuthority.safeTile(
                    pk.regionId,
                    pk.plane
                );

            require(tile!=null,"PK safe tile");

            int chunkX=tile.x>>3;
            int chunkY=tile.y>>3;
            int baseX=(chunkX-6)<<3;
            int baseY=(chunkY-6)<<3;

            attacker.movement().enterTransientRegion(
                tile.x-1,
                tile.y,
                pk.plane,
                baseX,
                baseY
            );
            target.movement().enterTransientRegion(
                tile.x,
                tile.y,
                pk.plane,
                baseX,
                baseY
            );

            require(
                LocalLabPvpRegionPolicy.INSTANCE
                    .evaluate(attacker,target)
                    .eligible,
                "initial PK eligibility"
            );

            final int[] presentations={0};
            PlayerPvpDelayedHitService service=
                new PlayerPvpDelayedHitService(
                    world,
                    "CUSTOM_LOCALLAB_G5_DELAYED_PVP_HIT_V1",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS,
                    outcome->{},
                    (deliveredTarget,result)->
                        presentations[0]++,
                    (source,victim)->
                        LocalLabPvpRegionPolicy.INSTANCE
                            .evaluate(source,victim)
                            .eligible
                );

            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                9
            );

            PlayerPvpDelayedHitService.Snapshot first=
                service.scheduleAtExpectedTick(
                    attacker,
                    attackerGeneration,
                    target,
                    targetGeneration,
                    9,
                    2,
                    "CUSTOM_LOCALLAB_TEST_DAMAGE",
                    "FIXED_9",
                    world.clock().tick()
                );

            require(
                first.state==
                    PlayerPvpDelayedHitService.State.SCHEDULED,
                "first schedule"
            );

            target.movement().returnHome();

            world.clock().advance();
            world.events().runDue(1L);

            require(
                target.playerState()
                    .currentLevel(PlayerState.HITPOINTS)==9&&
                presentations[0]==0,
                "pre-due PK exit mutated target"
            );

            world.clock().advance();
            world.events().runDue(2L);

            PlayerPvpDelayedHitService.Snapshot cancelled=
                service.get(first.hitId);

            require(
                cancelled!=null&&
                cancelled.state==
                    PlayerPvpDelayedHitService.State.CANCELLED&&
                cancelled.appliedDamage==-1&&
                target.playerState()
                    .currentLevel(PlayerState.HITPOINTS)==9&&
                !target.lifecycle().dead()&&
                presentations[0]==0,
                "PK-exited delayed hit was not cancelled atomically"
            );

            attacker.movement().enterTransientRegion(
                tile.x-1,
                tile.y,
                pk.plane,
                baseX,
                baseY
            );
            target.movement().enterTransientRegion(
                tile.x,
                tile.y,
                pk.plane,
                baseX,
                baseY
            );

            require(
                LocalLabPvpRegionPolicy.INSTANCE
                    .evaluate(attacker,target)
                    .eligible,
                "re-entered PK eligibility"
            );

            PlayerPvpDelayedHitService.Snapshot second=
                service.scheduleAtExpectedTick(
                    attacker,
                    attackerGeneration,
                    target,
                    targetGeneration,
                    9,
                    2,
                    "CUSTOM_LOCALLAB_TEST_DAMAGE",
                    "FIXED_9",
                    world.clock().tick()
                );

            world.clock().advance();
            world.events().runDue(3L);

            require(
                target.playerState()
                    .currentLevel(PlayerState.HITPOINTS)==9&&
                presentations[0]==0,
                "second hit delivered early"
            );

            world.clock().advance();
            world.events().runDue(4L);

            PlayerPvpDelayedHitService.Snapshot delivered=
                service.get(second.hitId);
            PlayerLifecycleState.DeathAttribution attribution=
                target.lifecycle().deathAttribution();

            require(
                delivered!=null&&
                delivered.state==
                    PlayerPvpDelayedHitService.State.DELIVERED&&
                delivered.appliedDamage==9&&
                target.lifecycle().dead()&&
                target.lifecycle().deathTick()==4L&&
                attribution!=null&&
                attacker.id().equals(
                    attribution.attackerId
                )&&
                attribution.attackerGeneration==
                    attackerGeneration&&
                "PLAYER_PVP".equals(
                    attribution.context
                )&&
                presentations[0]==1,
                "re-entered PK delayed hit did not deliver normally"
            );

            System.out.println(
                "G5_DELAYED_PVP_DELIVERY_ELIGIBILITY_PASS "+
                "scheduleEligible=true "+
                "pkExitBeforeDue=true "+
                "dueEventExecuted=true "+
                "cancelledNoDamage=true "+
                "cancelledNoPresentation=true "+
                "terminalCancelled=true "+
                "reenterPk=true "+
                "laterDueDelivery=true "+
                "lethalAttribution=true "+
                "generationFenced=true "+
                "authority=CUSTOM_LOCALLAB_G5_DELAYED_PVP_HIT_V1"
            );
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(target.registered())
                world.unregisterPlayer(
                    target,
                    targetGeneration
                );
            world.close();
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerPvpDelayedEligibilityIntegrationTest(){}
}
