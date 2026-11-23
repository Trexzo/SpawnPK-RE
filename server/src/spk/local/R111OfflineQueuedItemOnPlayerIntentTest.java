package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R111 OFFLINE TEST ONLY. Submit real WorldCommandInbox actions in FIFO
 * order and change the authoritative inventory/target owner between
 * the request's initial World preflight and its queued recheck.
 *
 * No live opcode14 routing is enabled. The SAME-ID SAME-QUANTITY ABA
 * negative control deliberately succeeds to expose missing slot epochs.
 */
public final class R111OfflineQueuedItemOnPlayerIntentTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R111 unexpected arguments");
        R110OfflineItemOnPlayerPreflightTest.main(new String[0]);
        queuedInventoryAndTargetChanges();
        System.out.println(
            "R111_OFFLINE_QUEUED_ITEM_ON_PLAYER_INTENT_PASS "+
            "R103ThroughR110InheritedBaseline=true "+
            "realWorldCommandInboxFifo=true "+
            "sameExactDecodedOpcode14EventRetainedAcrossQueue=true "+
            "pinnedSourceTargetIdAndGenerations=true "+
            "queuedQuantityChangeDenied=true "+
            "queuedEmptySelectedSlotDenied=true "+
            "queuedTargetGenerationChangeDenied=true "+
            "directInboundItemOnPlayerStillFailClosed=true "+
            "noPreflightOrRecheckMutations=true "+
            "SAME_ITEM_SAME_QUANTITY_ABA_BLINDSPOT_REPRODUCED=true "+
            "noInventorySlotEpochAvailable=true "+
            "noLiveSemanticPromotion=true "+
            "noPublicPluginOrContentApiChanged=true");
    }

    private static void queuedInventoryAndTargetChanges()
            throws Exception{
        try(World world=World.isolatedForTest(65L)){
            WorldPlayer source=new WorldPlayer();
            WorldPlayer target=new WorldPlayer();
            long sourceGeneration=world.registerPlayer(
                source,"r111-source");
            long targetGeneration=world.registerPlayer(
                target,"r111-target");
            ServerPacketWriter sourceWriter=writer(11);
            ServerPacketWriter targetWriter=writer(17);
            Player81WorldSync.Context viewer=
                Player81WorldSync.register(
                    sourceWriter,world,source,new DevAuthorityWorkbench());
            Player81WorldSync.Context targetViewer=
                Player81WorldSync.register(
                    targetWriter,world,target,new DevAuthorityWorkbench());
            try{
                require(source.bank().spawnItem(560,2,sourceWriter)
                        .startsWith("ITEM_SPAWN_OK"),
                    "R111 source item setup failed");
                require(source.bank().inventorySlotSnapshot(0).itemId==560&&
                        source.bank().inventorySlotSnapshot(0).quantity==2,
                    "R111 initial authoritative inventory slot mismatch");
                Player81WorldSync.transformForTest(
                    targetViewer,BootstrapPackets.player81Idle());
                Player81WorldSync.transformForTest(
                    viewer,BootstrapPackets.player81Idle());
                int index=viewer.clientIndexFor(target);
                require(index>=0&&viewer.resolveVisible(index)==target,
                    "R111 viewer does not resolve original target");
                GenericInteractionEvent decoded=
                    GenericInteractionPacketDecoder.decode(
                        14,opcode14(
                            BankState.NORMAL_INVENTORY_CONTAINER,
                            index,560,0));
                require(decoded.selectedItemId==560&&
                        decoded.selectedSlot==0&&decoded.targetId==index,
                    "R111 decoded original packet fields changed");

                LocalGenericInteractionHandler inbound=
                    new LocalGenericInteractionHandler(world.content());
                require(inbound.handle(decoded).contains(
                        "DECODED_FAIL_CLOSED"),
                    "R111 live opcode14 unexpectedly promoted");
                world.start();
                AtomicReference<R111OfflinePinnedItemOnPlayerIntent>
                    pin=new AtomicReference<>();
                world.submitAndWait(source,sourceGeneration,()->{
                    R111OfflinePinnedItemOnPlayerIntent captured=
                        R111OfflinePinnedItemOnPlayerIntent.captureForTest(
                            world,source,sourceGeneration,viewer,decoded);
                    require(captured.pinnedTargetIdForTest()
                            .equals(target.id())&&
                            captured.pinnedTargetGenerationForTest()
                                ==targetGeneration&&
                            captured.pinnedItemQuantityForTest()==2,
                        "R111 queue intake pin not source/target exact");
                    captured.recheckForTest(
                        world,source,sourceGeneration,viewer);
                    pin.set(captured);
                },10_000L);
                require(pin.get()!=null,
                    "R111 intake preflight capture missing");

                AtomicInteger staleQuantityDenied=new AtomicInteger();
                CompletableFuture<Void> itemChange=world.submit(
                    source,sourceGeneration,()->{
                        BankState.InventoryConsumeResult consumed=
                            source.bank().consumeInventoryAmountSemantic(
                                0,560,1);
                        require(consumed.beforeQuantity==2&&
                                consumed.afterQuantity==1,
                            "R111 queued inventory mutation not applied");
                    });
                CompletableFuture<Void> staleCheck=world.submit(
                    source,sourceGeneration,()->{
                        expectDenied(()->pin.get().recheckForTest(
                            world,source,sourceGeneration,viewer),
                            "R111_QUEUED_ITEM_INTENT_DENIED "+
                            "selected item quantity changed while queued");
                        staleQuantityDenied.incrementAndGet();
                    });
                done(itemChange);
                done(staleCheck);
                require(staleQuantityDenied.get()==1&&
                        source.bank().inventoryCount(560)==1,
                    "R111 stale quantity request not denied");

                AtomicInteger missingSlotDenied=new AtomicInteger();
                CompletableFuture<Void> remove=world.submit(
                    source,sourceGeneration,()->{
                        source.bank().consumeInventoryAmountSemantic(
                            0,560,1);
                    });
                CompletableFuture<Void> missingSlotCheck=world.submit(
                    source,sourceGeneration,()->{
                        expectDenied(()->pin.get().recheckForTest(
                            world,source,sourceGeneration,viewer),
                            "selected item/slot provenance mismatch");
                        missingSlotDenied.incrementAndGet();
                    });
                done(remove);
                done(missingSlotCheck);
                require(missingSlotDenied.get()==1&&
                        source.bank().inventoryCount(560)==0,
                    "R111 queued empty-slot request not denied");

                // Negative control for SAME-ID SAME-QUANTITY ABA:
                // no mutation epoch is carried by BankState's slot
                // snapshot. An identical newly inserted item can pass
                // a second snapshot check even though it was replaced.
                world.submitAndWait(source,sourceGeneration,()->{
                    require(source.bank().spawnItem(560,1,sourceWriter)
                            .startsWith("ITEM_SPAWN_OK"),
                        "R111 ABA restock fixture failed");
                },10_000L);
                AtomicReference<R111OfflinePinnedItemOnPlayerIntent>
                    abaPin=new AtomicReference<>();
                world.submitAndWait(source,sourceGeneration,()->{
                    abaPin.set(R111OfflinePinnedItemOnPlayerIntent
                        .captureForTest(
                            world,source,sourceGeneration,viewer,decoded));
                },10_000L);
                require(abaPin.get()!=null&&
                        abaPin.get().pinnedItemQuantityForTest()==1,
                    "R111 ABA pin not captured");
                AtomicInteger abaUndetected=new AtomicInteger();
                CompletableFuture<Void> replace=world.submit(
                    source,sourceGeneration,()->{
                        source.bank().consumeInventoryAmountSemantic(
                            0,560,1);
                        require(source.bank().spawnItem(
                                560,1,sourceWriter)
                                .startsWith("ITEM_SPAWN_OK"),
                            "R111 same-item replacement failed");
                        require(source.bank()
                                .inventorySlotSnapshot(0).quantity==1,
                            "R111 ABA quantity did not return to one");
                    });
                CompletableFuture<Void> abaCheck=world.submit(
                    source,sourceGeneration,()->{
                        // Deliberately EXPECT this to pass! It shows
                        // this read-only model cannot prove the same
                        // physical item stack remained selected.
                        abaPin.get().recheckForTest(
                            world,source,sourceGeneration,viewer);
                        abaUndetected.incrementAndGet();
                    });
                done(replace);
                done(abaCheck);
                require(abaUndetected.get()==1,
                    "R111 expected snapshot ABA limitation not observed");

                // The target changes registration generation while an
                // already decoded + pinned request is in the inbox.
                AtomicInteger staleTargetDenied=new AtomicInteger();
                CompletableFuture<Void> rollover=world.submit(
                    source,sourceGeneration,()->{
                        require(world.unregisterPlayer(
                                target,targetGeneration),
                            "R111 target unregister failed");
                        long fresh=world.registerPlayer(
                            target,"r111-target");
                        require(fresh>targetGeneration&&
                            world.players().owns(target,fresh),
                            "R111 target generation rollover failed");
                    });
                CompletableFuture<Void> staleTargetCheck=world.submit(
                    source,sourceGeneration,()->{
                        expectDenied(()->abaPin.get().recheckForTest(
                            world,source,sourceGeneration,viewer),
                            "target not currently visible");
                        staleTargetDenied.incrementAndGet();
                    });
                done(rollover);
                done(staleTargetCheck);
                require(staleTargetDenied.get()==1&&
                        source.bank().inventoryCount(560)==1&&
                        target.bank().inventoryCount(560)==0&&
                        inbound.handle(decoded).contains(
                            "DECODED_FAIL_CLOSED"),
                    "R111 stale target caused mutation or live promotion");

                System.out.println(
                    "R111_QUEUED_ITEM_SOURCE_AND_TARGET_PASS "+
                    "queuedQuantityChangeRejected=1 "+
                    "queuedSelectedSlotEmptyRejected=1 "+
                    "queuedTargetGenerationRolloverRejected=1 "+
                    "SAME_ITEM_SAME_QUANTITY_ABA_UNDETECTED=1 "+
                    "realWorldCommandInboxFifo=true "+
                    "noItemOnPlayerMutation=true "+
                    "liveOpcode14StillFailClosed=true");
            }finally{
                Player81WorldSync.unregister(sourceWriter);
                Player81WorldSync.unregister(targetWriter);
            }
        }
    }

    private static byte[] opcode14(
            int widget,int index,int item,int slot){
        return new byte[]{
            (byte)(widget>>>8),(byte)((widget+128)&255),
            (byte)(index>>>8),(byte)index,
            (byte)(item>>>8),(byte)item,
            (byte)slot,(byte)(slot>>>8)
        };
    }

    private static ServerPacketWriter writer(int seed){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3}));
    }

    @FunctionalInterface interface Checked {
        void run()throws Exception;
    }

    private static void expectDenied(Checked action,String reason)
            throws Exception{
        try{action.run();}
        catch(IllegalStateException denied){
            if(denied.getMessage()!=null&&
                    denied.getMessage().contains(reason))
                return;
            throw new AssertionError("R111 wrong denial",denied);
        }
        throw new AssertionError("R111 expected stale refusal "+reason);
    }

    private static void done(CompletableFuture<Void> task)
            throws Exception{
        task.get(12L,TimeUnit.SECONDS);
    }

    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }

    private R111OfflineQueuedItemOnPlayerIntentTest(){}
}
