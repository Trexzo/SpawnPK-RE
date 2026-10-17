package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class GroundItemVisibilityCollisionTest {
    public static void main(String[] args)
        throws Exception {

        publicizationCollisionRetriesSameId();
        persistentCollisionExpiresPrivateOnly();
        publicLifecycleExpiryRestoresPrivateOverlay();
        privatePickupRestoresPublicFallback();
        publicPickupRestoresPrivateOverlay();

        System.out.println(
            "GROUND_ITEM_VISIBILITY_COLLISION_PASS "+
            "duplicatePublicKeyPrevented=true "+
            "blockedPrivateOwnerVisible=true "+
            "blockedPrivateHiddenFromOther=true "+
            "collisionRemovalRetriesSameId=true "+
            "persistentCollisionExpiresPrivateOnly=true "+
            "publicStackPreserved=true "+
            "privateOwnerRemoveThenPublicRestore=true "+
            "publicLifecycleRemoveThenPrivateRestore=true "+
            "privatePickupRestoresPublicFallback=true "+
            "publicPickupRemoveThenPrivateRestore=true "+
            "crossProvenanceMerge=false"
        );
    }

    private static void publicizationCollisionRetriesSameId()
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
            Tile tile=
                new Tile(
                    owner.movement().x(),
                    owner.movement().y(),
                    owner.movement().plane()
                );

            GroundItem publicItem=
                world.groundItems().add(
                    995,7,tile,null,1L,false
                );
            GroundItem privateItem=
                world.groundItems().add(
                    995,3,tile,"killer",2L,false
                );

            PlayerDeathLootLifecycleService lifecycle=
                world.deathLootLifecycle();

            long start=1_000L;
            require(
                lifecycle.registerGroundItem(
                    privateItem.id,
                    start
                ),
                "private lifecycle registration rejected"
            );

            long publicAt=
                start+
                PlayerDeathLootLifecycleService
                    .PRIVATE_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult
                blocked=
                    lifecycle.tick(
                        publicAt
                    );

            require(
                blocked.publicized==0,
                "collision unexpectedly publicized"
            );
            require(
                world.groundItems().size()==2&&
                publicItem.owner==null&&
                "killer".equals(
                    privateItem.owner
                ),
                "collision changed canonical stacks"
            );
            require(
                countPublicKey(
                    world,
                    995,
                    tile
                )==1,
                "duplicate public stack key created"
            );
            require(
                world.groundItems().findVisible(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "killer"
                )==privateItem,
                "blocked private item not preferred for owner"
            );
            require(
                world.groundItems().findVisible(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "other"
                )==publicItem,
                "other viewer did not retain public stack"
            );
            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        other.id(),
                        otherGeneration,
                        publicAt
                    ).isEmpty(),
                "blocked publicization emitted other-view event"
            );

            require(
                world.groundItems().remove(
                    publicItem.id
                ),
                "could not remove collision fixture"
            );

            PlayerDeathLootLifecycleService.TickResult
                retried=
                    lifecycle.tick(
                        publicAt+1L
                    );

            require(
                retried.publicized==1&&
                world.groundItems().byId(
                    privateItem.id
                )==privateItem&&
                privateItem.owner==null,
                "same-id publicization did not retry"
            );
            require(
                countPublicKey(
                    world,
                    995,
                    tile
                )==1,
                "retried publicization broke uniqueness"
            );

            List<WorldGroundItemPresentationEvents.Event>
                otherPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            other.id(),
                            otherGeneration,
                            publicAt+1L
                        );

            require(
                otherPending.size()==1&&
                otherPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                otherPending.get(0).groundItemId==
                    privateItem.id,
                "same-id retry did not publish exact new public stack"
            );
            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        owner.id(),
                        ownerGeneration,
                        publicAt+1L
                    ).isEmpty(),
                "owner received duplicate publicization spawn"
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

    private static void persistentCollisionExpiresPrivateOnly()
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
            Tile tile=
                new Tile(
                    owner.movement().x(),
                    owner.movement().y(),
                    owner.movement().plane()
                );

            GroundItem publicItem=
                world.groundItems().add(
                    4151,1,tile,null,3L,false
                );
            GroundItem privateItem=
                world.groundItems().add(
                    4151,1,tile,"killer",4L,false
                );

            long start=10_000L;
            PlayerDeathLootLifecycleService lifecycle=
                world.deathLootLifecycle();

            lifecycle.registerGroundItem(
                privateItem.id,
                start
            );

            long publicAt=
                start+
                PlayerDeathLootLifecycleService
                    .PRIVATE_VISIBILITY_MS;

            lifecycle.tick(publicAt);

            long expiresAt=
                publicAt+
                PlayerDeathLootLifecycleService
                    .PUBLIC_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult
                expired=
                    lifecycle.tick(
                        expiresAt
                    );

            require(
                expired.expired==1,
                "blocked private stack did not expire"
            );
            require(
                world.groundItems().byId(
                    privateItem.id
                )==null,
                "expired private stack remained"
            );
            require(
                world.groundItems().byId(
                    publicItem.id
                )==publicItem&&
                publicItem.owner==null&&
                publicItem.amount==1,
                "unrelated public stack changed"
            );
            require(
                countPublicKey(
                    world,
                    4151,
                    tile
                )==1,
                "public key uniqueness changed at private expiry"
            );

            List<WorldGroundItemPresentationEvents.Event>
                ownerPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            owner.id(),
                            ownerGeneration,
                            expiresAt
                        );

            require(
                ownerPending.size()==2&&
                ownerPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
                ownerPending.get(0).groundItemId==
                    privateItem.id&&
                ownerPending.get(1).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                ownerPending.get(1).groundItemId==
                    publicItem.id,
                "owner scene was not normalized remove->public spawn"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        other.id(),
                        otherGeneration,
                        expiresAt
                    ).isEmpty(),
                "blocked private expiry leaked to unrelated viewer"
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

    private static void publicLifecycleExpiryRestoresPrivateOverlay()
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
            Tile tile=
                new Tile(
                    owner.movement().x(),
                    owner.movement().y(),
                    owner.movement().plane()
                );

            GroundItem publicItem=
                world.groundItems().add(
                    1337,5,tile,null,7L,false
                );
            GroundItem privateItem=
                world.groundItems().add(
                    1337,2,tile,"killer",8L,false
                );

            PlayerDeathLootLifecycleService lifecycle=
                world.deathLootLifecycle();

            long publicStart=20_000L;
            long privateStart=
                publicStart+
                70_000L;

            lifecycle.registerGroundItem(
                publicItem.id,
                publicStart
            );
            lifecycle.registerGroundItem(
                privateItem.id,
                privateStart
            );

            long publicExpiresAt=
                publicStart+
                PlayerDeathLootLifecycleService
                    .PUBLIC_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult
                publicExpired=
                    lifecycle.tick(
                        publicExpiresAt
                    );

            require(
                publicExpired.expired==1&&
                world.groundItems().byId(
                    publicItem.id
                )==null&&
                world.groundItems().byId(
                    privateItem.id
                )==privateItem&&
                "killer".equals(
                    privateItem.owner
                ),
                "public lifecycle expiry changed private canonical stack"
            );

            List<WorldGroundItemPresentationEvents.Event>
                ownerPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            owner.id(),
                            ownerGeneration,
                            publicExpiresAt
                        );

            require(
                ownerPending.size()==2&&
                ownerPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
                ownerPending.get(0).groundItemId==
                    publicItem.id&&
                ownerPending.get(1).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                ownerPending.get(1).groundItemId==
                    privateItem.id,
                "public lifecycle expiry did not restore private overlay"
            );

            List<WorldGroundItemPresentationEvents.Event>
                otherPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            other.id(),
                            otherGeneration,
                            publicExpiresAt
                        );

            require(
                otherPending.size()==1&&
                otherPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
                otherPending.get(0).groundItemId==
                    publicItem.id,
                "ordinary viewer public lifecycle remove changed"
            );

            long privatePublicAt=
                privateStart+
                PlayerDeathLootLifecycleService
                    .PRIVATE_VISIBILITY_MS;

            PlayerDeathLootLifecycleService.TickResult
                privatePublicized=
                    lifecycle.tick(
                        privatePublicAt
                    );

            require(
                privatePublicized.publicized==1&&
                privateItem.owner==null&&
                world.groundItems().byId(
                    privateItem.id
                )==privateItem,
                "private stack did not publicize after public expiry"
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

    private static void privatePickupRestoresPublicFallback()
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
            Tile tile=
                new Tile(
                    owner.movement().x(),
                    owner.movement().y(),
                    owner.movement().plane()
                );

            GroundItem publicItem=
                world.groundItems().add(
                    4151,1,tile,null,9L,false
                );
            GroundItem privateItem=
                world.groundItems().add(
                    4151,1,tile,"killer",10L,false
                );

            LocalGroundItemInteractionHandler handler=
                new LocalGroundItemInteractionHandler(
                    world,
                    owner.bank(),
                    owner.movement()
                );

            ServerPacketWriter writer=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{71,72,73,74}
                    )
                );
            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        owner.movement()
                            .loadedBaseX(),
                        owner.movement()
                            .loadedBaseY(),
                        owner.movement()
                            .plane()
                    )
                );

            LocalGroundItemInteractionHandler.Result result=
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        4151,
                        tile.x,
                        tile.y
                    ),
                    "killer",
                    scene,
                    writer
                );

            require(
                result!=null&&
                result.logText.contains(
                    "pickerFallbackQueued=1"),
                "private pickup did not queue public fallback"
            );
            require(
                world.groundItems().byId(
                    privateItem.id
                )==null&&
                world.groundItems().byId(
                    publicItem.id
                )==publicItem,
                "private pickup changed public canonical fallback"
            );

            long now=System.currentTimeMillis();

            List<WorldGroundItemPresentationEvents.Event>
                ownerPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            owner.id(),
                            ownerGeneration,
                            now
                        );

            require(
                ownerPending.size()==1&&
                ownerPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                ownerPending.get(0).groundItemId==
                    publicItem.id,
                "picker public fallback presentation missing"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        other.id(),
                        otherGeneration,
                        now
                    ).isEmpty(),
                "private pickup fallback leaked to unrelated viewer"
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

    private static void publicPickupRestoresPrivateOverlay()
        throws Exception {

        World world=
            World.isolatedForTest(600L);
        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer picker=
            new WorldPlayer();
        WorldPlayer other=
            new WorldPlayer();

        long ownerGeneration=
            world.registerPlayer(
                owner,
                "killer"
            );
        long pickerGeneration=
            world.registerPlayer(
                picker,
                "picker"
            );
        long otherGeneration=
            world.registerPlayer(
                other,
                "other"
            );

        try{
            Tile tile=
                new Tile(
                    picker.movement().x(),
                    picker.movement().y(),
                    picker.movement().plane()
                );

            GroundItem privateItem=
                world.groundItems().add(
                    995,11,tile,"killer",5L,false
                );
            GroundItem publicItem=
                world.groundItems().add(
                    995,7,tile,null,6L,false
                );

            LocalGroundItemInteractionHandler handler=
                new LocalGroundItemInteractionHandler(
                    world,
                    picker.bank(),
                    picker.movement()
                );

            ByteArrayOutputStream pickerWire=
                new ByteArrayOutputStream();
            ServerPacketWriter pickerWriter=
                new ServerPacketWriter(
                    pickerWire,
                    new IsaacCipher(
                        new int[]{61,62,63,64}
                    )
                );
            SceneUpdatePublisher pickerScene=
                new SceneUpdatePublisher(
                    pickerWriter,
                    new SceneCoordinateContext(
                        picker.movement()
                            .loadedBaseX(),
                        picker.movement()
                            .loadedBaseY(),
                        picker.movement()
                            .plane()
                    )
                );

            LocalGroundItemInteractionHandler.Result result=
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
                result!=null&&
                result.logText.contains(
                    "crossViewerRemoveQueued=2"),
                "public pickup did not queue both eligible viewers"
            );
            require(
                world.groundItems().byId(
                    publicItem.id
                )==null&&
                world.groundItems().byId(
                    privateItem.id
                )==privateItem,
                "public pickup changed private canonical state"
            );

            long now=System.currentTimeMillis();

            List<WorldGroundItemPresentationEvents.Event>
                ownerPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            owner.id(),
                            ownerGeneration,
                            now
                        );

            require(
                ownerPending.size()==2&&
                ownerPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
                ownerPending.get(0).groundItemId==
                    publicItem.id&&
                ownerPending.get(1).kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN&&
                ownerPending.get(1).groundItemId==
                    privateItem.id,
                "owner private overlay was not restored"
            );

            List<WorldGroundItemPresentationEvents.Event>
                otherPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            other.id(),
                            otherGeneration,
                            now
                        );

            require(
                otherPending.size()==1&&
                otherPending.get(0).kind==
                    WorldGroundItemPresentationEvents.Kind.REMOVE&&
                otherPending.get(0).groundItemId==
                    publicItem.id,
                "ordinary viewer public remove changed"
            );

            require(
                world.groundItemPresentationEvents()
                    .pendingFor(
                        picker.id(),
                        pickerGeneration,
                        now
                    ).isEmpty(),
                "picker received duplicate queued normalization"
            );
        }finally{
            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    ownerGeneration
                );
            if(picker.registered())
                world.unregisterPlayer(
                    picker,
                    pickerGeneration
                );
            if(other.registered())
                world.unregisterPlayer(
                    other,
                    otherGeneration
                );
            world.close();
        }
    }

    private static int countPublicKey(
        World world,
        int itemId,
        Tile tile
    ){
        int count=0;

        for(GroundItem item:
                world.groundItems().snapshot())
            if(item.itemId==itemId&&
               item.tile.equals(tile)&&
               item.owner==null&&
               !item.devOwned)
                count++;

        return count;
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

    private GroundItemVisibilityCollisionTest(){}
}
