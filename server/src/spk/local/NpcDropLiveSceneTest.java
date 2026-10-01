package spk.local;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

public final class NpcDropLiveSceneTest {
    public static void main(String[] args)throws Exception{
        spawnAndAmountForExactOwner();
        offlineAndStaleRecipients();
        sceneBoundsConsumedSafely();
        publishFailureRetries();
        regionPresentationBarrier();

        System.out.println(
            "NPC_DROP_LIVE_SCENE_PASS "+
            "ownerGeneration=true "+
            "spawn44=true "+
            "amount84=true "+
            "otherPlayerExcluded=true "+
            "idempotentNoDuplicate=true "+
            "offlineSettlement=true "+
            "sceneBoundsSafe=true "+
            "retryOnPublishFailure=true "+
            "regionPresentationBarrier=true "+
            "protocolAdapterOnly=true"
        );
    }

    private static void spawnAndAmountForExactOwner()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=
            new WorldPlayer();
        WorldPlayer other=
            new WorldPlayer();

        try{
            long killerGeneration=
                world.registerPlayer(
                    killer,
                    "killer"
                );
            long otherGeneration=
                world.registerPlayer(
                    other,
                    "other"
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            NpcDropResolutionService drops=
                dropService(
                    world,
                    lifecycle,
                    995,
                    10,
                    "CUSTOM_LOCALLAB_DROP_LIVE"
                );
            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            WorldNpc first=
                deadNpc(
                    world,
                    lifecycle,
                    1600,
                    killer.movement().x(),
                    killer.movement().y(),
                    0,
                    10L
                );

            NpcDropResolutionService.Resolution firstResolution=
                drops.resolve(
                    first,
                    "killer"
                );

            NpcDropGroundSettlementService.Receipt firstReceipt=
                settlement.settle(
                    firstResolution
                );

            require(
                firstReceipt!=null&&
                world.groundItemPresentationEvents()
                    .size()==1,
                "spawn settlement event"
            );

            List<WorldGroundItemPresentationEvents.Event>
                killerPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        );

            require(
                killerPending.size()==1&&
                killerPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                killerPending.get(0).itemId==995&&
                killerPending.get(0).newAmount==10,
                "spawn event facts"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        other.id(),
                        otherGeneration,
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "other player received owner event"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );
            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );
            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    killer,
                    killer.movement()
                );

            int before=
                wire.size();

            require(
                relay.publishPending(
                    System.currentTimeMillis(),
                    publisher
                )==1&&
                wire.size()>before&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "spawn packet publication"
            );

            require(
                settlement.settle(
                    firstResolution
                )==firstReceipt&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "idempotent settlement re-enqueued spawn"
            );

            WorldNpc second=
                deadNpc(
                    world,
                    lifecycle,
                    1601,
                    killer.movement().x(),
                    killer.movement().y(),
                    0,
                    11L
                );

            NpcDropResolutionService.Resolution secondResolution=
                drops.resolve(
                    second,
                    "killer"
                );

            settlement.settle(
                secondResolution
            );

            List<WorldGroundItemPresentationEvents.Event>
                amountPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        );

            require(
                amountPending.size()==1&&
                amountPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.AMOUNT&&
                amountPending.get(0).oldAmount==10&&
                amountPending.get(0).newAmount==20,
                "amount event facts"
            );

            int beforeAmount=
                wire.size();

            require(
                relay.publishPending(
                    System.currentTimeMillis(),
                    publisher
                )==1&&
                wire.size()>beforeAmount&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "amount packet publication"
            );
        }finally{
            world.unregisterPlayer(killer);
            world.unregisterPlayer(other);
            world.close();
        }
    }

    private static void offlineAndStaleRecipients()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer stale=
            new WorldPlayer();

        try{
            long staleGeneration=
                world.registerPlayer(
                    stale,
                    "stale"
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            NpcDropResolutionService drops=
                dropService(
                    world,
                    lifecycle,
                    995,
                    1,
                    "CUSTOM_LOCALLAB_DROP_OFFLINE"
                );
            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            WorldNpc offlineNpc=
                deadNpc(
                    world,
                    lifecycle,
                    1610,
                    stale.movement().x(),
                    stale.movement().y(),
                    0,
                    20L
                );

            require(
                settlement.settle(
                    drops.resolve(
                        offlineNpc,
                        "offline"
                    )
                )!=null&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "offline settlement created live event"
            );

            WorldNpc staleNpc=
                deadNpc(
                    world,
                    lifecycle,
                    1611,
                    stale.movement().x(),
                    stale.movement().y(),
                    0,
                    21L
                );

            settlement.settle(
                drops.resolve(
                    staleNpc,
                    "stale"
                )
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        stale.id(),
                        staleGeneration,
                        System.currentTimeMillis()
                    )
                    .size()==1,
                "stale fixture event"
            );

            require(
                world.unregisterPlayer(
                    stale,
                    staleGeneration
                ),
                "stale player unregister"
            );

            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    stale,
                    stale.movement()
                );
            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            SceneUpdatePublisher publisher=
                publisher(
                    wire,
                    stale.movement().plane()
                );

            require(
                relay.publishPending(
                    System.currentTimeMillis(),
                    publisher
                )==0&&
                wire.size()==0,
                "stale generation received event"
            );
        }finally{
            world.unregisterPlayer(stale);
            world.close();
        }
    }

    private static void sceneBoundsConsumedSafely()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    killer,
                    "killer"
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            NpcDropResolutionService drops=
                dropService(
                    world,
                    lifecycle,
                    995,
                    2,
                    "CUSTOM_LOCALLAB_DROP_BOUNDS"
                );
            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            WorldNpc planeTwo=
                deadNpc(
                    world,
                    lifecycle,
                    1620,
                    killer.movement().x(),
                    killer.movement().y(),
                    2,
                    30L
                );

            settlement.settle(
                drops.resolve(
                    planeTwo,
                    "killer"
                )
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    killer,
                    killer.movement()
                );

            require(
                relay.publishPending(
                    System.currentTimeMillis(),
                    publisher(
                        wire,
                        0
                    )
                )==0&&
                wire.size()==0&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "off-plane event was emitted or retained"
            );
        }finally{
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void publishFailureRetries()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    killer,
                    "killer"
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            NpcDropResolutionService drops=
                dropService(
                    world,
                    lifecycle,
                    995,
                    4,
                    "CUSTOM_LOCALLAB_DROP_RETRY_SCENE"
                );
            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            WorldNpc npc=
                deadNpc(
                    world,
                    lifecycle,
                    1630,
                    killer.movement().x(),
                    killer.movement().y(),
                    0,
                    40L
                );

            settlement.settle(
                drops.resolve(
                    npc,
                    "killer"
                )
            );

            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    killer,
                    killer.movement()
                );

            OutputStream failing=
                new OutputStream(){
                    @Override public void write(int value)
                        throws IOException{
                        throw new IOException(
                            "synthetic scene write failure"
                        );
                    }

                    @Override public void write(
                        byte[] bytes,
                        int offset,
                        int length
                    )throws IOException{
                        throw new IOException(
                            "synthetic scene write failure"
                        );
                    }
                };

            SceneUpdatePublisher broken=
                new SceneUpdatePublisher(
                    new ServerPacketWriter(
                        failing,
                        new IsaacCipher(
                            new int[]{1,2,3,4}
                        )
                    ),
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            expect(
                IOException.class,
                ()->relay.publishPending(
                    System.currentTimeMillis(),
                    broken
                ),
                "scene publication failure"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        System.currentTimeMillis()
                    )
                    .size()==1,
                "failed publication marked delivered"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            require(
                relay.publishPending(
                    System.currentTimeMillis(),
                    publisher(
                        wire,
                        0
                    )
                )==1&&
                wire.size()>0&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "scene publication retry"
            );

            for(Field field:
                    WorldGroundItemPresentationEvents
                        .Event.class
                        .getDeclaredFields())
                require(
                    field.getType()!=GroundItem.class,
                    "mutable GroundItem leaked into event "+
                    field.getName()
                );
        }finally{
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void regionPresentationBarrier()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer killer=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    killer,
                    "killer"
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();
            NpcDropResolutionService drops=
                dropService(
                    world,
                    lifecycle,
                    995,
                    6,
                    "CUSTOM_LOCALLAB_DROP_REGION_BARRIER"
                );
            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_OWNER_SCOPED_LOOT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            WorldNpc npc=
                deadNpc(
                    world,
                    lifecycle,
                    1640,
                    killer.movement().x(),
                    killer.movement().y(),
                    0,
                    50L
                );

            settlement.settle(
                drops.resolve(
                    npc,
                    "killer"
                )
            );

            long now=System.currentTimeMillis();
            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    killer,
                    killer.movement()
                );
            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            SceneUpdatePublisher live=
                publisher(
                    wire,
                    0
                );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        now
                    )
                    .size()==1,
                "region barrier fixture event"
            );

            require(
                relay.publishPendingIfSceneReady(
                    now,
                    live,
                    true
                )==0&&
                wire.size()==0&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        now
                    )
                    .size()==1,
                "pending region load published live ground event"
            );

            require(
                relay.consumeSnapshotCoveredAfterSnapshot(
                    now,
                    false
                )==0&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        now
                    )
                    .size()==1,
                "region begin falsely consumed snapshot-covered event"
            );

            GroundItem item=
                world.groundItems()
                    .snapshot()
                    .get(0);

            OutputStream failing=
                new OutputStream(){
                    @Override public void write(int value)
                        throws IOException{
                        throw new IOException(
                            "synthetic snapshot failure"
                        );
                    }

                    @Override public void write(
                        byte[] bytes,
                        int offset,
                        int length
                    )throws IOException{
                        throw new IOException(
                            "synthetic snapshot failure"
                        );
                    }
                };

            SceneUpdatePublisher broken=
                publisher(
                    failing,
                    0
                );

            expect(
                IOException.class,
                ()->broken.groundSpawn(item),
                "snapshot publication failure"
            );

            require(
                relay.consumeSnapshotCoveredAfterSnapshot(
                    now,
                    false
                )==0&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        now
                    )
                    .size()==1,
                "failed snapshot consumed pending event"
            );

            live.groundSpawn(item);

            require(
                relay.consumeSnapshotCoveredAfterSnapshot(
                    now,
                    true
                )==1&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        generation,
                        now
                    )
                    .isEmpty(),
                "successful snapshot did not consume covered event"
            );

            int afterSnapshot=wire.size();

            require(
                relay.publishPendingIfSceneReady(
                    now,
                    live,
                    false
                )==0&&
                wire.size()==afterSnapshot,
                "post-snapshot duplicate live publication"
            );
        }finally{
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static NpcDropResolutionService dropService(
        World world,
        NpcLifecycleService lifecycle,
        int itemId,
        int amount,
        String authority
    ){
        return new NpcDropResolutionService(
            world.npcs(),
            lifecycle,
            new NpcDropResolutionService.DropResolver(){
                @Override public List<NpcDropResolutionService.Drop>
                    resolve(
                        NpcDropResolutionService.DeathContext context
                    ){
                    return Collections.singletonList(
                        new NpcDropResolutionService.Drop(
                            itemId,
                            amount
                        )
                    );
                }

                @Override public String authority(){
                    return authority;
                }
            }
        );
    }

    private static WorldNpc deadNpc(
        World world,
        NpcLifecycleService lifecycle,
        int definitionId,
        int x,
        int y,
        int plane,
        long deathTick
    ){
        WorldNpc npc=
            world.npcs().spawn(
                definitionId,
                x,
                y,
                plane
            );

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
            "dead NPC fixture"
        );

        return npc;
    }

    private static SceneUpdatePublisher publisher(
        OutputStream output,
        int plane
    ){
        return new SceneUpdatePublisher(
            new ServerPacketWriter(
                output,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            ),
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                plane
            )
        );
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

    private NpcDropLiveSceneTest(){}
}
