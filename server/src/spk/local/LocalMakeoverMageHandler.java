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

    private enum Stage { NONE, INTRO, OPTIONS, DESIGN }

    private final WorldPlayer worldPlayer;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final InteractionApproachResolver approach;

    private Stage stage=Stage.NONE;

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

        if(stage!=Stage.NONE){
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
        boolean visible=stage!=Stage.NONE;

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
        boolean visible=stage!=Stage.NONE;

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
        if(stage!=Stage.INTRO||
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

        stage=Stage.OPTIONS;

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
        if(stage!=Stage.OPTIONS)return false;
        if(option==1)
            return handleWidget(
                CHANGE_LOOK_WIDGET,
                packets,
                tag
            );
        if(option==2)
            return handleWidget(
                NEVERMIND_WIDGET,
                packets,
                tag
            );
        return false;
    }

    boolean handleWidget(
        int widget,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(stage!=Stage.OPTIONS)return false;

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
            stage=Stage.DESIGN;

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

        if(stage!=Stage.DESIGN){
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
            stage!=Stage.NONE||
            pendingScene!=null;

        clearPending(
            pendingScene!=null
        );
        clearActive();
        return hadAnything;
    }

    boolean active(){
        return stage!=Stage.NONE;
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

        stage=Stage.INTRO;
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
        stage=Stage.NONE;
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
