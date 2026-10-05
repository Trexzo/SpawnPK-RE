package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class PvpDeathPresentationServiceTest {
    private static final int STANDARD_ITEM=995;

    public static void main(String[] args)
        throws Exception{
        abortRetainsDebtAndRetryCommits();
        allKeptCreatesNoDebt();

        System.out.println(
            "PVP_DEATH_PRESENTATION_PASS "+
            "packet53Inventory=true "+
            "packet53Equipment=true "+
            "abortZeroQueuedBytes=true "+
            "abortRetainsDebt=true "+
            "retryCommits=true "+
            "commitClearsDebt=true "+
            "respawnBlockedUntilCommit=true "+
            "allKeptNoDebt=true "+
            "authority="+
            PvpDeathPresentationService.AUTHORITY
        );
    }

    private static void abortRetainsDebtAndRetryCommits()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=emptyPlayer();
        WorldPlayer victim=
            playerWithInventory(
                STANDARD_ITEM,
                25
            );

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
            kill(victim,10L,0L);

            PvpDeathSettlementRuntime.Pending pending=
                world.pvpDeaths()
                    .registerPending(
                        killer,
                        victim,
                        victimGeneration,
                        10L
                    );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim
                );

            PvpDeathSettlementRuntime.RespawnGateResult
                settled=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            10L
                        );

            require(
                settled.settlement!=null&&
                settled.preparedRespawn==null&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "settlement did not create presentation debt"
            );

            PvpDeathPresentationService presentation=
                new PvpDeathPresentationService(
                    world.pvpDeaths(),
                    victim,
                    victim.equipment()
                );

            OutboundPacketQueue queue=
                new OutboundPacketQueue();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            writer.beginBatch();

            PvpDeathSettlementRuntime.PresentationDebt
                first=
                    presentation.stage(
                        writer
                    );

            require(
                first!=null&&
                first.deathSequence==
                    pending.deathSequence&&
                presentation.staged()&&
                queue.queuedBytes()==0,
                "first presentation stage"
            );

            writer.abortBatch();
            presentation.abortStaged();

            require(
                !presentation.staged()&&
                queue.queuedBytes()==0&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "abort leaked bytes or cleared debt"
            );

            PvpDeathSettlementRuntime.RespawnGateResult
                blocked=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            10L
                        );

            require(
                blocked.preparedRespawn==null,
                "presentation debt did not block respawn"
            );

            writer.beginBatch();

            PvpDeathSettlementRuntime.PresentationDebt
                retry=
                    presentation.stage(
                        writer
                    );

            require(
                retry!=null&&
                retry.deathSequence==
                    pending.deathSequence,
                "retry presentation identity"
            );

            writer.endBatch();

            require(
                queue.queuedBytes()>0&&
                world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "writer commit cleared runtime debt early"
            );

            presentation.commitStaged();

            require(
                !presentation.staged()&&
                !world.pvpDeaths()
                    .hasPresentationDebt(victim),
                "presentation commit did not clear debt"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            int drained=
                queue.drainTo(
                    wire,
                    1<<20
                );

            require(
                drained>0&&
                wire.size()==drained,
                "committed packet bytes missing"
            );

            PvpDeathSettlementRuntime.RespawnGateResult
                due=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            10L
                        );

            require(
                due.preparedRespawn!=null,
                "respawn not released after presentation commit"
            );

            lifecycle.commitPreparedRespawn(
                due.preparedRespawn
            );

            require(
                victim.lifecycle().alive(),
                "respawn commit"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void allKeptCreatesNoDebt()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=emptyPlayer();
        WorldPlayer victim=
            playerWithInventory(
                20466,
                1
            );

        world.registerPlayer(
            killer,
            "killer-keep"
        );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim-keep"
            );

        try{
            kill(victim,20L,0L);

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

            PvpDeathSettlementRuntime.RespawnGateResult
                result=
                    world.pvpDeaths()
                        .settleAndPrepareRespawn(
                            victim,
                            lifecycle,
                            20L
                        );

            require(
                result.settlement!=null&&
                result.settlement
                    .receipt
                    .lostTotalQuantity==0&&
                !world.pvpDeaths()
                    .hasPresentationDebt(victim)&&
                result.preparedRespawn!=null,
                "all-kept death incorrectly created debt"
            );

            PvpDeathPresentationService presentation=
                new PvpDeathPresentationService(
                    world.pvpDeaths(),
                    victim,
                    victim.equipment()
                );

            OutboundPacketQueue queue=
                new OutboundPacketQueue();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{5,6,7,8}
                    )
                );

            writer.beginBatch();
            require(
                presentation.stage(writer)==null,
                "all-kept death staged refresh"
            );
            writer.endBatch();

            require(
                queue.queuedBytes()==0,
                "all-kept death emitted bytes"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static WorldPlayer playerWithInventory(
        int itemId,
        int quantity
    ){
        WorldPlayer player=
            emptyPlayer();

        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];
        inventory[0]=
            new BankState.Stack(
                itemId,
                quantity
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
                "CUSTOM_LOCALLAB_G4_TEST"
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

    private PvpDeathPresentationServiceTest(){}
}
