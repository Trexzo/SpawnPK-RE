package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R112 OFFLINE ONLY. Conservative inventory-wide epoch, opt-in on
 * BankState, detects same-item/same-quantity ABA via real queued World
 * commands for covered mutators. Intentionally demonstrates a raw
 * Stack.qty alias can still bypass this cooperative audit.
 */
public final class R112OfflineInventoryEpochAuditTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R112 unexpected arguments");
        R111OfflineQueuedItemOnPlayerIntentTest.main(new String[0]);
        sameItemAbaAndAuditCoverage();
        System.out.println(
            "R112_OFFLINE_INVENTORY_EPOCH_AUDIT_PASS "+
            "R103ThroughR111RegressionsInherited=true "+
            "inventoryEpochOptInOnly=true "+
            "realWorldCommandInboxFifo=true "+
            "coveredConsumeAndRestockAbaDenied=true "+
            "coveredInventoryDragDoubleSwapAbaDenied=true "+
            "coveredBankDepositAndWithdrawInvalidated=true "+
            "coveredTradeRestoreConservativelyInvalidated=true "+
            "readOnlyPresentationDoesNotInvalidate=true "+
            "epochlessRequestStrictlyDenied=true "+
            "RAW_STACK_ALIAS_ABA_UNDETECTED=true "+
            "noPerSlotFineGrainedOptimizationClaim=true "+
            "noLiveItemOnPlayerPromotion=true "+
            "noProductionGameplaySemanticsChanged=true");
    }

    private static void sameItemAbaAndAuditCoverage()throws Exception{
        try(World world=World.isolatedForTest(65L)){
            WorldPlayer source=new WorldPlayer();
            WorldPlayer target=new WorldPlayer();
            long sourceGeneration=world.registerPlayer(source,"r112-source");
            long targetGeneration=world.registerPlayer(target,"r112-target");
            ServerPacketWriter sourceWriter=writer(11);
            ServerPacketWriter targetWriter=writer(17);
            Player81WorldSync.Context viewer=Player81WorldSync.register(
                sourceWriter,world,source,new DevAuthorityWorkbench());
            Player81WorldSync.Context targetView=Player81WorldSync.register(
                targetWriter,world,target,new DevAuthorityWorkbench());
            try{
                require(source.bank().spawnItem(560,1,sourceWriter)
                    .startsWith("ITEM_SPAWN_OK"),"R112 initial spawn failed");
                Player81WorldSync.transformForTest(
                    targetView,BootstrapPackets.player81Idle());
                Player81WorldSync.transformForTest(
                    viewer,BootstrapPackets.player81Idle());
                int index=viewer.clientIndexFor(target);
                require(index>=0&&viewer.resolveVisible(index)==target,
                    "R112 real target not currently visible");
                GenericInteractionEvent event=
                    GenericInteractionPacketDecoder.decode(
                        14,opcode14(index,560,0));
                world.start();

                // Strict R112 requires audit opt-in, while R111 remains
                // usable as an intentionally weaker legacy diagnostic.
                world.submitAndWait(source,sourceGeneration,()->{
                    expectDenied(
                        ()->R112OfflineEpochPinnedItemOnPlayerIntent
                          .captureForTest(world,source,sourceGeneration,
                              viewer,event),
                        "R112_QUEUED_ITEM_EPOCH_DENIED "+
                        "inventory mutation audit not enabled");
                    source.bank().enableOfflineInventoryEpochForTest();
                    require(source.bank()
                            .inventorySlotSnapshot(0).offlineMutationEpoch==1L,
                        "R112 initial epoch did not initialize");
                },10_000L);

                AtomicReference<R112OfflineEpochPinnedItemOnPlayerIntent>
                    pin=new AtomicReference<>();
                world.submitAndWait(source,sourceGeneration,()->{
                    pin.set(R112OfflineEpochPinnedItemOnPlayerIntent
                        .captureForTest(
                            world,source,sourceGeneration,viewer,event));
                    require(pin.get().initialEpochForTest()>0,
                        "R112 initial strict pin missing epoch");
                    source.bank().sendNormalInventory(sourceWriter);
                    pin.get().recheckForTest(
                        world,source,sourceGeneration,viewer);
                },10_000L);

                AtomicInteger abaDenied=new AtomicInteger();
                CompletableFuture<Void> replace=world.submit(
                    source,sourceGeneration,()->{
                        source.bank().consumeInventoryAmountSemantic(
                            0,560,1);
                        require(source.bank().spawnItem(560,1,sourceWriter)
                                .startsWith("ITEM_SPAWN_OK"),
                            "R112 identical-stack replacement failed");
                        require(source.bank().inventorySlotSnapshot(0)
                            .itemId==560&&
                            source.bank().inventorySlotSnapshot(0)
                            .quantity==1,
                            "R112 identical stack wasn't restored");
                    });
                CompletableFuture<Void> recheck=world.submit(
                    source,sourceGeneration,()->{
                        expectDenied(()->pin.get().recheckForTest(
                            world,source,sourceGeneration,viewer),
                            "R112_QUEUED_ITEM_EPOCH_DENIED "+
                            "inventory mutation epoch changed while queued");
                        abaDenied.incrementAndGet();
                    });
                done(replace);
                done(recheck);
                require(abaDenied.get()==1,
                    "R112 covered consume-and-restock ABA not denied");

                // Two normal inventory drag swaps return to the SAME
                // slot, item and quantity; conservative revision still
                // flags the intervening authoritative operations.
                world.submitAndWait(source,sourceGeneration,()->{
                    require(source.bank().spawnItem(565,1,sourceWriter)
                            .startsWith("ITEM_SPAWN_OK"),
                        "R112 secondary item failed");
                },10_000L);
                AtomicReference<R112OfflineEpochPinnedItemOnPlayerIntent>
                    dragPin=new AtomicReference<>();
                world.submitAndWait(source,sourceGeneration,()->{
                    dragPin.set(R112OfflineEpochPinnedItemOnPlayerIntent
                        .captureForTest(
                            world,source,sourceGeneration,viewer,event));
                },10_000L);
                AtomicInteger dragDenied=new AtomicInteger();
                CompletableFuture<Void> drag=world.submit(
                    source,sourceGeneration,()->{
                        ContainerDrag d=new ContainerDrag(
                            BankState.NORMAL_INVENTORY_CONTAINER,0,0,1);
                        require(source.bank().applyDrag(d,sourceWriter)
                                .startsWith("INVENTORY_DRAG_OK"),
                            "R112 first inventory drag failed");
                        require(source.bank().applyDrag(d,sourceWriter)
                                .startsWith("INVENTORY_DRAG_OK"),
                            "R112 reverse inventory drag failed");
                        require(source.bank().inventorySlotSnapshot(0)
                                .itemId==560&&
                            source.bank().inventorySlotSnapshot(0)
                                .quantity==1,
                            "R112 two drags didn't restore original slot");
                    });
                CompletableFuture<Void> dragCheck=world.submit(
                    source,sourceGeneration,()->{
                        expectDenied(()->dragPin.get().recheckForTest(
                            world,source,sourceGeneration,viewer),
                            "R112_QUEUED_ITEM_EPOCH_DENIED");
                        dragDenied.incrementAndGet();
                    });
                done(drag);
                done(dragCheck);
                require(dragDenied.get()==1,
                    "R112 covered double-drag ABA not denied");

                // Replacing a trade checkpoint may restore identical
                // values: conservative invalidation intentionally
                // rejects even a semantic no-change.
                AtomicInteger restoreDenied=new AtomicInteger();
                world.submitAndWait(source,sourceGeneration,()->{
                    AtomicReference<R112OfflineEpochPinnedItemOnPlayerIntent>
                        restorePin=new AtomicReference<>();
                    restorePin.set(R112OfflineEpochPinnedItemOnPlayerIntent
                        .captureForTest(
                            world,source,sourceGeneration,viewer,event));
                    BankState.Stack[] snapshot=
                        source.bank().captureTradeInventory();
                    source.bank().restoreTradeInventory(snapshot);
                    expectDenied(()->restorePin.get().recheckForTest(
                        world,source,sourceGeneration,viewer),
                        "R112_QUEUED_ITEM_EPOCH_DENIED");
                    restoreDenied.incrementAndGet();
                },10_000L);
                require(restoreDenied.get()==1,
                    "R112 trade restore should conservatively invalidate");

                // Deposit and then withdraw through actual BankState
                // inventory/bank APIs. The source item returns but
                // an earlier pin cannot survive the banking operation.
                AtomicInteger bankingDenied=new AtomicInteger();
                world.submitAndWait(source,sourceGeneration,()->{
                    AtomicReference<R112OfflineEpochPinnedItemOnPlayerIntent>
                        bankPin=new AtomicReference<>();
                    bankPin.set(R112OfflineEpochPinnedItemOnPlayerIntent
                        .captureForTest(
                            world,source,sourceGeneration,viewer,event));
                    source.bank().open(sourceWriter);
                    String dep=source.bank().depositInventory(sourceWriter);
                    require(dep.startsWith("DEPOSITED_ALL"),
                        "R112 deposit-all bank mutation failed");
                    String take=source.bank().apply(
                        new ItemContainerAction(145,
                            BankState.BANK_CONTAINER,1,560,0,
                            "WITHDRAW_1"),sourceWriter);
                    require(take.startsWith("WITHDRAW_OK")&&
                        source.bank().inventoryCount(560)>=1,
                        "R112 bank withdrawal failed");
                    expectDenied(()->bankPin.get().recheckForTest(
                        world,source,sourceGeneration,viewer),
                        "R112_QUEUED_ITEM_EPOCH_DENIED");
                    bankingDenied.incrementAndGet();
                },10_000L);
                require(bankingDenied.get()==1,
                    "R112 bank deposit/withdraw did not invalidate");

                // Important NEGATIVE CONTROL: another in-package
                // Java class can obtain BankState.Stack through
                // inventoryAt and edit qty directly, evading this
                // method-bound epoch. This must remain documented.
                AtomicInteger rawBypass=new AtomicInteger();
                world.submitAndWait(source,sourceGeneration,()->{
                    R112OfflineEpochPinnedItemOnPlayerIntent strict=
                        R112OfflineEpochPinnedItemOnPlayerIntent.captureForTest(
                            world,source,sourceGeneration,viewer,event);
                    BankState.Stack raw=source.bank().inventoryAt(0);
                    require(raw!=null&&raw.itemId==560&&raw.qty>0,
                        "R112 raw alias fixture not correct");
                    int previous=raw.qty;
                    raw.qty=previous+1;
                    raw.qty=previous;
                    strict.recheckForTest(
                        world,source,sourceGeneration,viewer);
                    rawBypass.incrementAndGet();
                },10_000L);
                require(rawBypass.get()==1&&
                    source.bank().inventoryCount(560)==1&&
                    target.bank().inventoryCount(560)==0&&
                    world.players().owns(target,targetGeneration)&&
                    new LocalGenericInteractionHandler(world.content())
                        .handle(event).contains("DECODED_FAIL_CLOSED"),
                    "R112 unexpected state or live opcode14 routing");
                System.out.println(
                    "R112_INVENTORY_EPOCH_REGRESSION_PASS "+
                    "coveredSameItemSameQuantityAbaDenied=1 "+
                    "coveredInventoryDoubleDragAbaDenied=1 "+
                    "coveredBankDepositWithdrawDenied=1 "+
                    "tradeRestoreFalsePositiveConservativelyDenied=1 "+
                    "readOnlyPresentationEpochStable=true "+
                    "RAW_STACK_ALIAS_ABA_UNDETECTED=1 "+
                    "noItemOnPlayerDispatch=true");
            }finally{
                Player81WorldSync.unregister(sourceWriter);
                Player81WorldSync.unregister(targetWriter);
            }
        }
    }

    private static byte[] opcode14(int target,int item,int slot){
        int widget=BankState.NORMAL_INVENTORY_CONTAINER;
        return new byte[]{
            (byte)(widget>>>8),(byte)((widget+128)&255),
            (byte)(target>>>8),(byte)target,
            (byte)(item>>>8),(byte)item,
            (byte)slot,(byte)(slot>>>8)
        };
    }

    private static ServerPacketWriter writer(int seed){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3}));
    }

    @FunctionalInterface interface Checked{
        void run()throws Exception;
    }

    private static void expectDenied(Checked operation,String token)
            throws Exception{
        try{operation.run();}
        catch(IllegalStateException refused){
            if(refused.getMessage()!=null&&
                    refused.getMessage().contains(token))
                return;
            throw new AssertionError("R112 wrong denial",refused);
        }
        throw new AssertionError("R112 missing refusal "+token);
    }

    private static void done(CompletableFuture<Void> f)throws Exception{
        f.get(12,TimeUnit.SECONDS);
    }

    private static void require(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }

    private R112OfflineInventoryEpochAuditTest(){}
}
