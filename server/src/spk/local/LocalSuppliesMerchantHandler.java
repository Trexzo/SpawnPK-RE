package spk.local;

import java.io.IOException;
import java.util.Objects;
import spk.content.api.*;
import spk.content.builtin.SuppliesMerchantDialogueContent;

/**
 * In-world LocalLab supplies coordinator for the exact-current Wandering
 * merchant Trade action.
 *
 * NPC identity/option transport is exact-current evidence. Economy and dialogue
 * branch policy are explicit LocalLab behavior. Native Shop widgets are not
 * owned here.
 */
final class LocalSuppliesMerchantHandler {
    static final int NPC_ID=410;
    static final int TRADE_OPTION=3;
    static final int TRADE_OPCODE=17;
    static final long APPROACH_TIMEOUT_MS=10_000L;
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_G2_HOME_MERCHANT_SHOP_V1";
    static final String SAVE_BUY=
        "G2_HOME_MERCHANT_BUY";
    static final String SAVE_SELL=
        "G2_HOME_MERCHANT_SELL";

    static final class Result {
        final boolean handled;
        final String saveReason;
        final String feedback;
        final String logText;

        private Result(
            boolean handled,
            String saveReason,
            String feedback,
            String logText
        ){
            this.handled=handled;
            this.saveReason=saveReason;
            this.feedback=feedback;
            this.logText=logText;
        }

        static Result notHandled(){
            return new Result(false,null,null,null);
        }

        static Result handled(
            String saveReason,
            String feedback,
            String logText
        ){
            return new Result(
                true,
                saveReason,
                feedback,
                logText
            );
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final InteractionApproachResolver approach;
    private final ContentRegistry content;
    private final SuppliesMerchantDialogueContent fallbackDialoguePolicy=
        new SuppliesMerchantDialogueContent();
    private final LocalLabShopRuntime runtime;
    private final G2ShopPurchaseService purchases;
    private final G2ShopSellbackInventoryService sellbacks;
    private final String dialoguePlayerRef;

    private DialogueSessionService dialogue;
    private String pendingDialogueOutcome;
    private Integer pendingScene;
    private NpcEntity pendingNpc;
    private long pendingDeadlineMs;
    private Integer activeScene;
    private NpcEntity activeNpc;

    LocalSuppliesMerchantHandler(
        World world,
        WorldPlayer player,
        MovementState movement,
        NpcRegistry npcs,
        ContentRegistry content,
        LocalLabShopRuntime runtime
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.npcs=npcs;
        this.content=content;
        this.runtime=Objects.requireNonNull(runtime,"runtime");
        this.approach=new InteractionApproachResolver(
            this.movement
        );
        this.purchases=new G2ShopPurchaseService(
            this.world,
            this.player,
            this.runtime.shops()
        );
        this.sellbacks=
            new G2ShopSellbackInventoryService(
                this.world,
                this.player,
                this.runtime.sellbacks()
            );
        this.dialoguePlayerRef=
            "entity:"+this.player.id();
        this.dialogue=createDialogueSession(
            effectiveDefinition()
        );
    }

    boolean beginIfSupported(
        NpcAction action,
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(action==null||clicked==null)
            return false;
        if(clicked.definitionId!=NPC_ID)
            return false;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(
                action,
                clicked
            );

        if(route.option!=TRADE_OPTION||
           route.service!=
                NpcInteractionRouter.Service.TRADE)
            return false;

        if(approach.adjacent(
                clicked.x,
                clicked.y)){
            clearPending(false);
            openActionChoices(
                clicked,
                packets,
                tag,
                "ADJACENT_IMMEDIATE"
            );
            return true;
        }

        InteractionApproachResolver.Result queued=
            approach.queueAdjacent(
                clicked.x,
                clicked.y
            );

        if(!queued.queued()){
            clearPending(false);
            System.out.println(
                tag+
                "G2_HOME_MERCHANT_SHOP_REJECTED npc="+
                clicked.definitionId+
                " scene="+clicked.sceneIndex+
                " reason=SERVER_APPROACH_"+queued.status+
                " approach="+queued
            );
            return true;
        }

        pendingScene=clicked.sceneIndex;
        pendingNpc=clicked;
        pendingDeadlineMs=
            System.currentTimeMillis()+
            APPROACH_TIMEOUT_MS;

        System.out.println(
            tag+
            "G2_HOME_MERCHANT_SHOP_DEFERRED npc="+
            clicked.definitionId+
            " scene="+clicked.sceneIndex+
            " world="+clicked.x+","+clicked.y+
            " action=SERVER_APPROACH approach="+queued
        );
        return true;
    }

    String tick(
        long now,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(pendingScene!=null){
            NpcEntity target=
                exactTarget(
                    pendingScene,
                    pendingNpc
                );

            if(target==null||
               now>pendingDeadlineMs){
                int scene=pendingScene;
                clearPending(true);
                return "G2_HOME_MERCHANT_SHOP_CANCEL scene="+
                    scene+
                    " reason="+
                    (target==null
                        ?"PENDING_TARGET_LOST"
                        :"PENDING_APPROACH_TIMEOUT");
            }

            if(approach.adjacent(
                    target.x,
                    target.y)){
                movement.clearQueuedPath();
                openActionChoices(
                    target,
                    packets,
                    tag,
                    "SERVER_ARRIVAL"
                );
                clearPending(false);
            }else if(movement.queued()==0){
                InteractionApproachResolver.Result reroute=
                    approach.queueAdjacent(
                        target.x,
                        target.y
                    );

                if(!reroute.queued()){
                    int scene=target.sceneIndex;
                    clearPending(false);
                    return "G2_HOME_MERCHANT_SHOP_CANCEL scene="+
                        scene+
                        " reason=PATH_ENDED_NOT_ADJACENT approach="+
                        reroute;
                }

                return "G2_HOME_MERCHANT_SHOP_APPROACH_REROUTED scene="+
                    target.sceneIndex+
                    " approach="+reroute;
            }
        }

        if(active()){
            NpcEntity target=
                exactTarget(
                    activeScene,
                    activeNpc
                );

            if(target==null||
               !approach.adjacent(
                    target.x,
                    target.y)){
                int scene=
                    activeScene==null
                        ?-1
                        :activeScene.intValue();
                publishClose(packets);
                clearActive();
                return "G2_HOME_MERCHANT_SHOP_CANCEL scene="+
                    scene+
                    " reason="+
                    (target==null
                        ?"ACTIVE_TARGET_LOST"
                        :"ACTIVE_TARGET_OUT_OF_RANGE");
            }
        }

        return null;
    }

    Result handleOption(
        int option,
        ServerPacketWriter packets
    )throws IOException{
        if(option<1||option>2)
            return Result.notHandled();

        DialogueSessionService.Snapshot current=
            dialogue.snapshot(
                dialoguePlayerRef
            );

        if(!current.active)
            return Result.notHandled();

        return handleOptionIndex(
            current,
            option,
            packets
        );
    }

    Result handleWidget(
        int widget,
        ServerPacketWriter packets
    )throws IOException{
        DialogueSessionService.Snapshot current=
            dialogue.snapshot(
                dialoguePlayerRef
            );

        if(!current.active)
            return Result.notHandled();

        if(StandardDialoguePresentationAdapter
                .isAugmentedOptionCloseWidget(widget)){
            DialogueSessionService.PreparedTransition prepared=
                prepareClose();

            String outcome=takeDialogueOutcome();
            if(!SuppliesMerchantDialogueContent
                    .OUTCOME_CANCEL
                    .equals(outcome))
                throw new IllegalStateException(
                    "unexpected supplies close outcome="+
                    outcome
                );

            publishClose(packets);
            DialogueSessionService.Snapshot ended=
                dialogue.commitPrepared(prepared);
            if(ended.active)
                throw new IllegalStateException(
                    "supplies dialogue close remained active"
                );
            clearActive();

            return Result.handled(
                null,
                null,
                "G2_HOME_MERCHANT_SHOP_CANCEL reason=CLIENT_CANCEL"
            );
        }

        int option=
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(widget);

        if(option==0)
            return Result.notHandled();

        return handleOptionIndex(
            current,
            option,
            packets
        );
    }

    boolean cancelForManualMovement(
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        return cancelVisible(
            packets,
            tag,
            "MANUAL_MOVEMENT",
            false
        );
    }

    boolean cancelForNewNpcAction(
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        return cancelVisible(
            packets,
            tag,
            "NEW_NPC_ACTION",
            true
        );
    }

    boolean cancelForInterfaceClose(
        String tag
    ){
        boolean had=
            active()||
            pendingScene!=null;

        if(!had)
            return false;

        clearPending(
            pendingScene!=null
        );
        clearActive();

        System.out.println(
            tag+
            "G2_HOME_MERCHANT_SHOP_CANCEL reason=CLIENT_INTERFACE_CLOSE"
        );
        return true;
    }

    boolean active(){
        return dialogue.snapshot(
            dialoguePlayerRef
        ).active;
    }

    boolean pending(){
        return pendingScene!=null;
    }

    DialogueSessionService.Snapshot
        semanticDialogueSnapshot()
    {
        return dialogue.snapshot(
            dialoguePlayerRef
        );
    }

    private Result handleOptionIndex(
        DialogueSessionService.Snapshot current,
        int option,
        ServerPacketWriter packets
    )throws IOException{
        pendingDialogueOutcome=null;

        DialogueSessionService.PreparedTransition prepared=
            dialogue.prepareOption(
                dialoguePlayerRef,
                option
            );

        String outcome=takeDialogueOutcome();

        if(SuppliesMerchantDialogueContent
                .ACTION_NODE
                .equals(current.nodeKey)||
           SuppliesMerchantDialogueContent
                .BUY_CATALOG_NODE
                .equals(current.nodeKey)){
            if(outcome!=null)
                throw new IllegalStateException(
                    "supplies nonterminal node produced terminal outcome="+
                    outcome
                );

            if(SuppliesMerchantDialogueContent
                    .ACTION_NODE
                    .equals(current.nodeKey)){
                if(option==1)
                    publishTwoOptions(
                        packets,
                        ()->
                            SuppliesMerchantDialogueContent
                                .presentBuyCatalog(
                                    ContentRuntimeAdapters
                                        .presentation(packets)
                                        .dialogue()
                                )
                    );
                else
                    publishTwoOptions(
                        packets,
                        ()->
                            SuppliesMerchantDialogueContent
                                .presentSellQuantity(
                                    ContentRuntimeAdapters
                                        .presentation(packets)
                                        .dialogue()
                                )
                    );
            }else{
                if(option==1)
                    publishTwoOptions(
                        packets,
                        ()->
                            SuppliesMerchantDialogueContent
                                .presentBuyQuantity(
                                    ContentRuntimeAdapters
                                        .presentation(packets)
                                        .dialogue()
                                )
                    );
                else
                    publishTwoOptions(
                        packets,
                        ()->
                            SuppliesMerchantDialogueContent
                                .presentWhipConfirm(
                                    ContentRuntimeAdapters
                                        .presentation(packets)
                                        .dialogue()
                                )
                    );
            }

            DialogueSessionService.Snapshot after=
                dialogue.commitPrepared(
                    prepared
                );

            return Result.handled(
                null,
                null,
                "G2_HOME_MERCHANT_SHOP_DIALOG node="+
                after.nodeKey+
                " standardDialogue=true"
            );
        }

        long quantity=0L;
        boolean buy=false;
        int itemId=-1;
        String itemName=null;

        if(SuppliesMerchantDialogueContent
                .OUTCOME_BUY_ONE
                .equals(outcome)){
            quantity=1L;
            buy=true;
            itemId=LocalLabShopRuntime.ROCKTAIL;
            itemName="Rocktail";
        }else if(SuppliesMerchantDialogueContent
                .OUTCOME_BUY_FIVE
                .equals(outcome)){
            quantity=5L;
            buy=true;
            itemId=LocalLabShopRuntime.ROCKTAIL;
            itemName="Rocktail";
        }else if(SuppliesMerchantDialogueContent
                .OUTCOME_BUY_WHIP_ONE
                .equals(outcome)){
            quantity=1L;
            buy=true;
            itemId=LocalLabShopRuntime.STARTER_WHIP;
            itemName="Abyssal whip";
        }else if(SuppliesMerchantDialogueContent
                .OUTCOME_SELL_ONE
                .equals(outcome)){
            quantity=1L;
            buy=false;
            itemId=LocalLabShopRuntime.ROCKTAIL;
            itemName="Rocktail";
        }else if(SuppliesMerchantDialogueContent
                .OUTCOME_SELL_FIVE
                .equals(outcome)){
            quantity=5L;
            buy=false;
            itemId=LocalLabShopRuntime.ROCKTAIL;
            itemName="Rocktail";
        }else if(SuppliesMerchantDialogueContent
                .OUTCOME_CANCEL
                .equals(outcome)){
            publishClose(packets);
            DialogueSessionService.Snapshot ended=
                dialogue.commitPrepared(
                    prepared
                );
            if(ended.active)
                throw new IllegalStateException(
                    "supplies cancel remained active"
                );
            clearActive();
            return Result.handled(
                null,
                null,
                "G2_HOME_MERCHANT_SHOP_CANCEL reason=SEMANTIC_CANCEL"
            );
        }else{
            throw new IllegalStateException(
                "unsupported supplies terminal outcome="+
                outcome
            );
        }

        publishClose(packets);
        DialogueSessionService.Snapshot ended=
            dialogue.commitPrepared(
                prepared
            );

        if(ended.active)
            throw new IllegalStateException(
                "supplies terminal option remained active"
            );

        clearActive();

        if(buy){
            G2ShopPurchaseService.Result purchase=
                purchases.purchase(
                    LocalLabShopRuntime.SUPPLIES,
                    "item:"+itemId,
                    quantity
                );

            if(!purchase.purchased())
                return Result.handled(
                    null,
                    "Shop buy rejected: "+
                        purchase.status,
                    "G2_HOME_MERCHANT_SHOP action=BUY item="+
                        itemId+
                        " quantity="+quantity+
                        " result="+purchase.status+
                        " stateMutation=false"
                );

            String stockSuffix=
                itemId==LocalLabShopRuntime.ROCKTAIL
                    ?" Stock="+runtime.rocktailStock()
                    :" Stock=UNLIMITED";

            return Result.handled(
                SAVE_BUY,
                "Bought "+quantity+" "+
                    itemName+" for "+
                    purchase.currencySpent+
                    " coins."+
                    stockSuffix,
                "G2_HOME_MERCHANT_SHOP action=BUY item="+
                    itemId+
                    " quantity="+quantity+
                    " result=PURCHASED stock="+
                    (itemId==LocalLabShopRuntime.ROCKTAIL
                        ?Long.toString(runtime.rocktailStock())
                        :"UNLIMITED")
            );
        }

        G2ShopSellbackInventoryService.Result sale=
            sellbacks.sell(
                LocalLabShopRuntime.SUPPLIES,
                "item:"+itemId,
                quantity
            );

        if(!sale.sold())
            return Result.handled(
                null,
                "Shop sell rejected: "+
                    sale.status,
                "G2_HOME_MERCHANT_SHOP action=SELL item="+
                    itemId+
                    " quantity="+quantity+
                    " result="+sale.status+
                    " stateMutation=false"
            );

        return Result.handled(
            SAVE_SELL,
            "Sold "+quantity+" "+
                itemName+" for "+
                sale.payout+
                " coins. Stock="+
                runtime.rocktailStock(),
            "G2_HOME_MERCHANT_SHOP action=SELL item="+
                itemId+
                " quantity="+quantity+
                " result=SOLD stock="+
                runtime.rocktailStock()
        );
    }

    private DialogueSessionService.PreparedTransition
        prepareClose()
    {
        pendingDialogueOutcome=null;
        return dialogue.prepareClose(
            dialoguePlayerRef
        );
    }

    private void openActionChoices(
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag,
        String reason
    )throws IOException{
        refreshDefinitionForNewSession();

        DialogueSessionService.PreparedBegin prepared=
            dialogue.prepareBegin(
                dialoguePlayerRef,
                SuppliesMerchantDialogueContent
                    .DIALOGUE_KEY
            );

        publishTwoOptions(
            packets,
            ()->
                SuppliesMerchantDialogueContent
                    .presentAction(
                        ContentRuntimeAdapters
                            .presentation(packets)
                            .dialogue()
                    )
        );

        DialogueSessionService.Snapshot begun=
            dialogue.commitPreparedBegin(
                prepared
            );

        if(!begun.active||
           !SuppliesMerchantDialogueContent
                .ACTION_NODE
                .equals(begun.nodeKey))
            throw new IllegalStateException(
                "supplies dialogue did not begin at action node"
            );

        activeScene=clicked.sceneIndex;
        activeNpc=clicked;

        System.out.println(
            tag+
            "G2_HOME_MERCHANT_SHOP_OPEN npc="+
            clicked.definitionId+
            " scene="+clicked.sceneIndex+
            " option3=true c2s17=true"+
            " action="+reason+
            " standardDialogue=true"+
            " nativeShopWidgetOwned=false"
        );
    }

    @FunctionalInterface
    private interface Presentation {
        void publish()throws IOException;
    }

    private static void publishTwoOptions(
        ServerPacketWriter packets,
        Presentation presentation
    )throws IOException{
        packets.beginBatch();
        boolean ended=false;
        try{
            presentation.publish();
            packets.endBatch();
            ended=true;
        }catch(IOException|RuntimeException|Error failure){
            if(!ended)
                abortBatch(
                    packets,
                    failure
                );
            throw failure;
        }
    }

    private static void publishClose(
        ServerPacketWriter packets
    )throws IOException{
        packets.beginBatch();
        boolean ended=false;
        try{
            StandardDialoguePresentationAdapter
                .close(packets);
            packets.endBatch();
            ended=true;
        }catch(IOException|RuntimeException|Error failure){
            if(!ended)
                abortBatch(
                    packets,
                    failure
                );
            throw failure;
        }
    }

    private static void abortBatch(
        ServerPacketWriter packets,
        Throwable primary
    ){
        try{
            packets.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
    }

    private boolean cancelVisible(
        ServerPacketWriter packets,
        String tag,
        String reason,
        boolean clearRoute
    )throws IOException{
        boolean pending=
            pendingScene!=null;
        boolean visible=active();

        if(!pending&&!visible)
            return false;

        if(visible)
            publishClose(packets);

        clearPending(
            clearRoute&&pending
        );
        clearActive();

        System.out.println(
            tag+
            "G2_HOME_MERCHANT_SHOP_CANCEL reason="+
            reason+
            " pending="+pending+
            " active="+visible
        );
        return true;
    }

    private NpcEntity exactTarget(
        Integer scene,
        NpcEntity expected
    ){
        if(scene==null||expected==null)
            return null;

        if(npcs==null)
            return expected;

        NpcEntity current=
            npcs.scene(
                scene.intValue()
            );

        return current==expected
            ?current
            :null;
    }

    private void clearPending(
        boolean clearRoute
    ){
        pendingScene=null;
        pendingNpc=null;
        pendingDeadlineMs=0L;

        if(clearRoute)
            movement.clearQueuedPath();
    }

    private void clearActive(){
        dialogue.abort(
            dialoguePlayerRef
        );
        pendingDialogueOutcome=null;
        activeScene=null;
        activeNpc=null;
    }

    private ContentDialogueDefinition
        effectiveDefinition()
    {
        ContentDialogueDefinition definition=
            content==null
                ?fallbackDialoguePolicy.definition()
                :content.dialogueDefinition(
                    SuppliesMerchantDialogueContent
                        .DIALOGUE_KEY
                );

        if(definition==null)
            throw new IllegalStateException(
                "supplies dialogue definition missing"
            );

        return definition;
    }

    private void refreshDefinitionForNewSession(){
        if(active())
            return;

        dialogue=createDialogueSession(
            effectiveDefinition()
        );
    }

    private DialogueSessionService createDialogueSession(
        ContentDialogueDefinition definition
    ){
        validateDefinition(
            definition
        );

        DialogueSessionService service=
            new DialogueSessionService(
                POLICY_AUTHORITY,
                (playerRef,dialogueDefinition,node,intent,before)->
                    resolveTransition(
                        node.nodeKey,
                        intent
                    )
            );

        java.util.ArrayList<
            DialogueSessionService.NodeDefinition
        > nodes=
            new java.util.ArrayList<>();

        for(ContentDialogueNode node:
                definition.nodes())
            nodes.add(
                new DialogueSessionService.NodeDefinition(
                    node.nodeKey(),
                    DialogueSessionService
                        .InputMode
                        .OPTIONS,
                    node.optionCount(),
                    node.closeSupported(),
                    POLICY_AUTHORITY
                )
            );

        service.register(
            new DialogueSessionService.DialogueDefinition(
                definition.dialogueKey(),
                definition.startNodeKey(),
                nodes,
                POLICY_AUTHORITY
            )
        );

        return service;
    }

    private static void validateDefinition(
        ContentDialogueDefinition definition
    ){
        if(!SuppliesMerchantDialogueContent
                .DIALOGUE_KEY
                .equals(definition.dialogueKey())||
           !SuppliesMerchantDialogueContent
                .ACTION_NODE
                .equals(definition.startNodeKey())||
           definition.nodes().size()!=5)
            throw new IllegalStateException(
                "incompatible supplies dialogue topology"
            );

        for(String key:new String[]{
                SuppliesMerchantDialogueContent.ACTION_NODE,
                SuppliesMerchantDialogueContent.BUY_CATALOG_NODE,
                SuppliesMerchantDialogueContent.BUY_QUANTITY_NODE,
                SuppliesMerchantDialogueContent.WHIP_CONFIRM_NODE,
                SuppliesMerchantDialogueContent.SELL_QUANTITY_NODE
        }){
            ContentDialogueNode node=
                definition.node(key);
            if(node==null||
               node.inputMode()!=
                    ContentDialogueNode.InputMode.OPTIONS||
               node.optionCount()!=2||
               !node.closeSupported())
                throw new IllegalStateException(
                    "incompatible supplies node "+key
                );
        }
    }

    private DialogueSessionService.Transition
        resolveTransition(
            String nodeKey,
            DialogueSessionService.Intent intent
        )
    {
        ContentDialogueIntent contentIntent;

        switch(intent.kind){
            case OPTION:
                contentIntent=
                    ContentDialogueIntent.option(
                        intent.optionIndex
                    );
                break;
            case CLOSE:
                contentIntent=
                    ContentDialogueIntent.closeIntent();
                break;
            default:
                throw new IllegalStateException(
                    "supplies dialogue unexpected intent="+
                    intent.kind
                );
        }

        ContentDialogueTransition transition;

        if(content!=null){
            transition=
                content.dispatchDialogue(
                    player,
                    SuppliesMerchantDialogueContent
                        .DIALOGUE_KEY,
                    nodeKey,
                    contentIntent
                );

            if(transition==null)
                throw new IllegalStateException(
                    "supplies dialogue content binding missing"
                );
        }else{
            transition=
                fallbackDialoguePolicy.handle(
                    new ContentDialogueContext(){
                        @Override public String dialogueKey(){
                            return SuppliesMerchantDialogueContent
                                .DIALOGUE_KEY;
                        }

                        @Override public String nodeKey(){
                            return nodeKey;
                        }

                        @Override public ContentDialogueIntent intent(){
                            return contentIntent;
                        }

                        @Override public ContentPlayer player(){
                            return ContentRuntimeAdapters.player(
                                player
                            );
                        }
                    }
                );
        }

        pendingDialogueOutcome=
            transition.outcomeKey();

        switch(transition.kind()){
            case STAY:
                return DialogueSessionService
                    .Transition.stay();
            case MOVE:
                return DialogueSessionService
                    .Transition.move(
                        transition.nextNodeKey()
                    );
            case END:
                return DialogueSessionService
                    .Transition.end();
            default:
                throw new AssertionError(
                    transition.kind()
                );
        }
    }

    private String takeDialogueOutcome(){
        String result=pendingDialogueOutcome;
        pendingDialogueOutcome=null;
        return result;
    }
}
