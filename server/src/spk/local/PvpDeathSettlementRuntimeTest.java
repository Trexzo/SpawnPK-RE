package spk.local;

import java.util.*;

public final class PvpDeathSettlementRuntimeTest {
    private static final int AUTO_KEEP_ITEM=20466;
    private static final int AUTO_LOSS_ITEM=24254;
    private static final int STANDARD_ITEM=995;

    public static void main(String[] args)
        throws Exception{
        exactPolicyRows();
        pendingSettlementBeforeRespawn();
        failedSettlementBlocksAndRetries();
        unregisterRetiresPending();

        System.out.println(
            "PVP_DEATH_SETTLEMENT_RUNTIME_PASS "+
            "exactAutoKeep=true "+
            "exactAutoLoss=true "+
            "standardFallbackDrop=true "+
            "pendingExactDeath=true "+
            "settlesBeforeRespawn=true "+
            "failedSettlementBlocksRespawn=true "+
            "presentationDebtBlocksRespawn=true "+
            "retryable=true "+
            "unregisterCleanup=true "+
            "policy="+
            LocalLabPvpDeathPolicy.POLICY_ID+
            " authority="+
            PvpDeathSettlementRuntime.AUTHORITY
        );
    }

    private static void exactPolicyRows(){
        WorldPlayer player=
            playerWithInventory(
                new int[]{
                    AUTO_KEEP_ITEM,
                    AUTO_LOSS_ITEM,
                    STANDARD_ITEM
                },
                new int[]{1,1,100}
            );

        kill(
            player,
            5L,
            5L
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_POLICY_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=
                resolver.previewCurrentDeath();

        LocalLabPvpDeathPolicy.Result result=
            new LocalLabPvpDeathPolicy()
                .decide(preview);

        require(
            result.autoKeptLines==1&&
            result.autoLostLines==1&&
            result.standardLostLines==1,
            "policy category counts"
        );

        Map<Integer,Integer> keptByItem=
            new HashMap<>();

        for(int i=0;
            i<preview.carried.size();
            i++)
            keptByItem.put(
                preview.carried.get(i).itemId,
                result.decisions.get(i).keptAmount
            );

        require(
            keptByItem.get(AUTO_KEEP_ITEM)==1&&
            keptByItem.get(AUTO_LOSS_ITEM)==0&&
            keptByItem.get(STANDARD_ITEM)==0,
            "policy decisions"
        );
    }

    private static void pendingSettlementBeforeRespawn()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=emptyPlayer();
        WorldPlayer victim=
            playerWithInventory(
                new int[]{
                    AUTO_KEEP_ITEM,
                    STANDARD_ITEM
                },
                new int[]{1,100}
            );

        long killerGeneration=
            world.registerPlayer(
                killer,
                "killer"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );

        try{
            kill(victim,10L,5L);

            PvpDeathSettlementRuntime.Pending pending=
                world.pvpDeaths()
                    .registerPending(
                        killer,
                        victim,
                        victimGeneration,
                        10L
                    );

            require(
                pending.deathSequence==
                    victim.lifecycle()
                        .deathSequence()&&
                pending.deathTick==10L&&
                pending.lootOwner.equals(
                    "killer"
                )&&
                world.pvpDeaths()
                    .hasPending(victim),
                "pending identity"
            );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim
                );

            PvpDeathSettlementRuntime.RespawnGateResult
                early=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            11L
                        );

            require(
                early.settlement!=null&&
                early.preparedRespawn==null&&
                !world.pvpDeaths()
                    .hasPending(victim)&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "early settlement gate"
            );

            require(
                victim.bank()
                    .inventoryCount(
                        AUTO_KEEP_ITEM
                    )==1&&
                victim.bank()
                    .inventoryCount(
                        STANDARD_ITEM
                    )==0,
                "policy carried postimage"
            );

            Tile tile=
                early.settlement
                    .receipt
                    .deathTile;

            GroundItem standardGround=
                world.groundItems()
                    .findOwned(
                        STANDARD_ITEM,
                        tile.x,
                        tile.y,
                        tile.plane,
                        "killer"
                    );

            require(
                standardGround!=null&&
                standardGround.amount==100,
                "standard loss ground"
            );

            PvpDeathSettlementRuntime.RespawnGateResult
                blockedByPresentation=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            15L
                        );

            require(
                blockedByPresentation.settlement==null&&
                blockedByPresentation.preparedRespawn==null&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "presentation debt did not block respawn"
            );

            world.pvpDeaths()
                .markPresentationCommitted(
                    victim,
                    victimGeneration,
                    pending.deathSequence
                );

            PvpDeathSettlementRuntime.RespawnGateResult
                due=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            15L
                        );

            require(
                due.settlement==null&&
                due.preparedRespawn!=null&&
                victim.lifecycle().dead(),
                "due respawn prepare"
            );

            lifecycle.commitPreparedRespawn(
                due.preparedRespawn
            );

            require(
                victim.lifecycle().alive(),
                "respawn after settled death"
            );

            require(
                world.players().owns(
                    killer,
                    killerGeneration
                ),
                "killer ownership changed"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void failedSettlementBlocksAndRetries()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=emptyPlayer();
        WorldPlayer victim=
            playerWithInventory(
                new int[]{STANDARD_ITEM},
                new int[]{1}
            );

        world.registerPlayer(
            killer,
            "killer-overflow"
        );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim-overflow"
            );

        try{
            kill(victim,20L,0L);

            Tile tile=
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            GroundItem blocker=
                world.groundItems().add(
                    STANDARD_ITEM,
                    Integer.MAX_VALUE,
                    tile,
                    "killer-overflow",
                    1L,
                    false
                );

            world.pvpDeaths()
                .registerPending(
                    killer,
                    victim,
                    victimGeneration,
                    20L
                );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim
                );

            boolean failed=false;
            try{
                world.pvpDeaths()
                    .settleAndPrepareRespawn(
                        victim,
                        lifecycle,
                        20L
                    );
            }catch(IllegalStateException expected){
                failed=true;
            }

            require(
                failed&&
                world.pvpDeaths()
                    .hasPending(victim)&&
                victim.lifecycle().dead()&&
                victim.bank()
                    .inventoryCount(
                        STANDARD_ITEM
                    )==1,
                "failed settlement did not block"
            );

            require(
                world.groundItems().remove(
                    blocker.id
                ),
                "overflow blocker remove"
            );

            PvpDeathSettlementRuntime.RespawnGateResult
                retry=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            20L
                        );

            require(
                retry.settlement!=null&&
                retry.preparedRespawn==null&&
                !world.pvpDeaths()
                    .hasPending(victim)&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim)&&
                victim.bank()
                    .inventoryCount(
                        STANDARD_ITEM
                    )==0,
                "settlement retry"
            );

            world.pvpDeaths()
                .markPresentationCommitted(
                    victim,
                    victimGeneration,
                    retry.settlement
                        .pending
                        .deathSequence
                );

            PvpDeathSettlementRuntime.RespawnGateResult
                retryDue=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            20L
                        );

            require(
                retryDue.preparedRespawn!=null,
                "presentation commit did not release respawn"
            );

            lifecycle.commitPreparedRespawn(
                retryDue.preparedRespawn
            );

            require(
                victim.lifecycle().alive(),
                "retry respawn"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void unregisterRetiresPending(){
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=emptyPlayer();
        WorldPlayer victim=
            playerWithInventory(
                new int[]{STANDARD_ITEM},
                new int[]{1}
            );

        world.registerPlayer(killer,"killer-exit");
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim-exit"
            );

        try{
            kill(victim,30L,5L);

            world.pvpDeaths()
                .registerPending(
                    killer,
                    victim,
                    victimGeneration,
                    30L
                );

            require(
                world.pvpDeaths()
                    .pendingCount()==1,
                "pending before unregister"
            );

            require(
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                ),
                "victim unregister"
            );

            require(
                world.pvpDeaths()
                    .pendingCount()==0,
                "pending survived unregister"
            );
        }finally{
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static WorldPlayer playerWithInventory(
        int[] itemIds,
        int[] quantities
    ){
        if(itemIds.length!=quantities.length)
            throw new IllegalArgumentException(
                "inventory fixture arrays"
            );

        WorldPlayer player=
            emptyPlayer();
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        for(int i=0;
            i<itemIds.length;
            i++)
            inventory[i]=
                new BankState.Stack(
                    itemIds[i],
                    quantities[i]
                );

        player.bank().restoreAccountState(
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ],
            inventory,
            false
        );

        return player;
    }

    private static WorldPlayer emptyPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        player.bank().restoreAccountState(
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ],
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ],
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
        Arrays.fill(items,-1);

        player.equipment()
            .restoreAccountState(
                items,
                quantities
            );
        player.playerState()
            .syncEquipmentPresentation(
                player.equipment()
            );

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        long delay
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_G3_TEST"
            ).applyDamage(
                500,
                tick,
                "PVP_TEST",
                delay
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PvpDeathSettlementRuntimeTest(){}
}
