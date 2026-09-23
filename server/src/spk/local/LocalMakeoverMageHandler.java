package spk.local;

import java.io.IOException;

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
    static final int NPC_ID=599;
    static final long APPROACH_TIMEOUT_MS=10_000L;

    static final int DESIGN_ROOT=3559;

    private static final String DIALOGUE_POLICY=
        "LOCAL_LAB_POLICY_MAKEOVER_MAGE_DIALOGUE";
    private static final String DIALOGUE_KEY=
        "dialogue:makeover-mage";
    private static final String INTRO_NODE=
        "node:intro";
    private static final String OPTIONS_NODE=
        "node:options";

    private final WorldPlayer worldPlayer;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final InteractionApproachResolver approach;
    private final String dialoguePlayerRef;
    private final DialogueSessionService dialogue;

    private boolean designActive;

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
        this.approach=
            new InteractionApproachResolver(
                this.movement
            );
        this.dialoguePlayerRef=
            "entity:"+
            this.worldPlayer.id();
        this.dialogue=
            new DialogueSessionService(
                DIALOGUE_POLICY,
                (player,definition,node,intent,before)->{
                    if(INTRO_NODE.equals(
                            node.nodeKey)&&
                       intent.kind==
                            DialogueSessionService
                                .IntentKind.CONTINUE)
                        return DialogueSessionService
                            .Transition.move(
                                OPTIONS_NODE
                            );

                    if(OPTIONS_NODE.equals(
                            node.nodeKey)&&
                       (intent.kind==
                            DialogueSessionService
                                .IntentKind.OPTION||
                        intent.kind==
                            DialogueSessionService
                                .IntentKind.CLOSE))
                        return DialogueSessionService
                            .Transition.end();

                    throw new IllegalStateException(
                        "unsupported Make-over dialogue transition node="+
                        node.nodeKey+
                        " intent="+
                        intent.kind
                    );
                }
            );

        this.dialogue.register(
            new DialogueSessionService
                .DialogueDefinition(
                    DIALOGUE_KEY,
                    INTRO_NODE,
                    java.util.Arrays.asList(
                        new DialogueSessionService
                            .NodeDefinition(
                                INTRO_NODE,
                                DialogueSessionService
                                    .InputMode.CONTINUE,
                                0,
                                false,
                                DIALOGUE_POLICY
                            ),
                        new DialogueSessionService
                            .NodeDefinition(
                                OPTIONS_NODE,
                                DialogueSessionService
                                    .InputMode.OPTIONS,
                                2,
                                true,
                                DIALOGUE_POLICY
                            )
                    ),
                    DIALOGUE_POLICY
                )
        );
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
            clearPending(false);
            openIntro(
                clicked,
                packets,
                tag,
                "ADJACENT_IMMEDIATE"
            );
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
                clearPending(false);
                openIntro(
                    target,
                    packets,
                    tag,
                    "SERVER_ARRIVAL"
                );
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

        StandardDialoguePresentationAdapter
            .openTwoOptions(
                packets,
                "Select an Option",
                java.util.Arrays.asList(
                    "I'd like to change my look.",
                    "Nevermind."
                )
            );

        DialogueSessionService.Snapshot after=
            dialogue.continueDialogue(
                dialoguePlayerRef
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

        int optionIndex=
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(widget);

        if(optionIndex==1){
            StandardDialoguePresentationAdapter
                .close(packets);
            packets.fixed(
                97,
                BootstrapPackets.interface97(
                    DESIGN_ROOT
                )
            );

            DialogueSessionService.Snapshot ended=
                dialogue.chooseOption(
                    dialoguePlayerRef,
                    1
                );

            if(ended.active)
                throw new IllegalStateException(
                    "Make-over option 1 did not end semantic dialogue"
                );

            designActive=true;

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DESIGN_OPEN root="+
                DESIGN_ROOT+
                " authority=EXACT_CURRENT_CLIENT_UI"
            );
            return true;
        }

        if(optionIndex==2){
            StandardDialoguePresentationAdapter
                .close(packets);

            DialogueSessionService.Snapshot ended=
                dialogue.chooseOption(
                    dialoguePlayerRef,
                    2
                );

            if(ended.active)
                throw new IllegalStateException(
                    "Make-over option 2 did not end semantic dialogue"
                );

            clearActive();

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_CANCEL reason=NEVERMIND"
            );
            return true;
        }

        return false;
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

        PlayerState player=
            worldPlayer.playerState();

        if(!player.setCharacterAppearance(
                request.gender(),
                request.kits(),
                request.colours()))
            throw new IllegalStateException(
                "validated character design was rejected"
            );

        StandardDialoguePresentationAdapter
            .close(packets);
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

    boolean pending(){
        return pendingScene!=null;
    }

    private void openIntro(
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag,
        String reason
    )throws IOException{
        StandardDialoguePresentationAdapter
            .openNamedNpc(
                packets,
                NPC_ID,
                "Make-over Mage",
                java.util.Collections.singletonList(
                    "How may I help you?"
                )
            );

        DialogueSessionService.Snapshot begun=
            dialogue.begin(
                dialoguePlayerRef,
                DIALOGUE_KEY
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
