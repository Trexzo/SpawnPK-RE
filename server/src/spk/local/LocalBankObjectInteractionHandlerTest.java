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
            testBankStructuralPublicationAtomicity();
            testBankTransferQuantityOverflow();
            testGenericInventoryPublicationAtomicity();
            testInventoryTransformPublicationAtomicity();

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
                "partialBankFullPresentationCoherent=true "+
                "placeholderToggleFailureAtomic=true "+
                "bankDragFailureAtomic=true "+
                "openInventoryDragFailureAtomic=true "+
                "setBankTabFailureAtomic=true "+
                "swapBankTabFailureAtomic=true "+
                "bankTransferOverflowRejected=true "+
                "bankTransferMaxBoundary=true "+
                "genericInventoryPublicationAtomic=true "+
                "genericInventoryOpenBankMirrorAtomic=true "+
                "inventoryTransformPublicationAtomic=true "+
                "inventorySplitPublicationAtomic=true "+
                "inventorySplitStackMergeAtomic=true "+
                "inventoryCombinePublicationAtomic=true"
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

    private static void testBankTransferQuantityOverflow()
        throws Exception
    {
        // Withdraw into an existing MAX_VALUE stack: reject before any
        // packet publication or canonical mutation.
        BankState withdrawMax=new BankState();
        ByteArrayOutputStream withdrawWire=
            new ByteArrayOutputStream();
        ServerPacketWriter withdrawWriter=
            new ServerPacketWriter(
                withdrawWire,
                new IsaacCipher(
                    new int[]{113,114,115,116}
                )
            );
        withdrawMax.open(withdrawWriter);
        withdrawMax.spawnItem(
            995,
            1,
            withdrawWriter
        );
        int withdrawSlot=-1;
        for(int i=0;i<withdrawMax.inventoryCapacity();i++){
            BankState.Stack stack=withdrawMax.inventoryAt(i);
            if(stack!=null&&stack.itemId==995){
                withdrawSlot=i;
                break;
            }
        }
        if(withdrawSlot<0)
            throw new AssertionError(
                "withdraw overflow fixture missing coins"
            );
        withdrawMax.inventoryAt(withdrawSlot).qty=
            Integer.MAX_VALUE;

        int withdrawBankBefore=
            withdrawMax.bankAt(0).qty;
        int withdrawWireBefore=
            withdrawWire.size();

        String withdrawRejected=
            withdrawMax.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                withdrawWriter
            );

        if(withdrawRejected==null||
           !withdrawRejected.contains(
                "REJECTED_QUANTITY_OVERFLOW"
           )||
           withdrawMax.bankAt(0).qty!=withdrawBankBefore||
           withdrawMax.inventoryAt(withdrawSlot).qty!=
                Integer.MAX_VALUE||
           withdrawWire.size()!=withdrawWireBefore)
            throw new AssertionError(
                "MAX withdraw overflow was not failure-atomic result="+
                withdrawRejected
            );

        // Cross MAX by exactly one via X amount=2 against MAX-1.
        withdrawMax.inventoryAt(withdrawSlot).qty=
            Integer.MAX_VALUE-1;
        String prompt=
            withdrawMax.apply(
                new ItemContainerAction(
                    135,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_X"
                ),
                withdrawWriter
            );
        if(prompt==null||
           !prompt.contains("WITHDRAW_X_PROMPT_SENT"))
            throw new AssertionError(
                "cross-one withdraw prompt failed"
            );

        int crossWireBefore=
            withdrawWire.size();
        int crossBankBefore=
            withdrawMax.bankAt(0).qty;

        String crossRejected=
            withdrawMax.applyAmount(
                2,
                withdrawWriter
            );

        if(crossRejected==null||
           !crossRejected.contains(
                "REJECTED_QUANTITY_OVERFLOW"
           )||
           withdrawMax.bankAt(0).qty!=crossBankBefore||
           withdrawMax.inventoryAt(withdrawSlot).qty!=
                Integer.MAX_VALUE-1||
           withdrawWire.size()!=crossWireBefore)
            throw new AssertionError(
                "MAX-1 + 2 withdraw overflow was not atomic result="+
                crossRejected
            );

        // Store into a MAX_VALUE bank stack.
        BankState storeMax=new BankState();
        ByteArrayOutputStream storeWire=
            new ByteArrayOutputStream();
        ServerPacketWriter storeWriter=
            new ServerPacketWriter(
                storeWire,
                new IsaacCipher(
                    new int[]{117,118,119,120}
                )
            );
        storeMax.open(storeWriter);
        storeMax.spawnItem(
            995,
            1,
            storeWriter
        );
        int storeSlot=-1;
        for(int i=0;i<storeMax.inventoryCapacity();i++){
            BankState.Stack stack=storeMax.inventoryAt(i);
            if(stack!=null&&stack.itemId==995){
                storeSlot=i;
                break;
            }
        }
        if(storeSlot<0)
            throw new AssertionError(
                "store overflow fixture missing coins"
            );

        storeMax.bankAt(0).qty=
            Integer.MAX_VALUE;
        int storeWireBefore=
            storeWire.size();

        String storeRejected=
            storeMax.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_INVENTORY_CONTAINER,
                    storeSlot,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                storeWriter
            );

        if(storeRejected==null||
           !storeRejected.contains(
                "REJECTED_QUANTITY_OVERFLOW"
           )||
           storeMax.bankAt(0).qty!=Integer.MAX_VALUE||
           storeMax.inventoryAt(storeSlot)==null||
           storeMax.inventoryAt(storeSlot).qty!=1||
           storeWire.size()!=storeWireBefore)
            throw new AssertionError(
                "MAX store overflow was not failure-atomic result="+
                storeRejected
            );

        // Deposit Inventory must reject the complete operation when a later
        // merge would overflow, even if an earlier stack could have moved.
        BankState depositOverflow=new BankState();
        ByteArrayOutputStream depositWire=
            new ByteArrayOutputStream();
        ServerPacketWriter depositWriter=
            new ServerPacketWriter(
                depositWire,
                new IsaacCipher(
                    new int[]{121,122,123,124}
                )
            );
        depositOverflow.open(depositWriter);
        depositOverflow.spawnItem(
            385,
            1,
            depositWriter
        );
        depositOverflow.spawnItem(
            995,
            2,
            depositWriter
        );
        int depositCoinSlot=-1;
        for(int i=0;i<depositOverflow.inventoryCapacity();i++){
            BankState.Stack stack=
                depositOverflow.inventoryAt(i);
            if(stack!=null&&stack.itemId==995){
                depositCoinSlot=i;
                break;
            }
        }
        if(depositCoinSlot<0)
            throw new AssertionError(
                "deposit overflow fixture missing coins"
            );

        depositOverflow.bankAt(0).qty=
            Integer.MAX_VALUE-1;
        int depositWireBefore=
            depositWire.size();
        int depositSlotsBefore=
            depositOverflow.inventorySlots();

        String depositRejected=
            depositOverflow.depositInventory(
                depositWriter
            );

        if(depositRejected==null||
           !depositRejected.contains(
                "REJECTED_QUANTITY_OVERFLOW"
           )||
           depositOverflow.bankAt(0).qty!=
                Integer.MAX_VALUE-1||
           depositOverflow.inventorySlots()!=
                depositSlotsBefore||
           depositOverflow.inventoryCount(385)!=1||
           depositOverflow.inventoryCount(995)!=2||
           depositWire.size()!=depositWireBefore)
            throw new AssertionError(
                "Deposit Inventory overflow partially committed result="+
                depositRejected
            );

        // Exact MAX boundary remains valid and publishes/commits once.
        BankState boundary=new BankState();
        ByteArrayOutputStream boundaryWire=
            new ByteArrayOutputStream();
        ServerPacketWriter boundaryWriter=
            new ServerPacketWriter(
                boundaryWire,
                new IsaacCipher(
                    new int[]{125,126,127,128}
                )
            );
        boundary.open(boundaryWriter);
        boundary.spawnItem(
            995,
            1,
            boundaryWriter
        );
        int boundarySlot=-1;
        for(int i=0;i<boundary.inventoryCapacity();i++){
            BankState.Stack stack=boundary.inventoryAt(i);
            if(stack!=null&&stack.itemId==995){
                boundarySlot=i;
                break;
            }
        }
        if(boundarySlot<0)
            throw new AssertionError(
                "boundary fixture missing coins"
            );

        boundary.bankAt(0).qty=
            Integer.MAX_VALUE-1;
        int boundaryWireBefore=
            boundaryWire.size();

        String boundaryResult=
            boundary.apply(
                new ItemContainerAction(
                    145,
                    BankState.BANK_INVENTORY_CONTAINER,
                    boundarySlot,
                    995,
                    0,
                    "ITEM_ACTION_1"
                ),
                boundaryWriter
            );

        if(boundaryResult==null||
           !boundaryResult.contains("STORE_OK amount=1")||
           boundary.bankAt(0).qty!=Integer.MAX_VALUE||
           boundary.inventoryAt(boundarySlot)!=null||
           boundaryWire.size()<=boundaryWireBefore)
            throw new AssertionError(
                "exact MAX boundary did not commit result="+
                boundaryResult
            );
    }

    private static void testBankStructuralPublicationAtomicity()
        throws Exception
    {
        // Placeholder toggle: failed disable must preserve both the flag and
        // zero-quantity placeholder slot.
        BankState placeholders=
            new BankState();
        ByteArrayOutputStream placeholderWire=
            new ByteArrayOutputStream();
        ServerPacketWriter placeholderGood=
            new ServerPacketWriter(
                placeholderWire,
                new IsaacCipher(
                    new int[]{77,78,79,80}
                )
            );
        placeholders.open(
            placeholderGood
        );
        String enabled=
            placeholders.togglePlaceholders(
                placeholderGood
            );
        if(enabled==null||
           !enabled.contains(
                "PLACEHOLDERS_ENABLED"
           ))
            throw new AssertionError(
                "placeholder fixture did not enable"
            );

        String allCoins=
            placeholders.apply(
                new ItemContainerAction(
                    129,
                    BankState.BANK_CONTAINER,
                    0,
                    995,
                    0,
                    "ITEM_ACTION_ALL"
                ),
                placeholderGood
            );
        if(allCoins==null||
           !allCoins.contains("WITHDRAW_OK")||
           placeholders.bankAt(0)==null||
           placeholders.bankAt(0).qty!=0)
            throw new AssertionError(
                "placeholder fixture did not retain zero slot"
            );

        boolean placeholderFailed=false;
        try{
            placeholders.togglePlaceholders(
                queueWriter(
                    fullQueue(),
                    new int[]{81,82,83,84}
                )
            );
        }catch(java.io.IOException expected){
            placeholderFailed=true;
        }

        if(!placeholderFailed||
           !placeholders.placeholdersEnabled()||
           placeholders.bankAt(0)==null||
           placeholders.bankAt(0).qty!=0)
            throw new AssertionError(
                "failed placeholder disable mutated canonical structure"
            );

        String disabled=
            placeholders.togglePlaceholders(
                placeholderGood
            );
        if(disabled==null||
           !disabled.contains(
                "PLACEHOLDERS_DISABLED"
           )||
           placeholders.bankAt(0)!=null)
            throw new AssertionError(
                "placeholder retry did not commit"
            );

        // Bank-slot drag.
        BankState bankDrag=
            new BankState();
        ServerPacketWriter bankDragGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{85,86,87,88}
                )
            );
        bankDrag.open(
            bankDragGood
        );

        int bank0=
            bankDrag.bankAt(0).itemId;
        int bank1=
            bankDrag.bankAt(1).itemId;

        boolean bankDragFailed=false;
        try{
            bankDrag.applyDrag(
                new ContainerDrag(
                    BankState.BANK_CONTAINER,
                    0,
                    0,
                    1
                ),
                queueWriter(
                    fullQueue(),
                    new int[]{89,90,91,92}
                )
            );
        }catch(java.io.IOException expected){
            bankDragFailed=true;
        }

        if(!bankDragFailed||
           bankDrag.bankAt(0).itemId!=bank0||
           bankDrag.bankAt(1).itemId!=bank1)
            throw new AssertionError(
                "failed bank drag mutated canonical slots"
            );

        String bankDragRetry=
            bankDrag.applyDrag(
                new ContainerDrag(
                    BankState.BANK_CONTAINER,
                    0,
                    0,
                    1
                ),
                bankDragGood
            );

        if(bankDragRetry==null||
           !bankDragRetry.contains("DRAG_OK")||
           bankDrag.bankAt(0).itemId!=bank1||
           bankDrag.bankAt(1).itemId!=bank0)
            throw new AssertionError(
                "bank drag retry did not commit"
            );

        // Normal inventory drag while Bank is open must publish normal
        // inventory + bank mirror in one batch before the slot swap commits.
        BankState inventoryDrag=
            new BankState();
        ServerPacketWriter inventoryDragGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{93,94,95,96}
                )
            );
        inventoryDrag.open(
            inventoryDragGood
        );
        inventoryDrag.spawnItem(
            385,
            1,
            inventoryDragGood
        );
        inventoryDrag.spawnItem(
            3144,
            1,
            inventoryDragGood
        );

        int firstSlot=-1;
        int secondSlot=-1;
        for(int i=0;i<inventoryDrag.inventoryCapacity();i++){
            BankState.Stack stack=
                inventoryDrag.inventoryAt(i);
            if(stack==null)
                continue;
            if(firstSlot<0)
                firstSlot=i;
            else{
                secondSlot=i;
                break;
            }
        }

        if(firstSlot<0||secondSlot<0)
            throw new AssertionError(
                "inventory drag fixture missing two items"
            );

        int firstItem=
            inventoryDrag.inventoryAt(firstSlot).itemId;
        int secondItem=
            inventoryDrag.inventoryAt(secondSlot).itemId;

        boolean inventoryDragFailed=false;
        try{
            inventoryDrag.applyDrag(
                new ContainerDrag(
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    0,
                    firstSlot,
                    secondSlot
                ),
                queueWriter(
                    fullQueue(),
                    new int[]{97,98,99,100}
                )
            );
        }catch(java.io.IOException expected){
            inventoryDragFailed=true;
        }

        if(!inventoryDragFailed||
           inventoryDrag.inventoryAt(firstSlot).itemId!=firstItem||
           inventoryDrag.inventoryAt(secondSlot).itemId!=secondItem)
            throw new AssertionError(
                "failed open inventory drag mutated canonical slots"
            );

        String inventoryDragRetry=
            inventoryDrag.applyDrag(
                new ContainerDrag(
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    0,
                    firstSlot,
                    secondSlot
                ),
                inventoryDragGood
            );

        if(inventoryDragRetry==null||
           !inventoryDragRetry.contains(
                "INVENTORY_DRAG_OK"
           )||
           inventoryDrag.inventoryAt(firstSlot).itemId!=secondItem||
           inventoryDrag.inventoryAt(secondSlot).itemId!=firstItem)
            throw new AssertionError(
                "open inventory drag retry did not commit"
            );

        // setbanktab failure/retry.
        BankState tabs=
            new BankState();
        ServerPacketWriter tabsGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{101,102,103,104}
                )
            );
        tabs.open(
            tabsGood
        );

        int originalTab=
            tabs.bankAt(0).tab;

        boolean setTabFailed=false;
        try{
            tabs.applyCommand(
                "setbanktab 0 3",
                queueWriter(
                    fullQueue(),
                    new int[]{105,106,107,108}
                )
            );
        }catch(java.io.IOException expected){
            setTabFailed=true;
        }

        if(!setTabFailed||
           tabs.bankAt(0).tab!=originalTab)
            throw new AssertionError(
                "failed setbanktab mutated canonical tab"
            );

        String setTabRetry=
            tabs.applyCommand(
                "setbanktab 0 3",
                tabsGood
            );

        if(setTabRetry==null||
           !setTabRetry.contains(
                "SETBANKTAB_OK"
           )||
           tabs.bankAt(0).tab!=3)
            throw new AssertionError(
                "setbanktab retry did not commit"
            );

        tabs.applyCommand(
            "setbanktab 0 1",
            tabsGood
        );
        tabs.applyCommand(
            "setbanktab 1 2",
            tabsGood
        );

        boolean swapTabFailed=false;
        try{
            tabs.applyCommand(
                "swapbanktab 0 2 1",
                queueWriter(
                    fullQueue(),
                    new int[]{109,110,111,112}
                )
            );
        }catch(java.io.IOException expected){
            swapTabFailed=true;
        }

        if(!swapTabFailed||
           tabs.bankAt(0).tab!=1||
           tabs.bankAt(1).tab!=2)
            throw new AssertionError(
                "failed swapbanktab mutated canonical tabs"
            );

        String swapRetry=
            tabs.applyCommand(
                "swapbanktab 0 2 1",
                tabsGood
            );

        if(swapRetry==null||
           !swapRetry.contains(
                "SWAPBANKTAB_OK"
           )||
           tabs.bankAt(0).tab!=2||
           tabs.bankAt(1).tab!=1)
            throw new AssertionError(
                "swapbanktab retry did not commit"
            );
    }

    private static void testGenericInventoryPublicationAtomicity()
        throws Exception
    {
        BankState bank=new BankState();
        ByteArrayOutputStream goodWire=
            new ByteArrayOutputStream();
        ServerPacketWriter good=
            new ServerPacketWriter(
                goodWire,
                new IsaacCipher(
                    new int[]{121,122,123,124}
                )
            );

        // Stackable fixture: failed consume-one must preserve the exact count,
        // then a working retry must commit one decrement.
        if(bank.spawnItem(995,2,good)==null)
            throw new AssertionError(
                "generic inventory coin fixture failed"
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
                "generic inventory coin fixture missing"
            );

        int coinsBefore=bank.inventoryCount(995);
        boolean consumeOneFailed=false;
        try{
            bank.consumeInventoryOne(
                coinSlot,
                995,
                queueWriter(
                    fullQueue(),
                    new int[]{125,126,127,128}
                )
            );
        }catch(java.io.IOException expected){
            consumeOneFailed=true;
        }
        if(!consumeOneFailed||
           bank.inventoryCount(995)!=coinsBefore)
            throw new AssertionError(
                "failed consume-one mutated canonical inventory"
            );

        String consumeRetry=
            bank.consumeInventoryOne(
                coinSlot,
                995,
                good
            );
        if(consumeRetry==null||
           !consumeRetry.contains(
                "INVENTORY_CONSUME_OK"
           )||
           bank.inventoryCount(995)!=
                coinsBefore-1)
            throw new AssertionError(
                "consume-one retry did not commit"
            );

        // Add-one into a concrete non-stackable destination.
        int sharksBefore=bank.inventoryCount(385);
        boolean addOneFailed=false;
        try{
            bank.addInventoryOne(
                385,
                queueWriter(
                    fullQueue(),
                    new int[]{129,130,131,132}
                )
            );
        }catch(java.io.IOException expected){
            addOneFailed=true;
        }
        if(!addOneFailed||
           bank.inventoryCount(385)!=sharksBefore)
            throw new AssertionError(
                "failed add-one mutated canonical inventory"
            );

        int sharkSlot=
            bank.addInventoryOne(
                385,
                good
            );
        if(sharkSlot<0||
           bank.inventoryCount(385)!=sharksBefore+1)
            throw new AssertionError(
                "add-one retry did not commit"
            );

        // Preferred-slot add must preserve its exact chosen destination on retry.
        int preferred=-1;
        for(int i=0;i<bank.inventoryCapacity();i++)
            if(bank.inventoryAt(i)==null){
                preferred=i;
                break;
            }
        if(preferred<0)
            throw new AssertionError(
                "preferred-slot fixture has no empty slot"
            );

        int preferredCountBefore=
            bank.inventoryCount(385);
        boolean preferredFailed=false;
        try{
            bank.addInventoryOnePreferred(
                385,
                preferred,
                queueWriter(
                    fullQueue(),
                    new int[]{133,134,135,136}
                )
            );
        }catch(java.io.IOException expected){
            preferredFailed=true;
        }
        if(!preferredFailed||
           bank.inventoryAt(preferred)!=null||
           bank.inventoryCount(385)!=
                preferredCountBefore)
            throw new AssertionError(
                "failed preferred add mutated canonical inventory"
            );

        int preferredResult=
            bank.addInventoryOnePreferred(
                385,
                preferred,
                good
            );
        if(preferredResult!=preferred||
           bank.inventoryAt(preferred)==null||
           bank.inventoryAt(preferred).itemId!=385)
            throw new AssertionError(
                "preferred add retry changed destination"
            );

        // Consume-all must preserve the entire concrete stack when publication fails.
        int consumeAllSlot=preferred;
        int consumeAllQty=
            bank.inventoryAt(
                consumeAllSlot
            ).qty;
        boolean consumeAllFailed=false;
        try{
            bank.consumeInventoryAll(
                consumeAllSlot,
                385,
                queueWriter(
                    fullQueue(),
                    new int[]{137,138,139,140}
                )
            );
        }catch(java.io.IOException expected){
            consumeAllFailed=true;
        }
        if(!consumeAllFailed||
           bank.inventoryAt(consumeAllSlot)==null||
           bank.inventoryAt(consumeAllSlot).qty!=
                consumeAllQty)
            throw new AssertionError(
                "failed consume-all mutated canonical inventory"
            );

        int consumed=
            bank.consumeInventoryAll(
                consumeAllSlot,
                385,
                good
            );
        if(consumed!=consumeAllQty||
           bank.inventoryAt(consumeAllSlot)!=null)
            throw new AssertionError(
                "consume-all retry did not commit exact stack"
            );

        // Add-amount uses the same prospective postimage while Bank is closed.
        int coinsBeforeAmount=
            bank.inventoryCount(995);
        boolean addAmountFailed=false;
        try{
            bank.addInventoryAmount(
                995,
                3,
                queueWriter(
                    fullQueue(),
                    new int[]{141,142,143,144}
                )
            );
        }catch(java.io.IOException expected){
            addAmountFailed=true;
        }
        if(!addAmountFailed||
           bank.inventoryCount(995)!=
                coinsBeforeAmount)
            throw new AssertionError(
                "failed add-amount mutated canonical inventory"
            );

        int amountSlot=
            bank.addInventoryAmount(
                995,
                3,
                good
            );
        if(amountSlot<0||
           bank.inventoryCount(995)!=
                coinsBeforeAmount+3)
            throw new AssertionError(
                "add-amount retry did not commit"
            );

        // Open-Bank mirror must be part of the same publication boundary.
        bank.open(good);
        int openCoinsBefore=
            bank.inventoryCount(995);
        boolean openMirrorFailed=false;
        try{
            bank.addInventoryAmount(
                995,
                2,
                queueWriter(
                    fullQueue(),
                    new int[]{145,146,147,148}
                )
            );
        }catch(java.io.IOException expected){
            openMirrorFailed=true;
        }
        if(!openMirrorFailed||
           bank.inventoryCount(995)!=
                openCoinsBefore)
            throw new AssertionError(
                "failed open-bank mirrored add mutated canonical inventory"
            );

        int openRetry=
            bank.addInventoryAmount(
                995,
                2,
                good
            );
        if(openRetry<0||
           bank.inventoryCount(995)!=
                openCoinsBefore+2)
            throw new AssertionError(
                "open-bank mirrored retry did not commit"
            );
    }

    private static void testInventoryTransformPublicationAtomicity()
        throws Exception
    {
        java.lang.reflect.Field inventoryField=
            BankState.class.getDeclaredField(
                "inventory"
            );
        inventoryField.setAccessible(true);

        // In-slot transform.
        BankState transform=
            new BankState();
        BankState.Stack[] transformSlots=
            (BankState.Stack[])inventoryField.get(
                transform
            );
        transformSlots[0]=
            new BankState.Stack(
                385,
                1
            );
        ServerPacketWriter transformGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{201,202,203,204}
                )
            );

        boolean transformFailed=false;
        try{
            transform.transformInventoryOne(
                0,
                385,
                995,
                queueWriter(
                    fullQueue(),
                    new int[]{205,206,207,208}
                )
            );
        }catch(java.io.IOException expected){
            transformFailed=true;
        }

        if(!transformFailed||
           transform.inventoryAt(0)==null||
           transform.inventoryAt(0).itemId!=385)
            throw new AssertionError(
                "failed inventory transform mutated canonical slot"
            );

        String transformRetry=
            transform.transformInventoryOne(
                0,
                385,
                995,
                transformGood
            );

        if(transformRetry==null||
           !transformRetry.contains(
                "INVENTORY_TRANSFORM_OK"
           )||
           transform.inventoryAt(0)==null||
           transform.inventoryAt(0).itemId!=995)
            throw new AssertionError(
                "inventory transform retry did not commit"
            );

        // Split into an empty extra slot while Bank is open, proving the
        // mirrored Bank presentation is part of the same failure boundary.
        BankState splitEmpty=
            new BankState();
        ServerPacketWriter splitEmptyGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{209,210,211,212}
                )
            );
        splitEmpty.open(
            splitEmptyGood
        );
        BankState.Stack[] splitEmptySlots=
            (BankState.Stack[])inventoryField.get(
                splitEmpty
            );
        splitEmptySlots[0]=
            new BankState.Stack(
                385,
                1
            );

        boolean splitEmptyFailed=false;
        try{
            splitEmpty.splitInventoryOne(
                0,
                385,
                3144,
                995,
                queueWriter(
                    fullQueue(),
                    new int[]{213,214,215,216}
                )
            );
        }catch(java.io.IOException expected){
            splitEmptyFailed=true;
        }

        if(!splitEmptyFailed||
           splitEmpty.inventoryAt(0)==null||
           splitEmpty.inventoryAt(0).itemId!=385||
           splitEmpty.inventoryCount(995)!=0)
            throw new AssertionError(
                "failed split-to-empty mutated canonical inventory"
            );

        String splitEmptyRetry=
            splitEmpty.splitInventoryOne(
                0,
                385,
                3144,
                995,
                splitEmptyGood
            );

        if(splitEmptyRetry==null||
           !splitEmptyRetry.contains(
                "INVENTORY_SPLIT_OK"
           )||
           splitEmpty.inventoryAt(0)==null||
           splitEmpty.inventoryAt(0).itemId!=3144||
           splitEmpty.inventoryCount(995)!=1)
            throw new AssertionError(
                "split-to-empty retry did not commit"
            );

        // Split with stackable extra output merging into an existing stack.
        BankState splitMerge=
            new BankState();
        BankState.Stack[] splitMergeSlots=
            (BankState.Stack[])inventoryField.get(
                splitMerge
            );
        splitMergeSlots[0]=
            new BankState.Stack(
                385,
                1
            );
        splitMergeSlots[2]=
            new BankState.Stack(
                995,
                5
            );
        ServerPacketWriter splitMergeGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{217,218,219,220}
                )
            );

        boolean splitMergeFailed=false;
        try{
            splitMerge.splitInventoryOne(
                0,
                385,
                3144,
                995,
                queueWriter(
                    fullQueue(),
                    new int[]{221,222,223,224}
                )
            );
        }catch(java.io.IOException expected){
            splitMergeFailed=true;
        }

        if(!splitMergeFailed||
           splitMerge.inventoryAt(0)==null||
           splitMerge.inventoryAt(0).itemId!=385||
           splitMerge.inventoryCount(995)!=5)
            throw new AssertionError(
                "failed split stack-merge mutated canonical inventory"
            );

        String splitMergeRetry=
            splitMerge.splitInventoryOne(
                0,
                385,
                3144,
                995,
                splitMergeGood
            );

        if(splitMergeRetry==null||
           !splitMergeRetry.contains(
                "INVENTORY_SPLIT_OK"
           )||
           splitMerge.inventoryAt(0)==null||
           splitMerge.inventoryAt(0).itemId!=3144||
           splitMerge.inventoryCount(995)!=6)
            throw new AssertionError(
                "split stack-merge retry did not commit"
            );

        // Combine one regular Doppel marker + one dye into a result item.
        BankState combine=
            new BankState();
        BankState.Stack[] combineSlots=
            (BankState.Stack[])inventoryField.get(
                combine
            );
        combineSlots[0]=
            new BankState.Stack(
                3241,
                1
            );
        combineSlots[1]=
            new BankState.Stack(
                995,
                1
            );
        ServerPacketWriter combineGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{225,226,227,228}
                )
            );

        boolean combineFailed=false;
        try{
            combine.combineInventoryOne(
                0,
                3241,
                1,
                995,
                385,
                queueWriter(
                    fullQueue(),
                    new int[]{229,230,231,232}
                )
            );
        }catch(java.io.IOException expected){
            combineFailed=true;
        }

        if(!combineFailed||
           combine.inventoryAt(0)==null||
           combine.inventoryAt(0).itemId!=3241||
           combine.inventoryAt(1)==null||
           combine.inventoryAt(1).itemId!=995)
            throw new AssertionError(
                "failed inventory combine mutated canonical sources"
            );

        String combineRetry=
            combine.combineInventoryOne(
                0,
                3241,
                1,
                995,
                385,
                combineGood
            );

        if(combineRetry==null||
           !combineRetry.contains(
                "INVENTORY_COMBINE_OK"
           )||
           combine.inventoryAt(0)==null||
           combine.inventoryAt(0).itemId!=385||
           combine.inventoryAt(1)!=null)
            throw new AssertionError(
                "inventory combine retry did not commit"
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
