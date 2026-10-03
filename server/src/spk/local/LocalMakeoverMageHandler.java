package spk.local;

import java.io.IOException;
import spk.content.api.*;
import spk.content.builtin.MakeoverMageDialogueContent;

/**
 * Session-local Make-over Mage dialogue + character designer coordinator.
 *
 * Trigger/UI/protocol authority:
 * - NPC 599 / name: exact current cache
 * - dialogue/interface roots/widgets: exact current v308 interface cache
 * - C2S40 / C2S101 schemas: exact current v308 client
 * - wording/normal branch: historical SpawnPK screenshot evidence
 *
 * Interaction policy is LocalLab-owned: Talk-to is resolved against the exact
 * clicked NPC identity, server-routed to adjacent range when necessary, and an
 * active dialogue is invalidated when that source disappears or leaves range.
 */
final class LocalMakeoverMageHandler {
    @FunctionalInterface
    interface DesignerRootAction {
        void open() throws IOException;
    }

    @FunctionalInterface
    interface DesignerRootCommit {
        void commit();
    }

    @FunctionalInterface
    interface DesignerRootOwner {
        void publish(
            DesignerRootAction action
        ) throws IOException;

        default void publish(
            DesignerRootAction action,
            DesignerRootCommit commit
        ) throws IOException{
            publish(
                ()->{
                    action.open();
                    commit.commit();
                }
            );
        }
    }

    static final int NPC_ID=599;
    static final long APPROACH_TIMEOUT_MS=10_000L;

    static final int DESIGN_ROOT=3559;

    private static final String DIALOGUE_POLICY=
        "LOCAL_LAB_POLICY_MAKEOVER_MAGE_DIALOGUE";
    private static final String DIALOGUE_KEY=
        MakeoverMageDialogueContent.DIALOGUE_KEY;
    private static final String INTRO_NODE=
        MakeoverMageDialogueContent.INTRO_NODE;
    private static final String OPTIONS_NODE=
        MakeoverMageDialogueContent.OPTIONS_NODE;

    private final WorldPlayer worldPlayer;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final InteractionApproachResolver approach;
    private final String dialoguePlayerRef;
    private DialogueSessionService dialogue;
    private final ContentRegistry contentRegistry;
    private final MakeoverMageDialogueContent fallbackDialoguePolicy=
        new MakeoverMageDialogueContent();

    private boolean designActive;
    private String pendingDialogueOutcome;
    private DesignerRootOwner designerRootOwner=
        action->action.open();
    private boolean designerRootOwnerInstalled;

    private Integer pendingScene;
    private NpcEntity pendingNpc;
    private long pendingDeadlineMs;

    private Integer activeScene;
    private NpcEntity activeNpc;

    LocalMakeoverMageHandler(
        WorldPlayer worldPlayer,
        EquipmentState equipment
    ){
        this(
            worldPlayer,
            equipment,
            java.util.Objects.requireNonNull(
                worldPlayer,
                "worldPlayer"
            ).movement(),
            null
        );
    }

    LocalMakeoverMageHandler(
        WorldPlayer worldPlayer,
        EquipmentState equipment,
        MovementState movement,
        NpcRegistry npcs
    ){
        this(
            worldPlayer,
            equipment,
            movement,
            npcs,
            null
        );
    }

    LocalMakeoverMageHandler(
        WorldPlayer worldPlayer,
        EquipmentState equipment,
        MovementState movement,
        NpcRegistry npcs,
        ContentRegistry contentRegistry
    ){
        this.worldPlayer=java.util.Objects.requireNonNull(
            worldPlayer,
            "worldPlayer"
        );
        this.equipment=java.util.Objects.requireNonNull(
            equipment,
            "equipment"
        );
        this.movement=java.util.Objects.requireNonNull(
            movement,
            "movement"
        );
        this.npcs=npcs;
        this.contentRegistry=contentRegistry;
        this.approach=
            new InteractionApproachResolver(
                this.movement
            );
        this.dialoguePlayerRef=
            "entity:"+
            this.worldPlayer.id();
        this.dialogue=
            createDialogueSession(
                effectiveDialogueDefinition()
            );
    }

    void installDesignerRootOwner(
        DesignerRootOwner owner
    ){
        DesignerRootOwner checked=
            java.util.Objects.requireNonNull(
                owner,
                "owner"
            );

        if(designerRootOwnerInstalled)
            throw new IllegalStateException(
                "Make-over designer root owner already installed"
            );

        designerRootOwner=checked;
        designerRootOwnerInstalled=true;
    }

    boolean beginIfSupported(
        NpcAction action,
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(action==null||clicked==null)return false;
        if(clicked.definitionId!=NPC_ID)return false;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(
                action,
                clicked
            );

        if(route.option!=1||
           route.service!=NpcInteractionRouter.Service.TALK)
            return false;

        if(approach.adjacent(
                clicked.x,
                clicked.y)){
            openIntro(
                clicked,
                packets,
                tag,
                "ADJACENT_IMMEDIATE"
            );
            clearPending(false);
            return true;
        }

        InteractionApproachResolver.Result result=
            approach.queueAdjacent(
                clicked.x,
                clicked.y
            );

        if(result.queued()){
            pendingScene=clicked.sceneIndex;
            pendingNpc=clicked;
            pendingDeadlineMs=
                System.currentTimeMillis()+
                APPROACH_TIMEOUT_MS;

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_DEFERRED npc="+
                clicked.definitionId+
                " scene="+clicked.sceneIndex+
                " world="+clicked.x+","+clicked.y+
                " distance="+distanceTo(clicked)+
                " action=SERVER_APPROACH"+
                " approach="+result
            );
            return true;
        }

        clearPending(false);

        System.out.println(
            tag+
            "MAKEOVER_MAGE_DIALOG_REJECTED npc="+
            clicked.definitionId+
            " scene="+clicked.sceneIndex+
            " distance="+distanceTo(clicked)+
            " reason=SERVER_APPROACH_"+result.status+
            " approach="+result
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
                return "MAKEOVER_MAGE_DIALOG_CANCEL scene="+
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
                openIntro(
                    target,
                    packets,
                    tag,
                    "SERVER_ARRIVAL"
                );
                clearPending(false);
                return null;
            }

            if(movement.queued()==0){
                InteractionApproachResolver.Result reroute=
                    approach.queueAdjacent(
                        target.x,
                        target.y
                    );

                if(reroute.queued()){
                    return "MAKEOVER_MAGE_DIALOG_APPROACH_REROUTED scene="+
                        target.sceneIndex+
                        " world="+target.x+","+target.y+
                        " approach="+reroute;
                }

                int scene=target.sceneIndex;
                clearPending(false);
                return "MAKEOVER_MAGE_DIALOG_CANCEL scene="+
                    scene+
                    " reason=PATH_ENDED_NOT_ADJACENT"+
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
                   target.y
               )){
                int scene=
                    activeScene==null
                        ?-1
                        :activeScene.intValue();
                int distance=
                    target==null
                        ?-1
                        :distanceTo(target);

                StandardDialoguePresentationAdapter
                    .close(packets);
                clearActive();

                return "MAKEOVER_MAGE_DIALOG_CANCEL scene="+
                    scene+
                    " reason="+
                    (target==null
                        ?"ACTIVE_TARGET_LOST"
                        :"ACTIVE_TARGET_OUT_OF_RANGE")+
                    " distance="+distance;
            }
        }

        return null;
    }

    boolean cancelForManualMovement(
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        boolean pending=pendingScene!=null;
        boolean visible=active();

        if(!pending&&!visible)
            return false;

        if(visible){
            StandardDialoguePresentationAdapter
                .close(packets);
        }

        clearPending(false);
        clearActive();

        System.out.println(
            tag+
            "MAKEOVER_MAGE_DIALOG_CANCEL reason=MANUAL_MOVEMENT"+
            " pending="+pending+
            " active="+visible
        );
        return true;
    }

    boolean cancelForNewNpcAction(
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        boolean pending=pendingScene!=null;
        boolean visible=active();

        if(!pending&&!visible)
            return false;

        if(visible){
            StandardDialoguePresentationAdapter
                .close(packets);
        }

        clearPending(pending);
        clearActive();

        System.out.println(
            tag+
            "MAKEOVER_MAGE_DIALOG_CANCEL reason=NEW_NPC_ACTION"+
            " pending="+pending+
            " active="+visible
        );
        return true;
    }

    boolean handleContinue(
        int widget,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        DialogueSessionService.Snapshot before=
            dialogue.snapshot(
                dialoguePlayerRef
            );

        if(!before.active||
           !INTRO_NODE.equals(
                before.nodeKey)||
           !StandardDialoguePresentationAdapter
                .acceptsNamedNpcContinue(
                    1,
                    widget
                ))
            return false;

        pendingDialogueOutcome=null;

        DialogueSessionService.PreparedTransition prepared=
            dialogue.prepareContinue(
                dialoguePlayerRef
            );

        if(takeDialogueOutcome()!=null)
            throw new IllegalStateException(
                "Make-over Continue produced unexpected outcome"
            );

        MakeoverMageDialogueContent
            .presentOptions(
                ContentRuntimeAdapters
                    .presentation(packets)
                    .dialogue()
            );

        DialogueSessionService.Snapshot after=
            dialogue.commitPrepared(
                prepared
            );

        if(!after.active||
           !OPTIONS_NODE.equals(
                after.nodeKey))
            throw new IllegalStateException(
                "Make-over Continue did not enter options"
            );

        System.out.println(
            tag+
            "MAKEOVER_MAGE_OPTIONS_OPEN root="+
            StandardDialoguePresentationAdapter
                .twoOptionRoot()+
            " changeWidget="+
            StandardDialoguePresentationAdapter
                .twoOptionWidget(1)+
            " nevermindWidget="+
            StandardDialoguePresentationAdapter
                .twoOptionWidget(2)
        );
        return true;
    }

    boolean handleOption(
        int option,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        DialogueSessionService.Snapshot current=
            dialogue.snapshot(
                dialoguePlayerRef
            );

        if(!current.active||
           !OPTIONS_NODE.equals(
                current.nodeKey))
            return false;
        if(option<1||option>2)return false;

        return handleWidget(
            StandardDialoguePresentationAdapter
                .twoOptionWidget(option),
            packets,
            tag
        );
    }

    boolean handleWidget(
        int widget,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        DialogueSessionService.Snapshot current=
            dialogue.snapshot(
                dialoguePlayerRef
            );

        if(!current.active||
           !OPTIONS_NODE.equals(
                current.nodeKey))
            return false;

        if(StandardDialoguePresentationAdapter
                .isAugmentedOptionCloseWidget(
                    widget
                )){
            pendingDialogueOutcome=null;

            DialogueSessionService.PreparedTransition prepared=
                dialogue.prepareClose(
                    dialoguePlayerRef
                );

            String outcome=takeDialogueOutcome();

            if(!MakeoverMageDialogueContent
                    .OUTCOME_CLIENT_CLOSE
                    .equals(outcome))
                throw new IllegalStateException(
                    "unsupported Make-over close outcome="+
                    outcome
                );

            StandardDialoguePresentationAdapter
                .close(packets);

            DialogueSessionService.Snapshot ended=
                dialogue.commitPrepared(
                    prepared
                );

            if(ended.active)
                throw new IllegalStateException(
                    "Make-over close did not end semantic dialogue"
                );

            clearActive();

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_CANCEL reason=CLIENT_CANCEL"
            );
            return true;
        }

        int optionIndex=
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(widget);

        if(optionIndex==0)
            return false;

        pendingDialogueOutcome=null;

        DialogueSessionService.PreparedTransition prepared=
            dialogue.prepareOption(
                dialoguePlayerRef,
                optionIndex
            );

        String outcome=takeDialogueOutcome();

        if(MakeoverMageDialogueContent
                .OUTCOME_OPEN_DESIGNER
                .equals(outcome)){
            try{
                designerRootOwner.publish(
                    ()->{
                        StandardDialoguePresentationAdapter
                            .close(packets);
                        packets.fixed(
                            97,
                            BootstrapPackets.interface97(
                                DESIGN_ROOT
                            )
                        );
                    },
                    ()->{
                        DialogueSessionService.Snapshot ended=
                            dialogue.commitPrepared(
                                prepared
                            );

                        if(ended.active)
                            throw new IllegalStateException(
                                "Make-over designer transition did not end semantic dialogue option="+
                                optionIndex
                            );

                        designActive=true;
                    }
                );
            }catch(IOException failure){
                pendingDialogueOutcome=null;
                throw failure;
            }catch(RuntimeException failure){
                pendingDialogueOutcome=null;
                throw failure;
            }catch(Error failure){
                pendingDialogueOutcome=null;
                throw failure;
            }

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DESIGN_OPEN root="+
                DESIGN_ROOT+
                " authority=EXACT_CURRENT_CLIENT_UI"
            );
            return true;
        }

        if(MakeoverMageDialogueContent
                .OUTCOME_CANCEL
                .equals(outcome)){
            StandardDialoguePresentationAdapter
                .close(packets);

            DialogueSessionService.Snapshot ended=
                dialogue.commitPrepared(
                    prepared
                );

            if(ended.active)
                throw new IllegalStateException(
                    "Make-over option did not end semantic dialogue option="+
                    optionIndex
                );

            clearActive();

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_CANCEL reason=NEVERMIND"
            );
            return true;
        }

        throw new IllegalStateException(
            "unsupported Make-over option outcome="+
            outcome+
            " option="+optionIndex
        );
    }

    Result handleDesign(
        CharacterDesignRequest request,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(request==null)return Result.notHandled();

        if(!designActive){
            return Result.handled(
                null,
                "MAKEOVER_MAGE_DESIGN_REJECTED reason=NO_ACTIVE_DESIGN request="+
                    request
            );
        }

        if(!request.valid()){
            StandardDialoguePresentationAdapter
                .close(packets);
            clearActive();
            return Result.handled(
                null,
                "MAKEOVER_MAGE_DESIGN_REJECTED reason=INVALID_EXACT_PROFILE request="+
                    request
            );
        }

        ContentActionResult authorization=
            authorizeCharacterDesign();

        if(!authorization.allowed()){
            StandardDialoguePresentationAdapter
                .close(packets);
            clearActive();

            return Result.handled(
                null,
                "MAKEOVER_MAGE_DESIGN_REJECTED reason=CONTENT_ACTION_DENIED action="+
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN+
                    " reasonKey="+
                    authorization.reasonKey()
            );
        }

        PlayerState player=
            worldPlayer.playerState();
        int gender=
            request.gender();
        int[] kits=
            request.kits();
        int[] colours=
            request.colours();

        /*
         * The exact designer remains authoritative until its close packet has
         * committed. The request above was already validated with the same
         * CharacterDesignProfile contract used by PlayerState, so the state
         * mutation after successful publication is deterministic.
         */
        StandardDialoguePresentationAdapter
            .close(packets);

        if(!player.setCharacterAppearance(
                gender,
                kits,
                colours))
            throw new IllegalStateException(
                "validated character design was rejected"
            );

        clearActive();

        return Result.handled(
            "CHARACTER_DESIGN",
            "MAKEOVER_MAGE_DESIGN_APPLIED gender="+
                request.gender()+
                " kits="+
                java.util.Arrays.toString(
                    request.kits()
                )+
                " colours="+
                java.util.Arrays.toString(
                    request.colours()
                )+
                " appearanceStateApplied=true"
        );
    }

    boolean cancel(){
        boolean hadAnything=
            active()||
            pendingScene!=null;

        clearPending(
            pendingScene!=null
        );
        clearActive();
        return hadAnything;
    }

    boolean active(){
        return designActive||
            dialogue.snapshot(
                dialoguePlayerRef
            ).active;
    }

    DialogueSessionService.Snapshot
        semanticDialogueSnapshot()
    {
        return dialogue.snapshot(
            dialoguePlayerRef
        );
    }

    boolean designActive(){
        return designActive;
    }

    boolean retireDesignerRoot(){
        if(!designActive)
            return false;

        designActive=false;
        activeScene=null;
        activeNpc=null;
        return true;
    }

    boolean pending(){
        return pendingScene!=null;
    }

    private void openIntro(
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag,
        String reason
    )throws IOException{
        refreshDialogueDefinitionForNewSession();

        DialogueSessionService.PreparedBegin prepared=
            dialogue.prepareBegin(
                dialoguePlayerRef,
                DIALOGUE_KEY
            );

        packets.beginBatch();
        boolean packetBatchCommitted=false;

        try{
            MakeoverMageDialogueContent
                .presentIntro(
                    ContentRuntimeAdapters
                        .presentation(packets)
                        .dialogue()
                );

            packets.endBatch();
            packetBatchCommitted=true;
        }catch(IOException failure){
            abortIntroPacketBatch(
                packets,
                packetBatchCommitted,
                failure
            );
            throw failure;
        }catch(RuntimeException failure){
            abortIntroPacketBatch(
                packets,
                packetBatchCommitted,
                failure
            );
            throw failure;
        }catch(Error failure){
            abortIntroPacketBatch(
                packets,
                packetBatchCommitted,
                failure
            );
            throw failure;
        }

        DialogueSessionService.Snapshot begun=
            dialogue.commitPreparedBegin(
                prepared
            );

        if(!begun.active||
           !INTRO_NODE.equals(
                begun.nodeKey))
            throw new IllegalStateException(
                "Make-over dialogue did not begin at intro"
            );

        designActive=false;
        activeScene=clicked.sceneIndex;
        activeNpc=clicked;

        System.out.println(
            tag+
            "MAKEOVER_MAGE_DIALOG_OPEN npc=599"+
            " scene="+clicked.sceneIndex+
            " root="+
            StandardDialoguePresentationAdapter
                .namedNpcRoot(1)+
            " headWidget="+
            StandardDialoguePresentationAdapter
                .namedNpcModelWidget(1)+
            " distance="+distanceTo(clicked)+
            " action="+reason+
            " authority=EXACT_CURRENT_CLIENT_UI+HISTORICAL_SCREENSHOT+LOCAL_LAB_INTERACTION_RANGE"
        );
    }

    private static void abortIntroPacketBatch(
        ServerPacketWriter packets,
        boolean committed,
        Throwable primary
    ){
        if(committed)
            return;

        try{
            packets.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
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

    private int distanceTo(
        NpcEntity npc
    ){
        return Math.max(
            Math.abs(
                movement.x()-npc.x
            ),
            Math.abs(
                movement.y()-npc.y
            )
        );
    }

    private ContentActionResult authorizeCharacterDesign(){
        ContentActionResult result;

        if(contentRegistry!=null){
            result=
                contentRegistry.dispatchAction(
                    worldPlayer,
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                );

            if(result==null)
                throw new IllegalStateException(
                    "Make-over character-design content action missing key="+
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                );
        }else{
            result=
                fallbackDialoguePolicy
                    .authorizeCharacterDesign(
                        new ContentActionContext(){
                            @Override public String actionKey(){
                                return MakeoverMageDialogueContent
                                    .ACTION_APPLY_CHARACTER_DESIGN;
                            }

                            @Override public ContentPlayer player(){
                                return ContentRuntimeAdapters.player(
                                    worldPlayer
                                );
                            }
                        }
                    );
        }

        return java.util.Objects.requireNonNull(
            result,
            "Make-over character-design authorization"
        );
    }

    private ContentDialogueDefinition
        effectiveDialogueDefinition()
    {
        ContentDialogueDefinition definition=
            contentRegistry==null
                ?fallbackDialoguePolicy.definition()
                :contentRegistry.dialogueDefinition(
                    DIALOGUE_KEY
                );

        if(definition==null)
            throw new IllegalStateException(
                "Make-over dialogue definition missing key="+
                DIALOGUE_KEY
            );

        if(!DIALOGUE_KEY.equals(
                definition.dialogueKey()))
            throw new IllegalStateException(
                "Make-over dialogue definition key mismatch "+
                definition.dialogueKey()
            );

        return definition;
    }

    private void refreshDialogueDefinitionForNewSession(){
        if(dialogue!=null&&
           dialogue.snapshot(
               dialoguePlayerRef
           ).active)
            return;

        dialogue=
            createDialogueSession(
                effectiveDialogueDefinition()
            );
    }

    private DialogueSessionService createDialogueSession(
        ContentDialogueDefinition contentDefinition
    ){
        validateMakeoverDefinition(
            contentDefinition
        );

        DialogueSessionService service=
            new DialogueSessionService(
                DIALOGUE_POLICY,
                (player,definition,node,intent,before)->
                    resolveDialogueTransition(
                        node.nodeKey,
                        intent
                    )
            );

        java.util.ArrayList<
            DialogueSessionService.NodeDefinition
        > nodes=
            new java.util.ArrayList<>();

        for(ContentDialogueNode node:
                contentDefinition.nodes())
            nodes.add(
                new DialogueSessionService
                    .NodeDefinition(
                        node.nodeKey(),
                        node.inputMode()==
                            ContentDialogueNode
                                .InputMode.CONTINUE
                            ?DialogueSessionService
                                .InputMode.CONTINUE
                            :DialogueSessionService
                                .InputMode.OPTIONS,
                        node.optionCount(),
                        node.closeSupported(),
                        DIALOGUE_POLICY
                    )
            );

        service.register(
            new DialogueSessionService
                .DialogueDefinition(
                    contentDefinition.dialogueKey(),
                    contentDefinition.startNodeKey(),
                    nodes,
                    DIALOGUE_POLICY
                )
        );

        return service;
    }

    private static void validateMakeoverDefinition(
        ContentDialogueDefinition definition
    ){
        if(!DIALOGUE_KEY.equals(
                definition.dialogueKey())||
           !INTRO_NODE.equals(
                definition.startNodeKey())||
           definition.nodes().size()!=2)
            throw new IllegalStateException(
                "incompatible Make-over dialogue topology key/start/nodeCount"
            );

        ContentDialogueNode intro=
            definition.node(
                INTRO_NODE
            );
        ContentDialogueNode options=
            definition.node(
                OPTIONS_NODE
            );

        if(intro==null||
           intro.inputMode()!=
                ContentDialogueNode.InputMode.CONTINUE||
           intro.optionCount()!=0||
           intro.closeSupported())
            throw new IllegalStateException(
                "incompatible Make-over intro topology"
            );

        if(options==null||
           options.inputMode()!=
                ContentDialogueNode.InputMode.OPTIONS||
           options.optionCount()!=2||
           !options.closeSupported())
            throw new IllegalStateException(
                "incompatible Make-over options topology"
            );
    }

    private DialogueSessionService.Transition
        resolveDialogueTransition(
            String nodeKey,
            DialogueSessionService.Intent intent
        )
    {
        ContentDialogueIntent contentIntent=
            toContentIntent(intent);

        ContentDialogueTransition contentTransition;

        if(contentRegistry!=null){
            contentTransition=
                contentRegistry.dispatchDialogue(
                    worldPlayer,
                    DIALOGUE_KEY,
                    nodeKey,
                    contentIntent
                );

            if(contentTransition==null)
                throw new IllegalStateException(
                    "Make-over dialogue content binding missing key="+
                    DIALOGUE_KEY
                );
        }else{
            contentTransition=
                fallbackDialoguePolicy.handle(
                    new ContentDialogueContext(){
                        @Override public String dialogueKey(){
                            return DIALOGUE_KEY;
                        }

                        @Override public String nodeKey(){
                            return nodeKey;
                        }

                        @Override public ContentDialogueIntent intent(){
                            return contentIntent;
                        }

                        @Override public ContentPlayer player(){
                            return ContentRuntimeAdapters.player(
                                worldPlayer
                            );
                        }
                    }
                );
        }

        pendingDialogueOutcome=
            contentTransition.outcomeKey();

        switch(contentTransition.kind()){
            case STAY:
                return DialogueSessionService
                    .Transition.stay();
            case MOVE:
                return DialogueSessionService
                    .Transition.move(
                        contentTransition.nextNodeKey()
                    );
            case END:
                return DialogueSessionService
                    .Transition.end();
            default:
                throw new AssertionError(
                    contentTransition.kind()
                );
        }
    }

    private static ContentDialogueIntent
        toContentIntent(
            DialogueSessionService.Intent intent
        )
    {
        switch(intent.kind){
            case CONTINUE:
                return ContentDialogueIntent
                    .continueIntent();
            case OPTION:
                return ContentDialogueIntent.option(
                    intent.optionIndex
                );
            case CLOSE:
                return ContentDialogueIntent
                    .closeIntent();
            default:
                throw new AssertionError(
                    intent.kind
                );
        }
    }

    private String takeDialogueOutcome(){
        String outcome=pendingDialogueOutcome;
        pendingDialogueOutcome=null;
        return outcome;
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
        designActive=false;
        activeScene=null;
        activeNpc=null;
    }

    static final class Result {
        final boolean handled;
        final String saveReason;
        final String logText;

        private Result(
            boolean handled,
            String saveReason,
            String logText
        ){
            this.handled=handled;
            this.saveReason=saveReason;
            this.logText=logText;
        }

        static Result notHandled(){
            return new Result(
                false,
                null,
                null
            );
        }

        static Result handled(
            String saveReason,
            String logText
        ){
            return new Result(
                true,
                saveReason,
                logText
            );
        }
    }
}
