package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalBankObjectInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "bank-object-handler"
            );
            world.start();

            BankState bank=player.bank();
            MovementState movement=player.movement();

            BankState failedClosedBank=
                new BankState();
            OutboundPacketQueue failedClosedQueue=
                new OutboundPacketQueue(1024);
            ServerPacketWriter failedClosedWriter=
                new ServerPacketWriter(
                    failedClosedQueue,
                    new IsaacCipher(
                        new int[]{9,10,11,12}
                    )
                );

            boolean closedOpenFailed=false;
            try{
                failedClosedBank.open(
                    failedClosedWriter
                );
            }catch(java.io.IOException expected){
                closedOpenFailed=true;
            }

            if(!closedOpenFailed||
               failedClosedBank.isOpen())
                throw new AssertionError(
                    "failed closed->open committed BankState open"
                );

            boolean placeholdersBeforeFailedOpen=
                failedClosedBank.placeholdersEnabled();
            String hiddenAfterFailedOpen=
                failedClosedBank.togglePlaceholders(
                    new ServerPacketWriter(
                        new ByteArrayOutputStream(),
                        new IsaacCipher(
                            new int[]{13,14,15,16}
                        )
                    )
                );

            if(!"IGNORED_BANK_CLOSED".equals(
                    hiddenAfterFailedOpen
                )||
               failedClosedBank.placeholdersEnabled()!=
                    placeholdersBeforeFailedOpen)
                throw new AssertionError(
                    "failed bank open left hidden bank mutation authority"
                );

            LocalBankObjectInteractionHandler h=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement,
                    world.content()
                );

            final int[] rootPublications={0};
            h.installRootOwner(
                action->{
                    rootPublications[0]++;
                    return action.open();
                }
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter w=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            ObjectInteraction immediate=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+1,
                    movement.y()
                );

            int before=wire.size();
            String opened=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        immediate,
                        w
                    )
                );

            if(opened==null||
               !opened.contains("V5_BANK_OPEN")||
               !opened.contains(
                   "action=OPENED_ADJACENT_IMMEDIATE"))
                throw new AssertionError(
                    "immediate bank route="+
                    opened
                );

            if(!bank.isOpen())
                throw new AssertionError(
                    "bank not opened"
                );

            if(wire.size()<=before)
                throw new AssertionError(
                    "bank open emitted no packets"
                );

            if(rootPublications[0]!=1)
                throw new AssertionError(
                    "immediate bank open bypassed root owner count="+
                    rootPublications[0]
                );

            int coinsBeforePending=
                bank.inventoryCount(995);
            String pendingX=
                bank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_CONTAINER,
                        0,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    w
                );

            if(pendingX==null||
               !pendingX.contains(
                    "WITHDRAW_X_PROMPT_SENT"
               ))
                throw new AssertionError(
                    "bank pending-X fixture failed result="+
                    pendingX
                );

            OutboundPacketQueue failedRetryQueue=
                new OutboundPacketQueue(1024);
            ServerPacketWriter failedRetryWriter=
                new ServerPacketWriter(
                    failedRetryQueue,
                    new IsaacCipher(
                        new int[]{17,18,19,20}
                    )
                );

            boolean retryFailed=false;
            try{
                bank.open(
                    failedRetryWriter
                );
            }catch(java.io.IOException expected){
                retryFailed=true;
            }

            if(!retryFailed||
               !bank.isOpen())
                throw new AssertionError(
                    "failed bank reopen altered prior open state"
                );

            String pendingAfterFailedRetry=
                bank.applyAmount(
                    1,
                    w
                );

            if(pendingAfterFailedRetry==null||
               !pendingAfterFailedRetry.contains(
                    "WITHDRAW_X_OK"
               )||
               bank.inventoryCount(995)!=
                    coinsBeforePending+1)
                throw new AssertionError(
                    "failed bank reopen cleared pending-X result="+
                    pendingAfterFailedRetry+
                    " coinsBefore="+
                    coinsBeforePending+
                    " coinsAfter="+
                    bank.inventoryCount(995)
                );
            BankState closeBank=
                new BankState();
            ByteArrayOutputStream closeWire=
                new ByteArrayOutputStream();
            ServerPacketWriter closeWriter=
                new ServerPacketWriter(
                    closeWire,
                    new IsaacCipher(
                        new int[]{25,26,27,28}
                    )
                );
            closeBank.open(
                closeWriter
            );

            String closePending=
                closeBank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_CONTAINER,
                        0,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    closeWriter
                );

            if(closePending==null||
               !closePending.contains(
                    "WITHDRAW_X_PROMPT_SENT"
               ))
                throw new AssertionError(
                    "bank close pending-X fixture failed result="+
                    closePending
                );

            OutboundPacketQueue failedCloseQueue=
                new OutboundPacketQueue(1024);
            failedCloseQueue.offer(
                new byte[1023]
            );
            ServerPacketWriter failedCloseWriter=
                new ServerPacketWriter(
                    failedCloseQueue,
                    new IsaacCipher(
                        new int[]{29,30,31,32}
                    )
                );

            boolean closeFailed=false;
            try{
                closeBank.close(
                    failedCloseWriter
                );
            }catch(java.io.IOException expected){
                closeFailed=true;
            }

            if(!closeFailed||
               !closeBank.isOpen())
                throw new AssertionError(
                    "failed bank close retired open state"
                );

            int closeCoinsBefore=
                closeBank.inventoryCount(995);
            String closePendingAfterFailure=
                closeBank.applyAmount(
                    1,
                    closeWriter
                );

            if(closePendingAfterFailure==null||
               !closePendingAfterFailure.contains(
                    "WITHDRAW_X_OK"
               )||
               closeBank.inventoryCount(995)!=
                    closeCoinsBefore+1)
                throw new AssertionError(
                    "failed bank close cleared pending-X result="+
                    closePendingAfterFailure
                );

            closeBank.close(
                closeWriter
            );

            if(closeBank.isOpen())
                throw new AssertionError(
                    "successful bank close did not retire state"
                );

            BankState promptBank=
                new BankState();
            ByteArrayOutputStream promptWire=
                new ByteArrayOutputStream();
            ServerPacketWriter promptWriter=
                new ServerPacketWriter(
                    promptWire,
                    new IsaacCipher(
                        new int[]{33,34,35,36}
                    )
                );
            promptBank.open(
                promptWriter
            );

            OutboundPacketQueue failedWithdrawPromptQueue=
                new OutboundPacketQueue(1024);
            failedWithdrawPromptQueue.offer(
                new byte[1024]
            );
            ServerPacketWriter failedWithdrawPromptWriter=
                new ServerPacketWriter(
                    failedWithdrawPromptQueue,
                    new IsaacCipher(
                        new int[]{37,38,39,40}
                    )
                );

            boolean withdrawPromptFailed=false;
            try{
                promptBank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_CONTAINER,
                        0,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    failedWithdrawPromptWriter
                );
            }catch(java.io.IOException expected){
                withdrawPromptFailed=true;
            }

            if(!withdrawPromptFailed)
                throw new AssertionError(
                    "failed Bank Withdraw-X prompt was not propagated"
                );

            String hiddenWithdrawAmount=
                promptBank.applyAmount(
                    1,
                    promptWriter
                );

            if(hiddenWithdrawAmount==null||
               !hiddenWithdrawAmount.contains(
                    "IGNORED_NO_PENDING_X"
               ))
                throw new AssertionError(
                    "failed Bank Withdraw-X left hidden pending authority result="+
                    hiddenWithdrawAmount
                );

            promptBank.spawnItem(
                995,
                1,
                promptWriter
            );
            int promptCoinSlot=-1;
            for(int i=0;
                i<promptBank.inventoryCapacity();
                i++){
                BankState.Stack stack=
                    promptBank.inventoryAt(i);
                if(stack!=null&&
                   stack.itemId==995){
                    promptCoinSlot=i;
                    break;
                }
            }
            if(promptCoinSlot<0)
                throw new AssertionError(
                    "Bank Store-X fixture coin missing"
                );

            OutboundPacketQueue failedStorePromptQueue=
                new OutboundPacketQueue(1024);
            failedStorePromptQueue.offer(
                new byte[1024]
            );
            ServerPacketWriter failedStorePromptWriter=
                new ServerPacketWriter(
                    failedStorePromptQueue,
                    new IsaacCipher(
                        new int[]{41,42,43,44}
                    )
                );

            boolean storePromptFailed=false;
            try{
                promptBank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_INVENTORY_CONTAINER,
                        promptCoinSlot,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    failedStorePromptWriter
                );
            }catch(java.io.IOException expected){
                storePromptFailed=true;
            }

            if(!storePromptFailed)
                throw new AssertionError(
                    "failed Bank Store-X prompt was not propagated"
                );

            String hiddenStoreAmount=
                promptBank.applyAmount(
                    1,
                    promptWriter
                );

            if(hiddenStoreAmount==null||
               !hiddenStoreAmount.contains(
                    "IGNORED_NO_PENDING_X"
               ))
                throw new AssertionError(
                    "failed Bank Store-X left hidden pending authority result="+
                    hiddenStoreAmount
                );

            String oldWithdrawPrompt=
                promptBank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_CONTAINER,
                        0,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    promptWriter
                );

            if(oldWithdrawPrompt==null||
               !oldWithdrawPrompt.contains(
                    "WITHDRAW_X_PROMPT_SENT"
               ))
                throw new AssertionError(
                    "Bank replacement fixture did not establish old Withdraw-X"
                );

            OutboundPacketQueue failedReplacementPromptQueue=
                new OutboundPacketQueue(1024);
            failedReplacementPromptQueue.offer(
                new byte[1024]
            );
            ServerPacketWriter failedReplacementPromptWriter=
                new ServerPacketWriter(
                    failedReplacementPromptQueue,
                    new IsaacCipher(
                        new int[]{45,46,47,48}
                    )
                );

            boolean replacementPromptFailed=false;
            try{
                promptBank.apply(
                    new ItemContainerAction(
                        135,
                        BankState.BANK_INVENTORY_CONTAINER,
                        promptCoinSlot,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    failedReplacementPromptWriter
                );
            }catch(java.io.IOException expected){
                replacementPromptFailed=true;
            }

            int coinsBeforeReplacementAmount=
                promptBank.inventoryCount(995);
            String replacementAmount=
                promptBank.applyAmount(
                    1,
                    promptWriter
                );

            if(!replacementPromptFailed||
               replacementAmount==null||
               !replacementAmount.contains(
                    "WITHDRAW_X_OK"
               )||
               promptBank.inventoryCount(995)!=
                    coinsBeforeReplacementAmount+1)
                throw new AssertionError(
                    "failed Bank Store-X replacement did not preserve old Withdraw-X result="+
                    replacementAmount
                );

            ObjectInteraction nonBank=
                new ObjectInteraction(
                    132,
                    12345,
                    movement.x(),
                    movement.y()
                );

            String unknown=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        nonBank,
                        w
                    )
                );

            if(unknown==null||
               !unknown.contains(
                   "action=DECODED_NOT_IMPLEMENTED"))
                throw new AssertionError(
                    "non-bank fail-closed route="+
                    unknown
                );

            if(h.hasPending())
                throw new AssertionError(
                    "non-bank object should clear stale pending state"
                );

            ObjectInteraction deferred=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+2,
                    movement.y()
                );

            String queued=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        deferred,
                        w
                    )
                );

            if(queued==null||
               !queued.contains(
                   "action=DEFERRED_UNTIL_ADJACENT"))
                throw new AssertionError(
                    "deferred bank route="+
                    queued
                );

            if(!h.hasPending())
                throw new AssertionError(
                    "deferred request not retained"
                );

            if(movement.queued()<=0)
                throw new AssertionError(
                    "server-owned approach route was not queued"
                );

            String whileQueued=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(whileQueued!=null)
                throw new AssertionError(
                    "pending interaction resolved before queued approach completed="+
                    whileQueued
                );

            movement.clearQueuedPath();

            String cancelled=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(cancelled==null||
               !cancelled.contains(
                   "action=CANCELLED_PATH_ENDED_NOT_ADJACENT"))
                throw new AssertionError(
                    "path-ended cancellation="+
                    cancelled
                );

            if(h.hasPending())
                throw new AssertionError(
                    "cancelled request still pending"
                );

            if(rootPublications[0]!=1)
                throw new AssertionError(
                    "non-opening bank paths entered root ownership count="+
                    rootPublications[0]
                );

            ObjectInteraction deferredOpen=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+2,
                    movement.y()
                );

            String queuedOpen=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        deferredOpen,
                        w
                    )
                );

            if(queuedOpen==null||
               !queuedOpen.contains(
                   "action=DEFERRED_UNTIL_ADJACENT"))
                throw new AssertionError(
                    "deferred-open route="+
                    queuedOpen
                );

            MovementState.Tick approachStep=
                movement.advance();

            if(approachStep==null)
                throw new AssertionError(
                    "deferred-open approach produced no movement"
                );

            String openedAfterArrival=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(openedAfterArrival==null||
               !openedAfterArrival.contains(
                   "action=OPENED_AFTER_AUTHORITATIVE_ARRIVAL")||
               rootPublications[0]!=2)
                throw new AssertionError(
                    "deferred bank open bypassed root owner result="+
                    openedAfterArrival+
                    " count="+rootPublications[0]
                );

            testBankTransferPublicationAtomicity();

            System.out.println(
                "LOCAL_BANK_OBJECT_HANDLER_PASS "+
                "contentOwned=true "+
                "immediateOpen=true "+
                "immediateRootOwnership=true "+
                "nonBankFailClosed=true "+
                "deferredOwnership=true "+
                "deferredRootOwnership=true "+
                "serverApproachQueued=true "+
                "pathEndCancel=true "+
                "bankClosedOpenFailureAtomic=true "+
                "bankRetryFailurePreservesPendingX=true "+
                "bankHiddenMutationRejectedAfterFailedOpen=true "+
                "bankCloseFailureAtomic=true "+
                "bankCloseFailurePreservesPendingX=true "+
                "bankWithdrawXPromptFailureAtomic=true "+
                "bankStoreXPromptFailureAtomic=true "+
                "bankXPromptReplacementPreservesPrior=true "+
                "bankWithdrawPublicationAtomic=true "+
                "bankStorePublicationAtomic=true "+
                "bankTransferXRetryPreserved=true "+
                "depositInventoryPublicationAtomic=true "+
                "partialBankFullPresentationCoherent=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void testBankTransferPublicationAtomicity()
        throws Exception
    {
        BankState bank=new BankState();
        ByteArrayOutputStream goodWire=
            new ByteArrayOutputStream();
        ServerPacketWriter good=
            new ServerPacketWriter(
                goodWire,
                new IsaacCipher(
                    new int[]{49,50,51,52}
                )
            );

        bank.open(good);

        int bankCoinsBefore=
            bank.bankAt(0).qty;
        int inventoryCoinsBefore=
            bank.inventoryCount(995);

        OutboundPacketQueue failedWithdrawQueue=
            fullQueue();
        ServerPacketWriter failedWithdraw=
            queueWriter(
                failedWithdrawQueue,
                new int[]{53,54,55,56}
            );

        boolean withdrawFailed=false;
        try{
            bank.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                failedWithdraw
            );
        }catch(java.io.IOException expected){
            withdrawFailed=true;
        }

        if(!withdrawFailed||
           bank.bankAt(0).qty!=bankCoinsBefore||
           bank.inventoryCount(995)!=inventoryCoinsBefore)
            throw new AssertionError(
                "failed bank withdraw mutated canonical item state"
            );

        String withdrawOk=
            bank.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                good
            );
        if(withdrawOk==null||
           !withdrawOk.contains("WITHDRAW_OK amount=1"))
            throw new AssertionError(
                "bank withdraw fixture failed result="+withdrawOk
            );

        int coinSlot=-1;
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==995){
                coinSlot=i;
                break;
            }
        }
        if(coinSlot<0)
            throw new AssertionError(
                "bank store fixture coin missing"
            );

        int bankBeforeStore=
            bank.bankAt(0).qty;
        int inventoryBeforeStore=
            bank.inventoryCount(995);

        OutboundPacketQueue failedStoreQueue=
            fullQueue();
        ServerPacketWriter failedStore=
            queueWriter(
                failedStoreQueue,
                new int[]{57,58,59,60}
            );

        boolean storeFailed=false;
        try{
            bank.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_INVENTORY_CONTAINER,
                    coinSlot,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                failedStore
            );
        }catch(java.io.IOException expected){
            storeFailed=true;
        }

        if(!storeFailed||
           bank.bankAt(0).qty!=bankBeforeStore||
           bank.inventoryCount(995)!=inventoryBeforeStore)
            throw new AssertionError(
                "failed bank store mutated canonical item state"
            );

        // A failed X completion must retain the pending request because
        // the item transfer did not commit.
        String prompt=
            bank.apply(
                new ItemContainerAction(
                    135,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_X"
                ),
                good
            );
        if(prompt==null||
           !prompt.contains("WITHDRAW_X_PROMPT_SENT"))
            throw new AssertionError(
                "bank transfer X fixture prompt failed"
            );

        OutboundPacketQueue failedXQueue=
            fullQueue();
        ServerPacketWriter failedX=
            queueWriter(
                failedXQueue,
                new int[]{61,62,63,64}
            );

        int xBankBefore=
            bank.bankAt(0).qty;
        int xInventoryBefore=
            bank.inventoryCount(995);

        boolean xFailed=false;
        try{
            bank.applyAmount(
                1,
                failedX
            );
        }catch(java.io.IOException expected){
            xFailed=true;
        }

        if(!xFailed||
           bank.bankAt(0).qty!=xBankBefore||
           bank.inventoryCount(995)!=xInventoryBefore)
            throw new AssertionError(
                "failed bank X completion mutated canonical state"
            );

        String xRetry=
            bank.applyAmount(
                1,
                good
            );

        if(xRetry==null||
           !xRetry.contains("WITHDRAW_X_OK amount=1")||
           bank.bankAt(0).qty!=xBankBefore-1||
           bank.inventoryCount(995)!=xInventoryBefore+1)
            throw new AssertionError(
                "failed bank X completion did not preserve retry authority result="+
                xRetry
            );

        // Deposit Inventory publication failure: neither bank nor
        // inventory may change.
        BankState depositAll=new BankState();
        ByteArrayOutputStream depositGoodWire=
            new ByteArrayOutputStream();
        ServerPacketWriter depositGood=
            new ServerPacketWriter(
                depositGoodWire,
                new IsaacCipher(
                    new int[]{65,66,67,68}
                )
            );
        depositAll.open(depositGood);
        depositAll.spawnItem(
            385,
            1,
            depositGood
        );
        depositAll.spawnItem(
            3144,
            1,
            depositGood
        );

        int depositSlotsBefore=
            depositAll.inventorySlots();
        int bankSlotsBefore=
            depositAll.bankSlots();

        OutboundPacketQueue failedDepositQueue=
            fullQueue();
        ServerPacketWriter failedDeposit=
            queueWriter(
                failedDepositQueue,
                new int[]{69,70,71,72}
            );

        boolean depositFailed=false;
        try{
            depositAll.depositInventory(
                failedDeposit
            );
        }catch(java.io.IOException expected){
            depositFailed=true;
        }

        if(!depositFailed||
           depositAll.inventorySlots()!=depositSlotsBefore||
           depositAll.bankSlots()!=bankSlotsBefore||
           depositAll.inventoryCount(385)!=1||
           depositAll.inventoryCount(3144)!=1)
            throw new AssertionError(
                "failed Deposit Inventory mutated canonical state"
            );

        // Preserve the existing PARTIAL_BANK_FULL policy, but require the
        // partial canonical postimage to be published before return.
        BankState partial=new BankState();
        ByteArrayOutputStream partialWire=
            new ByteArrayOutputStream();
        ServerPacketWriter partialWriter=
            new ServerPacketWriter(
                partialWire,
                new IsaacCipher(
                    new int[]{73,74,75,76}
                )
            );
        partial.open(partialWriter);
        partial.spawnItem(
            385,
            1,
            partialWriter
        );
        partial.spawnItem(
            3144,
            1,
            partialWriter
        );

        java.lang.reflect.Field bankField=
            BankState.class.getDeclaredField(
                "bank"
            );
        bankField.setAccessible(true);
        BankState.Stack[] slots=
            (BankState.Stack[])bankField.get(
                partial
            );

        boolean leftOneEmpty=false;
        for(int i=0;i<slots.length;i++){
            if(slots[i]!=null)
                continue;

            if(!leftOneEmpty){
                leftOneEmpty=true;
                continue;
            }

            slots[i]=
                new BankState.Stack(
                    100000+i,
                    1
                );
        }

        if(!leftOneEmpty)
            throw new AssertionError(
                "partial bank-full fixture found no empty slot"
            );

        int partialWireBefore=
            partialWire.size();

        String partialResult=
            partial.depositInventory(
                partialWriter
            );

        if(partialResult==null||
           !partialResult.contains(
                "PARTIAL_BANK_FULL movedQty=1"
           )||
           partial.inventorySlots()!=1||
           partialWire.size()<=partialWireBefore)
            throw new AssertionError(
                "partial bank-full postimage not coherently published result="+
                partialResult+
                " inventorySlots="+
                partial.inventorySlots()+
                " wireDelta="+
                (partialWire.size()-partialWireBefore)
            );
    }

    private static OutboundPacketQueue fullQueue()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return queue;
    }

    private static ServerPacketWriter queueWriter(
        OutboundPacketQueue queue,
        int[] seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static String onWorld(
        World world,
        WorldPlayer player,
        ThrowingString action
    )throws Exception{
        AtomicReference<String>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        action.run()
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "world action failed",
                failure.get()
            );

        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingString {
        String run()throws Exception;
    }

    private LocalBankObjectInteractionHandlerTest(){}
}
