package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class NpcDropLiveSettlementAtomicTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                killer,
                "killer"
            );

        try{
            Tile tile=
                new Tile(
                    killer.movement().x(),
                    killer.movement().y(),
                    killer.movement().plane()
                );

            GroundItem existingCoins=
                world.groundItems().add(
                    995,
                    10,
                    tile,
                    "killer",
                    1L,
                    false
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            WorldNpc npc=
                world.npcs().spawn(
                    1650,
                    tile.x,
                    tile.y,
                    tile.plane
                );

            lifecycle.register(
                npc,
                10,
                "CUSTOM_LOCALLAB_ATOMIC_LOOT_HP"
            );

            require(
                lifecycle.applyDamage(
                    npc.id,
                    99,
                    50L
                ).newlyDied,
                "dead NPC fixture"
            );

            NpcDropResolutionService drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<
                            NpcDropResolutionService.Drop
                        > resolve(
                            NpcDropResolutionService
                                .DeathContext context
                        ){
                            return Arrays.asList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    5
                                ),
                                new NpcDropResolutionService.Drop(
                                    4151,
                                    1
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_ATOMIC_LOOT_DROP";
                        }
                    }
                );

            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_ATOMIC_LOOT_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            NpcDropResolutionService.Resolution resolution=
                drops.resolve(
                    npc,
                    "killer"
                );

            NpcDropGroundSettlementService.Receipt receipt=
                settlement.settle(
                    resolution
                );

            NpcDropGroundSettlementService.SettledGroundItem
                coinReceipt=
                    receiptRow(
                        receipt,
                        995
                    );
            NpcDropGroundSettlementService.SettledGroundItem
                whipReceipt=
                    receiptRow(
                        receipt,
                        4151
                    );

            require(
                coinReceipt.settledAmount==5&&
                coinReceipt.stackAmountAfter==15&&
                whipReceipt.settledAmount==1&&
                whipReceipt.stackAmountAfter==1,
                "receipt did not derive from atomic batch facts"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            generation,
                            System.currentTimeMillis()
                        );

            require(
                pending.size()==2,
                "expected two live settlement events"
            );

            WorldGroundItemPresentationEvents.Event
                coinEvent=
                    eventFor(
                        pending,
                        995
                    );
            WorldGroundItemPresentationEvents.Event
                whipEvent=
                    eventFor(
                        pending,
                        4151
                    );

            require(
                coinEvent.kind==
                    WorldGroundItemPresentationEvents
                        .Kind.AMOUNT&&
                coinEvent.oldAmount==10&&
                coinEvent.newAmount==15&&
                whipEvent.kind==
                    WorldGroundItemPresentationEvents
                        .Kind.SPAWN&&
                whipEvent.oldAmount==0&&
                whipEvent.newAmount==1,
                "live events did not preserve exact commit old/new amounts"
            );

            GroundItem coins=
                world.groundItems()
                    .findOwned(
                        995,
                        tile.x,
                        tile.y,
                        tile.plane,
                        "killer"
                    );
            GroundItem whip=
                world.groundItems()
                    .findOwned(
                        4151,
                        tile.x,
                        tile.y,
                        tile.plane,
                        "killer"
                    );

            require(
                coins==existingCoins&&
                coins.amount==15&&
                whip!=null&&
                whip.amount==1,
                "canonical batch state"
            );

            world.groundItems().add(
                995,
                7,
                tile,
                "killer",
                51L,
                false
            );
            require(
                world.groundItems().remove(
                    whip.id
                ),
                "whip mutation fixture"
            );

            require(
                coinEvent.oldAmount==10&&
                coinEvent.newAmount==15&&
                whipEvent.oldAmount==0&&
                whipEvent.newAmount==1&&
                coinReceipt.stackAmountAfter==15&&
                whipReceipt.stackAmountAfter==1,
                "later mutable ground state changed retained settlement facts"
            );

            for(WorldGroundItemPresentationEvents.Event event:
                    pending)
                require(
                    world.groundItemPresentationEvents()
                        .markDelivered(
                            event.sequence,
                            killer.id(),
                            generation,
                            System.currentTimeMillis()
                        ),
                    "event cleanup"
                );

            require(
                settlement.settle(
                    resolution
                )==receipt&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "idempotent settlement emitted duplicate live event"
            );

            List<GroundItemRegistry.BatchMutation>
                direct=
                    world.groundItems()
                        .addBatchDetailed(
                            Collections.singletonList(
                                new GroundItemRegistry.AddRequest(
                                    1337,
                                    3,
                                    tile,
                                    "killer",
                                    52L,
                                    false
                                )
                            )
                        );

            require(
                direct.size()==1&&
                direct.get(0).created()&&
                direct.get(0).oldAmount==0&&
                direct.get(0).newAmount==3&&
                direct.get(0).addedAmount==3,
                "new-stack atomic mutation facts"
            );

            GroundItemRegistry.BatchMutation
                immutableFact=
                    direct.get(0);

            world.groundItems().add(
                1337,
                2,
                tile,
                "killer",
                53L,
                false
            );

            require(
                immutableFact.oldAmount==0&&
                immutableFact.newAmount==3&&
                immutableFact.addedAmount==3,
                "batch mutation facts followed mutable ground state"
            );

            for(Field field:
                    GroundItemRegistry.BatchMutation.class
                        .getDeclaredFields())
                require(
                    field.getType()!=GroundItem.class,
                    "mutable GroundItem leaked into batch mutation "+
                    field.getName()
                );

            System.out.println(
                "NPC_DROP_LIVE_SETTLEMENT_ATOMIC_PASS "+
                "atomicOldNew=true "+
                "immutableMutationFacts=true "+
                "spawnDerived=true "+
                "amountDerived=true "+
                "receiptDerived=true "+
                "mutableGroundItemNotAuthority=true"
            );
        }finally{
            if(world.players().owns(
                    killer,
                    generation
                ))
                world.unregisterPlayer(
                    killer,
                    generation
                );

            world.close();
        }
    }

    private static NpcDropGroundSettlementService
        .SettledGroundItem receiptRow(
            NpcDropGroundSettlementService.Receipt receipt,
            int itemId
        ){
        for(NpcDropGroundSettlementService
                .SettledGroundItem row:
                receipt.groundItems)
            if(row.itemId==itemId)
                return row;

        throw new AssertionError(
            "receipt row missing item="+
            itemId
        );
    }

    private static WorldGroundItemPresentationEvents.Event
        eventFor(
            List<WorldGroundItemPresentationEvents.Event>
                events,
            int itemId
        ){
        for(WorldGroundItemPresentationEvents.Event event:
                events)
            if(event.itemId==itemId)
                return event;

        throw new AssertionError(
            "presentation event missing item="+
            itemId
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcDropLiveSettlementAtomicTest(){}
}
