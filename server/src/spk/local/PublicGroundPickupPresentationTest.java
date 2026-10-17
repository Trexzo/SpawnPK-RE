package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class PublicGroundPickupPresentationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer picker=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();
        WorldPlayer wrongPlane=new WorldPlayer();

        long pickerGeneration=
            world.registerPlayer(picker,"picker");
        long viewerGeneration=
            world.registerPlayer(viewer,"viewer");
        long wrongPlaneGeneration=
            world.registerPlayer(wrongPlane,"wrongplane");

        try{
            MovementState pickerMovement=picker.movement();
            MovementState viewerMovement=viewer.movement();
            MovementState wrongMovement=wrongPlane.movement();

            wrongMovement.enterTransientRegion(
                wrongMovement.x(),
                wrongMovement.y(),
                2,
                wrongMovement.loadedBaseX(),
                wrongMovement.loadedBaseY()
            );

            LocalGroundItemInteractionHandler handler=
                new LocalGroundItemInteractionHandler(
                    world,
                    picker.bank(),
                    pickerMovement
                );

            ByteArrayOutputStream pickerWire=
                new ByteArrayOutputStream();
            ServerPacketWriter pickerWriter=
                new ServerPacketWriter(
                    pickerWire,
                    new IsaacCipher(
                        new int[]{81,82,83,84}
                    )
                );
            SceneUpdatePublisher pickerScene=
                new SceneUpdatePublisher(
                    pickerWriter,
                    new SceneCoordinateContext(
                        pickerMovement.loadedBaseX(),
                        pickerMovement.loadedBaseY(),
                        pickerMovement.plane()
                    )
                );

            Tile tile=
                new Tile(
                    pickerMovement.x(),
                    pickerMovement.y(),
                    pickerMovement.plane()
                );

            GroundItem publicItem=
                world.groundItems().add(
                    995,
                    25,
                    tile,
                    null,
                    100L,
                    false
                );

            LocalGroundItemInteractionHandler.Result publicTake=
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        995,
                        tile.x,
                        tile.y
                    ),
                    "picker",
                    pickerScene,
                    pickerWriter
                );

            require(
                publicTake!=null&&
                "GROUND_TAKE".equals(
                    publicTake.saveReason
                ),
                "public pickup did not commit"
            );
            require(
                picker.bank().inventoryCount(995)==25&&
                world.groundItems().byId(
                    publicItem.id
                )==null,
                "public pickup canonical mutation missing"
            );
            requireRemovePending(
                world,
                viewer,
                viewerGeneration,
                publicItem.id,
                "eligible viewer"
            );
            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        picker.id(),
                        pickerGeneration,
                        System.currentTimeMillis()
                    ).isEmpty(),
                "picker received duplicate queued remove"
            );
            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        wrongPlane.id(),
                        wrongPlaneGeneration,
                        System.currentTimeMillis()
                    ).isEmpty(),
                "wrong-plane viewer received remove"
            );

            proveAbortRetry(
                world,
                viewer
            );

            require(
                picker.bank().inventoryCount(995)==25&&
                world.groundItems().byId(
                    publicItem.id
                )==null,
                "presentation retry changed committed pickup"
            );

            GroundItem privateItem=
                world.groundItems().add(
                    4151,
                    1,
                    tile,
                    "picker",
                    101L,
                    false
                );

            LocalGroundItemInteractionHandler.Result privateTake=
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        4151,
                        tile.x,
                        tile.y
                    ),
                    "picker",
                    pickerScene,
                    pickerWriter
                );

            require(
                privateTake!=null&&
                "GROUND_TAKE".equals(
                    privateTake.saveReason
                )&&
                picker.bank().inventoryCount(4151)==1&&
                world.groundItems().byId(
                    privateItem.id
                )==null,
                "private pickup did not commit"
            );
            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewerGeneration,
                        System.currentTimeMillis()
                    ).isEmpty(),
                "private pickup leaked cross-viewer remove"
            );

            System.out.println(
                "PUBLIC_GROUND_PICKUP_PRESENTATION_PASS "+
                "publicOtherViewerRemove=true "+
                "pickerDuplicate=false "+
                "wrongPlaneExcluded=true "+
                "privateBroadcast=false "+
                "abortRetainsDebt=true "+
                "retryExactlyOnce=true "+
                "canonicalPickupIndependent=true"
            );
        }finally{
            if(picker.registered())
                world.unregisterPlayer(
                    picker,
                    pickerGeneration
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );
            if(wrongPlane.registered())
                world.unregisterPlayer(
                    wrongPlane,
                    wrongPlaneGeneration
                );
            world.close();
        }
    }

    private static void proveAbortRetry(
        World world,
        WorldPlayer viewer
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1<<20);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{91,92,93,94}
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
                System.currentTimeMillis(),
                publisher
            )==1,
            "public remove not staged"
        );
        writer.abortBatch();
        relay.abortStagedDeliveries();

        require(
            queue.queuedBytes()==before&&
            !world.groundItemPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    viewer.generation(),
                    System.currentTimeMillis()
                ).isEmpty(),
            "aborted remove debt was lost"
        );

        writer.beginBatch();
        require(
            relay.publishPending(
                System.currentTimeMillis(),
                publisher
            )==1,
            "remove retry not staged"
        );
        writer.endBatch();

        require(
            relay.commitStagedDeliveries(
                System.currentTimeMillis()
            )==1,
            "remove retry not committed"
        );
        require(
            queue.queuedBytes()>before&&
            world.groundItemPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    viewer.generation(),
                    System.currentTimeMillis()
                ).isEmpty(),
            "remove retry did not settle exactly once"
        );
    }

    private static void requireRemovePending(
        World world,
        WorldPlayer viewer,
        long generation,
        long groundItemId,
        String label
    ){
        List<WorldGroundItemPresentationEvents.Event> pending=
            world.groundItemPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    generation,
                    System.currentTimeMillis()
                );

        boolean found=false;
        for(WorldGroundItemPresentationEvents.Event event:
                pending)
            if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
               event.groundItemId==
                    groundItemId)
                found=true;

        require(
            found,
            label+" remove presentation missing"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private PublicGroundPickupPresentationTest(){}
}
