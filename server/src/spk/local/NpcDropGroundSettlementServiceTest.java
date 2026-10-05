package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class NpcDropGroundSettlementServiceTest {
    public static void main(String[] args){
        ownerScopedSettlementAndIdempotency();
        zeroDropSettlement();
        conflictingResolutionRejected();
        batchFailureRetryExactlyOnce();
        authorityBoundaryAndReceiptShape();

        System.out.println(
            "NPC_DROP_GROUND_SETTLEMENT_PASS "+
            "atomicBatch=true "+
            "ownerScopedLocalPolicy=true "+
            "deathTile=true "+
            "deathTick=true "+
            "idempotent=true "+
            "conflictFailClosed=true "+
            "retryAfterFailure=true "+
            "zeroDrop=true "+
            "immutableReceipt=true "+
            "visibilityPolicyOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void ownerScopedSettlementAndIdempotency(){
        Fixture f=new Fixture(
            Arrays.asList(
                new NpcDropResolutionService.Drop(
                    995,
                    100
                ),
                new NpcDropResolutionService.Drop(
                    4151,
                    1
                )
            ),
            "CUSTOM_LOCALLAB_DROP_A"
        );

        try{
            NpcDropResolutionService.Resolution resolution=
                f.resolve(
                    "player:killer"
                );

            NpcDropGroundSettlementService service=
                f.settlement();

            NpcDropGroundSettlementService.Receipt first=
                service.settle(
                    resolution
                );

            require(
                first.npcId.equals(f.npc.id)&&
                first.definitionId==f.npc.definitionId&&
                first.deathTick==f.deathTick&&
                first.deathTile.equals(f.npc.tile())&&
                "player:killer".equals(
                    first.recipientRef
                )&&
                first.groundItems.size()==2&&
                service.size()==1,
                "owner-scoped receipt facts"
            );

            GroundItem coins=
                f.world.groundItems()
                    .findOwned(
                        995,
                        first.deathTile.x,
                        first.deathTile.y,
                        first.deathTile.plane,
                        "player:killer"
                    );
            GroundItem whip=
                f.world.groundItems()
                    .findOwned(
                        4151,
                        first.deathTile.x,
                        first.deathTile.y,
                        first.deathTile.plane,
                        "player:killer"
                    );

            require(
                coins!=null&&
                coins.amount==100&&
                coins.spawnedTick==f.deathTick&&
                !coins.devOwned&&
                whip!=null&&
                whip.amount==1&&
                whip.spawnedTick==f.deathTick&&
                !whip.devOwned,
                "ground-item settlement policy"
            );

            NpcDropGroundSettlementService.Receipt second=
                service.settle(
                    resolution
                );

            require(
                second==first&&
                f.world.groundItems().size()==2&&
                coins.amount==100&&
                whip.amount==1,
                "successful settlement was not idempotent"
            );

            expect(
                UnsupportedOperationException.class,
                ()->first.groundItems.add(
                    first.groundItems.get(0)
                ),
                "receipt rows mutable"
            );
        }finally{
            f.close();
        }
    }

    private static void zeroDropSettlement(){
        Fixture f=new Fixture(
            Collections.emptyList(),
            "CUSTOM_LOCALLAB_DROP_EMPTY"
        );

        try{
            NpcDropGroundSettlementService service=
                f.settlement();

            NpcDropGroundSettlementService.Receipt receipt=
                service.settle(
                    f.resolve(
                        "player:empty"
                    )
                );

            require(
                receipt.groundItems.isEmpty()&&
                service.size()==1&&
                f.world.groundItems().size()==0,
                "zero-drop settlement"
            );
        }finally{
            f.close();
        }
    }

    private static void conflictingResolutionRejected(){
        Fixture f=new Fixture(
            Collections.singletonList(
                new NpcDropResolutionService.Drop(
                    995,
                    10
                )
            ),
            "CUSTOM_LOCALLAB_DROP_FIRST"
        );

        try{
            NpcDropGroundSettlementService service=
                f.settlement();
            NpcDropResolutionService.Resolution first=
                f.resolve(
                    "player:conflict"
                );

            service.settle(first);

            NpcDropResolutionService conflictingService=
                new NpcDropResolutionService(
                    f.world.npcs(),
                    f.lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<NpcDropResolutionService.Drop>
                            resolve(
                                NpcDropResolutionService.DeathContext context
                            ){
                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    11
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_DROP_SECOND";
                        }
                    }
                );

            NpcDropResolutionService.Resolution conflicting=
                conflictingService.resolve(
                    f.npc,
                    "player:conflict"
                );

            expect(
                IllegalStateException.class,
                ()->service.settle(
                    conflicting
                ),
                "conflicting same-NPC settlement"
            );

            GroundItem coins=
                f.world.groundItems()
                    .findOwned(
                        995,
                        f.npc.x(),
                        f.npc.y(),
                        f.npc.plane(),
                        "player:conflict"
                    );

            require(
                coins!=null&&
                coins.amount==10&&
                service.size()==1,
                "conflict mutated settled loot"
            );
        }finally{
            f.close();
        }
    }

    private static void batchFailureRetryExactlyOnce(){
        Fixture f=new Fixture(
            Collections.singletonList(
                new NpcDropResolutionService.Drop(
                    995,
                    10
                )
            ),
            "CUSTOM_LOCALLAB_DROP_RETRY"
        );

        try{
            NpcDropResolutionService.Resolution resolution=
                f.resolve(
                    "player:retry"
                );
            Tile tile=
                resolution.context.deathTile;

            GroundItem blocker=
                f.world.groundItems()
                    .add(
                        995,
                        Integer.MAX_VALUE-5,
                        tile,
                        "player:retry",
                        f.deathTick,
                        false
                    );

            NpcDropGroundSettlementService service=
                f.settlement();

            expect(
                IllegalStateException.class,
                ()->service.settle(
                    resolution
                ),
                "overflow settlement"
            );

            require(
                service.size()==0&&
                service.get(f.npc.id)==null&&
                f.world.groundItems().size()==1&&
                blocker.amount==
                    Integer.MAX_VALUE-5,
                "failed settlement published partial receipt/items"
            );

            require(
                f.world.groundItems()
                    .remove(
                        blocker.id
                    ),
                "retry blocker removal"
            );

            NpcDropGroundSettlementService.Receipt receipt=
                service.settle(
                    resolution
                );

            GroundItem coins=
                f.world.groundItems()
                    .findOwned(
                        995,
                        tile.x,
                        tile.y,
                        tile.plane,
                        "player:retry"
                    );

            require(
                receipt!=null&&
                service.size()==1&&
                coins!=null&&
                coins.amount==10,
                "retry did not settle exactly once"
            );

            require(
                service.settle(resolution)==receipt&&
                coins.amount==10,
                "post-retry idempotency"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityBoundaryAndReceiptShape(){
        World world=
            World.isolatedForTest(600L);

        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcDropGroundSettlementService(
                    world,
                    "EXACT_CURRENT_CLIENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "client settlement authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcDropGroundSettlementService(
                    world,
                    "UNKNOWN_SERVER_AUTHORITY",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "unknown settlement authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_SETTLEMENT",
                    "PUBLIC_AFTER_60_SECONDS"
                ),
                "unsupported visibility policy"
            );

            for(Field field:
                    NpcDropGroundSettlementService
                        .Receipt.class
                        .getDeclaredFields())
                require(
                    field.getType()!=GroundItem.class,
                    "mutable GroundItem leaked into receipt "+
                    field.getName()
                );

            for(Field field:
                    NpcDropGroundSettlementService
                        .SettledGroundItem.class
                        .getDeclaredFields())
                require(
                    field.getType()!=GroundItem.class,
                    "mutable GroundItem leaked into row "+
                    field.getName()
                );
        }finally{
            world.close();
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final NpcLifecycleService lifecycle=
            world.npcLifecycle();
        final WorldNpc npc=
            world.npcs()
                .spawn(
                    1530,
                    3200,
                    3200,
                    0
                );
        final long deathTick=51L;
        final NpcDropResolutionService drops;

        Fixture(
            List<NpcDropResolutionService.Drop> resolved,
            String authority
        ){
            lifecycle.register(
                npc,
                10,
                "CUSTOM_LOCALLAB_HP"
            );

            require(
                lifecycle.applyDamage(
                    npc.id,
                    99,
                    deathTick
                ).newlyDied,
                "fixture lethal transition"
            );

            List<NpcDropResolutionService.Drop>
                immutableInput=
                    Collections.unmodifiableList(
                        new ArrayList<>(
                            resolved
                        )
                    );

            drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<NpcDropResolutionService.Drop>
                            resolve(
                                NpcDropResolutionService.DeathContext context
                            ){
                            return immutableInput;
                        }

                        @Override public String authority(){
                            return authority;
                        }
                    }
                );
        }

        NpcDropResolutionService.Resolution resolve(
            String recipient
        ){
            return drops.resolve(
                npc,
                recipient
            );
        }

        NpcDropGroundSettlementService settlement(){
            return new NpcDropGroundSettlementService(
                world,
                "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                NpcDropGroundSettlementService
                    .OWNER_SCOPED_DEATH_TILE
            );
        }

        void close(){
            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcDropGroundSettlementServiceTest(){}
}
