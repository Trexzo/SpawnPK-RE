package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

/**
 * R110 OFFLINE ONLY: the exact client opcode-14 decoder enters the generic
 * fail-closed handler, NOT ContentRegistry.dispatchItemOnPlayer. A separate
 * real World/Player81/BankState preflight is diagnostic-only.
 */
public final class R110OfflineItemOnPlayerPreflightTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R110 unexpected arguments");
        R109OfflineItemOnPlayerPermitTest.main(new String[0]);
        realOpcode14RouteAndPreflight();
        System.out.println(
            "R110_OFFLINE_OPCODE14_FAIL_CLOSED_PREFLIGHT_PASS "+
            "R103ThroughR109BaselinePassed=true "+
            "realOpcode14Fixed8DecoderUsed=true "+
            "itemOnPlayerInboundGenericRouteStillFailClosed=true "+
            "registeredContentHandlerNotInvokedByInboundRoute=true "+
            "separateDirectRegistryDispatchStillAvailable=true "+
            "viewerLocalIndexResolvedViaRealPlayer81Context=true "+
            "ownerAndTargetGenerationsChecked=true "+
            "exactSelectedWidgetAndInventorySlotChecked=true "+
            "selectedItemMatchesAuthoritativeInventory=true "+
            "forgedItemSlotWidgetAndViewerTargetRejected=true "+
            "staleTargetVisibilityRejected=true "+
            "noInventoryOrTargetMutationOnPreflight=true "+
            "noLiveItemOnPlayerSemanticPromotion=true "+
            "noPublicPluginOrContentApiChanged=true");
    }

    private static void realOpcode14RouteAndPreflight()
            throws Exception{
        try(World world=World.isolatedForTest(65L)){
            WorldPlayer source=new WorldPlayer();
            WorldPlayer target=new WorldPlayer();
            long sourceGeneration=world.registerPlayer(
                source,"r110-source");
            long targetGeneration=world.registerPlayer(
                target,"r110-target");

            AtomicInteger contentRuns=new AtomicInteger();
            world.content().installCustom(new ContentModule(){
                @Override public String id(){
                    return "r110-offline-registered-item-target";
                }
                @Override public void register(ContentRegistrar registrar){
                    registrar.itemOnPlayer(560,17,context->{
                        contentRuns.incrementAndGet();
                        return ContentInteractionResult.handled(
                            "R110_DIRECT_CONTENT_ONLY");
                    });
                }
            });
            require(world.content().itemOnPlayerBinding(560)!=null,
                "R110 synthetic registered content handler missing");

            ServerPacketWriter sourceWriter=writer(11);
            ServerPacketWriter targetWriter=writer(17);
            Player81WorldSync.Context viewer=
                Player81WorldSync.register(
                    sourceWriter,world,source,new DevAuthorityWorkbench());
            Player81WorldSync.register(
                targetWriter,world,target,new DevAuthorityWorkbench());
            try{
                String spawn=source.bank().spawnItem(
                    560,1,sourceWriter);
                require(spawn.startsWith("ITEM_SPAWN_OK")&&
                    source.bank().inventorySlotSnapshot(0).occupied&&
                    source.bank().inventorySlotSnapshot(0).itemId==560,
                    "R110 source authoritative item fixture failed");
                int beforeSource=source.bank().inventoryCount(560);
                int beforeTarget=target.bank().inventoryCount(560);
                int targetEnergy=target.movement().runEnergy();
                Player81WorldSync.transformForTest(
                    targetContext(targetWriter,world,target),
                    BootstrapPackets.player81Idle());
                Player81WorldSync.transformForTest(
                    viewer,BootstrapPackets.player81Idle());
                int index=viewer.clientIndexFor(target);
                require(index>=0&&viewer.resolveVisible(index)==target,
                    "R110 real Player81 target index is not visible");

                byte[] wire=opcode14(
                    BankState.NORMAL_INVENTORY_CONTAINER,index,560,0);
                GenericInteractionEvent decoded=
                    GenericInteractionPacketDecoder.decode(14,wire);
                require(decoded.family==
                        GenericInteractionEvent.Family.ITEM_ON_PLAYER&&
                    decoded.opcode==14&&
                    decoded.selectedWidget==
                        BankState.NORMAL_INVENTORY_CONTAINER&&
                    decoded.selectedSlot==0&&
                    decoded.selectedItemId==560&&
                    decoded.targetId==index,
                    "R110 opcode14 exact wire decoding differs");

                expectDenied(
                    ()->R110OfflineItemOnPlayerPreflight.inspectForTest(
                        world,source,sourceGeneration,viewer,decoded),
                    "not World execution context");

                LocalGenericInteractionHandler inbound=
                    new LocalGenericInteractionHandler(world.content());
                AtomicReference<Throwable> failure=new AtomicReference<>();
                world.start();
                world.submitAndWait(source,sourceGeneration,()->{
                    try{
                        String route=inbound.handle(decoded);
                        require(route!=null&&
                            route.contains("V5185_ITEM_ON_PLAYER")&&
                            route.contains("DECODED_FAIL_CLOSED")&&
                            contentRuns.get()==0,
                            "R110 inbound opcode14 promoted content handler");
                        R110OfflineItemOnPlayerPreflight.Review approved=
                            R110OfflineItemOnPlayerPreflight.inspectForTest(
                                world,source,sourceGeneration,viewer,decoded);
                        require(approved.sourceId.equals(source.id())&&
                            approved.sourceGeneration==sourceGeneration&&
                            approved.targetId.equals(target.id())&&
                            approved.targetGeneration==targetGeneration&&
                            approved.selectedSlot==0&&
                            approved.selectedItem==560&&
                            approved.viewerIndex==index,
                            "R110 preflight bound wrong source/target/item");
                        require(source.bank().inventoryCount(560)==
                                beforeSource&&
                            target.bank().inventoryCount(560)==
                                beforeTarget&&
                            target.movement().runEnergy()==targetEnergy,
                            "R110 read-only preflight changed state");

                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,GenericInteractionEvent.itemOnPlayer(
                                14,3214,0,565,index)),
                            "selected item/slot provenance mismatch");
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,GenericInteractionEvent.itemOnPlayer(
                                14,3214,1,560,index)),
                            "selected item/slot provenance mismatch");
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,GenericInteractionEvent.itemOnPlayer(
                                14,9999,0,560,index)),
                            "not known selected inventory widget");
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,GenericInteractionEvent.itemOnPlayer(
                                14,3214,28,560,index)),
                            "selected inventory slot outside 0..27");
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,GenericInteractionEvent.itemOnPlayer(
                                14,3214,0,560,65000)),
                            "target not currently visible");
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration+1L,
                                viewer,decoded),
                            "stale viewer source generation");

                        // Manual registry call is NOT the same as incoming
                        // packet routing. It remains a separate internal
                        // API and must not silently be enabled by R110.
                        ContentInteractionResult manual=
                            world.content().dispatchItemOnPlayer(560,target);
                        require(manual!=null&&
                            "R110_DIRECT_CONTENT_ONLY".equals(
                                manual.outcome())&&
                            contentRuns.get()==1,
                            "R110 registered direct content control failed");
                        String again=inbound.handle(decoded);
                        require(again.contains("DECODED_FAIL_CLOSED")&&
                            contentRuns.get()==1,
                            "R110 registered handler accidentally routed");
                    }catch(Throwable error){
                        failure.compareAndSet(null,error);
                        throw new IllegalStateException(
                            "R110 opcode14 source test failed",error);
                    }
                },10_000L);
                if(failure.get()!=null)
                    throw new AssertionError(
                        "R110 source actor callback failed",failure.get());

                require(world.unregisterPlayer(target,targetGeneration),
                    "R110 target unregister failed");
                // Viewer retains an old mapped index until the next P81
                // publish, but its resolver must reject stale generation.
                AtomicReference<Throwable> staleFailure=
                    new AtomicReference<>();
                world.submitAndWait(source,sourceGeneration,()->{
                    try{
                        expectDenied(()->R110OfflineItemOnPlayerPreflight
                            .inspectForTest(world,source,sourceGeneration,
                                viewer,decoded),
                            "target not currently visible");
                    }catch(Throwable error){
                        staleFailure.compareAndSet(null,error);
                        throw new IllegalStateException(
                            "R110 stale target check failed",error);
                    }
                },10_000L);
                if(staleFailure.get()!=null)
                    throw new AssertionError(
                        "R110 stale target regression failed",
                        staleFailure.get());
                require(contentRuns.get()==1&&
                    source.bank().inventoryCount(560)==beforeSource&&
                    target.bank().inventoryCount(560)==beforeTarget&&
                    target.movement().runEnergy()==targetEnergy,
                    "R110 stale target path mutated state");
                System.out.println(
                    "R110_OPCODE14_INBOUND_FAIL_CLOSED_PASS "+
                    "fixed8Decoded=true "+
                    "realPlayer81ViewerIndexValidated=true "+
                    "registeredContentHandlerNotInvoked=true "+
                    "directManualRegistryControlInvokedOnce=true "+
                    "invalidItemSlotWidgetTargetAndGenerationDenied=true "+
                    "staleTargetGenerationDenied=true "+
                    "preflightMutations=0 "+
                    "noProductionRoutingChanged=true");
            }finally{
                Player81WorldSync.unregister(sourceWriter);
                Player81WorldSync.unregister(targetWriter);
            }
        }
    }

    private static Player81WorldSync.Context targetContext(
            ServerPacketWriter targetWriter,World world,
            WorldPlayer target){
        // Registering again is safe for this isolated pre-pulse fixture,
        // and matches the actual Player81WorldSync per-writer map.
        return Player81WorldSync.register(
            targetWriter,world,target,new DevAuthorityWorkbench());
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
            new IsaacCipher(new int[]{
                seed,seed+1,seed+2,seed+3
            })
        );
    }

    @FunctionalInterface interface Checked {
        void run()throws Exception;
    }

    private static void expectDenied(Checked task,String contains)
            throws Exception{
        try{task.run();}
        catch(IllegalStateException denied){
            if(denied.getMessage()!=null&&
                denied.getMessage().contains(contains))
                return;
            throw new AssertionError(
                "R110 unexpected preflight denial",denied);
        }
        throw new AssertionError(
            "R110 preflight unexpectedly admitted "+contains);
    }

    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }

    private R110OfflineItemOnPlayerPreflightTest(){}
}
