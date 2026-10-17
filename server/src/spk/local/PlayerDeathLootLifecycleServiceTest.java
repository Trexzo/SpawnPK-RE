package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class PlayerDeathLootLifecycleServiceTest {
    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(600L);
        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer other=
            new WorldPlayer();

        long ownerGeneration=
            world.registerPlayer(
                owner,
                "killer"
            );
        long otherGeneration=
            world.registerPlayer(
                other,
                "other"
            );

        try{
            int x=owner.movement().x();
            int y=owner.movement().y();
            int plane=owner.movement().plane();
            Tile tile=new Tile(x,y,plane);

            PlayerDeathLootLifecycleService lifecycle=
                world.deathLootLifecycle();

            long start=1_000L;

            GroundItem privateDrop=
                world.groundItems().add(
                    995,10,tile,"killer",1L,false
                );

            require(
                lifecycle.registerGroundItem(
                    privateDrop.id,
                    start
                ),
                "private drop registration rejected"
            );

            require(
                world.groundItems().findVisible(
                    995,x,y,plane,"killer"
                )==privateDrop,
                "owner cannot see private loot"
            );
            require(
                world.groundItems().findVisible(
                    995,x,y,plane,"other"
                )==null,
                "other player saw private loot"
            );

            PlayerDeathLootLifecycleService.TickResult before=
                lifecycle.tick(
                    start+
                    PlayerDeathLootLifecycleService
                        .PRIVATE_VISIBILITY_MS-
                    1L
                );

            require(
                before.publicized==0&&
                privateDrop.owner!=null,
                "private loot publicized early"
            );

            long publicAt=
                start+
                PlayerDeathLootLifecycleService
                    .PRIVATE_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult publicized=
                lifecycle.tick(publicAt);

            require(
                publicized.publicized==1,
                "private loot did not publicize"
            );
            require(
                world.groundItems().byId(
                    privateDrop.id
                )==privateDrop&&
                privateDrop.owner==null,
                "publicization did not preserve exact ground id/object"
            );
            require(
                world.groundItems().findVisible(
                    995,x,y,plane,"killer"
                )==privateDrop,
                "owner lost visibility after publicization"
            );
            require(
                world.groundItems().findVisible(
                    995,x,y,plane,"other"
                )==privateDrop,
                "other player cannot see publicized loot"
            );

            List<WorldGroundItemPresentationEvents.Event>
                otherSpawn=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            other.id(),
                            otherGeneration,
                            publicAt
                        );

            require(
                otherSpawn.size()==1&&
                otherSpawn.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                otherSpawn.get(0).groundItemId==
                    privateDrop.id,
                "public spawn presentation missing"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        owner.id(),
                        ownerGeneration,
                        publicAt
                    ).isEmpty(),
                "private owner received duplicate public spawn"
            );

            proveAbortRetry(
                world,
                other,
                publicAt
            );

            long expiresAt=
                publicAt+
                PlayerDeathLootLifecycleService
                    .PUBLIC_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult expired=
                lifecycle.tick(expiresAt);

            require(
                expired.expired==1&&
                world.groundItems().byId(
                    privateDrop.id
                )==null,
                "public loot did not expire"
            );

            requireRemovePending(
                world,
                owner,
                ownerGeneration,
                privateDrop.id,
                expiresAt,
                "owner remove"
            );
            requireRemovePending(
                world,
                other,
                otherGeneration,
                privateDrop.id,
                expiresAt,
                "other remove"
            );

            GroundItem pickedPrivate=
                world.groundItems().add(
                    996,1,tile,"killer",2L,false
                );
            lifecycle.registerGroundItem(
                pickedPrivate.id,
                10_000L
            );
            world.groundItems().remove(
                pickedPrivate.id
            );

            PlayerDeathLootLifecycleService.TickResult
                pickedBeforePublic=
                    lifecycle.tick(
                        10_000L+
                        PlayerDeathLootLifecycleService
                            .PRIVATE_VISIBILITY_MS
                    );

            require(
                pickedBeforePublic.cancelledMissing==1,
                "pickup before publicization did not cancel lifecycle"
            );

            GroundItem pickedPublic=
                world.groundItems().add(
                    997,1,tile,"killer",3L,false
                );
            long secondStart=20_000L;
            lifecycle.registerGroundItem(
                pickedPublic.id,
                secondStart
            );
            lifecycle.tick(
                secondStart+
                PlayerDeathLootLifecycleService
                    .PRIVATE_VISIBILITY_MS
            );
            world.groundItems().remove(
                pickedPublic.id
            );

            PlayerDeathLootLifecycleService.TickResult
                pickedAfterPublic=
                    lifecycle.tick(
                        secondStart+
                        PlayerDeathLootLifecycleService
                            .PRIVATE_VISIBILITY_MS+
                        PlayerDeathLootLifecycleService
                            .PUBLIC_VISIBILITY_MS
                    );

            require(
                pickedAfterPublic.cancelledMissing==1,
                "pickup after publicization did not cancel expiry"
            );

            GroundItem stale=
                world.groundItems().add(
                    998,1,tile,"killer",4L,false
                );
            long staleStart=30_000L;
            lifecycle.registerGroundItem(
                stale.id,
                staleStart
            );
            stale.amount=2;

            PlayerDeathLootLifecycleService.TickResult
                staleResult=
                    lifecycle.tick(
                        staleStart+
                        PlayerDeathLootLifecycleService
                            .PRIVATE_VISIBILITY_MS
                    );

            require(
                staleResult.stalePreimage==1&&
                stale.owner!=null,
                "stale preimage did not fail closed"
            );

            GroundItem initiallyPublic=
                world.groundItems().add(
                    999,1,tile,null,5L,false
                );
            long publicStart=40_000L;
            lifecycle.registerGroundItem(
                initiallyPublic.id,
                publicStart
            );

            PlayerDeathLootLifecycleService.TickResult
                initialPublicExpiry=
                    lifecycle.tick(
                        publicStart+
                        PlayerDeathLootLifecycleService
                            .PUBLIC_VISIBILITY_MS
                    );

            require(
                initialPublicExpiry.expired==1&&
                world.groundItems().byId(
                    initiallyPublic.id
                )==null,
                "initially public death loot did not expire"
            );

            System.out.println(
                "PLAYER_DEATH_LOOT_LIFECYCLE_PASS "+
                "privateFirst=true "+
                "sameGroundIdPublicized=true "+
                "ownerRetainsVisibility=true "+
                "otherVisibleAfterPublic=true "+
                "pickupBeforePublicCancels=true "+
                "pickupAfterPublicCancelsExpiry=true "+
                "expiryRemovesExactItem=true "+
                "stalePreimageFailClosed=true "+
                "presentationAbortRetry=true "+
                "authority="+
                PlayerDeathLootLifecycleService.AUTHORITY
            );
        }finally{
            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    ownerGeneration
                );
            if(other.registered())
                world.unregisterPlayer(
                    other,
                    otherGeneration
                );
            world.close();
        }
    }

    private static void proveAbortRetry(
        World world,
        WorldPlayer viewer,
        long now
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1<<20);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{41,42,43,44}
                )
            );
        MovementState movement=
            viewer.movement();
        SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    movement.loadedBaseX(),
                    movement.loadedBaseY(),
                    movement.plane()
                )
            );
        LocalGroundItemPresentationRelay relay=
            new LocalGroundItemPresentationRelay(
                world,
                viewer,
                movement
            );

        int before=queue.queuedBytes();

        writer.beginBatch();
        require(
            relay.publishPending(
                now,
                publisher
            )==1,
            "public spawn not staged"
        );
        writer.abortBatch();
        relay.abortStagedDeliveries();

        require(
            queue.queuedBytes()==before&&
            world.groundItemPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    viewer.generation(),
                    now
                ).size()==1,
            "aborted public spawn was not retryable"
        );

        writer.beginBatch();
        require(
            relay.publishPending(
                now+1L,
                publisher
            )==1,
            "public spawn retry not staged"
        );
        writer.endBatch();
        require(
            relay.commitStagedDeliveries(
                now+2L
            )==1,
            "public spawn retry not committed"
        );

        require(
            queue.queuedBytes()>before&&
            world.groundItemPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    viewer.generation(),
                    now+2L
                ).isEmpty(),
            "public spawn retry did not settle presentation debt"
        );

        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();
        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                Integer.MAX_VALUE
            );
    }

    private static void requireRemovePending(
        World world,
        WorldPlayer viewer,
        long generation,
        long groundItemId,
        long now,
        String label
    ){
        List<WorldGroundItemPresentationEvents.Event>
            pending=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        boolean found=false;
        for(WorldGroundItemPresentationEvents.Event event:
                pending)
            if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
               event.groundItemId==groundItemId)
                found=true;

        require(
            found,
            label+" presentation missing"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private PlayerDeathLootLifecycleServiceTest(){}
}
