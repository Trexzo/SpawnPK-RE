package spk.local;

import java.util.Arrays;
import java.util.List;

/** End-to-end LocalLab PK-risk death/drop policy regression. */
public final class LocalPkRiskDeathLoopTest {
    public static void main(String[] args){
        riskTransitionPolicy();

        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer killer=
            new WorldPlayer();
        WorldPlayer victim=
            configuredVictim();

        long killerGeneration=
            world.registerPlayer(
                killer,
                "killer"
            );
        world.registerPlayer(
            victim,
            "victim"
        );

        try{
            victim.riskZone()
                .onSuccessfulTeleport(
                    TeleportNavigationService.EntryKind.PK
                );

            LocalRiskZoneState.Snapshot armed=
                victim.riskZone()
                    .snapshot();

            require(
                armed.risk()&&
                armed.sourceKind==
                    TeleportNavigationService.EntryKind.PK,
                "PK teleport risk arming"
            );

            Tile deathTile=
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim
                );

            PlayerLifecycleService.DamageResult death=
                lifecycle.applyDamageFromPlayer(
                    500,
                    100L,
                    "PK_LOOP_TEST",
                    killer.id()
                );

            require(
                death.died&&
                killer.id().equals(
                    victim.lifecycle()
                        .responsiblePlayerId()
                )&&
                deathTile.equals(
                    victim.lifecycle()
                        .deathTile()
                )&&
                victim.lifecycle()
                    .deathRiskSnapshot()!=null&&
                victim.lifecycle()
                    .deathRiskSnapshot()
                    .risk(),
                "death identity/risk capture"
            );

            // Changing mutable session risk after death must not rewrite this
            // death's already-frozen policy identity.
            victim.riskZone()
                .returnHome();

            LocalDeathLoopService deathLoop=
                new LocalDeathLoopService(
                    world,
                    victim
                );

            PlayerDeathItemResolutionService.Resolution resolution=
                deathLoop.ensureCurrentDeathResolved();

            require(
                resolution.riskAtDeath&&
                resolution.riskSourceKind==
                    TeleportNavigationService.EntryKind.PK&&
                killer.id().equals(
                    resolution.responsiblePlayerId
                )&&
                deathTile.equals(
                    resolution.deathTile
                )&&
                resolution.keptTotalQuantity()==0&&
                resolution.lostTotalQuantity()==101,
                "DROP_ALL resolution"
            );

            PlayerDeathGroundSettlementService.Receipt receipt=
                deathLoop.settlement(
                    resolution.deathSequence
                );

            require(
                receipt!=null&&
                receipt.keptQuantity==0&&
                receipt.lostQuantity==101&&
                "killer".equals(
                    receipt.recipientRef
                ),
                "killer settlement receipt"
            );

            require(
                victim.bank()
                    .inventoryAt(0)==null&&
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.WEAPON
                            .equipmentIndex
                    )<0,
                "victim carried state cleared"
            );

            GroundItem coins=
                world.groundItems()
                    .findVisible(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );
            GroundItem whip=
                world.groundItems()
                    .findVisible(
                        4151,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );

            require(
                coins!=null&&
                coins.amount==100&&
                "killer".equalsIgnoreCase(
                    coins.owner
                )&&
                whip!=null&&
                whip.amount==1&&
                "killer".equalsIgnoreCase(
                    whip.owner
                ),
                "killer owner-scoped ground state"
            );

            require(
                world.groundItems()
                    .findVisible(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "victim"
                    )==null,
                "victim cannot see killer-private ground"
            );

            List<WorldGroundItemPresentationEvents.Event> events=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        killerGeneration,
                        System.currentTimeMillis()
                    );

            require(
                events.size()==2,
                "killer live ground presentation"
            );

            PlayerDeathItemResolutionService.Resolution replay=
                deathLoop.ensureCurrentDeathResolved();

            require(
                replay==resolution&&
                deathLoop.resolutionCount()==1&&
                world.groundItems().size()==2,
                "death settlement replay"
            );

            require(
                lifecycle.tick(104L)==
                    PlayerLifecycleService.TickResult.NONE,
                "early respawn"
            );
            require(
                lifecycle.tick(105L)==
                    PlayerLifecycleService.TickResult.RESPAWNED&&
                !victim.riskZone().risk()&&
                victim.lifecycle()
                    .responsiblePlayerId()==null,
                "HOME respawn risk reset"
            );

            offlineKillerFallsBackKeepAll();

            System.out.println(
                "LOCAL_PK_RISK_DEATH_LOOP_PASS "+
                "teleportRiskTransitions=true "+
                "riskBoundAtDeath=true "+
                "dropAll=true "+
                "killerOwnerScoped=true "+
                "killerPresentation=true "+
                "victimPrivateInvisible=true "+
                "replayIdempotent=true "+
                "offlineKillerKeepAll=true "+
                "respawnSafe=true "+
                "policy="+
                LocalRiskZoneState.POLICY_AUTHORITY
            );
        }finally{
            world.unregisterPlayer(
                victim
            );
            world.unregisterPlayer(
                killer
            );
            world.close();
        }
    }

    private static void riskTransitionPolicy(){
        LocalRiskZoneState risk=
            new LocalRiskZoneState();

        require(
            !risk.risk(),
            "initial SAFE risk state"
        );

        risk.onSuccessfulTeleport(
            TeleportNavigationService.EntryKind.PK
        );
        require(
            risk.risk(),
            "PK arms risk"
        );

        risk.onSuccessfulTeleport(
            TeleportNavigationService.EntryKind.TRAINING
        );
        require(
            !risk.risk(),
            "TRAINING clears risk"
        );

        risk.onSuccessfulTeleport(
            TeleportNavigationService.EntryKind.BOUNTY
        );
        require(
            risk.risk(),
            "BOUNTY arms risk"
        );

        long beforeRevision=
            risk.snapshot().revision;
        boolean houseRejected=false;

        try{
            risk.onSuccessfulTeleport(
                TeleportNavigationService.EntryKind.HOUSE
            );
        }catch(IllegalArgumentException expected){
            houseRejected=true;
        }

        require(
            houseRejected&&
            risk.risk()&&
            risk.snapshot().revision==beforeRevision,
            "failed HOUSE preserves risk"
        );

        risk.returnHome();
        require(
            !risk.risk()&&
            risk.snapshot().sourceKind==
                TeleportNavigationService.EntryKind.HOME,
            "HOME clears risk"
        );
    }

    private static void offlineKillerFallsBackKeepAll(){
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer killer=
            new WorldPlayer();
        WorldPlayer victim=
            configuredVictim();

        world.registerPlayer(
            killer,
            "gone"
        );
        world.registerPlayer(
            victim,
            "survivor"
        );

        try{
            victim.riskZone()
                .onSuccessfulTeleport(
                    TeleportNavigationService.EntryKind.BOUNTY
                );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim
                );

            require(
                lifecycle.applyDamageFromPlayer(
                    500,
                    200L,
                    "OFFLINE_KILLER",
                    killer.id()
                ).died,
                "offline-killer death"
            );

            world.unregisterPlayer(
                killer
            );

            LocalDeathLoopService deathLoop=
                new LocalDeathLoopService(
                    world,
                    victim
                );
            PlayerDeathItemResolutionService.Resolution resolution=
                deathLoop.ensureCurrentDeathResolved();

            require(
                resolution.keptTotalQuantity()==101&&
                resolution.lostTotalQuantity()==0&&
                world.groundItems().size()==0&&
                victim.bank().inventoryAt(0)!=null&&
                victim.bank().inventoryAt(0).qty==100&&
                victim.equipment().itemAt(
                    EquipmentSlot.WEAPON
                        .equipmentIndex
                )==4151,
                "offline killer KEEP_ALL fallback"
            );
        }finally{
            world.unregisterPlayer(
                victim
            );
            world.unregisterPlayer(
                killer
            );
            world.close();
        }
    }

    private static WorldPlayer configuredVictim(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            BankState.Stack[] bank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] inventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            inventory[0]=
                new BankState.Stack(
                    995,
                    100
                );

            player.bank()
                .restoreAccountState(
                    bank,
                    inventory,
                    false
                );

            int[] items=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                items,
                -1
            );

            items[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }

        return player;
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

    private LocalPkRiskDeathLoopTest(){}
}
